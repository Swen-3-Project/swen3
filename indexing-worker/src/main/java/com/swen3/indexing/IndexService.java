package com.swen3.indexing;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class IndexService {
    private final RestClient client;

    @Autowired
    public IndexService(@Value("${search.url:http://localhost:9200}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    IndexService(RestClient client) { this.client = client; }

    public void index(IndexJob job) {
        try {
            client.put().uri("/paperless-documents/_doc/{id}?refresh=wait_for", job.documentId())
                    .body(Map.of("documentId", job.documentId().toString(),
                            "filename", job.filename(), "content", job.content()))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IndexException(exception);
        }
    }
}
