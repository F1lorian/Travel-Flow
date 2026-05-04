package com.sep.sep_backend.EventHandling;

import com.sep.sep_backend.Enitys.Trip;

public class EventRequestDTO {
    private String name;
    private int kosten;
    private Trip trip;

    public EventRequestDTO() {
    }

    public EventRequestDTO(String name, int kosten, Trip trip) {
        this.name = name;
        this.kosten = kosten;
        this.trip = trip;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
