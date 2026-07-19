package com.sep.sep_backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** JPA entity for a calendar event with optional trip assignment, owner, category, and costs. */
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

  @ManyToOne
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne
  @JoinColumn(name = "categorie_id")
  private Categories categorie;

  @ManyToOne
  @JoinColumn(name = "owner_id")
  private User owner;

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

  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }
}
