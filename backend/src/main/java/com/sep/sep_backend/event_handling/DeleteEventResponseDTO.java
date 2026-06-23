package com.sep.sep_backend.event_handling;

/**
 * Response body for delete-event requests.
 *
 * @param message confirmation message returned to the client after deleting an event
 */
public record DeleteEventResponseDTO(String message) {}
