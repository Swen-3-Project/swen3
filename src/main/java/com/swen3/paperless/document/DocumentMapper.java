package com.swen3.paperless.document;

import com.swen3.paperless.document.dto.CreateDocumentRequest;
import com.swen3.paperless.document.dto.DocumentResponse;
import com.swen3.paperless.document.dto.UpdateDocumentRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    DocumentEntity toEntity(CreateDocumentRequest request);

    DocumentResponse toResponse(DocumentEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filename", ignore = true)
    @Mapping(target = "mimeType", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateMetadata(UpdateDocumentRequest request, @MappingTarget DocumentEntity entity);
}
