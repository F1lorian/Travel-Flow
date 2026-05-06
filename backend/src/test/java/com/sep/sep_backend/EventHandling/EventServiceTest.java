package com.sep.sep_backend.EventHandling;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void saveEventToDBSpeichertEventFuerVorhandeneTripId() throws Exception {
        Trip trip = new Trip("London");
        EventRequestDTO request = new EventRequestDTO("Museum", 25, 1L);
        LocalDateTime beforeSave = LocalDateTime.now();

        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        saveEventToDB(request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getName()).isEqualTo("Museum");
        assertThat(savedEvent.getKosten()).isEqualTo(25);
        assertThat(savedEvent.getTrip()).isSameAs(trip);
        assertThat(savedEvent.getDateTime()).isAfterOrEqualTo(beforeSave);
    }

    @Test
    void saveEventToDBWirftExceptionWennTripNichtExistiert() {
        EventRequestDTO request = new EventRequestDTO("Museum", 25, 99L);
        when(tripRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saveEventToDB(request))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Zu dieser trip Id findet sich kein eintrag in der Datenbank");

        verify(eventRepository, never()).save(any(Event.class));
    }

    private void saveEventToDB(EventRequestDTO request)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = EventService.class.getDeclaredMethod("SaveEventToDB", EventRequestDTO.class);
        method.invoke(eventService, request);
    }
}
