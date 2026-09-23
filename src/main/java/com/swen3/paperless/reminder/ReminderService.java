package com.swen3.paperless.reminder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swen3.paperless.common.ResourceNotFoundException;
import com.swen3.paperless.document.DocumentEntity;
import com.swen3.paperless.document.DocumentService;
import com.swen3.paperless.reminder.dto.CreateReminderRequest;
import com.swen3.paperless.reminder.dto.ReminderHistoryResponse;
import com.swen3.paperless.reminder.dto.ReminderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {
    private final DocumentService documentService;
    private final ReminderRepository repository;
    private final ReminderHistoryRepository historyRepository;
    private final ReminderMapper mapper;

    public ReminderService(DocumentService documentService, ReminderRepository repository,
            ReminderHistoryRepository historyRepository, ReminderMapper mapper) {
        this.documentService = documentService;
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ReminderResponse create(UUID documentId, CreateReminderRequest request) {
        DocumentEntity document = documentService.requireEntity(documentId);
        ReminderEntity reminder = mapper.toEntity(request);
        reminder.setId(UUID.randomUUID());
        reminder.setDocument(document);
        reminder.setStatus(ReminderStatus.OPEN);
        reminder.setCreatedAt(Instant.now());
        ReminderEntity saved = repository.save(reminder);
        historyRepository.save(new ReminderHistoryEntity(saved, ReminderEvent.CREATED, saved.getCreatedAt()));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> list(UUID documentId) {
        documentService.requireEntity(documentId);
        return repository.findByDocument_IdOrderByCreatedAtAsc(documentId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public ReminderResponse complete(UUID documentId, UUID reminderId) {
        ReminderEntity reminder = requireReminder(documentId, reminderId);
        if (reminder.getStatus() == ReminderStatus.COMPLETED) {
            return mapper.toResponse(reminder);
        }
        Instant completedAt = Instant.now();
        reminder.setStatus(ReminderStatus.COMPLETED);
        reminder.setCompletedAt(completedAt);
        ReminderEntity saved = repository.save(reminder);
        historyRepository.save(new ReminderHistoryEntity(saved, ReminderEvent.COMPLETED, completedAt));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReminderHistoryResponse> history(UUID documentId, UUID reminderId) {
        requireReminder(documentId, reminderId);
        return historyRepository.findByReminder_IdOrderByOccurredAtAsc(reminderId).stream()
                .map(mapper::toHistoryResponse).toList();
    }

    private ReminderEntity requireReminder(UUID documentId, UUID reminderId) {
        documentService.requireEntity(documentId);
        return repository.findByIdAndDocument_Id(reminderId, documentId).orElseThrow(() ->
                new ResourceNotFoundException("Reminder " + reminderId + " was not found for document " + documentId));
    }
}
