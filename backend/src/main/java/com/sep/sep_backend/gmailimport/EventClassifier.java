package com.sep.sep_backend.gmailimport;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Decides, cheaply and offline, whether an email is likely to describe a calendar-worthy event
 * (flight, hotel, restaurant, meeting, appointment, ticket, ...).
 *
 * <p>Running this before the (comparatively expensive) AI extraction lets the import step skip
 * marketing mail and newsletters, satisfying the requirement to "ignore emails that do not contain
 * event information".
 */
@Component
public class EventClassifier {

  /** Keywords that strongly hint at one of the supported event categories. */
  private static final List<String> EVENT_KEYWORDS =
      List.of(
          "flight",
          "boarding",
          "departure",
          "arrival",
          "itinerary",
          "check-in",
          "checkin",
          "reservation",
          "booking",
          "booked",
          "confirmed",
          "confirmation",
          "hotel",
          "stay",
          "table",
          "restaurant",
          "meeting",
          "invite",
          "invitation",
          "appointment",
          "ticket",
          "event",
          "seat",
          "gate",
          "concert",
          "rendez",
          "termin",
          "reservierung",
          "buchung");

  /**
   * Returns whether the email content looks like an event.
   *
   * @param subject email subject (may be {@code null})
   * @param body plain-text body (may be {@code null})
   * @param hasEventJsonLd whether structured schema.org event/reservation data was found
   * @return {@code true} if the email should be treated as a potential event
   */
  public boolean looksLikeEvent(String subject, String body, boolean hasEventJsonLd) {
    if (hasEventJsonLd) {
      return true;
    }
    String haystack =
        ((subject == null ? "" : subject) + " " + (body == null ? "" : body))
            .toLowerCase(Locale.ROOT);
    return EVENT_KEYWORDS.stream().anyMatch(haystack::contains);
  }
}
