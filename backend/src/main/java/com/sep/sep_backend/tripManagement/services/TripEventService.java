package com.sep.sep_backend.tripManagement.services;

/* back-front relation: TripEventService handles all trip-event business logic.
Called by TripEventController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.entities.Categories;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business logic for the events within trips: adding an event (resolving its category), listing a
 * trip's events, deleting an event, and moving an event to another trip. Sits between {@link
 * com.sep.sep_backend.tripManagement.controllers.TripEventController} and the JPA repositories.
 *
 * <p>Every operation takes the acting user's id and checks trip membership through {@link
 * TripService}, so events can only be read or changed by members of the trip they belong to.
 */
@Service
public class TripEventService {

  private final EventRepository eventRepository;
  private final TripService tripService;
  private final CategoryService categoryService;
  private final UserEventRepository userEventRepository;
  private final UserRepository userRepository;
  private final EmailNotificationService emailNotificationService;

  public TripEventService(
      EventRepository eventRepository,
      TripService tripService,
      CategoryService categoryService,
      UserEventRepository userEventRepository,
      UserRepository userRepository,
      EmailNotificationService emailNotificationService) {
    this.eventRepository = eventRepository;
    this.tripService = tripService;
    this.categoryService = categoryService;
    this.userEventRepository = userEventRepository;
    this.userRepository = userRepository;
    this.emailNotificationService = emailNotificationService;
  }

  /**
   * Adds a new event to a trip the user is a member of.
   *
   * <p>Only the name is required (the Event entity marks it nullable=false); times, cost and
   * category are optional. If a category name is given, it's resolved through {@link
   * CategoryService#getOrCreate} so typing a new label creates it and reusing an existing one links
   * to it.
   */
  public Event addEventToTrip(
      Long tripId,
      Long userId,
      String name,
      LocalDateTime startTime,
      LocalDateTime endTime,
      int kosten,
      String categoryName) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Event name cannot be empty");
    }

    // Resolves the trip and verifies membership in one step (404 unknown trip, 403 non-member).
    Trip trip = tripService.getTripForMember(tripId, userId);

    Event newEvent = new Event(name, startTime, endTime, kosten, trip);

    // Category is optional; only attach one when a non-blank name was provided.
    if (categoryName != null && !categoryName.isBlank()) {
      Categories category = categoryService.getOrCreate(categoryName);
      newEvent.setCategorie(category);
    }

    Event saved = eventRepository.save(newEvent);
    notifyByEmail(
        userId,
        "Event \"" + saved.getName() + "\" added",
        "The event \"" + saved.getName() + "\" was added to your trip \"" + trip.getName() + "\".");
    return saved;
  }

  /** Lists a trip's events, provided the user is a member of that trip. */
  public List<Event> getEventsForTrip(Long tripId, Long userId) {
    tripService.getTripForMember(tripId, userId);
    return eventRepository.findByTrip_Id(tripId);
  }

  /** Deletes an event, provided the user is a member of the trip the event belongs to. */
  @Transactional
  public void deleteEvent(Long eventId, Long userId) {
    Event event = getEventOrThrow(eventId);
    requireEventTripMember(event, userId);
    userEventRepository.deleteByEvent_Id(eventId);
    eventRepository.delete(event);
    notifyByEmail(
        userId,
        "Event \"" + event.getName() + "\" deleted",
        "The event \""
            + event.getName()
            + "\" was deleted from your trip \""
            + event.getTrip().getName()
            + "\".");
  }

  /**
   * Re-links an event to a different trip (the "move to another trip" feature). The user must be a
   * member of both the source and the destination trip, and moving an event to the trip it already
   * belongs to is rejected as a no-op.
   */
  public Event moveEventToTrip(Long eventId, Long newTripId, Long userId) {
    Event event = getEventOrThrow(eventId);
    requireEventTripMember(event, userId);
    Trip newTrip = tripService.getTripForMember(newTripId, userId);

    if (event.getTrip().getId() == newTrip.getId()) {
      throw new IllegalArgumentException("Event already belongs to this trip");
    }

    // Changing the trip reference and saving moves the event (updates its foreign key).
    event.setTrip(newTrip);
    Event moved = eventRepository.save(event);
    notifyByEmail(
        userId,
        "Event \"" + moved.getName() + "\" moved",
        "The event \""
            + moved.getName()
            + "\" was moved to your trip \""
            + newTrip.getName()
            + "\".");
    return moved;
  }

  private Event getEventOrThrow(Long eventId) {
    return eventRepository
        .findById(eventId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + eventId));
  }

  /**
   * Mirrors an in-app notification to the acting user's inbox, best-effort: every notification the
   * user sees in the bell is also announced by email. A missing user or mail failure is ignored so
   * the action itself never fails because of the email.
   */
  private void notifyByEmail(Long userId, String subject, String body) {
    userRepository
        .findById(userId)
        .ifPresent(user -> emailNotificationService.sendBestEffort(user.getEmail(), subject, body));
  }

  /**
   * An event can only be managed through the trip endpoints by members of its trip. Events without
   * a trip (standalone calendar events) are reported as not found rather than crashing.
   */
  private void requireEventTripMember(Event event, Long userId) {
    if (event.getTrip() == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Event does not belong to a trip: " + event.getId());
    }
    tripService.requireMember(event.getTrip().getId(), userId);
  }
}
