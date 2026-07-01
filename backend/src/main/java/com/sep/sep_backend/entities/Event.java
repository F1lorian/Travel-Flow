package com.sep.sep_backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Event {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  @Column(nullable = false)
  private String name;

  private LocalDateTime startTime;

  private LocalDateTime endTime;

  private double kosten;

  /** Optional free-text description, e.g. filled from an imported email. */
  @Column(length = 4096)
  private String description;

  /** Optional human-readable location (venue, address, gate, ...). */
  private String location;

  /** Optional IANA time zone the event times refer to (e.g. Europe/Berlin). */
  private String timeZone;

  /** Optional organizer/host name, when known. */
  private String organizer;

  @ManyToOne
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne
  @JoinColumn(name = "categorie_id")
  private Categories categorie;

  public Event() {}

  public Event(
      String name, LocalDateTime startTime, LocalDateTime endTime, double kosten, Trip trip) {
    this.name = name;
    this.startTime = startTime;
    this.endTime = endTime;
    this.kosten = kosten;
    this.trip = trip;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime dateTime) {
    this.startTime = dateTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime endTime) {
    this.endTime = endTime;
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

  public Categories getCategorie() {
    return categorie;
  }

  public void setCategorie(Categories categorie) {
    this.categorie = categorie;
  }

  public Long getTripId() {
    return this.trip == null ? null : this.trip.getId();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getOrganizer() {
    return organizer;
  }

  public void setOrganizer(String organizer) {
    this.organizer = organizer;
  }
}
