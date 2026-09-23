package com.swen3.paperless.reminder.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReminderRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull LocalDate dueDate) {
}
