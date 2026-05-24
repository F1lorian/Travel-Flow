package com.sep.sep_backend.Enitys;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Event {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private LocalDateTime dateTime;

  private int kosten;

  @ManyToOne
  @JoinColumn(name = "trip_id")
  private Trip trip;

  public Event() {}

  public Event(String name, LocalDateTime dateTime, int kosten, Trip trip) {
    this.name = name;
    this.dateTime = dateTime;
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

  public LocalDateTime getDateTime() {
    return dateTime;
  }

  public void setDateTime(LocalDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public int getKosten() {
    return kosten;
  }

  public void setKosten(int kosten) {
    this.kosten = kosten;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip trip) {
    this.trip = trip;
  }
}
