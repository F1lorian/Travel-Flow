package com.sep.sep_backend.gmailimport;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.gmailimport.dto.CreatedEventDTO;
import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import com.sep.sep_backend.gmailimport.dto.GmailConnectionStatusDTO;
import com.sep.sep_backend.gmailimport.dto.GmailImportRequestDTO;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the Gmail import use case, tying together the OAuth, Gmail-read, parsing and
 * persistence layers. This is where the feature's business logic lives so the controller can stay
 * thin.
 */
@Service
public class GmailImportService {

  private static final Logger log = LoggerFactory.getLogger(GmailImportService.class);

  private final GmailOAuthService oAuthService;
  private final GmailApiClient gmailApiClient;
  private final EmailEventParser emailEventParser;
  private final EventRepository eventRepository;
  private final UserRepository userRepository;
  private final UserEventRepository userEventRepository;
  private final TripRepository tripRepository;
  private final int maxMessages;
  private final int scanConcurrency;
  private final int scanTimeoutSeconds;

  public GmailImportService(
      GmailOAuthService oAuthService,
      GmailApiClient gmailApiClient,
      EmailEventParser emailEventParser,
      EventRepository eventRepository,
      UserRepository userRepository,
      UserEventRepository userEventRepository,
      TripRepository tripRepository,
      @org.springframework.beans.factory.annotation.Value("${gmail.import.max-messages:15}")
          int maxMessages,
      @org.springframework.beans.factory.annotation.Value("${gmail.import.scan-concurrency:5}")
          int scanConcurrency,
      @org.springframework.beans.factory.annotation.Value("${gmail.import.scan-timeout-seconds:45}")
          int scanTimeoutSeconds) {
    this.oAuthService = oAuthService;
    this.gmailApiClient = gmailApiClient;
    this.emailEventParser = emailEventParser;
    this.eventRepository = eventRepository;
    this.userRepository = userRepository;
    this.userEventRepository = userEventRepository;
    this.tripRepository = tripRepository;
    this.maxMessages = maxMessages;
    this.scanConcurrency = Math.max(1, scanConcurrency);
    this.scanTimeoutSeconds = scanTimeoutSeconds;
  }

  /**
   * Returns whether the user has connected Gmail and which address is linked.
   *
   * @param userId local user id
   * @return connection status
   */
  public GmailConnectionStatusDTO getStatus(long userId) {
    boolean connected = oAuthService.isConnected(userId);
    return new GmailConnectionStatusDTO(
        connected, connected ? oAuthService.getConnectedEmail(userId).orElse(null) : null);
  }

  /**
   * Builds the Google consent URL for connecting Gmail.
   *
   * @param userId local user id (kept for future per-user state validation)
   * @return the authorization URL
   */
  public String buildAuthorizationUrl(long userId) {
    // A random state protects the redirect against CSRF; the callback itself is JWT-authenticated.
    String state = UUID.randomUUID().toString();
    return oAuthService.buildAuthorizationUrl(state);
  }

  /**
   * Completes the OAuth flow by exchanging the authorization code and storing the connection.
   *
   * @param userId local user id
   * @param code authorization code from Google
   * @return the connected Gmail address
   */
  public String connect(long userId, String code) {
    return oAuthService.connectWithCode(userId, code);
  }

  /**
   * Disconnects the user's Gmail account.
   *
   * @param userId local user id
   */
  public void disconnect(long userId) {
    oAuthService.disconnect(userId);
  }

  /**
   * Scans the user's most recent emails and returns the events that could be extracted, skipping
   * non-event mail and duplicates.
   *
   * @param userId local user id
   * @return extracted events awaiting user confirmation
   */
  public List<ExtractedEventDTO> scanRecentEmails(long userId) {
    String accessToken = oAuthService.getValidAccessToken(userId);
    List<String> messageIds = gmailApiClient.listRecentMessageIds(accessToken, maxMessages);
    if (messageIds.isEmpty()) {
      return List.of();
    }

    // Fetch+parse messages concurrently: each message makes slow Gmail and AI calls, so doing them
    // sequentially would blow past the reverse-proxy read timeout. A bounded deadline guarantees we
    // return a response in time even if some messages are still in flight.
    ExecutorService pool =
        Executors.newFixedThreadPool(Math.min(messageIds.size(), scanConcurrency));
    try {
      List<Callable<Optional<ExtractedEventDTO>>> tasks = new ArrayList<>();
      for (String messageId : messageIds) {
        tasks.add(() -> parseMessageSafely(accessToken, messageId));
      }
      List<Future<Optional<ExtractedEventDTO>>> futures =
          pool.invokeAll(tasks, scanTimeoutSeconds, TimeUnit.SECONDS);

      List<ExtractedEventDTO> events = new ArrayList<>();
      Set<String> seenSignatures = new HashSet<>();
      for (Future<Optional<ExtractedEventDTO>> future : futures) {
        Optional<ExtractedEventDTO> parsed = resolveResult(future);
        if (parsed.isEmpty()) {
          continue;
        }
        ExtractedEventDTO event = parsed.get();
        // De-duplicate emails describing the same event (e.g. an original and a forwarded copy).
        if (seenSignatures.add(signatureOf(event))) {
          events.add(event);
        }
      }
      return events;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new GmailApiException("Gmail scan was interrupted before it could finish");
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Unwraps a completed scan task. Messages that timed out, were cancelled by the deadline, or
   * failed to parse are skipped; only the actionable auth/rate-limit failures are re-thrown so the
   * controller can turn them into 401/429.
   */
  private Optional<ExtractedEventDTO> resolveResult(Future<Optional<ExtractedEventDTO>> future) {
    if (future.isCancelled()) {
      return Optional.empty();
    }
    try {
      return future.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (ExecutionException executionException) {
      Throwable cause = executionException.getCause();
      if (cause instanceof GmailRateLimitException rateLimit) {
        throw rateLimit;
      }
      if (cause instanceof GmailAuthException auth) {
        throw auth;
      }
      log.warn("Skipping Gmail message due to error: {}", cause == null ? "unknown" : cause);
      return Optional.empty();
    }
  }

  /**
   * Creates and persists a calendar event from the values the user confirmed.
   *
   * @param userId local user id
   * @param request confirmed event values
   * @return details of the created event
   */
  @Transactional
  public CreatedEventDTO createEvent(long userId, GmailImportRequestDTO request) {
    LocalDate date = request.getDate();
    if (date == null) {
      throw new IllegalArgumentException("A date is required to create an event");
    }
    String title =
        request.getTitle() == null || request.getTitle().isBlank()
            ? "Untitled event"
            : request.getTitle().trim();

    LocalDateTime start =
        date.atTime(request.getStartTime() != null ? request.getStartTime() : LocalTime.MIDNIGHT);
    LocalDateTime end = resolveEnd(date, start, request.getStartTime(), request.getEndTime());

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    Trip trip = findTrip(request.getTripId());

    Event event = new Event(title, start, end, request.getKosten(), trip);
    event.setDescription(request.getDescription());
    Event savedEvent = eventRepository.save(event);

    UserEvent participation = new UserEvent(savedEvent, user, user);
    participation.setStatus(UserEvent.InviteStatus.ACCEPTED);
    userEventRepository.save(participation);

    log.info("Created calendar event {} for user {} from Gmail import", savedEvent.getId(), userId);
    return new CreatedEventDTO(
        String.valueOf(savedEvent.getId()),
        savedEvent.getName(),
        savedEvent.getStartTime(),
        savedEvent.getEndTime(),
        "Event created successfully");
  }

  private Optional<ExtractedEventDTO> parseMessageSafely(String accessToken, String messageId) {
    try {
      byte[] raw = gmailApiClient.getRawMessage(accessToken, messageId);
      return emailEventParser.parse(raw, messageId);
    } catch (GmailRateLimitException | GmailAuthException propagate) {
      // These are actionable by the user; let the controller turn them into 429/401.
      throw propagate;
    } catch (RuntimeException exception) {
      // A single unreadable message must not abort the whole scan.
      log.warn("Skipping Gmail message {} due to error: {}", messageId, exception.getMessage());
      return Optional.empty();
    }
  }

  private LocalDateTime resolveEnd(
      LocalDate date, LocalDateTime start, LocalTime startTime, LocalTime endTime) {
    if (endTime != null) {
      LocalDateTime end = date.atTime(endTime);
      // Guard against end-before-start (e.g. an event that crosses midnight).
      return end.isBefore(start) ? end.plusDays(1) : end;
    }
    // No end time: an event with a start time lasts an hour, an all-day event lasts a day.
    return startTime != null ? start.plusHours(1) : start.plusDays(1);
  }

  private Trip findTrip(Long tripId) {
    if (tripId == null) {
      return null;
    }
    return tripRepository
        .findById(tripId)
        .orElseThrow(() -> new IllegalArgumentException("Trip not found for id " + tripId));
  }

  private String signatureOf(ExtractedEventDTO event) {
    return (event.getTitle() == null ? "" : event.getTitle().trim().toLowerCase())
        + "|"
        + (event.getDate() == null ? "" : event.getDate())
        + "|"
        + (event.getStartTime() == null ? "" : event.getStartTime());
  }
}
