package com.swen3.paperless.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.swen3.paperless.common.ResourceNotFoundException;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.UpdateDocumentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock
    private DocumentRepository repository;

    private DocumentService service;
    private final com.swen3.paperless.search.SearchClient search = org.mockito.Mockito.mock(com.swen3.paperless.search.SearchClient.class);

    @BeforeEach
    void setUp() {
        service = new DocumentService(repository, Mappers.getMapper(DocumentMapper.class),
                org.mockito.Mockito.mock(com.swen3.paperless.storage.FileStorage.class), search);
    }

    @Test
    void createsDocumentWithServerOwnedFields() {
        when(repository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateDocumentRequest("invoice.pdf", "application/pdf", 123L,
                "Invoice", "For September"));

        assertNotNull(response.id());
        assertNotNull(response.createdAt());
        assertEquals(DocumentStatus.CREATED, response.status());
        assertEquals("invoice.pdf", response.filename());
        assertEquals(123L, response.fileSize());
        assertEquals("Invoice", response.title());
    }

    @Test
    void createsUploadedDocumentAndRemovesFailedUploads() {
        UUID id = UUID.randomUUID();
        when(repository.saveAndFlush(any(DocumentEntity.class))).thenAnswer(call -> call.getArgument(0));
        var response = service.createUploaded(id, new CreateDocumentRequest("a.pdf", "application/pdf", 10L, null, null), id + ".pdf");
        assertEquals(id, response.id());
        var saved = org.mockito.ArgumentCaptor.forClass(DocumentEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(id + ".pdf", saved.getValue().getStorageKey());
        service.removeFailedUpload(id);
        verify(repository).deleteById(id);
    }

    @Test
    void deletesFileBeforeDeletingMetadata() {
        UUID id = UUID.randomUUID();
        var document = existingDocument(id);
        document.setStorageKey(id + ".pdf");
        when(repository.findById(id)).thenReturn(Optional.of(document));
        var storage = org.mockito.Mockito.mock(com.swen3.paperless.storage.FileStorage.class);
        var withStorage = new DocumentService(repository, Mappers.getMapper(DocumentMapper.class), storage, search);
        withStorage.delete(id);
        var order = org.mockito.Mockito.inOrder(storage, repository);
        order.verify(storage).delete(id + ".pdf");
        order.verify(repository).delete(document);
    }

    @Test
    void listsEmptyDatabaseAsEmptyList() {
        when(repository.findAll()).thenReturn(List.of());
        assertEquals(List.of(), service.list());
    }

    @Test
    void updatesMetadataWithoutChangingFileFacts() {
        UUID id = UUID.randomUUID();
        DocumentEntity document = existingDocument(id);
        when(repository.findById(id)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);

        var response = service.update(id, new UpdateDocumentRequest("Revised", "Updated description"));

        assertEquals("Revised", response.title());
        assertEquals("Updated description", response.description());
        assertEquals("invoice.pdf", response.filename());
        assertEquals(123L, response.fileSize());
        assertEquals(DocumentStatus.CREATED, response.status());
    }

    @Test
    void missingDocumentFailsForReadAndDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(id));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(id));
    }

    @Test
    void deletesExistingDocument() {
        UUID id = UUID.randomUUID();
        DocumentEntity document = existingDocument(id);
        when(repository.findById(id)).thenReturn(Optional.of(document));

        service.delete(id);

        verify(repository).delete(document);
    }

    @Test
    void storesProcessingResultsWithoutDowngradingCompletedDocuments() {
        UUID id = UUID.randomUUID();
        var document = existingDocument(id);
        when(repository.findById(id)).thenReturn(Optional.of(document));
        service.applyOcrResult(id, false, null);
        assertEquals(DocumentStatus.FAILED, document.getStatus());
        service.applyOcrResult(id, true, "Hello");
        assertEquals(DocumentStatus.OCR_COMPLETED, document.getStatus());
        service.applyOcrResult(id, false, null);
        assertEquals(DocumentStatus.OCR_COMPLETED, document.getStatus());
        service.applyIndexResult(id, false);
        assertEquals(DocumentStatus.FAILED, document.getStatus());
        service.applyIndexResult(id, true);
        assertEquals(DocumentStatus.INDEXED, document.getStatus());
        service.applyOcrResult(id, true, "late");
        service.applyIndexResult(id, false);
        assertEquals(DocumentStatus.INDEXED, document.getStatus());
        assertEquals("Hello", document.getOcrText());
        assertEquals("Hello", service.get(id).ocrText());
    }

    @Test
    void ignoresMissingResultsAndUnprocessedIndexResults() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertNull(service.applyOcrResult(id, true, "Hello"));
        org.junit.jupiter.api.Assertions.assertFalse(service.applyIndexResult(id, true));
        var document = existingDocument(id);
        when(repository.findById(id)).thenReturn(Optional.of(document));
        service.applyIndexResult(id, true);
        assertEquals(DocumentStatus.CREATED, document.getStatus());
    }

    @Test
    void keepsSearchOrderAndFiltersDeletedDocuments() {
        UUID id = UUID.randomUUID(), deleted = UUID.randomUUID();
        when(search.search("Hello")).thenReturn(List.of(deleted, id));
        when(repository.findAllById(List.of(deleted, id))).thenReturn(List.of(existingDocument(id)));
        assertEquals(id, service.search("Hello").getFirst().id());
        when(repository.findAll()).thenReturn(List.of());
        assertEquals(List.of(), service.search(""));
    }

    private DocumentEntity existingDocument(UUID id) {
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setFilename("invoice.pdf");
        document.setMimeType("application/pdf");
        document.setFileSize(123);
        document.setStatus(DocumentStatus.CREATED);
        document.setCreatedAt(java.time.Instant.now());
        return document;
    }
}
