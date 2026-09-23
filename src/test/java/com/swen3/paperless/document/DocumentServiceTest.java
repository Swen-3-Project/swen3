package com.swen3.paperless.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @BeforeEach
    void setUp() {
        service = new DocumentService(repository, Mappers.getMapper(DocumentMapper.class));
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
