package com.sep.sep_backend.eventHandling;

/**
 * Outcome of checking one mail for an event during automatic import: an extracted event, a genuine
 * "no event", or a failure (detection could not run). Keeping these distinct lets the importer
 * report messages that could not be checked instead of silently dropping them.
 */
public record MailEventResult(EventResponseDTO event, boolean failed) {

  public static MailEventResult ofEvent(EventResponseDTO event) {
    return new MailEventResult(event, false);
  }

  public static MailEventResult noEvent() {
    return new MailEventResult(null, false);
  }

  public static MailEventResult failure() {
    return new MailEventResult(null, true);
  }

  public boolean hasEvent() {
    return event != null && !failed;
  }
}
