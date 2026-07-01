package com.sep.sep_backend.gmailimport;

import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import java.util.Optional;

/**
 * Abstraction over the AI model used to extract event fields from free-form email text. Keeping
 * this behind an interface lets the parser be unit-tested without a network call and lets the AI
 * provider be swapped without touching the parsing logic.
 */
public interface AiEventExtractor {

  /**
   * @return {@code true} if the extractor is configured and ready to be called
   */
  boolean isAvailable();

  /**
   * Extracts event fields from the given email content. Implementations must never throw; on any
   * failure they return {@link Optional#empty()} so the caller can fall back to heuristics.
   *
   * @param subject email subject
   * @param body plain-text email body
   * @return the extracted event, or empty if extraction was not possible
   */
  Optional<ExtractedEventDTO> extract(String subject, String body);
}
