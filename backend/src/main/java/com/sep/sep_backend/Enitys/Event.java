package com.sep.sep_backend.Enitys;

import jakarta.persistence.*;

@Entity
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private int kosten;

    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    public Event() {
    }

    public Event(int kosten, Trip trip) {
        this.kosten = kosten;
        this.trip = trip;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
