package com.swen3.genai;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SummaryJobListenerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final ResultPublisher publisher = mock(ResultPublisher.class);
    private final SummaryJobListener listener = new SummaryJobListener(mapper, gemini, publisher);
    private final UUID jobId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @Test void skipsJobsWithoutAKey() {
        listener.receive(message(1, "Text"));
        verify(gemini, never()).summarize(anyString());
        verifyNoInteractions(publisher);
    }

    @Test void retriesTransientFailureAndPublishesWithOriginalIds() {
        when(gemini.configured()).thenReturn(true);
        when(gemini.summarize("Text"))
                .thenThrow(new GeminiException("http_429", true))
                .thenReturn("Zusammenfassung");
        listener.receive(message(1, "Text"));
        verify(gemini, times(2)).summarize("Text");
        verify(publisher).publish(new SummaryResult(1, jobId, documentId, "Zusammenfassung"));
    }

    @Test void stopsAfterThreeAttempts() {
        when(gemini.configured()).thenReturn(true);
        when(gemini.summarize("Text")).thenThrow(new GeminiException("http_503", true));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message(1, "Text")));
        verify(gemini, times(3)).summarize("Text");
        verifyNoInteractions(publisher);
    }

    @Test void rejectsPermanentFailureWithoutRetry() {
        when(gemini.configured()).thenReturn(true);
        when(gemini.summarize("Text")).thenThrow(new GeminiException("http_401", false));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message(1, "Text")));
        verify(gemini).summarize("Text");
        verifyNoInteractions(publisher);
    }

    @Test void rejectsMalformedJobs() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message(2, "Text")));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message(1, " ")));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(new Message(new byte[0])));
        verifyNoInteractions(gemini, publisher);
    }

    private Message message(int version, String text) {
        return new Message(mapper.writeValueAsBytes(new SummaryJob(version, jobId, documentId, text)));
    }
}
