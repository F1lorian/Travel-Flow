package com.sep.sep_backend.gmailimport.dto;

/** Lightweight preview of a Gmail message shown in the import picker. */
public record GmailMessagePreviewDTO(
    String id, String subject, String from, String date, String snippet) {}
