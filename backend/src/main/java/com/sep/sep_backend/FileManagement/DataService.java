package com.sep.sep_backend.FileManagement;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for exporting events and trips as iCalendar data.
 */
@Service
class DataService {
  private static final DateTimeFormatter EVENT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter UTC_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final EventRepository eventRepository;
  private final TripRepository tripRepository;

  /**
   * Creates a data service with access to event and trip data.
   *
   * @param eventRepository repository used to load events for export
   * @param tripRepository repository used to load trips for export
   */
  @Autowired
  public DataService(EventRepository eventRepository, TripRepository tripRepository) {
    this.eventRepository = eventRepository;
    this.tripRepository = tripRepository;
  }

  /**
   * Exports all stored events and trips as an iCalendar string.
   *
   * @return complete iCalendar content containing event and trip entries
   */
  public String exportAllEventsAsIcal() {
    List<IcalRecord> events =
        eventRepository.findAll().stream()
            .filter(event -> event.getStartTime() != null)
            .map(this::toIcalRecord)
            .toList();
    List<IcalRecord> trips =
        tripRepository.findAll().stream()
            .filter(trip -> trip.getStartDate() != null)
            .map(this::toIcalRecord)
            .toList();
    return buildCalendar(events, trips);
  }

  private IcalRecord toIcalRecord(Event event) {
    String description = event.getTrip() == null ? "" : "Trip: " + event.getTrip().getName();
    LocalDateTime endDateTime =
        event.getEndTime() != null && event.getEndTime().isAfter(event.getStartTime())
            ? event.getEndTime()
            : null;
    return IcalRecord.timed(
        "event-" + event.getId() + "@sep-backend",
        event.getName(),
        description,
        event.getStartTime(),
        endDateTime);
  }

  private IcalRecord toIcalRecord(Trip trip) {
    LocalDate endDateInclusive =
        trip.getEndDate() != null && !trip.getEndDate().isBefore(trip.getStartDate())
            ? trip.getEndDate()
            : trip.getStartDate();
    return IcalRecord.allDay(
        "trip-" + trip.getId() + "@sep-backend",
        "Reise: " + trip.getName(),
        "Reise",
        trip.getStartDate(),
        endDateInclusive.plusDays(1));
  }

  private String buildCalendar(List<IcalRecord> events, List<IcalRecord> trips) {
    // Anfang von ics files
    StringBuilder calendar =
        new StringBuilder(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Meine App//Kalender Export//DE
            CALSCALE:GREGORIAN
            """);
    String dtStamp = UTC_FORMATTER.format(Instant.now());

    for (IcalRecord event : events) {
      calendar.append(buildEvent(event, dtStamp));
    }
    for (IcalRecord trip : trips) {
      calendar.append(buildEvent(trip, dtStamp));
    }

    calendar.append("END:VCALENDAR\n");
    return calendar.toString().replace("\n", "\r\n");
  }

  private String buildEvent(IcalRecord event, String dtStamp) {
    String dates =
        event.isAllDay()
            ? """
            DTSTART;VALUE=DATE:%s
            DTEND;VALUE=DATE:%s
            """
                .formatted(
                    DATE_FORMATTER.format(event.startDate()),
                    DATE_FORMATTER.format(event.endDateExclusive()))
            : buildTimedDates(event);
    return """
    BEGIN:VEVENT
    UID:%s
    DTSTAMP:%s
    %sSUMMARY:%s
    DESCRIPTION:%s
    END:VEVENT
    """
        .formatted(
            event.uid(), dtStamp, dates, escapeIcs(event.title()), escapeIcs(event.description()));
  }

  private String buildTimedDates(IcalRecord event) {
    String start = "DTSTART:" + EVENT_TIME_FORMATTER.format(event.startDateTime()) + "\n";
    if (event.endDateTime() == null) {
      return start;
    }
    return start + "DTEND:" + EVENT_TIME_FORMATTER.format(event.endDateTime()) + "\n";
  }

  private String escapeIcs(String value) {
    if (value == null) {
      return "";
    }

    return value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
        .replace("\r", "");
  }
}
