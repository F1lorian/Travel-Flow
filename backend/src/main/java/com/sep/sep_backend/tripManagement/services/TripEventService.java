package com.sep.sep_backend.tripManagement.services;

/* back-front relation: TripEventService handles all trip-event business logic.
Called by TripEventController, which exposes the results to the Angular frontend. */

import com.sep.sep_backend.entities.Categories;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the events within trips: adding an event (resolving its category), listing a
 * trip's events, deleting an event, and moving an event to another trip. Sits between {@link
 * com.sep.sep_backend.tripManagement.controllers.TripEventController} and the JPA repositories.
 */
@Service
public class TripEventService {

  private final EventRepository eventRepository;
  private final TripRepository tripRepository;
  private final CategoryService categoryService;
  private final UserEventRepository userEventRepository;
  private final UserTripRepository userTripRepository;

  public TripEventService(
      EventRepository eventRepository,
      TripRepository tripRepository,
      CategoryService categoryService,
      UserEventRepository userEventRepository,
      UserTripRepository userTripRepository) {
    this.eventRepository = eventRepository;
    this.tripRepository = tripRepository;
    this.categoryService = categoryService;
    this.userEventRepository = userEventRepository;
    this.userTripRepository = userTripRepository;
  }

  /**
   * Adds a new event to an existing trip.
   *
   * <p>Only the name is required (the Event entity marks it nullable=false); times, cost and
   * category are optional. If a category name is given, it's resolved through {@link
   * CategoryService#getOrCreate} so typing a new label creates it and reusing an existing one links
   * to it.
   */
  @Transactional
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

    Event savedEvent = eventRepository.save(newEvent);
    addTripMembersToEvent(savedEvent, tripId);
    return savedEvent;
  }

  private void addTripMembersToEvent(Event event, Long tripId) {
    for (UserTrip userTrip : userTripRepository.findByIdTripId(tripId)) {
      if (userTrip.getStatus() == UserTrip.InviteStatus.DECLINED) {
        continue;
      }
      if (userEventRepository.existsByEvent_IdAndUser_Id(
          event.getId(), userTrip.getUser().getId())) {
        continue;
      }

      User creator = userTrip.getInvitedBy() != null ? userTrip.getInvitedBy() : userTrip.getUser();
      UserEvent userEvent = new UserEvent(event, userTrip.getUser(), creator);
      userEvent.setStatus(toEventInviteStatus(userTrip.getStatus()));
      userEventRepository.save(userEvent);
    }
  }

  private UserEvent.InviteStatus toEventInviteStatus(UserTrip.InviteStatus tripStatus) {
    return tripStatus == UserTrip.InviteStatus.ACCEPTED
        ? UserEvent.InviteStatus.ACCEPTED
        : UserEvent.InviteStatus.PENDING;
  }

  public List<Event> getEventsForTrip(Long tripId) {
    return eventRepository.findByTrip_Id(tripId);
  }

  // Deletes an event by id, or throws if it doesn't exist.
  @Transactional
  public void deleteEvent(Long eventId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    userEventRepository.deleteByEvent_Id(eventId);
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
