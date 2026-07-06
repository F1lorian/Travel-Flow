package com.sep.sep_backend.gmailimport;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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
   * Strongly promotional terms. An email carrying one of these is treated as marketing/newsletter
   * mail and skipped, even if it happens to contain an event keyword (sales blasts routinely say
   * things like "book now" or "letzte Tickets").
   */
  private static final List<String> PROMO_KEYWORDS =
      List.of(
          "sale",
          "rabatt",
          "rabatte",
          "gutschein",
          "sonderangebot",
          "newsletter",
          "coupon",
          "voucher",
          "deal",
          "deals");

  /**
   * Phrases that mark bulk newsletters (job alerts, promotions) which are not personal events.
   * These are matched as substrings because they are distinctive multi-word phrases unlikely to
   * appear in a genuine booking or invitation.
   */
  private static final List<String> NEWSLETTER_PHRASES =
      List.of(
          "% off",
          "black friday",
          "job alert",
          "jobalert",
          "neue jobs",
          "new jobs",
          "stellenangebot",
          "stellenanzeige",
          "job-benachrichtigung");

  /**
   * Whole-word keyword match. Word boundaries matter: a plain substring search flags "event" inside
   * "eventually", "gate" inside "navigate" or "termin" inside "determine", which made long
   * marketing emails look like events.
   */
  private static final Pattern EVENT_PATTERN = wordPattern(EVENT_KEYWORDS);

  private static final Pattern PROMO_PATTERN = wordPattern(PROMO_KEYWORDS);

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
    if (!EVENT_PATTERN.matcher(haystack).find()) {
      return false;
    }
    // Even with an event keyword, drop obvious promotional mail and bulk newsletters (deals, sales,
    // job alerts), which are not personal calendar events.
    if (PROMO_PATTERN.matcher(haystack).find()) {
      return false;
    }
    for (String phrase : NEWSLETTER_PHRASES) {
      if (haystack.contains(phrase)) {
        return false;
      }
    }
    return true;
  }

  private static Pattern wordPattern(List<String> keywords) {
    return Pattern.compile(
        "\\b(?:" + String.join("|", keywords) + ")\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
  }
}
