package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByTripId(Long tripId);

    List<Event> findByDateTimeBetween(LocalDateTime dateTimeAfter, LocalDateTime dateTimeBefore);
}
