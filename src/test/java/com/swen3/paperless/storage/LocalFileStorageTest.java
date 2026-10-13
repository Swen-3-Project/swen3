package com.swen3.paperless.storage;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import com.swen3.paperless.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {
    @TempDir Path directory;

    @Test
    void storesExactBytesAndDeletesIdempotently() throws IOException {
        var storage = new LocalFileStorage(directory.toString(), DataSize.ofMegabytes(20));
        byte[] bytes = "%PDF-1.7\ncontent".getBytes();
        UUID id = UUID.randomUUID();
        var stored = storage.store(id, new MockMultipartFile("file", "../../invoice.pdf", "text/plain", bytes));
        assertEquals(id + ".pdf", stored.key());
        assertEquals(bytes.length, stored.size());
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve(stored.key())));
        var handler = new FileCleanupHandler(storage);
        assertEquals(com.swen3.paperless.jobs.JobKind.DELETE_FILE, handler.kind());
        handler.handle(new com.swen3.paperless.jobs.PendingJob(handler.kind(), id, stored.key()));
        storage.delete(stored.key());
        assertFalse(Files.exists(directory.resolve(stored.key())));
    }

    @Test
    void invalidFilesLeaveNoStoredFiles() throws IOException {
        var storage = new LocalFileStorage(directory.toString(), DataSize.ofBytes(10));
        assertThrows(UploadValidationException.class, () -> storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", new byte[0])));
        assertThrows(UploadValidationException.class, () -> storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", "not pdf".getBytes())));
        assertThrows(UploadTooLargeException.class, () -> storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", "%PDF-123456789".getBytes())));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test
    void storageErrorsAndUnsafeKeysAreControlled() throws IOException {
        Path occupied = directory.resolve("occupied");
        Files.writeString(occupied, "not a directory");
        var storage = new LocalFileStorage(occupied.toString(), DataSize.ofMegabytes(20));
        assertThrows(StorageException.class, () -> storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", "%PDF-content".getBytes())));
        assertThrows(StorageException.class, () -> storage.delete("../../outside.pdf"));
        assertThrows(StorageException.class, () -> storage.delete(null));
    }
}
