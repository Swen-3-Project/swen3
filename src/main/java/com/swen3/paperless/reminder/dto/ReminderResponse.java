package com.swen3.paperless.reminder.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.swen3.paperless.reminder.ReminderStatus;

public record ReminderResponse(
        UUID id,
        UUID documentId,
        String title,
        String description,
        LocalDate dueDate,
        ReminderStatus status,
        Instant createdAt,
        Instant completedAt) {
}
