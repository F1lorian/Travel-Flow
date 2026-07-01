package com.sep.sep_backend.gmailimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.springframework.stereotype.Service;

/**
 * Reusable parser that turns a raw email (RFC 822 bytes, as returned by Gmail or an uploaded {@code
 * .eml} file) into an {@link ExtractedEventDTO}.
 *
 * <p>Extraction strategy, most reliable first:
 *
 * <ol>
 *   <li>Structured schema.org JSON-LD embedded in the email (used by many booking providers).
 *   <li>AI extraction over the plain-text body via {@link AiEventExtractor}.
 *   <li>A minimal heuristic (subject as title) so the feature still works offline.
 * </ol>
 *
 * <p>The parser never throws on missing information: absent fields are left {@code null}. If the
 * email cannot be read at all, or clearly contains no event, it returns {@link Optional#empty()}.
 */
@Service
public class EmailEventParser {

  private final ObjectMapper objectMapper;
  private final AiEventExtractor aiEventExtractor;
  private final EventClassifier eventClassifier;

  public EmailEventParser(
      ObjectMapper objectMapper,
      AiEventExtractor aiEventExtractor,
      EventClassifier eventClassifier) {
    this.objectMapper = objectMapper;
    this.aiEventExtractor = aiEventExtractor;
    this.eventClassifier = eventClassifier;
  }

  /**
   * Parses a raw email into an extracted event.
   *
   * @param rawMessage RFC 822 message bytes
   * @param messageId identifier of the source message (stored for de-duplication/traceability)
   * @return the extracted event, or empty if the email is unreadable or holds no event
   */
  public Optional<ExtractedEventDTO> parse(byte[] rawMessage, String messageId) {
    if (rawMessage == null || rawMessage.length == 0) {
      return Optional.empty();
    }
    try {
      MimeMessage message =
          new MimeMessage(
              Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(rawMessage));

      String subject = safeSubject(message);
      String body = extractText(message);
      JsonNode eventJsonLd = findEventJsonLd(message);

      if (!eventClassifier.looksLikeEvent(subject, body, eventJsonLd != null)) {
        return Optional.empty();
      }

      ExtractedEventDTO dto = extractFields(subject, body, eventJsonLd);
      if (dto.getTitle() == null || dto.getTitle().isBlank()) {
        dto.setTitle(subject);
      }
      dto.setSourceMessageId(messageId);
      return Optional.of(dto);
    } catch (Exception exception) {
      // An unreadable email must never crash a whole import run; skip it instead.
      return Optional.empty();
    }
  }

  private ExtractedEventDTO extractFields(String subject, String body, JsonNode eventJsonLd) {
    if (eventJsonLd != null) {
      return mapFromJsonLd(eventJsonLd, subject);
    }
    if (aiEventExtractor.isAvailable()) {
      Optional<ExtractedEventDTO> aiResult = aiEventExtractor.extract(subject, body);
      if (aiResult.isPresent()) {
        return aiResult.get();
      }
    }
    return heuristicExtraction(subject);
  }

  /** Minimal offline fallback: keep the subject as the title, leave everything else empty. */
  private ExtractedEventDTO heuristicExtraction(String subject) {
    ExtractedEventDTO dto = new ExtractedEventDTO();
    dto.setTitle(subject);
    return dto;
  }

  // ---------------------------------------------------------------------------------------------
  // JSON-LD extraction
  // ---------------------------------------------------------------------------------------------

  private ExtractedEventDTO mapFromJsonLd(JsonNode eventNode, String subject) {
    // Reservations wrap the actual event in "reservationFor"; fall back to it for
    // name/date/location.
    JsonNode detail = eventNode.path("reservationFor");
    ExtractedEventDTO dto = new ExtractedEventDTO();

    String title = firstText(eventNode.path("name"), null);
    if (title == null) {
      title = firstText(detail.path("name"), null);
    }
    dto.setTitle(title != null ? title : subject);

    String description = firstText(eventNode.path("description"), null);
    dto.setDescription(
        description != null ? description : firstText(detail.path("description"), null));

    JsonNode dateSource = eventNode.has("startDate") ? eventNode : detail;
    applyDateTime(dto, dateSource.path("startDate").asText(null), true);
    applyDateTime(dto, dateSource.path("endDate").asText(null), false);

    dto.setLocation(extractLocation(eventNode, detail));
    dto.setOrganizer(extractOrganizer(eventNode));
    return dto;
  }

  private void applyDateTime(ExtractedEventDTO dto, String isoValue, boolean isStart) {
    if (isoValue == null || isoValue.isBlank()) {
      return;
    }
    ParsedInstant parsed = parseFlexibleDateTime(isoValue);
    if (parsed == null) {
      return;
    }
    if (isStart) {
      dto.setDate(parsed.date());
      dto.setStartTime(parsed.time());
      if (parsed.zone() != null) {
        dto.setTimeZone(parsed.zone());
      }
    } else {
      dto.setEndTime(parsed.time());
      if (dto.getDate() == null) {
        dto.setDate(parsed.date());
      }
    }
  }

  private String extractLocation(JsonNode eventNode, JsonNode detail) {
    String location = locationString(eventNode.path("location"));
    if (location == null) {
      location = locationString(detail.path("location"));
    }
    return location;
  }

  private String locationString(JsonNode locationNode) {
    if (locationNode == null || locationNode.isMissingNode() || locationNode.isNull()) {
      return null;
    }
    if (locationNode.isTextual()) {
      return blankToNull(locationNode.asText());
    }
    String name = blankToNull(locationNode.path("name").asText(null));
    String address = addressString(locationNode.path("address"));
    if (name != null && address != null) {
      return name + ", " + address;
    }
    return name != null ? name : address;
  }

  private String addressString(JsonNode addressNode) {
    if (addressNode == null || addressNode.isMissingNode() || addressNode.isNull()) {
      return null;
    }
    if (addressNode.isTextual()) {
      return blankToNull(addressNode.asText());
    }
    List<String> parts = new ArrayList<>();
    addIfPresent(parts, addressNode.path("streetAddress").asText(null));
    addIfPresent(parts, addressNode.path("postalCode").asText(null));
    addIfPresent(parts, addressNode.path("addressLocality").asText(null));
    addIfPresent(parts, addressNode.path("addressCountry").asText(null));
    return parts.isEmpty() ? null : String.join(", ", parts);
  }

  private String extractOrganizer(JsonNode eventNode) {
    JsonNode organizer = eventNode.path("organizer");
    if (organizer.isTextual()) {
      return blankToNull(organizer.asText());
    }
    return blankToNull(organizer.path("name").asText(null));
  }

  // ---------------------------------------------------------------------------------------------
  // MIME helpers
  // ---------------------------------------------------------------------------------------------

  private String safeSubject(MimeMessage message) {
    try {
      return message.getSubject();
    } catch (Exception exception) {
      return null;
    }
  }

  private String extractText(Part part) throws Exception {
    if (part.isMimeType("text/plain")) {
      Object content = part.getContent();
      return content == null ? null : content.toString();
    }
    if (part.isMimeType("text/html")) {
      Object content = part.getContent();
      return content == null ? null : content.toString().replaceAll("<[^>]+>", " ");
    }
    if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        String text = extractText(multipart.getBodyPart(i));
        if (text != null) {
          builder.append(text).append('\n');
        }
      }
      return builder.length() == 0 ? null : builder.toString();
    }
    return null;
  }

  private JsonNode findEventJsonLd(Part part) throws Exception {
    for (JsonNode node : extractJsonLd(part)) {
      JsonNode eventNode = eventNodeOrNull(node);
      if (eventNode != null) {
        return eventNode;
      }
    }
    return null;
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

  /** Returns the schema.org event/reservation node inside a JSON-LD block, or {@code null}. */
  private JsonNode eventNodeOrNull(JsonNode node) {
    if (isSchemaEventOrReservation(node.path("@type"))) {
      return node;
    }
    JsonNode graph = node.path("@graph");
    if (graph.isArray()) {
      for (JsonNode child : graph) {
        if (isSchemaEventOrReservation(child.path("@type"))) {
          return child;
        }
      }
    }
    return null;
  }

  private boolean isSchemaEventOrReservation(JsonNode type) {
    if (type == null || type.isNull()) {
      return false;
    }
    if (type.isTextual()) {
      String value = type.asText();
      return value.contains("Event") || value.contains("Reservation");
    }
    if (type.isArray()) {
      for (JsonNode child : type) {
        if (isSchemaEventOrReservation(child)) {
          return true;
        }
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------
  // Small utilities
  // ---------------------------------------------------------------------------------------------

  /** Result of splitting an ISO date-time string into calendar parts. */
  private record ParsedInstant(LocalDate date, LocalTime time, String zone) {}

  private ParsedInstant parseFlexibleDateTime(String value) {
    String trimmed = value.trim();
    try {
      // Handles offsets/zones, e.g. 2026-06-01T20:00:00+02:00
      OffsetDateTime odt = OffsetDateTime.parse(trimmed);
      return new ParsedInstant(odt.toLocalDate(), odt.toLocalTime(), odt.getOffset().getId());
    } catch (Exception ignored) {
      // fall through
    }
    try {
      var ldt = java.time.LocalDateTime.parse(trimmed);
      return new ParsedInstant(ldt.toLocalDate(), ldt.toLocalTime(), null);
    } catch (Exception ignored) {
      // fall through
    }
    try {
      LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
      return new ParsedInstant(date, null, null);
    } catch (Exception ignored) {
      return null;
    }
  }

  private String firstText(JsonNode node, String fallback) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }
    if (node.isArray() && node.size() > 0) {
      return blankToNull(node.get(0).asText(null)) == null ? fallback : node.get(0).asText();
    }
    String text = blankToNull(node.asText(null));
    return text == null ? fallback : text;
  }

  private void addIfPresent(List<String> parts, String value) {
    String cleaned = blankToNull(value);
    if (cleaned != null) {
      parts.add(cleaned);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Exposes the configured system default zone for callers that need a timezone fallback. */
  public static String systemTimeZone() {
    return ZoneId.systemDefault().getId();
  }
}
