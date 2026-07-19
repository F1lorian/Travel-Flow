package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.Ausgabe;
import com.sep.sep_backend.entities.Trip;
import com.sep.sep_backend.entities.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for querying manual expenses by date, trip, and owner. */
public interface AusgabeRepository extends JpaRepository<Ausgabe, Long> {

  List<Ausgabe> findByDateBetween(LocalDate start, LocalDate end);

  List<Ausgabe> findByTrip(Trip trip);

  void deleteByTrip_Id(Long tripId);

  List<Ausgabe> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);

  List<Ausgabe> findByUserAndTrip(User user, Trip trip);

  Optional<Ausgabe> findByIdAndUser(Long id, User user);
}
