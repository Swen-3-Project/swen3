package com.swen3.paperless.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.swen3.paperless.common.ResourceNotFoundException;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DocumentService service;

    @Test
    void createsDocumentAndReturnsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(CreateDocumentRequest.class))).thenReturn(new DocumentResponse(
                id, "invoice.pdf", "application/pdf", 123, DocumentStatus.CREATED,
                Instant.parse("2026-09-21T12:00:00Z"), "Invoice", null));

        mvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filename":"invoice.pdf","mimeType":"application/pdf",\
                                 "fileSize":123,"title":"Invoice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/documents/" + id))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void rejectsInvalidDocumentMetadata() throws Exception {
        mvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filename":"","mimeType":"application/pdf","fileSize":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.filename").exists())
                .andExpect(jsonPath("$.errors.fileSize").exists());
    }

    @Test
    void missingDocumentReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ResourceNotFoundException("Document missing"));

        mvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document missing"));
    }

    @Test
    void deletionReturns204() throws Exception {
        mvc.perform(delete("/api/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
