package com.sep.sep_backend.TripManagement.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripEventServiceTest {

  @Mock private EventRepository eventRepository;

  @Mock private TripRepository tripRepository;

  @InjectMocks private TripEventService eventService;

    private final LocalDateTime sampleStartTime= LocalDateTime.of(2026, 7, 15, 19, 30);
    private final LocalDateTime sampleEndTime = LocalDateTime.of(2026, 7, 16, 19, 30);


    @Test
    void addEventToTrip_savesAndReturnsEvent() {
        Trip trip = new Trip("Paris");
        Event event = new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(eventRepository.save(any(Event.class))).thenReturn(event);

        Event result = eventService.addEventToTrip(1L, "Dinner", sampleStartTime, sampleEndTime, 50);

    assertEquals("Dinner", result.getName());
    assertEquals(50, result.getKosten());
    verify(eventRepository).save(any(Event.class));
  }

  @Test
  void addEventToTrip_throwsWhenTripNotFound() {
    when(tripRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEventToTrip(99L, "Dinner", sampleStartTime, sampleEndTime, 50));
    }

    @Test
    void addEventToTrip_throwsWhenNameIsBlank() {
        // Validation runs before the trip lookup, so no repository stubbing is needed
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEventToTrip(1L, "", sampleStartTime, sampleEndTime, 50));
    }

    @Test
    void addEventToTrip_throwsWhenDateTimeIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEventToTrip(1L, "Dinner", null, null, 50));
    }

    @Test
    void getEventsForTrip_returnsEventList() {
        Trip trip = new Trip("Paris");
        List<Event> events = List.of(new Event("Dinner", sampleStartTime, sampleEndTime, 50, trip));
        when(eventRepository.findByTrip_Id(1L)).thenReturn(events);

    List<Event> result = eventService.getEventsForTrip(1L);

    assertEquals(1, result.size());
    assertEquals("Dinner", result.get(0).getName());
  }
}
