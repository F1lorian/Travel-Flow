package com.sep.sep_backend.ausgabe;

import com.sep.sep_backend.entities.Categories;
import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.Trip;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AusgabeResponseDTO {
  private long id;
  private double kosten;
  private String trip;
  private LocalDate date;
  private String kategorie;
  private String sourceType;
  private String eventName;
  private Long tripId;
  private LocalDateTime start;
  private LocalDateTime end;

  public AusgabeResponseDTO() {}

  public AusgabeResponseDTO(double kosten, Trip trip, LocalDate date, Categories kategorie) {
    this(0, kosten, trip, date, kategorie);
  }

  public AusgabeResponseDTO(
      long id, double kosten, Trip trip, LocalDate date, Categories kategorie) {
    this.id = id;
    this.kosten = kosten;
    this.trip = trip != null ? trip.getName() : "";
    this.date = date;
    this.kategorie = kategorie != null ? kategorie.getName() : "OTHER";
    this.sourceType = "EXPENSE";
    this.tripId = trip != null ? trip.getId() : null;
  }

  public AusgabeResponseDTO(Event event) {
    this.id = event.getId();
    this.kosten = event.getKosten();
    this.trip = event.getTrip() != null ? event.getTrip().getName() : "";
    this.date = event.getStartTime() != null ? event.getStartTime().toLocalDate() : null;
    this.kategorie = event.getCategorie() != null ? event.getCategorie().getName() : "OTHER";
    this.sourceType = "EVENT";
    this.eventName = event.getName();
    this.tripId = event.getTripId();
    this.start = event.getStartTime();
    this.end = event.getEndTime();
  }

  // Getter
  public long getId() {
    return this.id;
  }

  public double getKosten() {
    return this.kosten;
  }

  public String getTrip() {
    return this.trip;
  }

  public LocalDate getDate() {
    return this.date;
  }

  public String getKategorie() {
    return this.kategorie;
  }

  public String getSourceType() {
    return this.sourceType;
  }

  public String getEventName() {
    return this.eventName;
  }

  public Long getTripId() {
    return this.tripId;
  }

  public LocalDateTime getStart() {
    return this.start;
  }

  public LocalDateTime getEnd() {
    return this.end;
  }

  // Setter
  public void setId(long id) {
    this.id = id;
  }

  public void setKosten(double kosten) {
    this.kosten = kosten;
  }

  public void setTrip(String trip) {
    this.trip = trip;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public void setKategorie(String kategorie) {
    this.kategorie = kategorie;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public void setEventName(String eventName) {
    this.eventName = eventName;
  }

  public void setTripId(Long tripId) {
    this.tripId = tripId;
  }

  public void setStart(LocalDateTime start) {
    this.start = start;
  }

  public void setEnd(LocalDateTime end) {
    this.end = end;
  }
}
