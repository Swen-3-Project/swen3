package com.swen3.genai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GeminiClient client = new GeminiClient(builder.build(), "gemini-3.1-flash-lite", "test-key");

    @Test void sendsOcrTextAndExtractsPlainSummary() {
        server.expect(requestTo("http://gemini/v1beta/models/gemini-3.1-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Eine Sonnenblume."))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"STOP","content":{"parts":[
                          {"thought":true,"text":"Internal"},
                          {"text":" Eine Sonnenblume ist zu sehen. "}
                        ]}}]}
                        """, MediaType.APPLICATION_JSON));
        assertEquals("Eine Sonnenblume ist zu sehen.", client.summarize("Eine Sonnenblume."));
        server.verify();
    }

    @Test void recognizesAMissingKey() {
        assertFalse(new GeminiClient("http://gemini", "gemini-3.1-flash-lite", " ").configured());
        assertTrue(client.configured());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"candidates\":[{\"finishReason\":\"STOP\"}]}", "invalid JSON"})
    void rejectsMissingOrMalformedOutput(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertFalse(assertThrows(GeminiException.class, () -> client.summarize("Text")).isRetryable());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 408, 429, 503})
    void classifiesHttpFailures(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        var error = assertThrows(GeminiException.class, () -> client.summarize("Text"));
        assertEquals(status != 401, error.isRetryable());
        assertEquals("http_" + status, error.getMessage());
        server.verify();
    }
}
