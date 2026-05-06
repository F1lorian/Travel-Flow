package com.sep.sep_backend.TripManagement.dto;

import com.sep.sep_backend.Enitys.Trip;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TripDTO {
    private long id;
    private String name;
    private List<EventDTO> events;

    public static TripDTO from(Trip trip) {
        TripDTO dto = new TripDTO();
        dto.id = trip.getId();
        dto.name = trip.getName();
        dto.events = trip.getEvents() == null ? Collections.emptyList()
                : trip.getEvents().stream().map(EventDTO::from).collect(Collectors.toList());
        return dto;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public List<EventDTO> getEvents() { return events; }
}