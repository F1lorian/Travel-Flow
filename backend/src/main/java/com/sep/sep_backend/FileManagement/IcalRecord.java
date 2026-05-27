package com.sep.sep_backend.FileManagement;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
