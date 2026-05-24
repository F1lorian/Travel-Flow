package com.sep.sep_backend.EventHandling;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class EventCalendarDTO {
    private String id;
    private String title;
    private int kosten;
    private long tripId;
    private LocalDateTime start;
    private LocalDateTime end;
    //hier müssen noch die uhrzeiten hinzugefügt werden!

  public EventCalendarDTO() {}

    public EventCalendarDTO(String id, String title, int kosten, LocalDateTime start, LocalDateTime end, long tripId) {
        this.id = id;
        this.title = title;
        this.start = start;
        this.end = end;
        this.kosten = kosten;
        this.tripId = tripId;
    }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public LocalDateTime getStart() {
    return start;
  }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd(){return this.end;}

    public void setEnd(LocalDateTime end) {this.end = end;}

    public int getKosten(){return this.kosten;}

    public void setKosten(int kosten){ this.kosten=kosten;}
}
