package com.sep.sep_backend.eventHandling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Service for creating, importing, listing, editing, and deleting events. */
@Service
public class EventService {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventService.class);

  /** Placeholder name for an event whose real name could not be determined. */
  private static final String UNKNOWN_EVENT_NAME = "Unbekanntes Event";

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

  /** Request timeout for a Gemini call. Without one, a hung call has no backstop. */
  @Value("${gemini.timeout-seconds:60}")
  private int geminiTimeoutSeconds = 60;

  /** How many times a failed Gemini detection call is retried before giving up. */
  @Value("${gemini.detection.max-retries:3}")
  private int detectionMaxRetries = 3;

  /** Base backoff for a transient (non-rate-limit) detection failure; doubles each attempt. */
  @Value("${gemini.detection.backoff-millis:500}")
  private long detectionBackoffMillis = 500;

  /**
   * Base backoff for an HTTP 429. Gemini's quota resets per minute, not per second, so a sub-second
   * retry just burns an attempt against the same exhausted quota and fails the whole batch.
   */
  @Value("${gemini.detection.rate-limit-backoff-millis:5000}")
  private long detectionRateLimitBackoffMillis = 5000;

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
    if (!eventRepository.existsById(eventId)) {
      throw new IllegalArgumentException("Event not found");
    }

    boolean requesterIsParticipant =
        userEventRepository.existsByEvent_IdAndUser_Id(eventId, requestingUserId);
    if (!requesterIsParticipant) {
      throw new IllegalArgumentException("Only participants can remove friends from events");
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
    var jsonLdBlocks = extractJsonLd(message);
    // 1. Stufe: Strukturierte JSON-LD-Daten sind zuverlässiger als KI-Extraktion und werden
    // bevorzugt.
    if (!jsonLdBlocks.isEmpty()) {
      var jsonEvent =
          jsonLdBlocks.stream()
              .filter(node -> isSchemaOrg(node))
              .filter(node -> isSchemaEvent(node))
              .findFirst()
              .orElse(null);

      if (jsonEvent != null) {
        return mapJsonLdToDTO(jsonEvent);
      }
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

    var jsonLdBlocks = extractJsonLd(message);
    Optional<EventResponseDTO> event = Optional.empty();
    if (!jsonLdBlocks.isEmpty()) {
      var jsonEvent =
          jsonLdBlocks.stream()
              .filter(this::isSchemaOrg)
              .filter(this::isSchemaEvent)
              .findFirst()
              .orElse(null);
      if (jsonEvent != null) {
        event = Optional.of(mapJsonLdToDTO(jsonEvent));
      }
    }

    if (event.isEmpty()) {
      var body = extractText(message);
      event = detectEventWithGemini(message.getSubject(), body, resolveSentDate(message));
    }

    // Ein Event ohne echten Namen ("Unbekanntes Event") wird beim automatischen Import nicht
    // übernommen — der Nutzer soll keine namenlosen Platzhalter im Kalender bekommen.
    return event.filter(this::hasRealName);
  }

  /** True when the event has a real name; a blank or "Unbekanntes Event" placeholder is dropped. */
  private boolean hasRealName(EventResponseDTO event) {
    String name = event.getName();
    return name != null && !name.isBlank() && !name.equals(UNKNOWN_EVENT_NAME);
  }

  /**
   * Asks Gemini whether the mail text describes a real appointment and, if so, extracts it.
   *
   * <p>Returns empty for a mail that genuinely has no event (empty body or a {@code hasEvent:
   * false} verdict). Throws {@link EventDetectionException} when detection could not run (no Gemini
   * key, or the call keeps failing after retries) so the caller can report the message as "could
   * not be checked" rather than silently treating a failure as "no event".
   */
  private Optional<EventResponseDTO> detectEventWithGemini(
      String subject, String mailText, LocalDateTime sentDate) {
    if (mailText == null || mailText.isBlank()) {
      return Optional.empty();
    }
    if (geminiApiKey == null || geminiApiKey.isBlank()) {
      throw new EventDetectionException("Gemini API key is not configured");
    }

    String prompt = buildGeminiEventDetectionPrompt(subject, mailText, sentDate);

    int attempt = 0;
    while (true) {
      try {
        String raw = callGemini(prompt);
        log.info("Gmail event detection for [{}] -> {}", subject, raw);
        return mapDetectionResponse(objectMapper.readTree(raw), subject);
      } catch (Exception exception) {
        if (attempt++ >= detectionMaxRetries) {
          log.warn("Gmail event detection failed for [{}] after {} attempts", subject, attempt);
          throw new EventDetectionException("Gemini event detection failed", exception);
        }
        sleepBeforeRetry(exception, attempt, subject);
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
    Client client =
        Client.builder()
            .apiKey(geminiApiKey)
            .httpOptions(HttpOptions.builder().timeout(geminiTimeoutSeconds * 1000).build())
            .build();

    GenerateContentConfig config =
        GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .temperature(0.1f)
            .build();

    GenerateContentResponse response = client.models.generateContent(geminiModel, prompt, config);
    return response.text();
  }

  /**
   * Waits before the next detection attempt: exponential backoff with jitter, and a far longer base
   * for a rate limit, because Gemini's quota resets per minute and a sub-second retry would just
   * hit the same exhausted quota again.
   */
  private void sleepBeforeRetry(Exception failure, int attempt, String subject) {
    boolean rateLimited = isRateLimited(failure);
    long base = rateLimited ? detectionRateLimitBackoffMillis : detectionBackoffMillis;
    long backoff = base * (1L << (attempt - 1));
    long jittered = backoff + ThreadLocalRandom.current().nextLong(backoff / 4 + 1);

    if (rateLimited) {
      log.warn("Gemini rate-limited detection for [{}]; retrying in {} ms", subject, jittered);
    }

    try {
      Thread.sleep(jittered);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new EventDetectionException("Event detection interrupted", interrupted);
    }
  }

  /** True when Gemini refused the call because the quota is exhausted (HTTP 429). */
  private boolean isRateLimited(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ApiException api && api.code() == 429) {
        return true;
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return false;
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
        .formatted(formatReferenceDate(sentDate), subject, mailText);
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
      Client client = Client.builder().apiKey(geminiApiKey).build();

      GenerateContentConfig config =
          GenerateContentConfig.builder()
              .responseMimeType("application/json")
              // Niedrige temperature == weniger hallozination.
              .temperature(0.1f)
              .build();

      GenerateContentResponse response =
          client.models.generateContent(
              geminiModel, buildGeminiPrompt(subject, mailText, sentDate), config);

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
                - Erfinde keine Daten.

                Betreff:
                %s

                Mailtext:
                %s
                """
        .formatted(formatReferenceDate(sentDate), subject, mailText);
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
