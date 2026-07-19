package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.Trip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for loading trips directly and through user membership rows. */
public interface TripRepository extends JpaRepository<Trip, Long> {
  Trip findByName(String name);

  @Query(
"""
    SELECT DISTINCT t FROM Trip t
    JOIN t.userTrips ut
    JOIN ut.user u
    WHERE u.id = :userId
""")
  List<Trip> findAllByUserId(@Param("userId") Long userId);

  @Query(
"""
    SELECT DISTINCT t FROM Trip t
    JOIN t.userTrips ut
    JOIN ut.user u
    WHERE u.id = :userId AND ut.status = 'ACCEPTED'
""")
  List<Trip> findAcceptedByUserId(@Param("userId") Long userId);
}
