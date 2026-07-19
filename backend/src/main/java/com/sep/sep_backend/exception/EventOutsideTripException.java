package com.sep.sep_backend.exception;

/** Raised when an event's time range lies outside the assigned trip's date range. */
public class EventOutsideTripException extends RuntimeException {

  public EventOutsideTripException(String message) {
    super(message);
  }
}
