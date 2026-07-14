package com.sep.sep_backend.eventHandling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.ThinkingConfig;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.eventHandling.dto.EventCalendarDTO;
import com.sep.sep_backend.eventHandling.dto.EventRequestDTO;
import com.sep.sep_backend.eventHandling.dto.EventResponseDTO;
import com.sep.sep_backend.exception.EventDetectionException;
import com.sep.sep_backend.exception.EventOutsideTripException;
import com.sep.sep_backend.exception.GeminiQuotaExhaustedException;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Service for creating, importing, listing, editing, and deleting events. */
@Service
public class EventService {
  private static final ZoneId MAIL_REFERENCE_ZONE = ZoneId.of("Europe/Berlin");

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventService.class);

  /** Placeholder name for an event whose real name could not be determined. */
  private static final String UNKNOWN_EVENT_NAME = "Unbekanntes Event";

  /**
   * How much of the mail body is sent to Gemini. Appointment details sit at the top of a mail,
   * while the plain-text part of a marketing mail runs to tens of thousands of characters — and
   * Gemini's latency grows with the input, so that tail costs seconds per message and adds nothing.
   */
  private static final int MAX_PROMPT_MAIL_CHARS = 4000;

  /**
   * Something that could be a date: a numeric date, a month or weekday name, or a relative day.
   *
   * <p>A mail without any of these cannot yield an importable event — the prompt forbids inventing
   * a date and {@link #mapDetectionResponse} drops an event whose {@code startDate} does not parse
   * — so asking Gemini about it is tokens spent to learn nothing. Newsletters, receipts and
   * notifications are dropped here instead of being packed into the batch prompt; an import where
   * <em>every</em> mail is dateless costs no Gemini request at all.
   *
   * <p>Deliberately broad: a false positive only costs the call that would have happened anyway,
   * while a false negative would silently drop a real event. Month abbreviations carry a word
   * boundary on both ends on purpose — a bare {@code mai} prefix would match "Mail" in every German
   * mail and make the filter useless.
   */
  private static final Pattern DATE_HINT =
      Pattern.compile(
          "\\d{1,2}\\s*[./-]\\s*\\d{1,2}" // 12.06. · 12/06/2026 · 2026-06-01
              + "|\\b(?:januar|februar|märz|maerz|april|mai|juni|juli|august|september|oktober"
              + "|november|dezember)\\b"
              + "|\\b(?:january|february|march|april|may|june|july|august|september|october"
              + "|november|december)\\b"
              + "|\\b(?:jan|feb|mrz|apr|jun|jul|aug|sep|sept|okt|oct|nov|dez|dec)\\b"
              + "|\\b(?:montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonnabend|sonntag)\\b"
              + "|\\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"
              + "|\\b(?:heute|morgen|übermorgen|uebermorgen|nächste[nrms]?|naechste[nrms]?"
              + "|today|tomorrow)\\b",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private final EventRepository eventRepository;
  private final TripRepository tripRepository;
  private final AusgabeService ausgabeService;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;
  private final UserEventRepository userEventRepository;
  private final EventInviteNotificationService notificationService;
  private final EmailNotificationService emailNotificationService;

  @Value("${gemini.api-key}")
  private String geminiApiKey;

  @Value("${gemini.model}")
  private String geminiModel;

  /**
   * Request timeout for a Gemini call. Without one, a hung call has no backstop — and an auto
   * import now rides on a single batched request, so one stuck call stalls the whole import.
   *
   * <p>Not tighter than this on purpose: with thinking disabled a healthy call answers in seconds,
   * so 30s only trips on a genuinely stuck request instead of turning a merely slow one into a
   * spurious failure. (It was briefly 15s, which did exactly that.)
   */
  @Value("${gemini.timeout-seconds:30}")
  private int geminiTimeoutSeconds = 30;

  /** How many times a failed Gemini detection call is retried before giving up. */
  @Value("${gemini.detection.max-retries:3}")
  private int detectionMaxRetries = 3;

  /** Base backoff for a transient (non-rate-limit) detection failure; doubles each attempt. */
  @Value("${gemini.detection.backoff-millis:500}")
  private long detectionBackoffMillis = 500;

  /**
   * Shared Gemini client, built on first use and reused afterwards.
   *
   * <p>Built lazily rather than in the constructor because {@link #geminiApiKey} and {@link
   * #geminiTimeoutSeconds} are injected after construction. Volatile with double-checked locking
   * because auto import calls this from several threads at once.
   *
   * <p>It used to be rebuilt for every call <em>and every retry</em>, which paid for a fresh
   * connection pool and TLS handshake per mail.
   */
  private volatile Client geminiClient;

  /**
   * Config shared by both Gemini calls. Thinking is switched off: this is a
   * classify-and-extract-JSON task at temperature 0.1, where the thinking pass adds little but
   * dominates the response time.
   *
   * <p>Careful when changing {@code gemini.model} (env {@code GEMINI_MODEL}): a budget of 0 is
   * valid for the 2.5 Flash models but <em>rejected</em> by 2.5 Pro, which requires a minimum
   * thinking budget.
   */
  private static final GenerateContentConfig GEMINI_CONFIG =
      GenerateContentConfig.builder()
          .responseMimeType("application/json")
          // Niedrige temperature == weniger Halluzination.
          .temperature(0.1f)
          .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
          .build();

  /**
   * Creates an event service with repository access and helper services.
   *
   * @param eventRepository repository used to store and load events
   * @param triprepository repository used to load trips for events
   * @param ausgabeService service for expense-related event handling
   * @param objectMapper JSON mapper used for event import payloads
   */
  @Autowired
  public EventService(
      EventRepository eventRepository,
      TripRepository triprepository,
      AusgabeService ausgabeService,
      UserRepository userRepository,
      ObjectMapper objectMapper,
      UserEventRepository userEventRepository,
      EventInviteNotificationService notificationService,
      EmailNotificationService emailNotificationService) {
    this.eventRepository = eventRepository;
    this.tripRepository = triprepository;
    this.ausgabeService = ausgabeService;
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
    this.userEventRepository = userEventRepository;
    this.notificationService = notificationService;
    this.emailNotificationService = emailNotificationService;
  }

  /**
   * Saves a manually created event for the trip referenced by the request.
   *
   * @param dto request data containing event values and the trip identifier
   * @param userId is the id of the creating user
   * @return response data of the saved event
   */
  public EventResponseDTO SaveEventToDB(EventRequestDTO dto, Long userId) {
    Trip matchingTrip = findTripByIdIfPresent(dto.getTripId());
    LocalDateTime eventStart = dto.getStart() != null ? dto.getStart() : LocalDateTime.now();
    LocalDateTime eventEnd = resolveEndTime(dto.getEnd(), eventStart);
    if (matchingTrip != null && isEventOutsideTrip(eventStart, eventEnd, matchingTrip)) {
      throw new EventOutsideTripException("Event liegt außerhalb des Trips");
    }

    User creator =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    Event newEvent = new Event(dto.getName(), eventStart, eventEnd, dto.getKosten(), matchingTrip);
    newEvent.setOwner(creator);
    Event savedEvent = eventRepository.save(newEvent);

    UserEvent creatorEntry = new UserEvent(savedEvent, creator, creator);
    creatorEntry.setStatus(UserEvent.InviteStatus.ACCEPTED);
    userEventRepository.save(creatorEntry);

    return mapToResponseDTO(savedEvent);
  }

  /**
   * Deletes the event with the given identifier.
   *
   * @param eventId identifier of the event to delete
   */
  @Transactional
  public void DeleteEventFromDB(Long eventId, Long userId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Event mit ID " + eventId + " wurde nicht gefunden"));

    requireOwner(event, userId);

    userEventRepository.deleteByEvent_Id(eventId);
    eventRepository.deleteById(eventId);
  }

  /**
   * Updates an existing event with the values provided in the request.
   *
   * @param eventId identifier of the event to update
   * @param dto request data containing the new event values
   * @return response data of the updated event
   */
  public EventResponseDTO EditEvent(Long eventId, EventRequestDTO dto, Long userId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Event mit ID " + eventId + " wurde nicht gefunden"));

    requireOwner(event, userId);

    if (dto.getName() != null) {
      event.setName(dto.getName());
    }
    // -1 bedeutet bei Edit-Requests: Kosten nicht ändern.
    if (dto.getKosten() != -1) {
      event.setKosten(dto.getKosten());
    }
    if (dto.getStart() != null) {
      event.setStartTime(dto.getStart());
    }
    if (dto.getEnd() != null) {
      event.setEndTime(dto.getEnd());
    }
    if (dto.getTripId() != null) {
      var newTrip =
          tripRepository.findById(dto.getTripId()).orElseThrow(() -> new RuntimeException());
      event.setTrip(newTrip);
    }
    eventRepository.save(event);

    return mapToResponseDTO(event);
  }

  /**
   * Saves an imported event after the client has parsed it and handled possible conflicts.
   *
   * @param dto parsed imported event values
   * @param tripId optional identifier of the trip the event belongs to
   * @param UserID is the id of the creating user
   * @return response data of the saved event
   */
  public EventResponseDTO SaveEventResponseToDB(EventResponseDTO dto, Long tripId, Long UserID) {
    Trip matchingTrip = findTripByIdIfPresent(tripId);
    String eventName =
        dto.getName() == null || dto.getName().isBlank() ? UNKNOWN_EVENT_NAME : dto.getName();
    LocalDateTime eventStart = dto.getStart() != null ? dto.getStart() : LocalDateTime.now();
    LocalDateTime eventEnd = resolveEndTime(dto.getEnd(), eventStart);
    if (matchingTrip != null && isEventOutsideTrip(eventStart, eventEnd, matchingTrip)) {
      throw new EventOutsideTripException("Event liegt außerhalb des Trips");
    }

    User creator =
        userRepository
            .findById(UserID)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    Event newEvent = new Event(eventName, eventStart, eventEnd, dto.getKosten(), matchingTrip);
    newEvent.setOwner(creator);
    Event savedEvent = eventRepository.save(newEvent);

    UserEvent creatorEntry = new UserEvent(savedEvent, creator, creator);
    creatorEntry.setStatus(UserEvent.InviteStatus.ACCEPTED);
    userEventRepository.save(creatorEntry);

    return mapToResponseDTO(savedEvent);
  }

  /**
   * Returns all stored events in the format used by the calendar view.
   *
   * @param userId is the id of the requesting user
   * @return list of calendar event DTOs
   */
  public List<EventCalendarDTO> getAllEventsFromDb(Long userId) {
    return userEventRepository.findAllByUserId(userId).stream()
        .map(
            event ->
                new EventCalendarDTO(
                    String.valueOf(event.getId()),
                    event.getName(),
                    event.getKosten(),
                    event.getStartTime(),
                    event.getEndTime(),
                    event.getTripId(),
                    event.getOwner() != null ? event.getOwner().getName() : null))
        .toList();
  }

  /**
   * Finds all saved events whose time interval overlaps the given interval.
   *
   * @param eventStart start date and time of the event being checked
   * @param eventEnd end date and time of the event being checked
   * @return response data for all conflicting events
   */
  public List<EventResponseDTO> conflictCheck(
      LocalDateTime eventStart, LocalDateTime eventEnd, Long userId) {
    List<Event> events = eventRepository.findConflictingEventsForUser(eventStart, eventEnd, userId);
    List<EventResponseDTO> result = new ArrayList<>();
    for (Event event : events) {
      EventResponseDTO dto = mapToResponseDTO(event);
      result.add(dto);
    }
    return result;
  }

  /**
   * sends an invitation to an event to a friend
   *
   * @param eventId
   * @param friendUserId
   * @param requestingUserId
   */
  public void inviteFriendToEvent(Long eventId, Long friendUserId, Long requestingUserId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

    requireOwner(event, requestingUserId);

    if (userEventRepository.existsByEvent_IdAndUser_Id(eventId, friendUserId)) {
      throw new IllegalArgumentException("User already invited");
    }

    User friend =
        userRepository
            .findById(friendUserId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    User requestingUser =
        userRepository
            .findById(requestingUserId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    UserEvent invite = new UserEvent(event, friend, requestingUser);
    userEventRepository.save(invite);

    notificationService.notifyNewInvite(friend, event, requestingUser);

    // The invited friend learns about the event even while logged out.
    emailNotificationService.sendBestEffort(
        friend.getEmail(),
        "You were invited to the event \"" + event.getName() + "\"",
        requestingUser.getName()
            + " invited you to the event \""
            + event.getName()
            + "\". Log in to SEP to see the details.");
  }

  /**
   * Removes a friend from an event without deleting the event or changing the friendship.
   *
   * @param eventId event from which the friend should be removed
   * @param friendUserId user to remove from the event
   * @param requestingUserId user requesting the removal
   */
  @Transactional
  public void removeFriendFromEvent(Long eventId, Long friendUserId, Long requestingUserId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found"));

    boolean isOwner = event.getOwner() != null && event.getOwner().getId() == requestingUserId;
    boolean isSelf = friendUserId.equals(requestingUserId);

    if (!isOwner && !isSelf) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the event owner can remove other participants");
    }

    if (!userEventRepository.existsByEvent_IdAndUser_Id(eventId, friendUserId)) {
      throw new IllegalArgumentException("User is not part of this event");
    }

    userEventRepository.deleteByEvent_IdAndUser_Id(eventId, friendUserId);
  }

  /**
   * responds to the invitation
   *
   * @param eventId
   * @param userId
   * @param status can be PENDING, ACCEPTED or DECLINED
   */
  public void respondToInvite(Long eventId, Long userId, UserEvent.InviteStatus status) {
    if (status == UserEvent.InviteStatus.PENDING) {
      throw new IllegalArgumentException("Cannot set status back to pending");
    }

    UserEvent userEvent =
        userEventRepository
            .findByEvent_IdAndUser_Id(eventId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Invite not found"));
    userEvent.setStatus(status);
    userEventRepository.save(userEvent);

    notificationService.notifyInviteAnswered(userEvent);

    // Tell the inviter how their invite was answered (skip self-created participant rows).
    User invitee = userEvent.getUser();
    User inviter = userEvent.getCreator();
    if (inviter != null && invitee != null && inviter.getId() != invitee.getId()) {
      String answer = status == UserEvent.InviteStatus.ACCEPTED ? "accepted" : "declined";
      emailNotificationService.sendBestEffort(
          inviter.getEmail(),
          "Your invite to \"" + userEvent.getEvent().getName() + "\" was " + answer,
          invitee.getName()
              + " "
              + answer
              + " your invitation to the event \""
              + userEvent.getEvent().getName()
              + "\".");
    }
  }

  /**
   * returns all pending invites for a user
   *
   * @param userId
   * @return the user's pending event invitations
   */
  public List<UserEvent> getIncomingEventInvites(Long userId) {
    return userEventRepository.findByUser_IdAndStatus(userId, UserEvent.InviteStatus.PENDING);
  }

  /**
   * @param eventId
   * @param requestingUserId
   * @return gives a list of every invitation sent by a user for a single event
   */
  public List<UserEvent> getInvitesSentByUser(Long eventId, Long requestingUserId) {
    return userEventRepository.findByEvent_Id(eventId).stream()
        .filter(ue -> ue.getCreator().getId() == requestingUserId)
        .toList();
  }

  public List<UserEvent> getParticipants(Long eventId) {
    return userEventRepository.findByEvent_Id(eventId);
  }

  /**
   * checks if the user is the creator of the event
   *
   * @param event
   * @param userId
   */
  private void requireOwner(Event event, Long userId) {
    if (event.getOwner() == null || event.getOwner().getId() != userId) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the event creator can perform this action");
    }
  }

  /**
   * Parses an uploaded email stream and extracts event data without saving it.
   *
   * @param inputStream email file content
   * @return response data containing the extracted event values, without an event id
   * @throws Exception if the email content cannot be read or parsed
   */
  public EventResponseDTO ParseMailFile(InputStream inputStream) throws Exception {
    Session session = Session.getDefaultInstance(new Properties());
    MimeMessage message = new MimeMessage(session, inputStream);

    // 1. Stufe: Strukturierte JSON-LD-Daten sind zuverlässiger als KI-Extraktion und werden
    // bevorzugt.
    Optional<EventResponseDTO> jsonLdEvent = findSchemaOrgEvent(message);
    if (jsonLdEvent.isPresent()) {
      return jsonLdEvent.get();
    }
    // 2. Stufe: Fehlt JSON-LD, wird der Mailtext als Fallback mit Gemini interpretiert.
    var body = extractText(message);
    return parseTextWithGemini(message.getSubject(), body, resolveSentDate(message));
  }

  /**
   * Parses a mail into an event, but only when it actually describes one. A structured JSON-LD
   * event always counts; otherwise Gemini decides whether the text contains a real appointment.
   * Returns an empty {@link Optional} for messages without an event so bulk importers can skip
   * them.
   *
   * @param inputStream raw RFC822 mail
   * @return the parsed event, or empty when the mail contains no event
   */
  public Optional<EventResponseDTO> parseMailFileIfEvent(InputStream inputStream) throws Exception {
    Session session = Session.getDefaultInstance(new Properties());
    MimeMessage message = new MimeMessage(session, inputStream);

    Optional<EventResponseDTO> event = findSchemaOrgEvent(message);
    if (event.isEmpty()) {
      var body = extractText(message);
      event = detectEventWithGemini(message.getSubject(), body, resolveSentDate(message));
    }

    // Ein Event ohne echten Namen ("Unbekanntes Event") wird beim automatischen Import nicht
    // übernommen — der Nutzer soll keine namenlosen Platzhalter im Kalender bekommen.
    return event.filter(this::hasRealName);
  }

  /** A mail reduced to the three things event detection needs. */
  public record MailContent(String subject, String body, LocalDateTime sentDate) {}

  /**
   * A mail read once and ready for detection: the schema.org event it already carries (if any),
   * plus the text to fall back on. Reading the MIME message is the expensive-ish local step, and
   * the stream can only be consumed once, so both come out of a single pass.
   */
  public record PreparedMail(Optional<EventResponseDTO> jsonLdEvent, MailContent content) {}

  /**
   * What detection concluded about one mail. Three outcomes, and they must stay distinct: an event,
   * a confident "no event", and "could not be checked" — collapsing the last two silently loses
   * mail.
   */
  public record DetectionResult(Optional<EventResponseDTO> event, boolean failed) {

    static DetectionResult of(EventResponseDTO event) {
      return new DetectionResult(Optional.of(event), false);
    }

    static DetectionResult noEvent() {
      return new DetectionResult(Optional.empty(), false);
    }

    /** Named to stay clear of the {@code failed()} accessor this record already generates. */
    static DetectionResult unchecked() {
      return new DetectionResult(Optional.empty(), true);
    }
  }

  /**
   * How many mails go into one Gemini request. The free tier's cap is counted in <em>requests</em>,
   * not tokens (20/day for {@code gemini-2.5-flash}), so folding a whole import into one request is
   * what makes the quota usable at all: a 10-mail import costs 1 request instead of 10.
   *
   * <p>Not unbounded, though — one oversized prompt is slower, likelier to be truncated, and takes
   * every mail in it down with it if the call fails.
   */
  private static final int MAX_MAILS_PER_GEMINI_CALL = 10;

  /**
   * Reads a raw mail once: its schema.org event if it has one, and its text either way.
   *
   * @param inputStream raw RFC822 mail
   * @return the mail's structured event (if present) and its content
   */
  public PreparedMail prepareMail(InputStream inputStream) throws Exception {
    MimeMessage message =
        new MimeMessage(Session.getDefaultInstance(new Properties()), inputStream);
    return new PreparedMail(
        // Filtered here, so whatever comes out is already importable and the caller carries no
        // rule.
        findSchemaOrgEvent(message).filter(this::hasRealName),
        new MailContent(message.getSubject(), extractText(message), resolveSentDate(message)));
  }

  /**
   * Classifies several mails using as few Gemini requests as possible, returning one result per
   * input <em>in input order</em>.
   *
   * <p>Two filters run before anything is sent: a mail with no date-like text cannot yield an
   * importable event (see {@link #DATE_HINT}), and an empty body has nothing to classify. Those are
   * answered locally as "no event" and never cost a request. Whatever survives is chunked into
   * {@link #MAX_MAILS_PER_GEMINI_CALL}-sized requests.
   *
   * <p>If a chunk's call fails, every mail in that chunk is reported {@code failed} — honest, since
   * we genuinely do not know what was in them. A 429 aborts the whole thing via {@link
   * GeminiQuotaExhaustedException}: no other chunk would fare any better.
   *
   * @param mails the mails to classify
   * @return one result per input mail, in the same order
   */
  public List<DetectionResult> detectEventsInBatch(List<MailContent> mails) {
    List<DetectionResult> results = new ArrayList<>(Collections.nCopies(mails.size(), null));

    // Indices of the mails that actually warrant a call; the rest are settled locally.
    List<Integer> pending = new ArrayList<>();
    for (int i = 0; i < mails.size(); i++) {
      MailContent mail = mails.get(i);
      String promptText = limitForPrompt(mail.body());
      if (promptText.isBlank() || !mentionsDate(mail.subject(), promptText)) {
        results.set(i, DetectionResult.noEvent());
      } else {
        pending.add(i);
      }
    }

    if (pending.isEmpty()) {
      return List.copyOf(results);
    }
    if (geminiApiKey == null || geminiApiKey.isBlank()) {
      throw new EventDetectionException("Gemini API key is not configured");
    }

    for (int from = 0; from < pending.size(); from += MAX_MAILS_PER_GEMINI_CALL) {
      List<Integer> chunk =
          pending.subList(from, Math.min(from + MAX_MAILS_PER_GEMINI_CALL, pending.size()));
      detectChunk(mails, chunk, results);
    }
    return List.copyOf(results);
  }

  /** Runs one Gemini request for {@code chunk} and writes its outcomes into {@code results}. */
  private void detectChunk(
      List<MailContent> mails, List<Integer> chunk, List<DetectionResult> results) {
    List<MailContent> batch = chunk.stream().map(mails::get).toList();
    try {
      JsonNode reply = callGeminiForJson(buildBatchPrompt(batch), batch.size() + " mails");
      // Map by the echoed "index", never by position: a reply that drops or reorders an entry would
      // otherwise attach one mail's event to another mail.
      for (int slot = 0; slot < chunk.size(); slot++) {
        results.set(chunk.get(slot), mapBatchEntry(reply, slot, batch.get(slot)));
      }
    } catch (GeminiQuotaExhaustedException quota) {
      throw quota;
    } catch (Exception exception) {
      log.warn("Batch event detection failed for {} mails", batch.size(), exception);
      chunk.forEach(index -> results.set(index, DetectionResult.unchecked()));
    }
  }

  /** Picks the entry for one mail out of the batch reply and maps it onto an event. */
  private DetectionResult mapBatchEntry(JsonNode reply, int slot, MailContent mail) {
    JsonNode entries = reply.isArray() ? reply : reply.path("events");
    for (JsonNode entry : entries) {
      if (entry.path("index").asInt(-1) == slot) {
        return mapDetectionResponse(entry, mail.subject())
            .filter(this::hasRealName)
            .map(DetectionResult::of)
            .orElseGet(DetectionResult::noEvent);
      }
    }
    // Gemini simply did not answer for this mail. That is "could not be checked", not "no event".
    log.warn("Batch reply had no entry for mail {} [{}]", slot, mail.subject());
    return DetectionResult.unchecked();
  }

  /** The schema.org event carried by the mail, if it has one. */
  private Optional<EventResponseDTO> findSchemaOrgEvent(MimeMessage message) throws Exception {
    return extractJsonLd(message).stream()
        .filter(this::isSchemaOrg)
        .filter(this::isSchemaEvent)
        .findFirst()
        .map(this::mapJsonLdToDTO);
  }

  /** True when the event has a real name; a blank or "Unbekanntes Event" placeholder is dropped. */
  private boolean hasRealName(EventResponseDTO event) {
    String name = event.getName();
    return name != null && !name.isBlank() && !name.equals(UNKNOWN_EVENT_NAME);
  }

  /**
   * Asks Gemini whether the mail text describes a real appointment and, if so, extracts it.
   *
   * <p>Returns empty for a mail that genuinely has no event (empty body, no date anywhere in it, or
   * a {@code hasEvent: false} verdict). Throws {@link EventDetectionException} when detection could
   * not run (no Gemini key, or the call keeps failing after retries) so the caller can report the
   * message as "could not be checked" rather than silently treating a failure as "no event".
   */
  private Optional<EventResponseDTO> detectEventWithGemini(
      String subject, String mailText, LocalDateTime sentDate) {
    if (mailText == null || mailText.isBlank()) {
      return Optional.empty();
    }
    if (geminiApiKey == null || geminiApiKey.isBlank()) {
      throw new EventDetectionException("Gemini API key is not configured");
    }

    String promptText = limitForPrompt(mailText);
    if (!mentionsDate(subject, promptText)) {
      return Optional.empty();
    }

    String prompt = buildGeminiEventDetectionPrompt(subject, mailText, sentDate);
    return mapDetectionResponse(callGeminiForJson(prompt, subject), subject);
  }

  /**
   * Calls Gemini and parses its JSON reply, retrying transient failures (timeout, 5xx) with
   * exponential backoff.
   *
   * <p>A 429 is the exception: it aborts at once with {@link GeminiQuotaExhaustedException} rather
   * than being retried. See {@link #rateLimitFailure} for why waiting it out cannot work.
   */
  private JsonNode callGeminiForJson(String prompt, String label) {
    int attempt = 0;
    while (true) {
      try {
        String raw = callGemini(prompt);
        log.info("Gemini event detection for [{}] -> {}", label, raw);
        return objectMapper.readTree(raw);
      } catch (Exception exception) {
        Optional<ApiException> rateLimit = rateLimitFailure(exception);
        if (rateLimit.isPresent()) {
          throw quotaExhausted(rateLimit.get());
        }
        if (attempt++ >= detectionMaxRetries) {
          log.warn("Gemini event detection failed for [{}] after {} attempts", label, attempt);
          throw new EventDetectionException("Gemini event detection failed", exception);
        }
        sleepBeforeRetry(attempt);
      }
    }
  }

  /**
   * Maps one Gemini detection response onto an event.
   *
   * <p>Empty means "this mail is not an event", for either of two reasons. Gemini said so via
   * {@code hasEvent} — the prompt defines that flag as the verdict, and ignoring it (as this code
   * once did) turns any newsletter carrying a date-like string into a calendar entry. Or the start
   * date is missing/unparseable, in which case the event cannot be placed anyway; importing it
   * would pin the mail to "now" and scatter dateless mails across the import day.
   */
  private Optional<EventResponseDTO> mapDetectionResponse(JsonNode node, String subject) {
    if (!node.path("hasEvent").asBoolean(false)) {
      return Optional.empty();
    }

    Optional<LocalDateTime> startOpt = tryParseDatetime(node.path("startDate").asText(""));
    if (startOpt.isEmpty()) {
      return Optional.empty();
    }

    LocalDateTime start = startOpt.get();
    LocalDateTime end =
        tryParseDatetime(node.path("endDate").asText(""))
            .orElseGet(() -> resolveEndTime(null, start));
    String name = node.path("name").asText(subject);
    int kosten = node.path("kosten").asInt(0);
    return Optional.of(new EventResponseDTO(name, kosten, start, end));
  }

  /**
   * The raw Gemini detection call. Kept as its own method so tests can stub the network away, and
   * so the client is built once per message rather than once per retry.
   */
  String callGemini(String prompt) {
    GenerateContentResponse response =
        geminiClient().models.generateContent(geminiModel, prompt, GEMINI_CONFIG);
    return response.text();
  }

  /** The shared Gemini client, built on first use; see {@link #geminiClient}. */
  private Client geminiClient() {
    Client existing = geminiClient;
    if (existing != null) {
      return existing;
    }
    synchronized (this) {
      if (geminiClient == null) {
        geminiClient =
            Client.builder()
                .apiKey(geminiApiKey)
                .httpOptions(HttpOptions.builder().timeout(geminiTimeoutSeconds * 1000).build())
                .build();
      }
      return geminiClient;
    }
  }

  /**
   * Waits before the next detection attempt: exponential backoff with jitter.
   *
   * <p>Only for genuinely transient failures (timeout, 5xx). A rate limit never gets here — see
   * {@link #rateLimitFailure}.
   */
  private void sleepBeforeRetry(int attempt) {
    long backoff = detectionBackoffMillis * (1L << (attempt - 1));
    long jittered = backoff + ThreadLocalRandom.current().nextLong(backoff / 4 + 1);

    try {
      Thread.sleep(jittered);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new EventDetectionException("Event detection interrupted", interrupted);
    }
  }

  /**
   * The HTTP 429 buried in a failed Gemini call, if that is what went wrong.
   *
   * <p>A 429 is <em>not</em> retried. The free tier's binding limit is per <em>day</em> (20
   * requests for {@code gemini-2.5-flash}), not per minute, so no backoff we could sit through
   * inside a request will clear it — the old 5s-and-double loop just spent ~35s per mail to fail
   * anyway, and every rejected attempt is one more request against the same cap. Fail immediately
   * instead, and let the caller tell the user the truth.
   */
  private Optional<ApiException> rateLimitFailure(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ApiException api && api.code() == 429) {
        return Optional.of(api);
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return Optional.empty();
  }

  /**
   * Turns a 429 into the exception that aborts the import.
   *
   * <p>The SDK keeps only {@code error.message} from the response and drops the structured {@code
   * details} (quota id, retry delay), so the raw message is logged as-is — it is the only place the
   * actual limit ("limit: 20") is visible for diagnosis.
   */
  private GeminiQuotaExhaustedException quotaExhausted(ApiException api) {
    log.warn("Gemini quota exhausted (HTTP 429): {}", api.getMessage());
    return new GeminiQuotaExhaustedException(
        "Das Gemini-Kontingent ist aufgebraucht. Der Free-Tier erlaubt nur wenige Anfragen pro Tag;"
            + " bitte später erneut versuchen.",
        api);
  }

  /**
   * Cuts the mail body down to the part that can plausibly carry the appointment; see {@link
   * #MAX_PROMPT_MAIL_CHARS}.
   */
  private static String limitForPrompt(String mailText) {
    if (mailText == null) {
      return "";
    }
    return mailText.length() <= MAX_PROMPT_MAIL_CHARS
        ? mailText
        : mailText.substring(0, MAX_PROMPT_MAIL_CHARS);
  }

  /**
   * True when subject or body names something that could be a date; see {@link #DATE_HINT}. The
   * body must already be trimmed to what the prompt will carry — scanning the full text would let a
   * date past the cut-off wave through a prompt that no longer contains it.
   */
  private static boolean mentionsDate(String subject, String promptText) {
    return DATE_HINT.matcher(subject == null ? "" : subject).find()
        || DATE_HINT.matcher(promptText).find();
  }

  /**
   * The batch counterpart of {@link #buildGeminiEventDetectionPrompt}: several mails, one request,
   * one array back.
   *
   * <p>Each mail is numbered and carries its own reference date (mails in one import were not
   * necessarily sent on the same day). The reply must echo that number back as {@code index} —
   * {@link #mapBatchEntry} matches on it rather than on position, so a dropped or reordered entry
   * cannot silently shift every event onto the wrong mail.
   */
  private String buildBatchPrompt(List<MailContent> mails) {
    StringBuilder mailBlocks = new StringBuilder();
    for (int i = 0; i < mails.size(); i++) {
      MailContent mail = mails.get(i);
      mailBlocks
          .append("--- Mail %d ---%n".formatted(i))
          .append("index: %d%n".formatted(i))
          .append(
              "Referenzdatum (Sendedatum): %s%n".formatted(formatReferenceDate(mail.sentDate())))
          .append("Betreff: %s%n".formatted(mail.subject() == null ? "" : mail.subject()))
          .append("Mailtext:%n%s%n%n".formatted(limitForPrompt(mail.body())));
    }

    return """
            Du bekommst mehrere E-Mails. Pruefe JEDE Mail einzeln und unabhaengig von den anderen.

            Entscheide pro Mail, ob sie einen konkreten, echten Termin beschreibt (z. B. Buchung,
            Reservierung, Einladung, Ticket). Werbung, Newsletter, Rechnungen und Benachrichtigungen
            sind KEIN Termin.

            Regeln:
            - Erfinde niemals ein Datum. Wenn kein Datum in der Mail steht, ist hasEvent false.
            - Relative Angaben ("morgen", "naechsten Freitag") beziehen sich auf das Referenzdatum
              der jeweiligen Mail.
            - Datumsformat: yyyy-MM-dd'T'HH:mm:ss
            - kosten ist eine ganze Zahl in Euro, 0 wenn nichts genannt wird.

            Antworte AUSSCHLIESSLICH mit einem JSON-Array — genau ein Objekt pro Mail, und gib fuer
            JEDE Mail den passenden index zurueck:
            [
              {"index": 0, "hasEvent": true, "name": "...", "kosten": 0,
               "startDate": "...", "endDate": "..."},
              {"index": 1, "hasEvent": false}
            ]

            %s"""
        .formatted(mailBlocks);
  }

  private String buildGeminiEventDetectionPrompt(
      String subject, String mailText, LocalDateTime sentDate) {
    return """
                Prüfe, ob diese E-Mail eine konkrete Veranstaltung oder einen Termin beschreibt
                (z. B. Meeting, Konzert, Reservierung, Arzttermin, Buchung mit Datum/Uhrzeit).
                Werbung, Newsletter, Rechnungen ohne Termin und reine Benachrichtigungen sind KEIN Event.

                Antworte nur mit JSON:
                {
                  "hasEvent": true,
                  "name": "Eventname",
                  "kosten": 0,
                  "startDate": "2026-06-01T20:00:00",
                  "endDate": "2026-06-01T22:00:00"
                }

                Regeln:
                - hasEvent ist nur true, wenn ein echter Termin mit erkennbarem Datum vorhanden ist.
                - Falls hasEvent false ist, sind die übrigen Felder egal.
                - kosten ist eine ganze Zahl.
                - startDate ist yyyy-MM-ddTHH:mm:ss (ISO 8601), aus dem Mailtext entnommen.
                - endDate ist yyyy-MM-ddTHH:mm:ss.
                - Das Bezugsdatum (Sende-Datum der E-Mail) ist %s. Relative Angaben wie "morgen",
                  "übermorgen", "nächsten Montag" oder Wochentage beziehen sich auf dieses
                  Bezugsdatum, NICHT auf das heutige Datum.
                - Nutze immer das im Text genannte konkrete Datum, wenn vorhanden.
                - Falls kein Eventname gefunden wird: Betreff nutzen.
                - Erfinde keine Daten.

                Betreff:
                %s

                Mailtext:
                %s
                """
        .formatted(formatReferenceDate(sentDate), subject, limitForPrompt(mailText));
  }

  /**
   * Connects to the user's IMAP mailbox and returns previews (UID, subject, sender, date) of the
   * newest messages, newest first, so the user can pick which mail to import as an event.
   *
   * @param request IMAP connection details plus optional folder and result limit
   * @return previews of the most recent messages, or an empty list for an empty folder
   * @throws Exception when the IMAP connection, authentication, or folder access fails
   */
  public List<ImapMailPreviewDTO> getImapMailPreviews(ImapMailboxRequestDTO request)
      throws Exception {
    Store store = null;
    Folder folder = null;
    try {
      store = connectToImap(request.host(), request.port(), request.username(), request.password());
      folder = openFolder(store, request.folder());

      int messageCount = folder.getMessageCount();
      if (messageCount == 0) {
        return List.of();
      }

      int limit = resolveImapLimit(request.limit());
      int start = Math.max(1, messageCount - limit + 1);
      Message[] messages = folder.getMessages(start, messageCount);
      UIDFolder uidFolder = getUidFolder(folder);

      return Arrays.stream(messages)
          .sorted(Comparator.comparingInt(Message::getMessageNumber).reversed())
          .map(message -> toImapPreview(message, uidFolder))
          .toList();
    } finally {
      closeFolder(folder);
      closeStore(store);
    }
  }

  /**
   * Fetches a single mail by UID from the user's IMAP mailbox and parses it into event data using
   * the same pipeline as uploaded mail files (JSON-LD first, Gemini text extraction as fallback).
   *
   * @param request IMAP connection details plus the UID of the mail to import
   * @return the event details extracted from the mail
   * @throws EntityNotFoundException when no mail with the given UID exists in the folder
   * @throws Exception when the IMAP connection or mail parsing fails
   */
  public EventResponseDTO parseImapMessage(ImapMailSelectionRequestDTO request) throws Exception {
    Store store = null;
    Folder folder = null;
    try {
      store = connectToImap(request.host(), request.port(), request.username(), request.password());
      folder = openFolder(store, request.folder());
      UIDFolder uidFolder = getUidFolder(folder);
      Message message = uidFolder.getMessageByUID(request.uid());

      if (message == null) {
        throw new EntityNotFoundException("IMAP mail with UID " + request.uid() + " not found");
      }

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      message.writeTo(outputStream);
      return ParseMailFile(new ByteArrayInputStream(outputStream.toByteArray()));
    } finally {
      closeFolder(folder);
      closeStore(store);
    }
  }

  private Store connectToImap(String host, Integer port, String username, String password)
      throws MessagingException {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("IMAP host is required");
    }
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("IMAP username is required");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("IMAP password is required");
    }

    int resolvedPort = port == null ? 993 : port;
    Properties properties = new Properties();
    properties.put("mail.store.protocol", "imaps");
    properties.put("mail.imaps.ssl.enable", "true");
    properties.put("mail.imaps.connectiontimeout", "10000");
    properties.put("mail.imaps.timeout", "10000");

    Session session = Session.getInstance(properties);
    Store store = session.getStore("imaps");
    store.connect(host, resolvedPort, username, password);
    return store;
  }

  private Folder openFolder(Store store, String requestedFolder) throws MessagingException {
    String folderName =
        requestedFolder == null || requestedFolder.isBlank() ? "INBOX" : requestedFolder;
    Folder folder = store.getFolder(folderName);
    folder.open(Folder.READ_ONLY);
    return folder;
  }

  private UIDFolder getUidFolder(Folder folder) {
    if (!(folder instanceof UIDFolder uidFolder)) {
      throw new IllegalStateException("IMAP server does not support message UIDs");
    }
    return uidFolder;
  }

  private int resolveImapLimit(Integer requestedLimit) {
    if (requestedLimit == null || requestedLimit <= 0) {
      return 50;
    }
    return Math.min(requestedLimit, 50);
  }

  private ImapMailPreviewDTO toImapPreview(Message message, UIDFolder uidFolder) {
    try {
      return new ImapMailPreviewDTO(
          uidFolder.getUID(message),
          nullToBlank(message.getSubject()),
          formatAddresses(message.getFrom()),
          formatDate(message.getSentDate()),
          formatDate(message.getReceivedDate()),
          buildSnippet(extractText(message)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not read IMAP mail preview", exception);
    }
  }

  private String formatAddresses(Address[] addresses) {
    if (addresses == null || addresses.length == 0) {
      return "";
    }
    return Arrays.stream(addresses)
        .map(Address::toString)
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }

  private String formatDate(Date date) {
    if (date == null) {
      return null;
    }
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        date.toInstant().atZone(ZoneId.systemDefault()));
  }

  private String buildSnippet(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }

  private void closeFolder(Folder folder) throws MessagingException {
    if (folder != null && folder.isOpen()) {
      folder.close(false);
    }
  }

  private void closeStore(Store store) throws MessagingException {
    if (store != null && store.isConnected()) {
      store.close();
    }
  }

  private EventResponseDTO parseTextWithGemini(
      String subject, String mailText, LocalDateTime sentDate) {
    if (geminiApiKey == null || geminiApiKey.isBlank() || mailText == null || mailText.isBlank()) {
      return mapTextToDTO(mailText);
    }

    try {
      // Über geminiClient(), damit auch dieser Aufruf den Timeout bekommt — vorher hatte er gar
      // keinen und konnte unbegrenzt hängen.
      GenerateContentResponse response =
          geminiClient()
              .models
              .generateContent(
                  geminiModel, buildGeminiPrompt(subject, mailText, sentDate), GEMINI_CONFIG);

      JsonNode node = objectMapper.readTree(response.text());

      String name = node.path("name").asText(subject);
      int kosten = node.path("kosten").asInt(0);
      LocalDateTime start = parseDatetime(node.path("startDate").asText());
      LocalDateTime end = parseEndDatetime(node.path("endDate").asText(), start);

      return new EventResponseDTO(name, kosten, start, end);
    } catch (Exception exception) {
      // Ein fehlgeschlagener Gemini-Aufruf (z. B. 429 Quota) darf NICHT als Event mit dem heutigen
      // Datum "erfunden" werden — sonst landet jeder Import auf heute. Der Fehler wird
      // weitergereicht, damit der Import als fehlgeschlagen gemeldet statt falsch gespeichert wird.
      log.warn("Gemini text extraction failed for [{}]", subject, exception);
      throw new EventDetectionException("Gemini event extraction failed", exception);
    }
  }

  private String buildGeminiPrompt(String subject, String mailText, LocalDateTime sentDate) {
    return """
                Lies diese E-Mail und extrahiere ein Event.

                Referenzdatum: %s
                Zeitzone: Europe/Berlin
                Relative Datumsangaben wie "heute", "morgen" oder "nächste Woche"
                beziehen sich auf das Referenzdatum.

                Antworte nur mit JSON:
                {
                  "name": "Eventname",
                  "kosten": 0,
                  "startDate": "2026-06-01T20:00:00",
                  "endDate": "2026-06-01T22:00:00"
                }

                Regeln:
                - kosten ist eine ganze Zahl.
                - startDate ist yyyy-MM-ddTHH:mm:ss (ISO 8601), aus dem Mailtext entnommen.
                - endDate ist yyyy-MM-ddTHH:mm:ss.
                - Das Bezugsdatum (Sende-Datum der E-Mail) ist %s. Relative Angaben wie "morgen",
                  "übermorgen", "nächsten Montag" oder Wochentage beziehen sich auf dieses
                  Bezugsdatum, NICHT auf das heutige Datum.
                - Nutze immer das im Text genannte konkrete Datum, wenn vorhanden.
                - Falls kein Preis gefunden wird: kosten 0.
                - Falls kein Eventname gefunden wird: Betreff nutzen.
                - Erfinde keine Daten außer fehlender Uhrzeit nach obiger Regel.

                Betreff:
                %s

                Mailtext:
                %s
                """
        .formatted(formatReferenceDate(sentDate), subject, limitForPrompt(mailText));
  }

  private boolean isSchemaOrg(JsonNode node) {
    JsonNode context = node.get("@context");

    if (context == null || context.isNull()) {
      return false;
    }

    if (context.isTextual() && context.asText().contains("schema.org")) {
      return true;
    }

    // Schema.org-Kontexte können als Liste statt als einzelner Textwert geliefert werden.
    if (context.isArray()) {
      for (JsonNode child : context) {
        if (child.isTextual() && child.asText().contains("schema.org")) {
          return true;
        }
      }
    }

    // Manche Mails liefern den Kontext als Objekt mit Namespace-Zuordnungen.
    if (context.isObject() && context.toString().contains("schema.org")) {
      return true;
    }

    return false;
  }

  private boolean isSchemaEvent(JsonNode node) {
    JsonNode type = node.get("@type");

    if (isEventType(type)) {
      return true;
    }

    // Event-Typen können in @graph-Elementen statt direkt am Wurzelknoten stehen.
    if (node.get("@graph") != null && node.get("@graph").isArray()) {
      var graph = node.get("@graph");
      for (JsonNode child : graph) {
        if (isEventType(child.get("@type"))) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isEventType(JsonNode type) {
    if (type == null || type.isNull()) {
      return false;
    }

    // Schema.org-Events kommen je nach Anbieter mit unterschiedlichen Typ-Schreibweisen.
    if (type.isTextual()) {
      String typeValue = type.asText();
      return typeValue.equals("Event")
          || typeValue.equals("schema:Event")
          || typeValue.equals("https://schema.org/Event")
          || typeValue.equals("http://schema.org/Event");
    }

    // @type kann auch als Array geliefert werden.
    if (type.isArray()) {
      for (JsonNode child : type) {
        if (isEventType(child)) {
          return true;
        }
      }
    }

    return false;
  }

  private EventResponseDTO mapTextToDTO(String text) {
    var name = text == null || text.isBlank() ? UNKNOWN_EVENT_NAME : text;
    LocalDateTime start = LocalDateTime.now();
    return new EventResponseDTO(name, 0, start, resolveEndTime(null, start));
  }

  private EventResponseDTO mapJsonLdToDTO(JsonNode jsonLdBlocks) {
    var eventNode = jsonLdBlocks;

    // JSON-LD kann das eigentliche Event in einem @graph-Knoten kapseln.
    if (!isEventType(eventNode.get("@type")) && eventNode.path("@graph").isArray()) {
      for (JsonNode child : eventNode.path("@graph")) {
        if (isEventType(child.get("@type"))) {
          eventNode = child;
          break;
        }
      }
    }

    var price = eventNode.path("offers").path("price").asInt();
    var start = parseDatetime(eventNode.path("startDate").asText());
    var end = parseEndDatetime(eventNode.path("endDate").asText(), start);
    var name = eventNode.path("name").asText(UNKNOWN_EVENT_NAME);
    return new EventResponseDTO(name, price, start, end);
  }

  private EventResponseDTO mapToResponseDTO(Event event) {
    return new EventResponseDTO(
        String.valueOf(event.getId()),
        event.getName(),
        event.getKosten(),
        event.getStartTime(),
        event.getEndTime(),
        event.getTripId());
  }

  private String extractText(Part part) throws Exception {
    if (part.isMimeType("text/plain")) {
      return part.getContent().toString();
    }
    if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();
      for (int i = 0; i < multipart.getCount(); i++) {
        String text = extractText(multipart.getBodyPart(i));
        if (text != null && !text.isBlank()) {
          return text;
        }
      }
    }
    return null;
  }

  /** yyyy-MM-ddTHH:mm without seconds, one of the shapes Gemini and JSON-LD sometimes emit. */
  private static final DateTimeFormatter LOCAL_NO_SECONDS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  /**
   * Leniently parses the various datetime shapes Gemini and JSON-LD return, returning empty when
   * the value is blank or in no recognized format. Accepts an offset/zone (trailing {@code Z} or
   * {@code +02:00}), the canonical {@code yyyy-MM-ddTHH:mm:ss}, the same without seconds, and a
   * date-only value (mapped to start of day). Never throws, so a malformed date no longer aborts
   * detection.
   */
  private static Optional<LocalDateTime> tryParseDatetime(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String trimmed = value.trim();
    try {
      return Optional.of(OffsetDateTime.parse(trimmed).toLocalDateTime());
    } catch (DateTimeParseException ignored) {
      // not an offset/zoned value
    }
    try {
      return Optional.of(LocalDateTime.parse(trimmed));
    } catch (DateTimeParseException ignored) {
      // not canonical local datetime
    }
    try {
      return Optional.of(LocalDateTime.parse(trimmed, LOCAL_NO_SECONDS));
    } catch (DateTimeParseException ignored) {
      // not a seconds-less local datetime
    }
    try {
      return Optional.of(LocalDate.parse(trimmed).atStartOfDay());
    } catch (DateTimeParseException ignored) {
      // not a date-only value either
    }
    return Optional.empty();
  }

  private LocalDateTime parseDatetime(String jsonDatetime) {
    return tryParseDatetime(jsonDatetime).orElseGet(LocalDateTime::now);
  }

  private LocalDateTime parseEndDatetime(String jsonDatetime, LocalDateTime start) {
    return tryParseDatetime(jsonDatetime).orElseGet(() -> resolveEndTime(null, start));
  }

  private LocalDateTime resolveEndTime(LocalDateTime end, LocalDateTime start) {
    return end != null ? end : start.plusDays(1);
  }

  /**
   * The mail's sent date, used as the reference point for relative date phrases ("morgen", weekday
   * names) in the AI prompt. Falls back to now only when the header is absent, so relative
   * resolution has an anchor even for hand-crafted test mails.
   */
  private LocalDateTime resolveSentDate(MimeMessage message) throws MessagingException {
    Date sentDate = message.getSentDate();
    if (sentDate == null) {
      return LocalDateTime.now();
    }
    return LocalDateTime.ofInstant(sentDate.toInstant(), ZoneId.systemDefault());
  }

  private String formatReferenceDate(LocalDateTime sentDate) {
    LocalDateTime reference = sentDate != null ? sentDate : LocalDateTime.now();
    // yyyy-MM-dd plus the weekday so "nächsten Montag" and similar resolve correctly.
    return reference.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        + " ("
        + reference.getDayOfWeek()
        + ")";
  }

  private boolean isEventOutsideTrip(LocalDateTime eventStart, LocalDateTime eventEnd, Trip trip) {
    if (trip.getStartDate() == null || trip.getEndDate() == null) {
      return false;
    }

    boolean startsBeforeTrip = eventStart.isBefore(trip.getStartDate().atStartOfDay());
    boolean endsAfterTrip = eventEnd.isAfter(trip.getEndDate().plusDays(1).atStartOfDay());
    return startsBeforeTrip || endsAfterTrip;
  }

  private Trip findTripByIdIfPresent(Long tripId) {
    if (tripId == null) {
      return null;
    }

    return tripRepository
        .findById(tripId)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Zu dieser trip Id findet sich kein eintrag in der Datenbank"));
  }

  private List<JsonNode> extractJsonLd(Part part) throws Exception {
    List<JsonNode> result = new ArrayList<>();

    if (part.isMimeType("application/ld+json")) {
      String json = new String(part.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      result.add(objectMapper.readTree(json));
      return result;
    }

    if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();

      for (int i = 0; i < multipart.getCount(); i++) {
        result.addAll(extractJsonLd(multipart.getBodyPart(i)));
      }
    }

    return result;
  }
}
