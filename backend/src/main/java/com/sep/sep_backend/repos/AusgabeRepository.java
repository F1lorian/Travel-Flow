package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Trip;
import com.sep.sep_backend.Enitys.User;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AusgabeRepository extends JpaRepository<Ausgabe, Long> {

  List<Ausgabe> findByDateBetween(LocalDate start, LocalDate end);

  List<Ausgabe> findByTrip(Trip trip);

  void deleteByTrip_Id(Long tripId);

  List<Ausgabe> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);

  List<Ausgabe> findByUserAndTrip(User user, Trip trip);
}
