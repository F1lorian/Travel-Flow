package com.sep.sep_backend.Enitys;

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

  @Column(nullable = false, columnDefinition = "double precision default 0")
  private double budget = 0;

  @Column(nullable = false, columnDefinition = "double precision default 0")
  private double warningRange = 0;

  @OneToMany(mappedBy = "trip")
  private List<Event> events;

  private LocalDate startDate;

  private LocalDate endDate;

  @OneToMany(mappedBy = "trip")
  private List<Ausgabe> ausgaben;

  @ManyToMany(mappedBy = "trips")
  private List<User> users;

  public List<User> getUsers() {
    return users;
  }

  public void setUsers(List<User> users) {
    this.users = users;
  }

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

  public double getBudget() {
    return this.budget;
  }

  public void setBudget(double budget) {
    this.budget = budget;
  }

  public double getWarningRange() {
    return this.warningRange;
  }

  public void setWarningRange(double warningRange) {
    this.warningRange = warningRange;
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
}
