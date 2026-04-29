package com.sep.sep_backend.Enitys;

import jakarta.persistence.*;

@Entity
public class Ausgabe {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private int kosten;

    public Ausgabe() {}

    public Ausgabe(int kosten, Trip trip) {
        this.kosten = kosten;
        this.trip = trip;
    }

    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

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