package com.sep.sep_backend.repos;

import com.sep.sep_backend.Enitys.Ausgabe;
import com.sep.sep_backend.Enitys.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AusgabeRepository extends JpaRepository<com.sep.sep_backend.Enitys.Ausgabe, Long> {

    List<Ausgabe> findByDatumBetween(LocalDate start, LocalDate end);

    List<Ausgabe> findAllByTrip(Trip trip);

}
