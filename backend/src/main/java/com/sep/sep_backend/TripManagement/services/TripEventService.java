package com.sep.sep_backend.TripManagement.services;

/* back-front relation: TripEventService handles all trip-event business logic.
Called by TripEventController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.Enitys.Categories;
import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TripEventService {

  private final EventRepository eventRepository;
  private final TripRepository tripRepository;
  private final CategoryService categoryService;

  public TripEventService(
      EventRepository eventRepository,
      TripRepository tripRepository,
      CategoryService categoryService) {
    this.eventRepository = eventRepository;
    this.tripRepository = tripRepository;
    this.categoryService = categoryService;
  }

  public Event addEventToTrip(
      Long tripId,
      String name,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int kosten,
      String categoryName) {
    // Only the name is required (matches the Event entity's nullable=false)
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Event name cannot be empty");
    }

    Trip trip =
        tripRepository
            .findById(tripId)
            .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

    Event newEvent = new Event(name, startTime, endTime, kosten, trip);

    if (categoryName != null && !categoryName.isBlank()) {
      Categories category = categoryService.getOrCreate(categoryName);
      newEvent.setCategorie(category);
    }

    return eventRepository.save(newEvent);
  }

  public List<Event> getEventsForTrip(Long tripId) {
    return eventRepository.findByTrip_Id(tripId);
  }

  public void deleteEvent(Long eventId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    eventRepository.delete(event);
  }

  public Event moveEventToTrip(Long eventId, Long newTripId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    Trip newTrip =
        tripRepository
            .findById(newTripId)
            .orElseThrow(() -> new IllegalArgumentException("Target trip not found: " + newTripId));

    if (event.getTrip() != null && event.getTrip().getId() == newTrip.getId()) {
      throw new IllegalArgumentException("Event already belongs to this trip");
    }

    event.setTrip(newTrip);
    return eventRepository.save(event);
  }
}
