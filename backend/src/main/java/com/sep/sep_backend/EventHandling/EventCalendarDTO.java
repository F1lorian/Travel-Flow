package com.sep.sep_backend.EventHandling;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class EventCalendarDTO {
    private String id;
    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime start;

    public EventCalendarDTO() {
    }

    public EventCalendarDTO(String id, String title, LocalDateTime start) {
        this.id = id;
        this.title = title;
        this.start = start;
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
}
