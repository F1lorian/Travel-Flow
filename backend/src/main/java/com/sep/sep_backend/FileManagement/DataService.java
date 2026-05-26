package com.sep.sep_backend.FileManagement;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
class DataService {
  private static final DateTimeFormatter EVENT_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
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
    List<IcalRecord> events = eventRepository.findAll().stream().map(this::toIcalRecord).toList();
    List<IcalRecord> trips =
        tripRepository.findAll().stream()
            .filter(trip -> trip.getDateTime() != null)
            .map(this::toIcalRecord)
            .toList();
    return buildCalendar(events, trips);
  }

  private IcalRecord toIcalRecord(Event event) {
    String description = event.getTrip() == null ? "" : "Trip: " + event.getTrip().getName();
    return new IcalRecord(
        "event-" + event.getId() + "@sep-backend",
        event.getName(),
        description,
        event.getDateTime());
  }

  private IcalRecord toIcalRecord(Trip trip) {
    return new IcalRecord(
        "trip-" + trip.getId() + "@sep-backend", trip.getName(), "Reise", trip.getDateTime());
  }

  private String buildCalendar(List<IcalRecord> events, List<IcalRecord> trips) {
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
    return """
    BEGIN:VEVENT
    UID:%s
    DTSTAMP:%s
    DTSTART:%s
    SUMMARY:%s
    DESCRIPTION:%s
    END:VEVENT
    """
        .formatted(
            event.uid(),
            dtStamp,
            EVENT_TIME_FORMATTER.format(event.startDateTime()),
            escapeIcs(event.title()),
            escapeIcs(event.description()));
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
