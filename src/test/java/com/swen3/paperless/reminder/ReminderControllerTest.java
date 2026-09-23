package com.swen3.paperless.reminder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.swen3.paperless.common.ResourceNotFoundException;
import com.swen3.paperless.reminder.dto.CreateReminderRequest;
import com.swen3.paperless.reminder.dto.ReminderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReminderController.class)
class ReminderControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReminderService service;

    @Test
    void createsReminderAndReturnsLocation() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        when(service.create(eq(documentId), any(CreateReminderRequest.class))).thenReturn(new ReminderResponse(
                reminderId, documentId, "Pay invoice", null, LocalDate.of(2026, 10, 1),
                ReminderStatus.OPEN, Instant.parse("2026-09-21T12:00:00Z"), null));

        mvc.perform(post("/api/documents/{documentId}/reminders", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Pay invoice","dueDate":"2026-10-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/documents/" + documentId + "/reminders/" + reminderId))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void rejectsReminderWithoutRequiredFields() throws Exception {
        mvc.perform(post("/api/documents/{documentId}/reminders", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.dueDate").exists());
    }

    @Test
    void missingDocumentReturns404ForReminderList() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(service.list(documentId)).thenThrow(new ResourceNotFoundException("Document missing"));

        mvc.perform(get("/api/documents/{documentId}/reminders", documentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void completesReminder() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        when(service.complete(documentId, reminderId)).thenReturn(new ReminderResponse(
                reminderId, documentId, "Pay invoice", null, LocalDate.of(2026, 10, 1),
                ReminderStatus.COMPLETED, Instant.parse("2026-09-21T12:00:00Z"),
                Instant.parse("2026-09-21T13:00:00Z")));

        mvc.perform(patch("/api/documents/{documentId}/reminders/{reminderId}/complete", documentId, reminderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
