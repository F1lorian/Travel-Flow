package com.sep.sep_backend.filemanagement;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Internal representation of a calendar entry before it is written as iCalendar data.
 *
 * @param uid unique identifier of the calendar entry
 * @param title title shown for the calendar entry
 * @param description description shown for the calendar entry
 * @param startDateTime start date and time for timed entries
 * @param endDateTime optional end date and time for timed entries
 * @param startDate start date for all-day entries
 * @param endDateExclusive exclusive end date for all-day entries
 */
record IcalRecord(
    String uid,
    String title,
    String description,
    LocalDateTime startDateTime,
    LocalDateTime endDateTime,
    LocalDate startDate,
    LocalDate endDateExclusive) {

  static IcalRecord timed(
      String uid,
      String title,
      String description,
      LocalDateTime startDateTime,
      LocalDateTime endDateTime) {
    return new IcalRecord(uid, title, description, startDateTime, endDateTime, null, null);
  }

  static IcalRecord allDay(
      String uid,
      String title,
      String description,
      LocalDate startDate,
      LocalDate endDateExclusive) {
    return new IcalRecord(uid, title, description, null, null, startDate, endDateExclusive);
  }

  boolean isAllDay() {
    return startDate != null;
  }
}
