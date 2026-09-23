package com.swen3.paperless.reminder.dto;

import java.time.Instant;
import java.util.UUID;

import com.swen3.paperless.reminder.ReminderEvent;

public record ReminderHistoryResponse(UUID id, ReminderEvent event, Instant occurredAt) {
}
