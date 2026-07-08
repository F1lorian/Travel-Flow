package com.sep.sep_backend.gmailimport.dto;

/**
 * Summary returned after an automatic Gmail scan that creates calendar events without a review
 * step.
 *
 * @param scanned number of event-like emails the scan detected
 * @param created number of new calendar events created
 * @param skippedNoDate detections skipped because no date could be extracted (low confidence)
 * @param skippedDuplicate detections skipped because the message was already imported
 * @param message human-readable summary
 */
public record GmailAutoImportResultDTO(
    int scanned, int created, int skippedNoDate, int skippedDuplicate, String message) {}
