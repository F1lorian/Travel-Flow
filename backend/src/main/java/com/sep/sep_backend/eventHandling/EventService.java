package com.sep.sep_backend.eventHandling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.exception.EventOutsideTripException;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for creating, importing, listing, editing, and deleting events. */
@Service
public class EventService {

  private final EventRepository eventRepository;
  private final TripRepository tripRepository;
  private final AusgabeService ausgabeService;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;
  private final UserEventRepository userEventRepository;

  @Value("${gemini.api-key}")
  private String geminiApiKey;

  @Value("${gemini.model}")
  private String geminiModel;

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
      UserEventRepository userEventRepository) {
    this.eventRepository = eventRepository;
    this.tripRepository = triprepository;
    this.ausgabeService = ausgabeService;
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
    this.userEventRepository = userEventRepository;
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
    Event newEvent = new Event(dto.getName(), eventStart, eventEnd, dto.getKosten(), matchingTrip);
    Event savedEvent = eventRepository.save(newEvent);

    User creator =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    UserEvent creatorEntry = new UserEvent(savedEvent, creator, creator);
    creatorEntry.setStatus(UserEvent.InviteStatus.ACCEPTED);
    userEventRepository.save(creatorEntry);

    return mapToResponseDTO(savedEvent);
  }

  /**
   * Deletes the event with the given identifier.
   *
   * @param EventID identifier of the event to delete
   */
  @Transactional
  public void DeleteEventFromDB(Long EventID) {
    if (!eventRepository.existsById(EventID)) {
      throw new EntityNotFoundException("Event mit ID " + EventID + " wurde nicht gefunden");
    }

    userEventRepository.deleteByEvent_Id(EventID);
    eventRepository.deleteById(EventID);
  }

  /**
   * Updates an existing event with the values provided in the request.
   *
   * @param eventId identifier of the event to update
   * @param dto request data containing the new event values
   * @return response data of the updated event
   */
  public EventResponseDTO EditEvent(Long eventId, EventRequestDTO dto) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Event mit ID " + eventId + " wurde nicht gefunden"));

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
        dto.getName() == null || dto.getName().isBlank() ? "Unbekanntes Event" : dto.getName();
    LocalDateTime eventStart = dto.getStart() != null ? dto.getStart() : LocalDateTime.now();
    LocalDateTime eventEnd = resolveEndTime(dto.getEnd(), eventStart);
    if (matchingTrip != null && isEventOutsideTrip(eventStart, eventEnd, matchingTrip)) {
      throw new EventOutsideTripException("Event liegt außerhalb des Trips");
    }
    Event newEvent = new Event(eventName, eventStart, eventEnd, dto.getKosten(), matchingTrip);
    Event savedEvent = eventRepository.save(newEvent);

    User creator =
        userRepository
            .findById(UserID)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

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
                    event.getTripId()))
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

    boolean requesterIsParticipant =
        userEventRepository.existsByEvent_IdAndUser_Id(eventId, requestingUserId);
    if (!requesterIsParticipant) {
      throw new IllegalArgumentException("Only participants can invite friends");
    }

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
    return parseTextWithGemini(message.getSubject(), body);
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
    if (!jsonLdBlocks.isEmpty()) {
      var jsonEvent =
          jsonLdBlocks.stream()
              .filter(this::isSchemaOrg)
              .filter(this::isSchemaEvent)
              .findFirst()
              .orElse(null);
      if (jsonEvent != null) {
        return Optional.of(mapJsonLdToDTO(jsonEvent));
      }
    }

    var body = extractText(message);
    return detectEventWithGemini(message.getSubject(), body);
  }

  /**
   * Asks Gemini whether the mail text describes a real appointment and, if so, extracts it. Returns
   * empty when there is no event, no Gemini key, or the AI response is unusable — in automatic
   * import it is safer to skip an uncertain mail than to create a bogus event.
   */
  private Optional<EventResponseDTO> detectEventWithGemini(String subject, String mailText) {
    if (geminiApiKey == null || geminiApiKey.isBlank() || mailText == null || mailText.isBlank()) {
      return Optional.empty();
    }

    try {
      Client client = Client.builder().apiKey(geminiApiKey).build();

      GenerateContentConfig config =
          GenerateContentConfig.builder()
              .responseMimeType("application/json")
              .temperature(0.1f)
              .build();

      GenerateContentResponse response =
          client.models.generateContent(
              geminiModel, buildGeminiEventDetectionPrompt(subject, mailText), config);

      JsonNode node = objectMapper.readTree(response.text());
      if (!node.path("hasEvent").asBoolean(false)) {
        return Optional.empty();
      }

      String name = node.path("name").asText(subject);
      int kosten = node.path("kosten").asInt(0);
      LocalDateTime start = parseDatetime(node.path("startDate").asText());
      LocalDateTime end = parseEndDatetime(node.path("endDate").asText(), start);
      return Optional.of(new EventResponseDTO(name, kosten, start, end));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  private String buildGeminiEventDetectionPrompt(String subject, String mailText) {
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
                - startDate ist yyyy-MM-ddTHH:mm:ss.
                - endDate ist yyyy-MM-ddTHH:mm:ss.
                - Falls kein Eventname gefunden wird: Betreff nutzen.
                - Erfinde keine Daten.

                Betreff:
                %s

                Mailtext:
                %s
                """
        .formatted(subject, mailText);
  }

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

  private EventResponseDTO parseTextWithGemini(String subject, String mailText) {
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
          client.models.generateContent(geminiModel, buildGeminiPrompt(subject, mailText), config);

      JsonNode node = objectMapper.readTree(response.text());

      String name = node.path("name").asText(subject);
      int kosten = node.path("kosten").asInt(0);
      LocalDateTime start = parseDatetime(node.path("startDate").asText());
      LocalDateTime end = parseEndDatetime(node.path("endDate").asText(), start);

      return new EventResponseDTO(name, kosten, start, end);
    } catch (Exception exception) {
      // Bei ungültiger KI-Antwort wird trozdem versucht ein Evnt zu erstellen.
      return mapTextToDTO(mailText);
    }
  }

  private String buildGeminiPrompt(String subject, String mailText) {
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
                - startDate ist yyyy-MM-ddTHH:mm:ss.
                - endDate ist yyyy-MM-ddTHH:mm:ss.
                - Falls kein Datum gefunden wird: heutiger Tag.
                - Falls kein Preis gefunden wird: kosten 0.
                - Falls kein Eventname gefunden wird: Betreff nutzen.
                - Erfinde keine Daten.

                Betreff:
                %s

                Mailtext:
                %s
                """
        .formatted(subject, mailText);
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
    var name = text == null || text.isBlank() ? "Unbekanntes Event" : text;
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
    var name = eventNode.path("name").asText("Unbekanntes Event");
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

  private LocalDateTime parseDatetime(String jsonDatetime) {
    if (jsonDatetime == null || jsonDatetime.isBlank()) {
      return LocalDateTime.now();
    }

    return LocalDateTime.parse(jsonDatetime);
  }

  private LocalDateTime parseEndDatetime(String jsonDatetime, LocalDateTime start) {
    if (jsonDatetime == null || jsonDatetime.isBlank()) {
      return resolveEndTime(null, start);
    }

    return LocalDateTime.parse(jsonDatetime);
  }

  private LocalDateTime resolveEndTime(LocalDateTime end, LocalDateTime start) {
    return end != null ? end : start.plusDays(1);
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
