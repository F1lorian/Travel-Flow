package com.sep.sep_backend.gmailimport.dto;

import com.sep.sep_backend.eventHandling.EventResponseDTO;
import java.util.List;

/**
 * Result of an automatic Gmail import: the events found in the selected messages, and how many
 * messages could not be checked (e.g. the AI detection failed) so the client can report them.
 */
public record GmailAutoImportResultDTO(List<EventResponseDTO> events, int failed) {}
