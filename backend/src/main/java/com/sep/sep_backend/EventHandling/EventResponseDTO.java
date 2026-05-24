package com.sep.sep_backend.EventHandling;

import java.time.LocalDateTime;

public class EventResponseDTO {
  private String name;
  private int kosten;
  private LocalDateTime uhrzeit;

  public EventResponseDTO() {}

  public EventResponseDTO(String name, int kosten, LocalDateTime uhrzeit) {
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

  public LocalDateTime getUhrzeit() {
    return uhrzeit;
  }

  public void setUhrzeit(LocalDateTime uhrzeit) {
    this.uhrzeit = uhrzeit;
  }
}
