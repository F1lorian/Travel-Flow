package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Trip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {
  Trip findByName(String name);

  @Query(
"""
    SELECT DISTINCT t FROM Trip t
    JOIN t.users u
    WHERE u.id = :userId
""")
  List<Trip> findAllByUserId(@Param("userId") Long userId);
}
