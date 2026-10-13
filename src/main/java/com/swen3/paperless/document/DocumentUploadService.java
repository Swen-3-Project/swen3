package com.swen3.paperless.document;

import java.time.Instant;
import java.util.UUID;
import com.swen3.paperless.common.UploadValidationException;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.jobs.*;
import com.swen3.paperless.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {
    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);
    private final FileStorage storage;
    private final DocumentRepository repository;
    private final DocumentMapper mapper;
    private final OutboxService outbox;
    private final TransactionTemplate transaction;

    public DocumentUploadService(FileStorage storage, DocumentRepository repository, DocumentMapper mapper,
                                 OutboxService outbox, PlatformTransactionManager manager) {
        this.storage = storage;
        this.repository = repository;
        this.mapper = mapper;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(manager);
    }

    public DocumentResponse upload(MultipartFile file, String title, String description) {
        String original = file.getOriginalFilename();
        String filename = original == null ? "document.pdf" : original.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)) {
            throw new UploadValidationException("file", "The filename must contain 1 to 255 printable characters");
        }
        String cleanTitle = title == null ? null : title.trim();
        if (cleanTitle != null && cleanTitle.length() > 255) {
            throw new UploadValidationException("title", "The title must contain at most 255 characters");
        }
        UUID id = UUID.randomUUID();
        try (var scope = MDC.putCloseable("documentId", id.toString())) {
            FileStorage.StoredFile stored;
            try {
                stored = storage.store(id, file);
            } catch (RuntimeException exception) {
                log.warn("upload_storage_failed errorType={}", exception.getClass().getSimpleName());
                throw exception;
            }
            String finalFilename = filename;
            try {
                DocumentResponse response = transaction.execute(status -> {
                    DocumentEntity document = mapper.toEntity(new CreateDocumentRequest(finalFilename,
                            "application/pdf", stored.size(), cleanTitle, description));
                    document.setId(id);
                    document.setStatus(DocumentStatus.CREATED);
                    document.setCreatedAt(Instant.now());
                    document.setStorageKey(stored.key());
                    repository.saveAndFlush(document);
                    outbox.enqueue(JobKind.OCR_REQUESTED, id, stored.key());
                    return mapper.toResponse(document);
                });
                log.info("document_uploaded");
                return response;
            } catch (RuntimeException exception) {
                try { storage.delete(stored.key()); }
                catch (RuntimeException cleanupException) {
                    log.error("upload_rollback_cleanup_failed errorType={}", cleanupException.getClass().getSimpleName());
                }
                log.warn("upload_failed errorType={}", exception.getClass().getSimpleName());
                throw exception;
            }
        }
    }
}
