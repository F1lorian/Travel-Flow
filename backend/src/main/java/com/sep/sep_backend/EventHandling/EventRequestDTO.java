package com.sep.sep_backend.EventHandling;

import java.time.LocalDateTime;

public class EventRequestDTO {
    private String name;
    private int kosten;
    private Long tripId;
    private LocalDateTime start;

    public EventRequestDTO() {
    }

    public EventRequestDTO(String name, int kosten, Long tripId, LocalDateTime start) {
        this.name = name;
        this.kosten = kosten;
        this.tripId = tripId;
        this.start = start;
    }

    public EventRequestDTO(String name, int kosten, Long tripId) {
        this(name, kosten, tripId, null);
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

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }
}
