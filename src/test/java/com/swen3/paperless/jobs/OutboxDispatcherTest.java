package com.swen3.paperless.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class OutboxDispatcherTest {
    @Test
    void confirmedJobIsMarkedSentAndKeepsItsIdentity() {
        var repository = mock(PendingJobRepository.class);
        var handler = mock(JobHandler.class);
        when(handler.kind()).thenReturn(JobKind.OCR_REQUESTED);
        var job = new PendingJob(JobKind.OCR_REQUESTED, UUID.randomUUID(), "document.pdf");
        when(repository.lockDue(any(), any())).thenReturn(List.of(job));
        new OutboxDispatcher(repository, List.of(handler)).dispatch();
        verify(handler).handle(job);
        verify(repository).save(job);
        assertEquals(JobState.SENT, job.getState());
        assertNotNull(job.getId());
        assertEquals("document.pdf", job.getStorageKey());
        assertEquals(0, job.getAttempts());
    }

    @Test
    void failuresRemainPendingWithBoundedBackoff() {
        var repository = mock(PendingJobRepository.class);
        var handler = mock(JobHandler.class);
        when(handler.kind()).thenReturn(JobKind.OCR_REQUESTED);
        var job = new PendingJob(JobKind.OCR_REQUESTED, UUID.randomUUID(), "document.pdf");
        when(repository.lockDue(any(), any())).thenReturn(List.of(job));
        doThrow(new IllegalStateException()).when(handler).handle(job);
        Instant before = Instant.now();
        new OutboxDispatcher(repository, List.of(handler)).dispatch();
        assertEquals(JobState.PENDING, job.getState());
        assertTrue(job.getNextAttemptAt().isAfter(before));
        for (int i = 0; i < 10; i++) job.retry(before);
        assertEquals(before.plusSeconds(60), job.getNextAttemptAt());
        assertEquals(11, job.getAttempts());
    }

    @Test
    void enqueuesAndCancelsWithinTheCallersTransaction() {
        var repository = mock(PendingJobRepository.class);
        var service = new OutboxService(repository);
        UUID id = UUID.randomUUID();
        service.enqueue(JobKind.OCR_REQUESTED, id, "document.pdf");
        service.cancelPendingOcr(id);
        verify(repository).save(any(PendingJob.class));
        verify(repository).cancelPendingOcr(id);
    }
}
