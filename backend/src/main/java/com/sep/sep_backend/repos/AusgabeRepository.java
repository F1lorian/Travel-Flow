package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AusgabeRepository extends JpaRepository<Ausgabe, Long> {

    List<Ausgabe> findByDateBetween(LocalDate start, LocalDate end);

    List<Ausgabe> findAllByTrip(Trip trip);

}
