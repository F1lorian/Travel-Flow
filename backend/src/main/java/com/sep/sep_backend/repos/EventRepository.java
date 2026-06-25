package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {
  List<Event> findByTrip_Id(Long tripId);

  List<Event> findByStartTimeBetween(LocalDateTime dateTimeAfter, LocalDateTime dateTimeBefore);

  List<Event> findByStartTimeBeforeAndEndTimeAfter(LocalDateTime endTime, LocalDateTime startTime);

}
