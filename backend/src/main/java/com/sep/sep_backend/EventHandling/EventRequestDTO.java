package com.sep.sep_backend.EventHandling;

public class EventRequestDTO {
    private String name;
    private int kosten;
    private Long tripId;

    public EventRequestDTO() {
    }

    public EventRequestDTO(String name, int kosten, Long tripId) {
        this.name = name;
        this.kosten = kosten;
        this.tripId = tripId;
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

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }
}
