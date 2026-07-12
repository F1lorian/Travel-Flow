package com.sep.sep_backend.privacy;

import java.time.LocalDateTime;

/** DTO for events that are shared with a friend through privacy permissions. */
public record SharedEventDTO(
    long id,
    String name,
    LocalDateTime startTime,
    LocalDateTime endTime,
    double kosten,
    Long tripId) {}
