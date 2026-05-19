package com.sep.sep_backend.EventHandling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.ausgabe.AusgabeRequestDTO;
import com.sep.sep_backend.ausgabe.AusgabeService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.sep.sep_backend.ausgabe.Kategorie;
import jakarta.mail.Session;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TripRepository tripRepository;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model}")
    private String geminiModel;


    @Autowired
    public EventService(EventRepository eventRepository, TripRepository triprepository, AusgabeService ausgabeService, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.tripRepository = triprepository;
        this.objectMapper = objectMapper;
    }


    //Todo Name bei Trip muss besser gelöst werden jetzt vereinfacht über id
    public EventResponseDTO SaveEventToDB(EventRequestDTO dto)
    {
        Trip matchingTrip = tripRepository.findById(dto.getTripId()).orElseThrow(() -> new RuntimeException("Zu dieser trip Id findet sich kein eintrag in der Datenbank"));
        LocalDateTime eventStart = dto.getStart() != null ? dto.getStart() : LocalDateTime.now();
        LocalDateTime eventEnd = dto.getEnd() != null ? dto.getEnd() : LocalDateTime.now().plusDays(1);
        Event newEvent = new Event(dto.getName(), eventStart, eventEnd, dto.getKosten(), matchingTrip);
        EventResponseDTO ausgabe = new EventResponseDTO(dto.getName(), dto.getKosten(), eventStart, eventEnd);
        eventRepository.save(newEvent);

        // add the expence automatically to the db
        AusgabeRequestDTO ausgabeRequestDTO = new AusgabeRequestDTO( (double) dto.getKosten(), matchingTrip, eventStart.toLocalDate(), Kategorie.SONSTIGES);

        return ausgabe;
    }

    public EventResponseDTO SaveEventResponseToDB(EventResponseDTO dto, Long tripId)
    {
        Trip matchingTrip = tripRepository.findById(tripId).orElseThrow(() -> new RuntimeException("Zu dieser trip Id findet sich kein eintrag in der Datenbank"));
        String eventName = dto.getName() == null || dto.getName().isBlank() ? "Unbekanntes Event" : dto.getName();
        LocalDateTime eventStart = dto.getUhrzeit() != null ? dto.getUhrzeit() : LocalDateTime.now();
        Event newEvent = new Event(eventName, eventStart, dto.getKosten(), matchingTrip);
        EventResponseDTO response = new EventResponseDTO(eventName, dto.getKosten(), eventStart);
        eventRepository.save(newEvent);

        AusgabeRequestDTO ausgabeRequestDTO = new AusgabeRequestDTO((double) dto.getKosten(), matchingTrip, eventStart.toLocalDate(), Kategorie.SONSTIGES);

        return response;
    }

    public List<EventCalendarDTO> GetAllEventsFromDb()
    {
        return eventRepository.findAll()
                .stream()
                .map(event -> new EventCalendarDTO(
                        String.valueOf(event.getId()),
                        event.getName(),
                        event.getKosten(),
                        event.getStartTime(),
                        event.getEndTime(),
                        event.getTripId()
                ))
                .toList();
    }

    public EventResponseDTO ParseMailFile(InputStream inputStream) throws Exception {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session, inputStream);
        var jsonLdBlocks = extractJsonLd(message);
        if(!jsonLdBlocks.isEmpty())
        {
            var jsonEvent = jsonLdBlocks.stream()
                    .filter(node -> isSchemaOrg(node))
                    .filter(node -> isSchemaEvent(node)).findFirst().orElse(null);

            if(jsonEvent != null)
            {
                return  mapJsonLdToDTO(jsonEvent);
            }

        }

        var body = extractText(message);
        return parseTextWithGemini(message.getSubject(), body);
    }

    private EventResponseDTO parseTextWithGemini(String subject, String mailText)
    {
        if (geminiApiKey == null || geminiApiKey.isBlank() || mailText == null || mailText.isBlank())
        {
            return mapTextToDTO(mailText);
        }

        try
        {
            Client client = Client.builder()
                    .apiKey(geminiApiKey)
                    .build();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .temperature(0.1f)
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    geminiModel,
                    buildGeminiPrompt(subject, mailText),
                    config
            );

            JsonNode node = objectMapper.readTree(response.text());

            String name = node.path("name").asText(subject);
            int kosten = node.path("kosten").asInt(0);
            String startDate = node.path("startDate").asText();
            LocalDateTime date = startDate == null || startDate.isBlank()
                    ? LocalDateTime.now()
                    : LocalDateTime.parse(startDate);

            return new EventResponseDTO(name, kosten, date);
        }
        catch (Exception exception)
        {
            return mapTextToDTO(mailText);
        }
    }

    private String buildGeminiPrompt(String subject, String mailText)
    {
        return """
                Lies diese E-Mail und extrahiere ein Event.
                Antworte nur mit JSON:
                {
                  "name": "Eventname",
                  "kosten": 0,
                  "startDate": "2026-06-01T20:00:00"
                }

                Regeln:
                - kosten ist eine ganze Zahl.
                - startDate ist yyyy-MM-ddTHH:mm:ss.
                - Falls kein Datum gefunden wird: heutiger Tag.
                - Falls kein Preis gefunden wird: kosten 0.
                - Falls kein Eventname gefunden wird: Betreff nutzen.
                - Erfinde keine Daten.

                Betreff:
                %s

                Mailtext:
                %s
                """.formatted(subject, mailText);
    }

    private boolean isSchemaOrg(JsonNode node)
    {
        JsonNode context = node.get("@context");

        if (context == null || context.isNull())
        {
            return false;
        }

        if (context.isTextual() && context.asText().contains("schema.org"))
        {
            return true;
        }

        if (context.isArray())
        {
            for (JsonNode child : context)
            {
                if (child.isTextual() && child.asText().contains("schema.org"))
                {
                    return true;
                }
            }
        }

        if (context.isObject() && context.toString().contains("schema.org"))
        {
            return true;
        }

        return false;
    }

    private boolean isSchemaEvent(JsonNode node)
    {
        JsonNode type = node.get("@type");

        if(isEventType(type))
        {
            return true;
        }

        if (node.get("@graph")!= null && node.get("@graph").isArray())
        {
            var graph = node.get("@graph");
            for(JsonNode child : graph)
            {
                if(isEventType(child.get("@type")))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isEventType(JsonNode type)
    {
        if (type == null || type.isNull())
        {
            return false;
        }

        if (type.isTextual())
        {
            String typeValue = type.asText();
            return typeValue.equals("Event")
                    || typeValue.equals("schema:Event")
                    || typeValue.equals("https://schema.org/Event")
                    || typeValue.equals("http://schema.org/Event");
        }

        if (type.isArray())
        {
            for (JsonNode child : type)
            {
                if (isEventType(child))
                {
                    return true;
                }
            }
        }

        return false;
    }
    private EventResponseDTO mapTextToDTO(String text)
    {
        var name = text == null || text.isBlank() ? "Unbekanntes Event" : text;
        return new EventResponseDTO(name, 0, LocalDateTime.now());
    }

    private EventResponseDTO mapJsonLdToDTO(JsonNode jsonLdBlocks)
    {
        var eventNode = jsonLdBlocks;

        if(!isEventType(eventNode.get("@type")) && eventNode.path("@graph").isArray())
        {
            for(JsonNode child : eventNode.path("@graph"))
            {
                if(isEventType(child.get("@type")))
                {
                    eventNode = child;
                    break;
                }
            }
        }

        var price = eventNode.path("offers").path("price").asInt();
        var date = parseDatetime(eventNode.path("startDate").asText());
        var name = eventNode.path("name").asText("Unbekanntes Event");
        return new EventResponseDTO(name, price, date);
    }

    private String extractText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return part.getContent().toString();
        }
        return null;
    }

    private LocalDateTime parseDatetime(String jsonDatetime)
    {
        if(jsonDatetime == null || jsonDatetime.isBlank())
        {
            return LocalDateTime.now();
        }

        return LocalDateTime.parse(jsonDatetime);
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
