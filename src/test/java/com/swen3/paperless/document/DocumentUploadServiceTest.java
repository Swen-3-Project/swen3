package com.swen3.paperless.document;

import java.util.UUID;
import com.swen3.paperless.common.UploadValidationException;
import com.swen3.paperless.jobs.*;
import com.swen3.paperless.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentUploadServiceTest {
    FileStorage storage;
    DocumentRepository repository;
    OutboxService outbox;
    PlatformTransactionManager manager;
    DocumentUploadService service;

    @BeforeEach void setUp() {
        storage = mock(FileStorage.class);
        repository = mock(DocumentRepository.class);
        outbox = mock(OutboxService.class);
        manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DocumentUploadService(storage, repository, Mappers.getMapper(DocumentMapper.class), outbox, manager);
    }

    @Test void commitsDocumentAndJobUsingFileFacts() {
        when(storage.store(any(), any())).thenAnswer(call -> new FileStorage.StoredFile(call.getArgument(0) + ".pdf", 10));
        var response = service.upload(pdf("../../invoice.pdf"), " Invoice ", "Notes");
        assertEquals("invoice.pdf", response.filename());
        assertEquals("application/pdf", response.mimeType());
        assertEquals(10, response.fileSize());
        assertEquals("Invoice", response.title());
        assertEquals("Notes", response.description());
        assertNotNull(response.createdAt());
        assertEquals(DocumentStatus.CREATED, response.status());
        verify(manager).commit(any());
        verify(outbox).enqueue(JobKind.OCR_REQUESTED, response.id(), response.id() + ".pdf");
        var saved = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(response.id() + ".pdf", saved.getValue().getStorageKey());
        verify(storage, never()).delete(any());
    }

    @Test void databaseAndCommitFailuresCleanUpTheStoredFile() {
        when(storage.store(any(), any())).thenReturn(new FileStorage.StoredFile("key.pdf", 10));
        doThrow(new IllegalStateException("database details")).when(repository).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service.upload(pdf("file.pdf"), null, null));
        verify(manager).rollback(any());
        verify(storage).delete("key.pdf");
        reset(repository, storage);
        when(storage.store(any(), any())).thenReturn(new FileStorage.StoredFile("key.pdf", 10));
        doThrow(new IllegalStateException("commit details")).when(manager).commit(any());
        assertThrows(IllegalStateException.class, () -> service.upload(pdf("file.pdf"), null, null));
        verify(storage).delete("key.pdf");
    }

    @Test void invalidMetadataAndStorageFailuresNeverCreateDocumentOrJob() {
        assertThrows(UploadValidationException.class, () -> service.upload(pdf(""), null, null));
        assertThrows(UploadValidationException.class, () -> service.upload(pdf("file.pdf"), "x".repeat(256), null));
        when(storage.store(any(), any())).thenThrow(new StorageException(new IllegalStateException()));
        assertThrows(StorageException.class, () -> service.upload(pdf("file.pdf"), null, null));
        verifyNoInteractions(repository, outbox);
    }

    @Test void deletionSchedulesCleanupAndMetadataMappingProtectsStorageReference() {
        UUID id = UUID.randomUUID();
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setStorageKey(id + ".pdf");
        when(repository.findById(id)).thenReturn(java.util.Optional.of(document));
        var mapper = Mappers.getMapper(DocumentMapper.class);
        mapper.updateMetadata(new com.swen3.paperless.document.dto.UpdateDocumentRequest("New", null), document);
        assertEquals(id + ".pdf", document.getStorageKey());
        new DocumentService(repository, mapper, outbox).delete(id);
        verify(outbox).cancelPendingOcr(id);
        verify(outbox).enqueue(JobKind.DELETE_FILE, id, id + ".pdf");
        verify(repository).delete(document);
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", "%PDF-1.7\n".getBytes());
    }
}
