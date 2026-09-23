package com.swen3.paperless.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swen3.paperless.common.ResourceNotFoundException;
import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.document.dto.UpdateDocumentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final DocumentRepository repository;
    private final DocumentMapper mapper;

    public DocumentService(DocumentRepository repository, DocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        DocumentEntity document = mapper.toEntity(request);
        document.setId(UUID.randomUUID());
        document.setStatus(DocumentStatus.CREATED);
        document.setCreatedAt(Instant.now());
        return mapper.toResponse(repository.save(document));
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
        repository.delete(requireEntity(id));
    }

    @Transactional(readOnly = true)
    public DocumentEntity requireEntity(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Document " + id + " was not found"));
    }
}
