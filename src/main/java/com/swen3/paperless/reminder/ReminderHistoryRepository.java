package com.swen3.paperless.reminder;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderHistoryRepository extends JpaRepository<ReminderHistoryEntity, UUID> {
    List<ReminderHistoryEntity> findByReminder_IdOrderByOccurredAtAsc(UUID reminderId);
}
