package com.swen3.paperless.reminder;

import com.swen3.paperless.reminder.dto.CreateReminderRequest;
import com.swen3.paperless.reminder.dto.ReminderHistoryResponse;
import com.swen3.paperless.reminder.dto.ReminderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReminderMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "document", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    ReminderEntity toEntity(CreateReminderRequest request);

    @Mapping(target = "documentId", source = "document.id")
    ReminderResponse toResponse(ReminderEntity entity);

    ReminderHistoryResponse toHistoryResponse(ReminderHistoryEntity entity);
}
