package com.swen3.paperless.document;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    @Modifying
    @Query("update DocumentEntity d set d.summary = :summary where d.id = :id and d.summary is null and d.ocrText is not null")
    int saveSummary(UUID id, String summary);
}
