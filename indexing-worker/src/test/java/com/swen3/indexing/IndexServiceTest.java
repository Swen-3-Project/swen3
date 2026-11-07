package com.swen3.indexing;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IndexServiceTest {
    @Test void replacesTheSameDocumentIdAndIncludesRequiredFields() {
        var builder = RestClient.builder().baseUrl("http://search");
        var server = MockRestServiceServer.bindTo(builder).build();
        var id = UUID.randomUUID();
        var job = new IndexJob(1, UUID.randomUUID(), id, "a.pdf", "Hello");
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo("http://search/paperless-documents/_doc/" + id + "?refresh=wait_for"))
                    .andExpect(method(HttpMethod.PUT))
                    .andExpect(content().json("{\"documentId\":\"" + id + "\",\"filename\":\"a.pdf\",\"content\":\"Hello\"}"))
                    .andRespond(withSuccess());
        }
        var service = new IndexService(builder.build());
        service.index(job);
        service.index(job);
        server.verify();
        assertNotNull(new IndexService("http://localhost:9200"));
    }

    @Test void wrapsElasticsearchFailures() {
        var builder = RestClient.builder().baseUrl("http://search");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var job = new IndexJob(1, UUID.randomUUID(), UUID.randomUUID(), "a.pdf", "");
        assertThrows(IndexException.class, () -> new IndexService(builder.build()).index(job));
        server.verify();
    }
}
