package com.sep.sep_backend.entities;

import jakarta.persistence.*;
import java.util.List;

/** JPA entity for reusable expense and event categories. */
@Entity
public class Categories {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  private String name;

  @OneToMany(mappedBy = "categorie")
  private List<Event> events;

  @OneToMany(mappedBy = "kategorie")
  private List<Ausgabe> ausgaben;

  public Categories() {}

  public Categories(String name) {
    this.name = name;
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

  public List<Event> getEvents() {
    return events;
  }

  public void setEvents(List<Event> events) {
    this.events = events;
  }
}
