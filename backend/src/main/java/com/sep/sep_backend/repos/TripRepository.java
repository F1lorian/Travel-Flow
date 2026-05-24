package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {
  Trip findByName(String name);
}
