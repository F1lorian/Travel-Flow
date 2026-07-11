package com.sep.sep_backend.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Entity
public class Trip {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  @Column(nullable = false)
  private String name;

  @OneToMany(mappedBy = "trip")
  private List<Event> events;

  private LocalDate startDate;

  private LocalDate endDate;

  @OneToMany(mappedBy = "trip")
  private List<Ausgabe> ausgaben;

  @OneToMany(mappedBy = "trip")
  private List<UserTrip> userTrips;

  @ManyToOne
  @JoinColumn(name = "owner_id")
  private User owner;

  public Trip() {}

  public Trip(String name) {
    this.name = name;
  }

  public Trip(
      String name,
      List<Event> events,
      List<Ausgabe> ausgaben,
      LocalDate startDate,
      LocalDate endDate) {
    this.name = name;
    this.events = events;
    this.ausgaben = ausgaben;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setId(long id) {
    this.id = id;
  }

  public List<Event> getEvents() {
    return events;
  }

  public void setEvents(List<Event> events) {
    this.events = events;
  }

  public List<Ausgabe> getAusgaben() {
    return ausgaben;
  }

  public void setAusgaben(List<Ausgabe> ausgaben) {
    this.ausgaben = ausgaben;
  }

  public List<UserTrip> getUserTrips() {
    return userTrips;
  }

  public void setUserTrips(List<UserTrip> userTrips) {
    this.userTrips = userTrips;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }
}
