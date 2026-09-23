package com.swen3.paperless.reminder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {
    List<ReminderEntity> findByDocument_IdOrderByCreatedAtAsc(UUID documentId);

    Optional<ReminderEntity> findByIdAndDocument_Id(UUID id, UUID documentId);
}
