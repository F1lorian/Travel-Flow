package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Event;
import com.sep.sep_backend.Enitys.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
    List<UserEvent> findByEvent_Id(Long eventId);
    List<UserEvent> findByUser_Id(Long userId);
    Optional<UserEvent> findByEvent_IdAndUser_Id(Long eventId, Long userId);
    boolean existsByEvent_IdAndUser_Id(Long eventId, Long userId);

    @Query("""
    SELECT DISTINCT e FROM Event e
    LEFT JOIN UserEvent ue ON ue.event.id = e.id
    WHERE ue.user.id = :userId
""")
    List<Event> findAllByUserId(@Param("userId") Long userId);
}
