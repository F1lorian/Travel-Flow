package com.sep.sep_backend.gmailimport.dto;

import java.time.LocalDateTime;

/**
 * Confirmation returned to the frontend after a calendar event has been created from an imported
 * email.
 *
 * @param id identifier of the newly created event
 * @param title event title
 * @param start event start date and time
 * @param end event end date and time
 * @param message human-readable success message
 */
public record CreatedEventDTO(
    String id, String title, LocalDateTime start, LocalDateTime end, String message) {}
