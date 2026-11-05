package com.swen3.paperless.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.swen3.paperless.common.UploadTooLargeException;
import com.swen3.paperless.common.UploadValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
public class S3FileStorage implements FileStorage {
    private final S3Client client;
    private final String bucket;
    private final long maxBytes;

    public S3FileStorage(S3Client client, @Value("${storage.bucket}") String bucket,
                         @Value("${spring.servlet.multipart.max-file-size:20MB}") DataSize maxSize) {
        this.client = client;
        this.bucket = bucket;
        this.maxBytes = maxSize.toBytes();
    }

    @Override
    public StoredFile store(UUID documentId, MultipartFile file) {
        if (file.isEmpty()) throw new UploadValidationException("file", "Select a non-empty PDF");
        if (file.getSize() > maxBytes) throw new UploadTooLargeException();
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > maxBytes) throw new UploadTooLargeException();
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new UploadValidationException("file", "The file must have a PDF header");
            }
            ensureBucket();
            String key = documentId + ".pdf";
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType("application/pdf").build(), RequestBody.fromBytes(bytes));
            return new StoredFile(key, bytes.length);
        } catch (IOException | software.amazon.awssdk.core.exception.SdkException exception) {
            throw new StorageException(exception);
        }
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) throw exception;
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception creationFailure) {
                if (creationFailure.statusCode() != 409) throw creationFailure;
            }
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.pdf")) {
            throw new StorageException(new IllegalArgumentException("Invalid storage key"));
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) throw new StorageException(exception);
        } catch (software.amazon.awssdk.core.exception.SdkException exception) {
            throw new StorageException(exception);
        }
    }
}
