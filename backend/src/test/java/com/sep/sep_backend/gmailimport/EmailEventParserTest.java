package com.sep.sep_backend.gmailimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for the reusable email parser: JSON-LD extraction, fallbacks and robustness. */
class EmailEventParserTest {

  /** Extractor that is never available, forcing the parser onto its offline paths. */
  private final AiEventExtractor unavailableAi =
      new AiEventExtractor() {
        @Override
        public boolean isAvailable() {
          return false;
        }

        @Override
        public Optional<ExtractedEventDTO> extract(String subject, String body) {
          return Optional.empty();
        }
      };

  private final EmailEventParser parser =
      new EmailEventParser(new ObjectMapper(), unavailableAi, new EventClassifier());

  @Test
  void parsesAllFieldsFromSchemaOrgEventJsonLd() {
    String json =
        """
        {
          "@context": "https://schema.org",
          "@type": "Event",
          "name": "Concert",
          "description": "Live show",
          "startDate": "2026-06-01T20:00:00+02:00",
          "endDate": "2026-06-01T22:00:00+02:00",
          "location": {
            "@type": "Place",
            "name": "Arena",
            "address": {"streetAddress": "Main St 1", "addressLocality": "Berlin"}
          },
          "organizer": {"@type": "Organization", "name": "EventCorp"}
        }
        """;

    ExtractedEventDTO event = parser.parse(jsonLdMail(json), "msg-1").orElseThrow();

    assertThat(event.getTitle()).isEqualTo("Concert");
    assertThat(event.getDescription()).isEqualTo("Live show");
    assertThat(event.getDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(event.getStartTime()).isEqualTo(LocalTime.of(20, 0));
    assertThat(event.getEndTime()).isEqualTo(LocalTime.of(22, 0));
    assertThat(event.getTimeZone()).isEqualTo("+02:00");
    assertThat(event.getLocation()).isEqualTo("Arena, Main St 1, Berlin");
    assertThat(event.getOrganizer()).isEqualTo("EventCorp");
    assertThat(event.getSourceMessageId()).isEqualTo("msg-1");
  }

  @Test
  void parsesReservationJsonLdViaReservationFor() {
    String json =
        """
        {
          "@context": "https://schema.org",
          "@type": "EventReservation",
          "reservationFor": {
            "@type": "Event",
            "name": "Theatre",
            "startDate": "2026-07-10T19:30:00",
            "location": {"@type": "Place", "name": "Grand Theatre"}
          }
        }
        """;

    ExtractedEventDTO event = parser.parse(jsonLdMail(json), "msg-2").orElseThrow();

    assertThat(event.getTitle()).isEqualTo("Theatre");
    assertThat(event.getDate()).isEqualTo(LocalDate.of(2026, 7, 10));
    assertThat(event.getStartTime()).isEqualTo(LocalTime.of(19, 30));
    assertThat(event.getLocation()).isEqualTo("Grand Theatre");
  }

  @Test
  void fallsBackToSubjectAndBodyWhenNoStructuredDataAndNoAi() {
    ExtractedEventDTO event =
        parser
            .parse(textMail("Flight confirmation", "Your flight is booked."), "msg-3")
            .orElseThrow();

    assertThat(event.getTitle()).isEqualTo("Flight confirmation");
    // Without structured data or AI, the full email body is used as the description
    // so the user still sees the email content instead of an empty form.
    assertThat(event.getDescription()).isEqualTo("Your flight is booked.");
    assertThat(event.getDate()).isNull();
    assertThat(event.getStartTime()).isNull();
  }

  @Test
  void stripsStyleAndScriptFromHtmlBodyDescription() {
    String html =
        "<html><head><style>.a{color:red}</style>"
            + "<script>var x=1;</script></head>"
            + "<body><p>Your ticket for the Concert is confirmed.</p>"
            + "<p>See you &amp; enjoy!</p></body></html>";

    ExtractedEventDTO event =
        parser
            .parse(mail("text/html; charset=UTF-8", "Concert ticket", html), "msg-7")
            .orElseThrow();

    assertThat(event.getDescription())
        .contains("Your ticket for the Concert is confirmed.")
        .contains("See you & enjoy!")
        .doesNotContain("color:red")
        .doesNotContain("var x=1")
        .doesNotContain("<");
  }

  @Test
  void cleansHtmlEvenWhenDeliveredAsTextPlain() {
    String html =
        "<!DOCTYPE html><html lang=\"en\"><head><title>Verify your email</title>"
            + "<style>a.button-link{background-color:#0f1122;color:#fff}</style></head>"
            + "<body><p>Please confirm your ticket for the Concert.</p></body></html>";

    ExtractedEventDTO event =
        parser
            .parse(mail("text/plain; charset=UTF-8", "Verify your email", html), "msg-8")
            .orElseThrow();

    assertThat(event.getDescription())
        .contains("Please confirm your ticket for the Concert.")
        .doesNotContain("<!DOCTYPE")
        .doesNotContain("background-color")
        .doesNotContain("<");
  }

  @Test
  void ignoresEmailsWithoutEventInformation() {
    Optional<ExtractedEventDTO> event =
        parser.parse(textMail("Weekly newsletter", "Read our latest blog posts."), "msg-4");

    assertThat(event).isEmpty();
  }

  @Test
  void returnsEmptyForUnreadableContentInsteadOfThrowing() {
    assertThat(parser.parse(new byte[0], "msg-5")).isEmpty();
    assertThat(parser.parse("not a real mime message".getBytes(StandardCharsets.UTF_8), "msg-6"))
        .isEmpty();
  }

  private byte[] jsonLdMail(String json) {
    return mail("application/ld+json; charset=UTF-8", "JSON-LD mail", json);
  }

  private byte[] textMail(String subject, String body) {
    return mail("text/plain; charset=UTF-8", subject, body);
  }

  private byte[] mail(String contentType, String subject, String body) {
    String rawMail =
        """
        Subject: %s
        Content-Type: %s

        %s
        """
            .formatted(subject, contentType, body);
    return rawMail.getBytes(StandardCharsets.UTF_8);
  }
}
