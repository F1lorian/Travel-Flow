package com.sep.sep_backend.Enitys;

import jakarta.persistence.*;

import java.util.List;

@Entity
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @OneToMany(mappedBy = "trip")
    private List<Event> events;

    @OneToMany(mappedBy = "trip")
    private List<Ausgabe> ausgaben;

    public Trip() {
    }

    public Trip(List<Event> events, List<Ausgabe> ausgaben) {
        this.events = events;
        this.ausgaben = ausgaben;
    }

    public long getId() {
        return id;
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
}
