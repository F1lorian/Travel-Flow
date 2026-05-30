package com.sep.sep_backend.EventHandling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EventService {

  private final EventRepository eventRepository;
  private final TripRepository tripRepository;
  private final AusgabeService ausgabeService;
  private final ObjectMapper objectMapper;

  @Value("${gemini.api-key}")
  private String geminiApiKey;

  @Value("${gemini.model}")
  private String geminiModel;

  @Autowired
  public EventService(
      EventRepository eventRepository,
      TripRepository triprepository,
      AusgabeService ausgabeService,
      ObjectMapper objectMapper) {
    this.eventRepository = eventRepository;
    this.tripRepository = triprepository;
    this.ausgabeService = ausgabeService;
    this.objectMapper = objectMapper;
  }

  //Einfaches Speichern von Events
  public EventResponseDTO SaveEventToDB(EventRequestDTO dto) {
    Trip matchingTrip =
        tripRepository
            .findById(dto.getTripId())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Zu dieser trip Id findet sich kein eintrag in der Datenbank"));
    //Behandlung wenn Startdate == null heutiger Tag wenn endDate == null dann startdate +1
    LocalDateTime eventStart = dto.getStart() != null ? dto.getStart() : LocalDateTime.now();
    LocalDateTime eventEnd = resolveEndTime(dto.getEnd(), eventStart);
    Event newEvent = new Event(dto.getName(), eventStart, eventEnd, dto.getKosten(), matchingTrip);
    Event savedEvent = eventRepository.save(newEvent);

    return mapToResponseDTO(savedEvent);
  }

  //Löschen eines Events aus der Db
  public void DeleteEventFromDB(Long EventID) {
    if (!eventRepository.existsById(EventID)) {
      throw new EntityNotFoundException("Event mit ID " + EventID + " wurde nicht gefunden");
    }

    eventRepository.deleteById(EventID);
  }

  //Event bearbeiten auf Basis der mitgegebenen Daten im DTO
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
    // -1 bei Kosten als Error State
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

  //Gleiche Speicher Logik wie Oben nur für Response DTOs
  public EventResponseDTO SaveEventResponseToDB(EventResponseDTO dto, Long tripId) {
    Trip matchingTrip =
        tripRepository
            .findById(tripId)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Zu dieser trip Id findet sich kein eintrag in der Datenbank"));
    String eventName =
        dto.getName() == null || dto.getName().isBlank() ? "Unbekanntes Event" : dto.getName();
    LocalDateTime eventStart = dto.getStart() != null ? dto.getStart() : LocalDateTime.now();
    LocalDateTime eventEnd = resolveEndTime(dto.getEnd(), eventStart);
    Event newEvent = new Event(eventName, eventStart, eventEnd, dto.getKosten(), matchingTrip);
    Event savedEvent = eventRepository.save(newEvent);

    return mapToResponseDTO(savedEvent);
  }

  // Holt alle Events aus de DB um den Kalender zu Füllen (Alle Trips werden im Trip Management geholt)
  public List<EventCalendarDTO> GetAllEventsFromDb() {
    return eventRepository.findAll().stream()
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

  // Zweistufiges Email Parsing um Events zu erzeugen
  public EventResponseDTO ParseMailFile(InputStream inputStream) throws Exception {
    Session session = Session.getDefaultInstance(new Properties());
    MimeMessage message = new MimeMessage(session, inputStream);
    var jsonLdBlocks = extractJsonLd(message);
    // 1.Stufe überprüfen ob Json LD File vorliegt
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
  // 2. Stufe wenn kein Json LD gefunden wurde wird der Text und der Betreff mit Gemini Interpretiert
    var body = extractText(message);
    return parseTextWithGemini(message.getSubject(), body);
  }

  private EventResponseDTO parseTextWithGemini(String subject, String mailText) {
    if (geminiApiKey == null || geminiApiKey.isBlank() || mailText == null || mailText.isBlank()) {
      return mapTextToDTO(mailText);
    }

    try {
      Client client = Client.builder().apiKey(geminiApiKey).build();

      //Ausgabe typ Festkegen. temperature == Grad der Hallozination
      GenerateContentConfig config =
          GenerateContentConfig.builder()
              .responseMimeType("application/json")
              .temperature(0.1f)
              .build();

      GenerateContentResponse response =
          client.models.generateContent(geminiModel, buildGeminiPrompt(subject, mailText), config);

      JsonNode node = objectMapper.readTree(response.text());

      //Ergebnis wurde als Json erzeugt daher wird dass als Json node durchlaufen um dass DTO zu bauen
      String name = node.path("name").asText(subject);
      int kosten = node.path("kosten").asInt(0);
      LocalDateTime start = parseDatetime(node.path("startDate").asText());
      LocalDateTime end = parseEndDatetime(node.path("endDate").asText(), start);

      return new EventResponseDTO(name, kosten, start, end);
    } catch (Exception exception) {
      return mapTextToDTO(mailText);
    }
  }

  //Prompt für Gemini
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

  //Überprüfung ob ein Part der Email nach Schema.org aufgebaut ist
  private boolean isSchemaOrg(JsonNode node) {
    JsonNode context = node.get("@context");

    if (context == null || context.isNull()) {
      return false;
    }

    if (context.isTextual() && context.asText().contains("schema.org")) {
      return true;
    }

    //Json kann verschachtekt sein daher hier die Extra Prüfung über Json Nodes
    if (context.isArray()) {
      for (JsonNode child : context) {
        if (child.isTextual() && child.asText().contains("schema.org")) {
          return true;
        }
      }
    }

    //Falls kein text vorliegt
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

    //Umgang mit verschachtelungen
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

  //Überprüfung ob es sich um ein Schem event handelt (Aufbau nach
  // Schema.org sollte vorher überprüft werden)
  private boolean isEventType(JsonNode type) {
    if (type == null || type.isNull()) {
      return false;
    }

    //Behandlung von unterschiedlichen Schreibweisen
    if (type.isTextual()) {
      String typeValue = type.asText();
      return typeValue.equals("Event")
          || typeValue.equals("schema:Event")
          || typeValue.equals("https://schema.org/Event")
          || typeValue.equals("http://schema.org/Event");
    }

    // Umgang mit verschachtelung durch Rekursion
    if (type.isArray()) {
      for (JsonNode child : type) {
        if (isEventType(child)) {
          return true;
        }
      }
    }

    return false;
  }

  //Wird nur genutz falls gemini keine Gültige antwort findet (vermeidet Absturz)
  private EventResponseDTO mapTextToDTO(String text) {
    var name = text == null || text.isBlank() ? "Unbekanntes Event" : text;
    LocalDateTime start = LocalDateTime.now();
    return new EventResponseDTO(name, 0, start, resolveEndTime(null, start));
  }

  private EventResponseDTO mapJsonLdToDTO(JsonNode jsonLdBlocks) {
    var eventNode = jsonLdBlocks;

    //Falls dass Event selber in einem Graph verschachtelt ist wird solange durchgegangen
    // bis man den passenden Teil findet
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

  //Durchläuft die Multipart Email und sucht nach dem Json LD
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
