package com.swen3.paperless.search;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class SearchClient {
    private final RestClient client;

    @Autowired
    public SearchClient(@Value("${search.url:http://localhost:9200}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    SearchClient(RestClient client) { this.client = client; }

    public List<UUID> search(String query) {
        var body = Map.of("size", 100, "_source", false, "query",
                Map.of("match", Map.of("content", Map.of("query", query, "fuzziness", "AUTO"))));
        try {
            JsonNode response = client.post().uri("/paperless-documents/_search").body(body)
                    .retrieve().body(JsonNode.class);
            var ids = new ArrayList<UUID>();
            if (response != null) {
                for (JsonNode hit : response.path("hits").path("hits")) {
                    ids.add(UUID.fromString(hit.path("_id").asString()));
                }
            }
            return ids;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return List.of();
            throw new SearchException(exception);
        } catch (RestClientException exception) {
            throw new SearchException(exception);
        }
    }

    public void delete(UUID id) {
        try {
            client.delete().uri("/paperless-documents/_doc/{id}?refresh=wait_for", id)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) throw new SearchException(exception);
        } catch (RestClientException exception) {
            throw new SearchException(exception);
        }
    }
}
