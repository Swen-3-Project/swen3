package com.swen3.paperless.document;

import java.time.Instant;
import java.util.UUID;
import com.swen3.paperless.common.*;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentUploadController.class)
class DocumentUploadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DocumentUploadService service;

    @Test void multipartReturnsTheExistingDocumentContract() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.upload(any(), eq("Invoice"), isNull())).thenReturn(new DocumentResponse(id,
                "invoice.pdf", "application/pdf", 10, DocumentStatus.CREATED, Instant.now(), "Invoice", null));
        mvc.perform(multipart("/api/documents/upload").file(pdf()).param("title", "Invoice"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/documents/" + id))
                .andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test void mapsValidationSizeStorageAndUnexpectedErrors() throws Exception {
        doThrow(new UploadValidationException("file", "Invalid PDF")).when(service).upload(any(), any(), any());
        mvc.perform(multipart("/api/documents/upload").file(pdf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.file").value("Invalid PDF"));
        doThrow(new UploadTooLargeException()).when(service).upload(any(), any(), any());
        mvc.perform(multipart("/api/documents/upload").file(pdf())).andExpect(status().is(413));
        doThrow(new StorageException(new IllegalStateException("secret"))).when(service).upload(any(), any(), any());
        mvc.perform(multipart("/api/documents/upload").file(pdf())).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Document storage is temporarily unavailable"));
        doThrow(new IllegalStateException("secret")).when(service).upload(any(), any(), any());
        mvc.perform(multipart("/api/documents/upload").file(pdf())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("The request could not be completed"));
    }

    @Test void missingFileAndUnsupportedMediaAreClientErrors() throws Exception {
        mvc.perform(multipart("/api/documents/upload")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents/upload").contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private MockMultipartFile pdf() { return new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF-1.7\n".getBytes()); }
}
