package com.swen3.paperless.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.swen3.paperless.common.UploadTooLargeException;
import com.swen3.paperless.common.UploadValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private final Path root;
    private final long maxBytes;

    public LocalFileStorage(@Value("${storage.directory:./output/uploads}") String directory,
                            @Value("${spring.servlet.multipart.max-file-size:20MB}") DataSize maxSize) {
        root = Path.of(directory).toAbsolutePath().normalize();
        maxBytes = maxSize.toBytes();
    }

    @Override
    public StoredFile store(UUID documentId, MultipartFile file) {
        if (file.isEmpty()) throw new UploadValidationException("file", "Select a non-empty PDF");
        if (file.getSize() > maxBytes) throw new UploadTooLargeException();
        String key = documentId + ".pdf";
        Path temporary = null;
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            if (!Arrays.equals(header, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                throw new UploadValidationException("file", "The file must have a PDF header");
            }
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, "upload-", ".tmp");
            long size = header.length;
            try (var output = Files.newOutputStream(temporary)) {
                output.write(header);
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) {
                    size += count;
                    if (size > maxBytes) throw new UploadTooLargeException();
                    output.write(buffer, 0, count);
                }
            }
            Files.move(temporary, resolve(key), StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(key, size);
        } catch (IOException exception) {
            throw new StorageException(exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) {
                    log.error("temporary_file_cleanup_failed errorType={}", exception.getClass().getSimpleName());
                }
            }
        }
    }

    @Override
    public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException exception) { throw new StorageException(exception); }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.pdf")) {
            throw new StorageException(new IllegalArgumentException("Invalid storage key"));
        }
        return root.resolve(key);
    }
}
