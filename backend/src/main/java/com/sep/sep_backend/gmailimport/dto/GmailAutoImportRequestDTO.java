package com.sep.sep_backend.gmailimport.dto;

import java.util.List;

/** Request listing the messages to auto-import; only those containing an event are returned. */
public record GmailAutoImportRequestDTO(List<String> messageIds) {}
