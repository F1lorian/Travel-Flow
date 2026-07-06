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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
      // If no description was extracted (no structured data and AI unavailable or silent),
      // fall back to the email body so the user still sees the email content. Strip tracking URLs
      // and cap the length first, otherwise a marketing email's body is just a wall of links.
      if ((dto.getDescription() == null || dto.getDescription().isBlank())
          && body != null
          && !body.isBlank()) {
        String cleaned = cleanBodyForDescription(body);
        if (!cleaned.isBlank()) {
          dto.setDescription(cleaned);
        }
      }
      // When no date was found (AI slow/unavailable and no structured data), try to read one
      // straight from the email text so the event still carries the date/time it plainly states.
      if (dto.getDate() == null) {
        applyHeuristicDateTime(dto, subject, body);
      }
      dto.setSourceMessageId(messageId);
      return Optional.of(dto);
    } catch (Exception exception) {
      // An unreadable email must never crash a whole import run; skip it instead.
      return Optional.empty();
    }
  }

  /** Longest body-fallback description kept; anything past this is truncated with an ellipsis. */
  private static final int MAX_FALLBACK_DESCRIPTION_LENGTH = 600;

  /**
   * Turns a raw email body into a readable fallback description: removes URLs (bare,
   * angle-bracketed and www.), drops {@code [bracketed]} link labels, collapses whitespace and caps
   * the length. This keeps a marketing email's tracking-link soup from becoming the event
   * description.
   */
  private String cleanBodyForDescription(String body) {
    String text =
        body.replaceAll("(?i)<https?://[^>]*>", " ")
            .replaceAll("(?i)https?://\\S+", " ")
            .replaceAll("(?i)www\\.\\S+", " ")
            .replaceAll("\\[[^\\]]*\\]", " ")
            .replaceAll("[ \\t]+", " ")
            .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "")
            .replaceAll("\\n{3,}", "\n\n")
            .strip();
    if (text.length() > MAX_FALLBACK_DESCRIPTION_LENGTH) {
      text = text.substring(0, MAX_FALLBACK_DESCRIPTION_LENGTH).strip() + "…";
    }
    return text;
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
      if (content == null) {
        return null;
      }
      String text = content.toString();
      // Some senders wrap HTML markup inside a text/plain part; clean it too so the
      // description never shows raw tags/CSS instead of readable text.
      return looksLikeHtml(text) ? htmlToText(text) : text;
    }
    if (part.isMimeType("text/html")) {
      Object content = part.getContent();
      return content == null ? null : htmlToText(content.toString());
    }
    if (part.isMimeType("multipart/alternative")) {
      // Alternatives carry the same message in several formats; prefer the clean
      // text/plain version and only fall back to HTML when no plain part exists.
      Multipart multipart = (Multipart) part.getContent();
      String htmlFallback = null;
      for (int i = 0; i < multipart.getCount(); i++) {
        Part bodyPart = multipart.getBodyPart(i);
        if (bodyPart.isMimeType("text/plain")) {
          String text = extractText(bodyPart);
          if (text != null && !text.isBlank()) {
            return text;
          }
        } else if (htmlFallback == null) {
          htmlFallback = extractText(bodyPart);
        }
      }
      return htmlFallback;
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

  /** Heuristic: does this text contain HTML markup that should be stripped before display? */
  private boolean looksLikeHtml(String text) {
    if (text == null) {
      return false;
    }
    String lower = text.toLowerCase();
    return lower.contains("<!doctype html")
        || lower.contains("<html")
        || lower.contains("<body")
        || lower.contains("<div")
        || lower.contains("<table")
        || lower.contains("<style")
        || lower.contains("<p>")
        || lower.contains("<br");
  }

  /**
   * Converts an HTML email body into readable plain text: drops {@code <style>}/{@code <script>}
   * blocks (their CSS/JS would otherwise leak into the description), removes remaining tags and
   * comments, decodes common HTML entities and collapses excess whitespace.
   */
  private String htmlToText(String html) {
    String text =
        html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
            .replaceAll("(?s)<!--.*?-->", " ")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n")
            .replaceAll("<[^>]+>", " ");
    text =
        text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'");
    // Collapse runs of spaces/tabs and trim blank lines so the result reads like an email.
    text = text.replaceAll("[ \\t]+", " ").replaceAll("(?m)^[ \\t]+|[ \\t]+$", "");
    text = text.replaceAll("\\n{3,}", "\n\n");
    return text.strip();
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

  // ---------------------------------------------------------------------------------------------
  // Offline date/time extraction — a fallback for when the AI is slow or unavailable, so an event
  // still carries the date/time the email plainly states in its text.
  // ---------------------------------------------------------------------------------------------

  private static final Map<String, Integer> MONTH_NAMES = buildMonthNames();

  /** German dotted date, e.g. {@code 6.07.2026}. */
  private static final Pattern DATE_DOTTED =
      Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\b");

  /** ISO date, e.g. {@code 2026-07-06}. */
  private static final Pattern DATE_ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");

  /** Slash date, assumed day-first (European), e.g. {@code 6/7/2026}. */
  private static final Pattern DATE_SLASH = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");

  /** Day month-name year, e.g. {@code 6. Juli 2026} or {@code 6 July 2026}. */
  private static final Pattern DATE_DAY_MONTH_YEAR =
      Pattern.compile("\\b(\\d{1,2})\\.?\\s+([A-Za-zÄÖÜäöü]+)\\.?\\s+(\\d{4})\\b");

  /** Month-name day, year, e.g. {@code July 6, 2026}. */
  private static final Pattern DATE_MONTH_DAY_YEAR =
      Pattern.compile("\\b([A-Za-zÄÖÜäöü]+)\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})\\b");

  /** 24-hour time, optionally followed by "Uhr", e.g. {@code 12:00} / {@code 12:00 Uhr}. */
  private static final Pattern TIME_24H =
      Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b(?:\\s*Uhr)?", Pattern.CASE_INSENSITIVE);

  /** 12-hour am/pm time, e.g. {@code 8:30 pm} / {@code 8 pm}. */
  private static final Pattern TIME_AMPM =
      Pattern.compile("\\b(\\d{1,2})(?::([0-5]\\d))?\\s*([AaPp])\\.?[Mm]\\.?\\b");

  /**
   * Fills {@code date} (and, if a date is known, {@code startTime}) from the email text, without
   * ever overriding values already provided by structured data or the AI.
   */
  private void applyHeuristicDateTime(ExtractedEventDTO dto, String subject, String body) {
    String text = (subject == null ? "" : subject) + "\n" + (body == null ? "" : body);
    LocalDate date = findDate(text);
    if (date == null) {
      return;
    }
    dto.setDate(date);
    if (dto.getStartTime() == null) {
      LocalTime time = findTime(text);
      if (time != null) {
        dto.setStartTime(time);
      }
    }
  }

  /** Returns the first plausible date in the text, trying numeric formats before month names. */
  private LocalDate findDate(String text) {
    Matcher dotted = DATE_DOTTED.matcher(text);
    if (dotted.find()) {
      LocalDate date = toDate(intAt(dotted, 3), intAt(dotted, 2), intAt(dotted, 1));
      if (date != null) {
        return date;
      }
    }
    Matcher iso = DATE_ISO.matcher(text);
    if (iso.find()) {
      LocalDate date = toDate(intAt(iso, 1), intAt(iso, 2), intAt(iso, 3));
      if (date != null) {
        return date;
      }
    }
    Matcher slash = DATE_SLASH.matcher(text);
    if (slash.find()) {
      LocalDate date = toDate(intAt(slash, 3), intAt(slash, 2), intAt(slash, 1));
      if (date != null) {
        return date;
      }
    }
    LocalDate named = findNamedMonthDate(text, DATE_DAY_MONTH_YEAR, 1, 2, 3);
    if (named != null) {
      return named;
    }
    return findNamedMonthDate(text, DATE_MONTH_DAY_YEAR, 2, 1, 3);
  }

  /** Matches a month-name date pattern, skipping matches whose "month" word is not a real month. */
  private LocalDate findNamedMonthDate(
      String text, Pattern pattern, int dayGroup, int monthGroup, int yearGroup) {
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      Integer month = MONTH_NAMES.get(matcher.group(monthGroup).toLowerCase(Locale.ROOT));
      if (month != null) {
        LocalDate date =
            toDate(Integer.parseInt(matcher.group(yearGroup)), month, intAt(matcher, dayGroup));
        if (date != null) {
          return date;
        }
      }
    }
    return null;
  }

  private LocalTime findTime(String text) {
    Matcher time24 = TIME_24H.matcher(text);
    if (time24.find()) {
      return LocalTime.of(intAt(time24, 1), intAt(time24, 2));
    }
    Matcher ampm = TIME_AMPM.matcher(text);
    if (ampm.find()) {
      int hour = intAt(ampm, 1) % 12;
      if (ampm.group(3).equalsIgnoreCase("p")) {
        hour += 12;
      }
      int minute = ampm.group(2) == null ? 0 : Integer.parseInt(ampm.group(2));
      try {
        return LocalTime.of(hour, minute);
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  private LocalDate toDate(int year, int month, int day) {
    try {
      return LocalDate.of(year, month, day);
    } catch (Exception ignored) {
      return null;
    }
  }

  private int intAt(Matcher matcher, int group) {
    return Integer.parseInt(matcher.group(group));
  }

  private static Map<String, Integer> buildMonthNames() {
    String[][] names = {
      {"january", "jan", "januar"},
      {"february", "feb", "februar"},
      {"march", "mar", "märz", "maerz"},
      {"april", "apr"},
      {"may", "mai"},
      {"june", "jun", "juni"},
      {"july", "jul", "juli"},
      {"august", "aug"},
      {"september", "sep", "sept"},
      {"october", "oct", "okt", "oktober"},
      {"november", "nov"},
      {"december", "dec", "dez", "dezember"}
    };
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < names.length; i++) {
      for (String name : names[i]) {
        map.put(name, i + 1);
      }
    }
    return map;
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
