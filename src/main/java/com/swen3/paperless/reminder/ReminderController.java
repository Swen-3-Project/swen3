package com.swen3.paperless.reminder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.swen3.paperless.reminder.dto.CreateReminderRequest;
import com.swen3.paperless.reminder.dto.ReminderHistoryResponse;
import com.swen3.paperless.reminder.dto.ReminderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/reminders")
public class ReminderController {
    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReminderResponse> create(@PathVariable UUID documentId,
            @Valid @RequestBody CreateReminderRequest request) {
        ReminderResponse response = service.create(documentId, request);
        URI location = URI.create("/api/documents/" + documentId + "/reminders/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<ReminderResponse> list(@PathVariable UUID documentId) {
        return service.list(documentId);
    }

    @PatchMapping("/{reminderId}/complete")
    public ReminderResponse complete(@PathVariable UUID documentId, @PathVariable UUID reminderId) {
        return service.complete(documentId, reminderId);
    }

    @GetMapping("/{reminderId}/history")
    public List<ReminderHistoryResponse> history(@PathVariable UUID documentId, @PathVariable UUID reminderId) {
        return service.history(documentId, reminderId);
    }
}
