package com.sep.sep_backend.TestConfig;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.entities.UserTrip;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import com.sep.sep_backend.repos.UserTripRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Profile("test-users")
public class TestUserSeeder {

  @Bean
  CommandLineRunner seedTestUsers(
      UserRepository userRepository,
      TripRepository tripRepository,
      EventRepository eventRepository,
      UserTripRepository userTripRepository,
      UserEventRepository userEventRepository,
      PasswordEncoder passwordEncoder,
      PlatformTransactionManager transactionManager) {
    return args -> {
      TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
      transactionTemplate.executeWithoutResult(
          status -> {
            User testUser =
                createUserIfMissing(
                    userRepository,
                    passwordEncoder,
                    "testuser",
                    "testuser@example.com",
                    "Test1234!",
                    "Test",
                    "User");

            User friendUser =
                createUserIfMissing(
                    userRepository,
                    passwordEncoder,
                    "frienduser",
                    "frienduser@example.com",
                    "Test1234!",
                    "Friend",
                    "User");

            User strangerUser =
                createUserIfMissing(
                    userRepository,
                    passwordEncoder,
                    "strangeruser",
                    "strangeruser@example.com",
                    "Test1234!",
                    "Stranger",
                    "User");

            addFriendshipIfMissing(testUser, friendUser);
            userRepository.save(testUser);
            userRepository.save(friendUser);

            seedPersonalData(
                testUser, tripRepository, eventRepository, userTripRepository, userEventRepository);
            seedPersonalData(
                friendUser,
                tripRepository,
                eventRepository,
                userTripRepository,
                userEventRepository);
            seedPersonalData(
                strangerUser,
                tripRepository,
                eventRepository,
                userTripRepository,
                userEventRepository);

            seedFriendData(
                testUser,
                friendUser,
                tripRepository,
                eventRepository,
                userTripRepository,
                userEventRepository);
          });
    };
  }

  private User createUserIfMissing(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      String username,
      String email,
      String password,
      String firstName,
      String surname) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, email)
        .orElseGet(
            () -> {
              User user =
                  new User(
                      username,
                      email,
                      passwordEncoder.encode(password),
                      passwordEncoder.encode("blue"));
              user.setFirstName(firstName);
              user.setSurname(surname);
              user.setSecurityQuestion("What is your favorite color?");
              user.setEmailVerified(true);

              return userRepository.save(user);
            });
  }

  private void addFriendshipIfMissing(User user, User friend) {
    if (!hasFriend(user, friend)) {
      user.getFriends().add(friend);
    }
    if (!hasFriend(friend, user)) {
      friend.getFriends().add(user);
    }
  }

  private boolean hasFriend(User user, User friend) {
    return user.getFriends().stream()
        .anyMatch(existingFriend -> existingFriend.getId() == friend.getId());
  }

  private void seedPersonalData(
      User user,
      TripRepository tripRepository,
      EventRepository eventRepository,
      UserTripRepository userTripRepository,
      UserEventRepository userEventRepository) {
    String displayName = user.getName();
    LocalDate startDate = LocalDate.of(2026, 8, 1);
    LocalDate endDate = LocalDate.of(2026, 8, 5);

    createEventIfMissing(
        displayName + " Einzel-Event",
        null,
        user,
        user,
        UserEvent.InviteStatus.ACCEPTED,
        LocalDateTime.of(2026, 7, 20, 10, 0),
        LocalDateTime.of(2026, 7, 20, 12, 0),
        15.0,
        eventRepository,
        userEventRepository);

    Trip personalTrip =
        createTripIfMissing(
            displayName + " Test-Trip",
            user,
            null,
            startDate,
            endDate,
            tripRepository,
            userTripRepository);

    createEventIfMissing(
        displayName + " Trip-Event",
        personalTrip,
        user,
        user,
        UserEvent.InviteStatus.ACCEPTED,
        LocalDateTime.of(2026, 8, 2, 14, 0),
        LocalDateTime.of(2026, 8, 2, 16, 0),
        25.0,
        eventRepository,
        userEventRepository);
  }

  private void seedFriendData(
      User testUser,
      User friendUser,
      TripRepository tripRepository,
      EventRepository eventRepository,
      UserTripRepository userTripRepository,
      UserEventRepository userEventRepository) {
    Trip sharedTrip =
        createTripIfMissing(
            "Gemeinsamer Test-Trip",
            testUser,
            null,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 7),
            tripRepository,
            userTripRepository);
    addUserToTripIfMissing(friendUser, sharedTrip, testUser, userTripRepository);

    Event sharedTripEvent =
        createEventIfMissing(
            "Gemeinsames Trip-Event",
            sharedTrip,
            testUser,
            testUser,
            UserEvent.InviteStatus.ACCEPTED,
            LocalDateTime.of(2026, 9, 3, 18, 0),
            LocalDateTime.of(2026, 9, 3, 20, 0),
            40.0,
            eventRepository,
            userEventRepository);
    addUserToEventIfMissing(
        friendUser,
        sharedTripEvent,
        testUser,
        UserEvent.InviteStatus.PENDING,
        userEventRepository);

    Event invitedEvent =
        createEventIfMissing(
            "Eingeladenes Einzel-Event",
            null,
            testUser,
            testUser,
            UserEvent.InviteStatus.ACCEPTED,
            LocalDateTime.of(2026, 9, 10, 19, 0),
            LocalDateTime.of(2026, 9, 10, 21, 0),
            30.0,
            eventRepository,
            userEventRepository);
    addUserToEventIfMissing(
        friendUser,
        invitedEvent,
        testUser,
        UserEvent.InviteStatus.PENDING,
        userEventRepository);
  }

  private Trip createTripIfMissing(
      String name,
      User user,
      User invitedBy,
      LocalDate startDate,
      LocalDate endDate,
      TripRepository tripRepository,
      UserTripRepository userTripRepository) {
    Trip trip =
        tripRepository.findAll().stream()
            .filter(existingTrip -> name.equals(existingTrip.getName()))
            .findFirst()
            .orElseGet(
                () -> {
                  Trip newTrip = new Trip(name);
                  newTrip.setStartDate(startDate);
                  newTrip.setEndDate(endDate);
                  return tripRepository.save(newTrip);
                });

    addUserToTripIfMissing(user, trip, invitedBy, userTripRepository);
    return trip;
  }

  private void addUserToTripIfMissing(
      User user, Trip trip, User invitedBy, UserTripRepository userTripRepository) {
    if (!userTripRepository.existsByIdUserIdAndIdTripId(user.getId(), trip.getId())) {
      userTripRepository.save(new UserTrip(user, trip, invitedBy));
    }
  }

  private Event createEventIfMissing(
      String name,
      Trip trip,
      User participant,
      User creator,
      UserEvent.InviteStatus status,
      LocalDateTime startTime,
      LocalDateTime endTime,
      double kosten,
      EventRepository eventRepository,
      UserEventRepository userEventRepository) {
    Event event =
        eventRepository.findAll().stream()
            .filter(existingEvent -> name.equals(existingEvent.getName()))
            .filter(existingEvent -> hasSameTrip(existingEvent, trip))
            .findFirst()
            .orElseGet(
                () ->
                    eventRepository.save(new Event(name, startTime, endTime, kosten, trip)));

    addUserToEventIfMissing(participant, event, creator, status, userEventRepository);
    return event;
  }

  private boolean hasSameTrip(Event event, Trip trip) {
    if (event.getTrip() == null || trip == null) {
      return event.getTrip() == null && trip == null;
    }
    return event.getTrip().getId() == trip.getId();
  }

  private void addUserToEventIfMissing(
      User participant,
      Event event,
      User creator,
      UserEvent.InviteStatus status,
      UserEventRepository userEventRepository) {
    UserEvent userEvent =
        userEventRepository
            .findByEvent_IdAndUser_Id(event.getId(), participant.getId())
            .orElseGet(() -> new UserEvent(event, participant, creator));
    userEvent.setStatus(status);
    userEventRepository.save(userEvent);
  }
}
