package com.sep.sep_backend.FileManagement;

import java.time.LocalDateTime;

public record IcalRecord(
    String uid, String title, String description, LocalDateTime startDateTime) {}
