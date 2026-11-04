package com.swen3.paperless.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swen3.paperless.storage.FileStorage;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.document.dto.UpdateDocumentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final DocumentRepository repository;
    private final DocumentMapper mapper;
    private final FileStorage storage;

    public DocumentService(DocumentRepository repository, DocumentMapper mapper, FileStorage storage) {
        this.repository = repository;
        this.mapper = mapper;
        this.storage = storage;
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        DocumentEntity document = newDocument(UUID.randomUUID(), request);
        return mapper.toResponse(repository.save(document));
    }

    @Transactional
    public DocumentResponse createUploaded(UUID id, CreateDocumentRequest request, String storageKey) {
        DocumentEntity document = newDocument(id, request);
        document.setStorageKey(storageKey);
        return mapper.toResponse(repository.saveAndFlush(document));
    }

    @Transactional
    public void removeFailedUpload(UUID id) {
        repository.deleteById(id);
    }

    private DocumentEntity newDocument(UUID id, CreateDocumentRequest request) {
        DocumentEntity document = mapper.toEntity(request);
        document.setId(id);
        document.setStatus(DocumentStatus.CREATED);
        document.setCreatedAt(Instant.now());
        return document;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id) {
        return mapper.toResponse(requireEntity(id));
    }

    @Transactional
    public DocumentResponse update(UUID id, UpdateDocumentRequest request) {
        DocumentEntity document = requireEntity(id);
        mapper.updateMetadata(request, document);
        return mapper.toResponse(repository.save(document));
    }

    @Transactional
    public void delete(UUID id) {
        DocumentEntity document = requireEntity(id);
        if (document.getStorageKey() != null) {
            storage.delete(document.getStorageKey());
        }
        repository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentEntity requireEntity(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new DocumentNotFoundException(id));
    }
}
