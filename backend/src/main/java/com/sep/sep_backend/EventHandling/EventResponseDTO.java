package com.sep.sep_backend.EventHandling;

import java.time.LocalTime;

public class EventResponseDTO {
    private String name;
    private int kosten;
    private LocalTime uhrzeit;

    public EventResponseDTO() {
    }

    public EventResponseDTO(String name, int kosten, LocalTime uhrzeit) {
        this.name = name;
        this.kosten = kosten;
        this.uhrzeit = uhrzeit;
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

    public LocalTime getUhrzeit() {
        return uhrzeit;
    }

    public void setUhrzeit(LocalTime uhrzeit) {
        this.uhrzeit = uhrzeit;
    }
}
