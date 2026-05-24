package com.sep.sep_backend.Enitys;

import com.sep.sep_backend.ausgabe.Kategorie;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Ausgabe {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  private double kosten;
  private LocalDate date;
  private Kategorie kategorie;

  public Ausgabe() {}

  public Ausgabe(double kosten, Trip trip, LocalDate date, Kategorie kategorie) {
    this.kosten = kosten;
    this.trip = trip;
    this.date = date;
    this.kategorie = kategorie;
  }

  @ManyToOne
  @JoinColumn(name = "trip_id")
  private Trip trip;

  // Getter und Setter
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public double getKosten() {
    return kosten;
  }

  public void setKosten(double kosten) {
    this.kosten = kosten;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip trip) {
    this.trip = trip;
  }

  public LocalDate getDate() {
    return this.date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public Kategorie getKategorie() {
    return this.kategorie;
  }

  public void setKategorie(Kategorie kategorie) {
    this.kategorie = kategorie;
  }
}
