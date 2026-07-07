package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.UserEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
  List<UserEvent> findByEvent_Id(Long eventId);

  void deleteByEvent_Id(Long eventId);

  void deleteByEvent_Trip_Id(Long tripId);

  List<UserEvent> findByUser_Id(Long userId);

  List<UserEvent> findByUser_IdAndStatus(Long userId, UserEvent.InviteStatus status);

  Optional<UserEvent> findByEvent_IdAndUser_Id(Long eventId, Long userId);

  boolean existsByEvent_IdAndUser_Id(Long eventId, Long userId);

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
