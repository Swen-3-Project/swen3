package com.swen3.paperless.search;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SearchClientTest {
    @Test void sendsFuzzyContentQueryAndReadsIdsInRelevanceOrder() {
        var builder = RestClient.builder().baseUrl("http://search");
        var server = MockRestServiceServer.bindTo(builder).build();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        server.expect(requestTo("http://search/paperless-documents/_search")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"size\":100,\"_source\":false,\"query\":{\"match\":{\"content\":{\"query\":\"Hello\",\"fuzziness\":\"AUTO\"}}}}"))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[{\"_id\":\"" + first + "\"},{\"_id\":\"" + second + "\"}]}}", MediaType.APPLICATION_JSON));
        assertEquals(java.util.List.of(first, second), new SearchClient(builder.build()).search("Hello"));
        server.verify();
    }

    @Test void handlesMissingIndexAndServiceFailures() {
        var builder = RestClient.builder().baseUrl("http://search");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var client = new SearchClient(builder.build());
        assertTrue(client.search("Hello").isEmpty());
        assertThrows(SearchException.class, () -> client.search("Hello"));
        server.verify();
    }

    @Test void deletesIdempotentlyAndSurfacesFailures() {
        var builder = RestClient.builder().baseUrl("http://search");
        var server = MockRestServiceServer.bindTo(builder).build();
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://search/paperless-documents/_doc/" + id + "?refresh=wait_for"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var client = new SearchClient(builder.build());
        assertDoesNotThrow(() -> client.delete(id));
        assertThrows(SearchException.class, () -> client.delete(id));
        server.verify();
        assertNotNull(new SearchClient("http://localhost:9200"));
    }
}
