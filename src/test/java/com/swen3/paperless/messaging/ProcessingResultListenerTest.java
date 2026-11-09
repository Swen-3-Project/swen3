package com.swen3.paperless.messaging;

import java.util.UUID;
import com.swen3.paperless.document.DocumentEntity;
import com.swen3.paperless.document.DocumentService;
import com.swen3.paperless.document.DocumentStatus;
import com.swen3.paperless.search.SearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProcessingResultListenerTest {
    JsonMapper mapper = JsonMapper.builder().build();
    DocumentService documents = mock(DocumentService.class);
    JobPublisher publisher = mock(JobPublisher.class);
    SearchClient search = mock(SearchClient.class);
    ProcessingResultListener listener = new ProcessingResultListener(mapper, documents, publisher, search);
    UUID id = UUID.randomUUID(), job = UUID.randomUUID();

    @Test void savesTextBeforeSendingIndexJobAndAllowsRedelivery() {
        var document = new DocumentEntity();
        document.setId(id);
        document.setFilename("a.pdf");
        document.setOcrText("Hello");
        document.setStatus(DocumentStatus.OCR_COMPLETED);
        when(documents.applyOcrResult(id, true, "Hello")).thenReturn(document);
        var message = message("ocr.completed", new OcrResult(1, job, id, true, "Hello"));
        listener.receive(message);
        var order = inOrder(documents, publisher);
        order.verify(documents).applyOcrResult(id, true, "Hello");
        order.verify(publisher).publish(new IndexJob(1, job, id, "a.pdf", "Hello"));
        document.setStatus(DocumentStatus.INDEXED);
        listener.receive(message);
        verify(publisher, times(1)).publish(any(IndexJob.class));
        assertNull(org.slf4j.MDC.get("documentId"));
    }

    @Test void ignoresDeletedDocumentsAndPersistsOcrFailures() {
        listener.receive(message("ocr.completed", new OcrResult(1, job, id, true, "Hello")));
        var document = new DocumentEntity();
        document.setId(id);
        when(documents.applyOcrResult(id, false, null)).thenReturn(document);
        listener.receive(message("ocr.completed", new OcrResult(1, job, id, false, null)));
        verifyNoInteractions(publisher);
        verify(documents).applyOcrResult(id, false, null);
    }

    @Test void cleansLateIndexResultForDeletedDocument() {
        listener.receive(message("index.completed", new IndexResult(1, job, id, true)));
        verify(search).delete(id);
        when(documents.applyIndexResult(id, false)).thenReturn(true);
        listener.receive(message("index.completed", new IndexResult(1, job, id, false)));
        verifyNoMoreInteractions(search);
    }

    @Test void rejectsInvalidContractsWithoutExposingPayloads() {
        for (Object result : java.util.List.of(new OcrResult(2, job, id, true, "Hello"),
                new OcrResult(1, null, id, true, "Hello"), new OcrResult(1, job, id, true, null))) {
            assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message("ocr.completed", result)));
        }
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message("index.completed", new IndexResult(1, job, null, true))));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message("wrong", new IndexResult(1, job, id, true))));
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("ocr.completed");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(new Message("invalid".getBytes(), properties)));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(new Message("null".getBytes(), properties)));
        verifyNoInteractions(documents, publisher, search);
    }

    private Message message(String route, Object payload) {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey(route);
        return new Message(mapper.writeValueAsBytes(payload), properties);
    }
}
