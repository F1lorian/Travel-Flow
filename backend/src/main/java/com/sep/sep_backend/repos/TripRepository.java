package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Trip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {
  Trip findByName(String name);

  @Query("SELECT t FROM User u JOIN u.trips t WHERE u.name = :username")
  List<Trip> findTripsByUsername(@Param("username") String username);

  @Query("SELECT COUNT(u) FROM User u JOIN u.trips t WHERE u.name = :username AND t.id = :tripId")
  long countByUsernameAndTripId(@Param("username") String username, @Param("tripId") Long tripId);
}
