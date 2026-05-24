package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Event;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByTrip_Id(Long tripId);

}
