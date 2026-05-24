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

    private LocalDate startDate;

    private LocalDate endDate;

    @OneToMany(mappedBy = "trip")
    private List<Event> events;

  @OneToMany(mappedBy = "trip")
  private List<Ausgabe> ausgaben;

  public Trip() {}

  public Trip(String name) {
    this.name = name;
  }

    public Trip(String name, List<Event> events, List<Ausgabe> ausgaben, LocalDate startDate, LocalDate endDate) {
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

    public LocalDate getStartDate() {return startDate;}

    public void setStartDate(LocalDate startDate){this.startDate = startDate;}

    public LocalDate getEndDate() {return endDate;}

    public void setEndDate(LocalDate endDate) {this.endDate = endDate;}
}
