package com.sep.sep_backend.FileManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceTest {

  @Test
  void exportAllEventsAsIcalExportsEventsWithTheirTrip() {
    EventRepository eventRepository = mock(EventRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    Trip trip = new Trip("Berlin");
    trip.setId(7L);
    trip.setStartDate(LocalDate.of(2026, 6, 1));
    trip.setEndDate(LocalDate.of(2026, 6, 2));
    Event museum =
        new Event(
            "Museum",
            LocalDateTime.of(2026, 6, 1, 10, 30),
            LocalDateTime.of(2026, 6, 1, 12, 0),
            20,
            trip);
    museum.setId(42L);
    Event concert =
        new Event(
            "Konzert",
            LocalDateTime.of(2026, 6, 2, 20, 0),
            LocalDateTime.of(2026, 6, 2, 22, 30),
            49,
            trip);
    concert.setId(43L);
    when(eventRepository.findAll()).thenReturn(List.of(museum, concert));
    when(tripRepository.findAll()).thenReturn(List.of(trip));

    String calendar = new DataService(eventRepository, tripRepository).exportAllEventsAsIcal();

    assertThat(calendar)
        .startsWith("BEGIN:VCALENDAR\r\n")
        .endsWith("END:VCALENDAR\r\n")
        .contains("UID:event-42@sep-backend\r\n")
        .contains("DTSTART:20260601T103000\r\n")
        .contains("DTEND:20260601T120000\r\n")
        .contains("SUMMARY:Museum\r\n")
        .contains("UID:event-43@sep-backend\r\n")
        .contains("DTSTART:20260602T200000\r\n")
        .contains("DTEND:20260602T223000\r\n")
        .contains("SUMMARY:Konzert\r\n")
        .contains("DESCRIPTION:Trip: Berlin\r\n")
        .contains("UID:trip-7@sep-backend\r\n")
        .contains("DTSTART;VALUE=DATE:20260601\r\n")
        .contains("DTEND;VALUE=DATE:20260603\r\n")
        .contains("SUMMARY:Reise: Berlin\r\n");
  }

  @Test
  void exportAllEventsAsIcalSkipsUndatedEventsAndExportsSingleDayTrips() {
    EventRepository eventRepository = mock(EventRepository.class);
    TripRepository tripRepository = mock(TripRepository.class);
    Trip trip = new Trip("Essen");
    trip.setId(8L);
    trip.setStartDate(LocalDate.of(2026, 7, 4));
    Event undatedEvent = new Event("Ohne Datum", null, null, 0, trip);
    undatedEvent.setId(44L);
    when(eventRepository.findAll()).thenReturn(List.of(undatedEvent));
    when(tripRepository.findAll()).thenReturn(List.of(trip));

    String calendar = new DataService(eventRepository, tripRepository).exportAllEventsAsIcal();

    assertThat(calendar)
        .doesNotContain("UID:event-44@sep-backend")
        .contains("UID:trip-8@sep-backend\r\n")
        .contains("DTSTART;VALUE=DATE:20260704\r\n")
        .contains("DTEND;VALUE=DATE:20260705\r\n");
  }
}
