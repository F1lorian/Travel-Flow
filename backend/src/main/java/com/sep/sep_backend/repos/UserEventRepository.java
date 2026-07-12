package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.UserEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link UserEvent} rows, which link a user to an event together with
 * their invite status (pending/accepted/declined) and who invited them.
 */
public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
  /** Lists every participation record attached to the given event. */
  List<UserEvent> findByEvent_Id(Long eventId);

  /** Deletes all participation records of the given event, e.g. when the event is removed. */
  void deleteByEvent_Id(Long eventId);

  /** Deletes the participation records of every event belonging to the given trip. */
  void deleteByEvent_Trip_Id(Long tripId);

  /** Lists every event participation of the given user, regardless of invite status. */
  List<UserEvent> findByUser_Id(Long userId);

  /** Lists the given user's event participations filtered by invite status. */
  List<UserEvent> findByUser_IdAndStatus(Long userId, UserEvent.InviteStatus status);

  /** Lists all participation records created (i.e. invitations issued) by the given user. */
  List<UserEvent> findByCreator_Id(Long userId);

  /** Returns whether any user is linked to the given event. */
  boolean existsByEvent_Id(Long eventId);

  /** Finds the participation record connecting a specific user to a specific event. */
  Optional<UserEvent> findByEvent_IdAndUser_Id(Long eventId, Long userId);

  /** Returns whether the given user is linked to the given event. */
  boolean existsByEvent_IdAndUser_Id(Long eventId, Long userId);

  /**
   * Returns every event the user actually takes part in: events they accepted directly, plus all
   * events of trips where their trip membership is accepted.
   */
  @Query(
"""
    SELECT DISTINCT e FROM Event e
        LEFT JOIN UserEvent ue ON ue.event.id = e.id
        LEFT JOIN e.trip t
        LEFT JOIN t.userTrips ut
        WHERE (ue.user.id = :userId AND ue.status = 'ACCEPTED')
           OR (ut.user.id = :userId AND ut.status = 'ACCEPTED')
""")
  List<Event> findAllByUserId(@Param("userId") Long userId);
}
