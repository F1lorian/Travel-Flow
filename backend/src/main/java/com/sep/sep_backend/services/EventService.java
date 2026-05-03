package com.sep.sep_backend.services;

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

    // Core method: add an event to an existing trip
    public Event addEventToTrip(Long tripId, String name, LocalDateTime dateTime, int kosten) {
        // 1. Find the trip — must exist
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        // 2. Validate inputs
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be empty");
        }
        if (dateTime == null) {
            throw new IllegalArgumentException("Event date/time is required");
        }

        // 3. Create the event linked to the trip
        Event event = new Event(name, dateTime, kosten, trip);
        Event savedEvent = eventRepository.save(event);

        // 4. Notify related users (acceptance criteria)
        notifyRelatedUsers(savedEvent);

        return savedEvent;
    }

    // Get all events for a specific trip
    public List<Event> getEventsForTrip(Long tripId) {
        return eventRepository.findByTripId(tripId);
    }

    // Stub for the notification acceptance criteria — to be expanded next sprint
    private void notifyRelatedUsers(Event event) {
        System.out.println("Notification: New event '" + event.getName()
                + "' added to trip ID " + event.getTrip().getId());
    }
}
