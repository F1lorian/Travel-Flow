package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {
  List<Event> findByTrip_Id(Long tripId);

  void deleteByTrip_Id(Long tripId);

  List<Event> findByStartTimeBetween(LocalDateTime dateTimeAfter, LocalDateTime dateTimeBefore);

  @Query(
"""
    SELECT DISTINCT e FROM Event e
    JOIN UserEvent ue ON ue.event.id = e.id
    WHERE ue.user = :user AND e.startTime BETWEEN :dateTimeAfter AND :dateTimeBefore
""")
  List<Event> findByUsersContainingAndStartTimeBetween(
      @Param("user") User user,
      @Param("dateTimeAfter") LocalDateTime dateTimeAfter,
      @Param("dateTimeBefore") LocalDateTime dateTimeBefore);

  @Query(
"""
    SELECT DISTINCT e FROM Event e
    JOIN UserEvent ue ON ue.event.id = e.id
    WHERE ue.user = :user AND e.trip.id = :tripId
""")
  List<Event> findByUsersContainingAndTrip_Id(
      @Param("user") User user, @Param("tripId") Long tripId);

  @Query(
      """
      SELECT DISTINCT e FROM Event e
      JOIN UserEvent ue ON ue.event.id = e.id
      WHERE ue.user.id = :userId
        AND ue.status IN ('ACCEPTED', 'PENDING')
        AND e.startTime < :endTime
        AND e.endTime > :startTime
  """)
  List<Event> findConflictingEventsForUser(
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("userId") Long userId);
}
