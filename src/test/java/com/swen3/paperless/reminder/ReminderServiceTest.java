package com.swen3.paperless.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.swen3.paperless.common.ResourceNotFoundException;
import com.swen3.paperless.document.DocumentEntity;
import com.swen3.paperless.document.DocumentService;
import com.swen3.paperless.reminder.dto.CreateReminderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {
    @Mock
    private DocumentService documentService;
    @Mock
    private ReminderRepository repository;
    @Mock
    private ReminderHistoryRepository historyRepository;

    private ReminderService service;

    @BeforeEach
    void setUp() {
        service = new ReminderService(documentService, repository, historyRepository,
                Mappers.getMapper(ReminderMapper.class));
    }

    @Test
    void createsReminderAndCreationHistoryForExistingDocument() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = document(documentId);
        when(documentService.requireEntity(documentId)).thenReturn(document);
        when(repository.save(any(ReminderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(documentId,
                new CreateReminderRequest("Pay invoice", "Before deadline", LocalDate.of(2026, 10, 1)));

        assertNotNull(response.id());
        assertEquals(documentId, response.documentId());
        assertEquals(ReminderStatus.OPEN, response.status());
        assertEquals(LocalDate.of(2026, 10, 1), response.dueDate());
        verify(historyRepository).save(org.mockito.ArgumentMatchers.argThat(history ->
                history.getEvent() == ReminderEvent.CREATED && history.getReminder().getId().equals(response.id())));
    }

    @Test
    void completesReminderOnceAndRecordsTransition() {
        UUID documentId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        ReminderEntity reminder = reminder(documentId, reminderId);
        when(documentService.requireEntity(documentId)).thenReturn(reminder.getDocument());
        when(repository.findByIdAndDocument_Id(reminderId, documentId)).thenReturn(Optional.of(reminder));
        when(repository.save(reminder)).thenReturn(reminder);

        var first = service.complete(documentId, reminderId);
        var second = service.complete(documentId, reminderId);

        assertEquals(ReminderStatus.COMPLETED, first.status());
        assertNotNull(first.completedAt());
        assertEquals(first.completedAt(), second.completedAt());
        verify(historyRepository, times(1)).save(org.mockito.ArgumentMatchers.argThat(history ->
                history.getEvent() == ReminderEvent.COMPLETED));
    }

    @Test
    void missingReminderReturnsNotFound() {
        UUID documentId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        when(documentService.requireEntity(documentId)).thenReturn(document(documentId));
        when(repository.findByIdAndDocument_Id(reminderId, documentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.complete(documentId, reminderId));
    }

    @Test
    void missingDocumentReturnsNotFoundForList() {
        UUID documentId = UUID.randomUUID();
        when(documentService.requireEntity(documentId)).thenThrow(new ResourceNotFoundException("missing"));

        assertThrows(ResourceNotFoundException.class, () -> service.list(documentId));
    }

    @Test
    void listsHistoryForReminderBelongingToDocument() {
        UUID documentId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        ReminderEntity reminder = reminder(documentId, reminderId);
        when(documentService.requireEntity(documentId)).thenReturn(reminder.getDocument());
        when(repository.findByIdAndDocument_Id(reminderId, documentId)).thenReturn(Optional.of(reminder));
        when(historyRepository.findByReminder_IdOrderByOccurredAtAsc(reminderId))
                .thenReturn(List.of(new ReminderHistoryEntity(reminder, ReminderEvent.CREATED, Instant.now())));

        var history = service.history(documentId, reminderId);

        assertEquals(1, history.size());
        assertEquals(ReminderEvent.CREATED, history.getFirst().event());
    }

    private DocumentEntity document(UUID id) {
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        return document;
    }

    private ReminderEntity reminder(UUID documentId, UUID reminderId) {
        ReminderEntity reminder = new ReminderEntity();
        reminder.setId(reminderId);
        reminder.setDocument(document(documentId));
        reminder.setTitle("Pay invoice");
        reminder.setDueDate(LocalDate.of(2026, 10, 1));
        reminder.setStatus(ReminderStatus.OPEN);
        reminder.setCreatedAt(Instant.now());
        return reminder;
    }
}
