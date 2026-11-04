package com.swen3.paperless.document;

import java.util.UUID;
import com.swen3.paperless.common.UploadValidationException;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.messaging.OcrJob;
import com.swen3.paperless.messaging.OcrJobPublisher;
import com.swen3.paperless.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {
    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);
    private final FileStorage storage;
    private final DocumentService documents;
    private final OcrJobPublisher publisher;

    public DocumentUploadService(FileStorage storage, DocumentService documents, OcrJobPublisher publisher) {
        this.storage = storage;
        this.documents = documents;
        this.publisher = publisher;
    }

    public DocumentResponse upload(MultipartFile file, String title, String description) {
        String filename = cleanFilename(file.getOriginalFilename());
        String cleanTitle = title == null ? null : title.trim();
        if (cleanTitle != null && cleanTitle.length() > 255) {
            throw new UploadValidationException("title", "The title must contain at most 255 characters");
        }
        UUID id = UUID.randomUUID();
        try (var scope = MDC.putCloseable("documentId", id.toString())) {
            FileStorage.StoredFile stored = storage.store(id, file);
            try {
                var request = new CreateDocumentRequest(filename, "application/pdf", stored.size(), cleanTitle, description);
                // This separate service commits the document before a worker can receive its ID.
                DocumentResponse response = documents.createUploaded(id, request, stored.key());
                publisher.publish(new OcrJob(1, UUID.randomUUID(), id, stored.key()));
                log.info("document_uploaded");
                return response;
            } catch (RuntimeException exception) {
                cleanupFailedUpload(id, stored.key());
                log.warn("upload_failed errorType={}", exception.getClass().getSimpleName());
                throw exception;
            }
        }
    }

    private String cleanFilename(String original) {
        String filename = original == null ? "document.pdf" : original.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)) {
            throw new UploadValidationException("file", "The filename must contain 1 to 255 printable characters");
        }
        return filename;
    }

    private void cleanupFailedUpload(UUID id, String key) {
        try {
            documents.removeFailedUpload(id);
        } catch (RuntimeException exception) {
            log.error("upload_document_cleanup_failed errorType={}", exception.getClass().getSimpleName());
        }
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.error("upload_file_cleanup_failed errorType={}", exception.getClass().getSimpleName());
        }
    }
}
