package com.sep.sep_backend.EventHandling;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class EventResponseDTO {
    private String name;
    private int kosten;
    private LocalDateTime startZeit;
    private LocalDateTime endZeit;

  public EventResponseDTO() {}

    public EventResponseDTO(String name, int kosten, LocalDateTime startZeit, LocalDateTime endZeit) {
        this.name = name;
        this.kosten = kosten;
        this.startZeit = startZeit;
        this.endZeit = endZeit;
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

    public LocalDateTime getStartZeit() {
        return startZeit;
    }

    public void setStartZeit(LocalDateTime uhrzeit) {
        this.startZeit = uhrzeit;
    }

    public LocalDateTime getEndZeit() {return endZeit;}

    public void setEndZeit(LocalDateTime endZeit) {this.endZeit = endZeit;}
}
