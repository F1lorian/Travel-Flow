package com.sep.sep_backend.TripManagement.services;

/* back-front relation: EventService handles all event business logic.
   Called by EventController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TripRepository tripRepository;

    public EventService(EventRepository eventRepository, TripRepository tripRepository) {
        this.eventRepository = eventRepository;
        this.tripRepository = tripRepository;
    }

    public Event addEventToTrip(Long tripId, String name, LocalDateTime dateTime, int kosten) {
        // Validate input first — fail fast before touching the database
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be empty");
        }
        if (dateTime == null) {
            throw new IllegalArgumentException("Event date/time is required");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        Event savedEvent = eventRepository.save(new Event(name, dateTime, kosten, trip));
        notifyRelatedUsers(savedEvent);
        return savedEvent;
    }

    public List<Event> getEventsForTrip(Long tripId) {
        return eventRepository.findByTripId(tripId);
    }

    // Stub: notify users when an event is added — to be implemented
    private void notifyRelatedUsers(Event event) {
        System.out.println("Notification: New event '" + event.getName()
                + "' added to trip ID " + event.getTrip().getId());
    }
}