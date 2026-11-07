package com.swen3.paperless.document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentDetailResponse;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.document.dto.UpdateDocumentRequest;
import com.swen3.paperless.search.SearchClient;
import com.swen3.paperless.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final DocumentRepository repository;
    private final DocumentMapper mapper;
    private final FileStorage storage;
    private final SearchClient search;

    public DocumentService(DocumentRepository repository, DocumentMapper mapper, FileStorage storage, SearchClient search) {
        this.repository = repository;
        this.mapper = mapper;
        this.storage = storage;
        this.search = search;
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        return mapper.toResponse(repository.save(newDocument(UUID.randomUUID(), request)));
    }

    @Transactional
    public DocumentResponse createUploaded(UUID id, CreateDocumentRequest request, String storageKey) {
        DocumentEntity document = newDocument(id, request);
        document.setStorageKey(storageKey);
        return mapper.toResponse(repository.saveAndFlush(document));
    }

    @Transactional
    public void removeFailedUpload(UUID id) { repository.deleteById(id); }

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
    public List<DocumentResponse> search(String query) {
        if (query.isBlank()) return list();
        List<UUID> ids = search.search(query);
        var documents = new HashMap<UUID, DocumentEntity>();
        for (var document : repository.findAllById(ids)) documents.put(document.getId(), document);
        var results = new ArrayList<DocumentResponse>();
        for (UUID id : ids) {
            DocumentEntity document = documents.get(id);
            if (document != null) results.add(mapper.toResponse(document));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse get(UUID id) {
        return mapper.toDetailResponse(requireEntity(id));
    }

    @Transactional
    public DocumentResponse update(UUID id, UpdateDocumentRequest request) {
        DocumentEntity document = requireEntity(id);
        mapper.updateMetadata(request, document);
        return mapper.toResponse(repository.save(document));
    }

    @Transactional
    public DocumentEntity applyOcrResult(UUID id, boolean success, String text) {
        DocumentEntity document = repository.findById(id).orElse(null);
        if (document == null || document.getStatus() == DocumentStatus.INDEXED) return document;
        if (success) {
            document.setOcrText(text);
            document.setStatus(DocumentStatus.OCR_COMPLETED);
        } else if (document.getOcrText() == null) {
            document.setStatus(DocumentStatus.FAILED);
        }
        repository.save(document);
        return document;
    }

    @Transactional
    public boolean applyIndexResult(UUID id, boolean success) {
        DocumentEntity document = repository.findById(id).orElse(null);
        if (document == null) return false;
        if (document.getStatus() != DocumentStatus.INDEXED && document.getOcrText() != null) {
            document.setStatus(success ? DocumentStatus.INDEXED : DocumentStatus.FAILED);
            repository.save(document);
        }
        return true;
    }

    @Transactional
    public void delete(UUID id) {
        DocumentEntity document = requireEntity(id);
        if (document.getStorageKey() != null) {
            storage.delete(document.getStorageKey());
            search.delete(id);
        }
        repository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentEntity requireEntity(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
