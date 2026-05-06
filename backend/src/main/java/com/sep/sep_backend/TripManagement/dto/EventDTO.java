package com.sep.sep_backend.TripManagement.dto;

import com.sep.sep_backend.Enitys.Event;
import java.time.LocalDateTime;

public class EventDTO {
    private long id;
    private String name;
    private LocalDateTime dateTime;
    private int kosten;
    private long tripId;

    public static EventDTO from(Event event) {
        EventDTO dto = new EventDTO();
        dto.id = event.getId();
        dto.name = event.getName();
        dto.dateTime = event.getDateTime();
        dto.kosten = event.getKosten();
        dto.tripId = event.getTrip().getId();
        return dto;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public LocalDateTime getDateTime() { return dateTime; }
    public int getKosten() { return kosten; }
    public long getTripId() { return tripId; }
}
