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

/**
 * Business logic for the events within trips: adding an event (resolving its category), listing a
 * trip's events, deleting an event, and moving an event to another trip. Sits between {@link
 * com.sep.sep_backend.TripManagement.controllers.TripEventController} and the JPA repositories.
 */
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

  /**
   * Adds a new event to an existing trip.
   *
   * <p>Only the name is required (the Event entity marks it nullable=false); times, cost and
   * category are optional. If a category name is given, it's resolved through {@link
   * CategoryService#getOrCreate} so typing a new label creates it and reusing an existing one links
   * to it.
   */
  public Event addEventToTrip(
      Long tripId,
      String name,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int kosten,
      String categoryName) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Event name cannot be empty");
    }

    // The event must belong to a real trip — fail clearly if the id is unknown.
    Trip trip =
        tripRepository
            .findById(tripId)
            .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

    Event newEvent = new Event(name, startTime, endTime, kosten, trip);

    // Category is optional; only attach one when a non-blank name was provided.
    if (categoryName != null && !categoryName.isBlank()) {
      Categories category = categoryService.getOrCreate(categoryName);
      newEvent.setCategorie(category);
    }

    return eventRepository.save(newEvent);
  }

  public List<Event> getEventsForTrip(Long tripId) {
    return eventRepository.findByTrip_Id(tripId);
  }

  // Deletes an event by id, or throws if it doesn't exist.
  public void deleteEvent(Long eventId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    eventRepository.delete(event);
  }

  /**
   * Re-links an event to a different trip (the "move to another trip" feature). Both the event and
   * the destination trip must exist, and moving an event to the trip it already belongs to is
   * rejected as a no-op.
   */
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

    // Changing the trip reference and saving moves the event (updates its foreign key).
    event.setTrip(newTrip);
    return eventRepository.save(event);
  }
}
