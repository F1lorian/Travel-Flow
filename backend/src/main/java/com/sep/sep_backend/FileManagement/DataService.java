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

@Service
class DataService {
  private static final DateTimeFormatter EVENT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter UTC_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final EventRepository eventRepository;
  private final TripRepository tripRepository;

  @Autowired
  public DataService(EventRepository eventRepository, TripRepository tripRepository) {
    this.eventRepository = eventRepository;
    this.tripRepository = tripRepository;
  }

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

  // Events und trips werden zu einem Record gemacht (Expliziete darrstellung von Trips ist bei
  // Ical nicht vorgesehen daher gleiche behandlung)
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

  // Setzt dass Gesamte file als String zusammen (Umwandlung in ics im frontend informationen werden
  // im
  // header mirgegeben)
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

  // Baut aus den Records die formatierten strings für das ics file(Evnts können auch Trips sein ->
  // gleich Behandlung)
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

  // Bringt Datumsfelder ins richtige Format für ics files
  private String buildTimedDates(IcalRecord event) {
    String start = "DTSTART:" + EVENT_TIME_FORMATTER.format(event.startDateTime()) + "\n";
    if (event.endDateTime() == null) {
      return start;
    }
    return start + "DTEND:" + EVENT_TIME_FORMATTER.format(event.endDateTime()) + "\n";
  }

  // Innerhalb con ics files haben bestimmte Zeichen eine bedeutung daher werden diese heir ersetzt
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
