package com.swen3.paperless.storage;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {
    record StoredFile(String key, long size) { }
    StoredFile store(UUID documentId, MultipartFile file);
    void delete(String storageKey);
}
