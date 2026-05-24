package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Trip;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AusgabeRepository extends JpaRepository<Ausgabe, Long> {

  List<Ausgabe> findByDateBetween(LocalDate start, LocalDate end);

  List<Ausgabe> findAllByTrip(Trip trip);
}
