package com.swen3.paperless.document;

import java.time.Instant;
import java.util.UUID;
import com.swen3.paperless.common.UploadValidationException;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.messaging.MessagingException;
import com.swen3.paperless.messaging.OcrJob;
import com.swen3.paperless.messaging.JobPublisher;
import com.swen3.paperless.storage.FileStorage;
import com.swen3.paperless.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentUploadServiceTest {
    FileStorage storage = mock(FileStorage.class);
    DocumentService documents = mock(DocumentService.class);
    JobPublisher publisher = mock(JobPublisher.class);
    DocumentUploadService service = new DocumentUploadService(storage, documents, publisher);

    @BeforeEach void setUp() {
        when(storage.store(any(), any())).thenAnswer(call ->
                new FileStorage.StoredFile(call.getArgument(0) + ".pdf", 10));
        when(documents.createUploaded(any(), any(), any())).thenAnswer(call -> {
            CreateDocumentRequest request = call.getArgument(1);
            return new DocumentResponse(call.getArgument(0), request.filename(), request.mimeType(),
                    request.fileSize(), DocumentStatus.CREATED, Instant.now(), request.title(), request.description());
        });
    }

    @Test void savesBeforePublishingAndUsesActualFileFacts() {
        var response = service.upload(pdf("../../invoice.pdf"), " Invoice ", "Notes");
        assertEquals("invoice.pdf", response.filename());
        assertEquals(10, response.fileSize());
        assertEquals("Invoice", response.title());
        var order = inOrder(documents, publisher);
        order.verify(documents).createUploaded(eq(response.id()), any(), eq(response.id() + ".pdf"));
        var message = ArgumentCaptor.forClass(OcrJob.class);
        order.verify(publisher).publish(message.capture());
        assertEquals(response.id(), message.getValue().documentId());
        assertNotNull(message.getValue().jobId());
        verify(storage, never()).delete(any());
    }

    @Test void databaseAndSendFailuresCleanUpTheUpload() {
        doThrow(new IllegalStateException("commit failed")).when(documents).createUploaded(any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.upload(pdf("a.pdf"), null, null));
        verify(documents).removeFailedUpload(any());
        verify(storage).delete(any());
        verifyNoInteractions(publisher);
        reset(documents, storage);
        when(storage.store(any(), any())).thenReturn(new FileStorage.StoredFile("key.pdf", 10));
        doThrow(new MessagingException(new IllegalStateException())).when(publisher).publish(any(OcrJob.class));
        assertThrows(MessagingException.class, () -> service.upload(pdf("b.pdf"), null, null));
        verify(documents).removeFailedUpload(any());
        verify(storage).delete("key.pdf");
    }

    @Test void cleanupErrorsDoNotHideTheOriginalFailure() {
        var failure = new MessagingException(new IllegalStateException());
        doThrow(failure).when(publisher).publish(any(OcrJob.class));
        doThrow(new IllegalStateException()).when(documents).removeFailedUpload(any());
        doThrow(new IllegalStateException()).when(storage).delete(any());
        assertSame(failure, assertThrows(MessagingException.class, () -> service.upload(pdf("a.pdf"), null, null)));
    }

    @Test void rejectsInvalidMetadataAndStopsOnStorageFailure() {
        assertThrows(UploadValidationException.class, () -> service.upload(pdf(""), null, null));
        assertThrows(UploadValidationException.class, () -> service.upload(pdf("\u0001.pdf"), null, null));
        assertThrows(UploadValidationException.class, () -> service.upload(pdf("a.pdf"), "x".repeat(256), null));
        when(storage.store(any(), any())).thenThrow(new StorageException(new IllegalStateException()));
        assertThrows(StorageException.class, () -> service.upload(pdf("a.pdf"), null, null));
        verifyNoInteractions(documents, publisher);
    }

    @Test void usesDefaultFilenameWhenMissing() {
        assertEquals("document.pdf", service.upload(mock(org.springframework.web.multipart.MultipartFile.class), null, null).filename());
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", "%PDF-1.7\n".getBytes());
    }
}
