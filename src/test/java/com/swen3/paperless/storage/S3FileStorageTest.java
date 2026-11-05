package com.swen3.paperless.storage;

import java.io.IOException;
import java.util.UUID;
import com.swen3.paperless.common.UploadTooLargeException;
import com.swen3.paperless.common.UploadValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class S3FileStorageTest {
    S3Client client = mock(S3Client.class);
    S3FileStorage storage = new S3FileStorage(client, "documents", DataSize.ofMegabytes(20));

    @Test void storesExactBytesInTheConfiguredBucket() throws IOException {
        UUID id = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7\ncontent".getBytes();
        var stored = storage.store(id, new MockMultipartFile("file", "../../a.pdf", "text/plain", bytes));
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertEquals("documents", request.getValue().bucket());
        assertEquals(id + ".pdf", request.getValue().key());
        assertEquals("application/pdf", request.getValue().contentType());
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        assertEquals(bytes.length, stored.size());
    }

    @Test void createsMissingBucketAndAcceptsConcurrentCreation() {
        when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        storage.store(UUID.randomUUID(), pdf());
        verify(client).createBucket(any(CreateBucketRequest.class));
        when(client.createBucket(any(CreateBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(409).build());
        assertDoesNotThrow(() -> storage.store(UUID.randomUUID(), pdf()));
    }

    @Test void rejectsEmptyInvalidAndOversizedFilesBeforeContactingStorage() {
        var small = new S3FileStorage(client, "documents", DataSize.ofBytes(10));
        assertThrows(UploadValidationException.class, () -> small.store(UUID.randomUUID(), new MockMultipartFile("file", new byte[0])));
        assertThrows(UploadValidationException.class, () -> small.store(UUID.randomUUID(), new MockMultipartFile("file", "bad".getBytes())));
        assertThrows(UploadTooLargeException.class, () -> small.store(UUID.randomUUID(), new MockMultipartFile("file", "%PDF-1234567".getBytes())));
        verifyNoInteractions(client);
    }

    @Test void turnsStorageAndInputFailuresIntoStorageExceptions() throws IOException {
        when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        assertThrows(StorageException.class, () -> storage.store(UUID.randomUUID(), pdf()));
        var unreadable = mock(org.springframework.web.multipart.MultipartFile.class);
        when(unreadable.getBytes()).thenThrow(new IOException());
        assertThrows(StorageException.class, () -> storage.store(UUID.randomUUID(), unreadable));
        reset(client);
        when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        when(client.createBucket(any(CreateBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(500).build());
        assertThrows(StorageException.class, () -> storage.store(UUID.randomUUID(), pdf()));
    }

    @Test void deletionIsIdempotentAndRejectsInvalidKeys() {
        String key = UUID.randomUUID() + ".pdf";
        storage.delete(key);
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertDoesNotThrow(() -> storage.delete(key));
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertThrows(StorageException.class, () -> storage.delete(key));
        assertThrows(StorageException.class, () -> storage.delete("../a.pdf"));
        assertThrows(StorageException.class, () -> storage.delete(null));
    }

    @Test void configuresPathStyleS3ClientWithoutContactingTheServer() {
        try (var configured = new S3Configuration().s3Client("http://localhost:9000", "test-key", "test-secret")) {
            assertEquals("s3", configured.serviceName());
        }
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.7\n".getBytes());
    }
}
