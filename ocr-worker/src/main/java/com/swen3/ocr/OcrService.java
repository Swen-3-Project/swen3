package com.swen3.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
public class OcrService {
    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private final S3Client storage;
    private final CommandRunner commands;
    private final String bucket;

    public OcrService(S3Client storage, CommandRunner commands, @Value("${storage.bucket}") String bucket) {
        this.storage = storage;
        this.commands = commands;
        this.bucket = bucket;
    }

    public String extract(String storageKey) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("paperless-ocr-");
            Path pdf = directory.resolve("original.pdf");
            try (var input = storage.getObject(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())) {
                Files.copy(input, pdf);
            }
            commands.run(List.of("pdftoppm", "-r", "150", "-png", pdf.toString(),
                    directory.resolve("page").toString()), directory, Duration.ofSeconds(120));
            List<Path> pages;
            try (var files = Files.list(directory)) {
                pages = files.filter(path -> path.getFileName().toString().matches("page-\\d+\\.png"))
                        .sorted(Comparator.comparingInt(this::pageNumber)).toList();
            }
            if (pages.isEmpty()) throw new OcrException("PDF has no readable pages");
            var texts = new ArrayList<String>();
            for (Path page : pages) {
                Path output = directory.resolve("text-" + pageNumber(page));
                commands.run(List.of("tesseract", page.toString(), output.toString(), "-l", "deu+eng"),
                        directory, Duration.ofSeconds(60));
                texts.add(Files.readString(Path.of(output + ".txt")).strip());
            }
            String text = String.join("\n\n", texts).strip();
            log.info("ocr_text_extracted pages={} characters={}", pages.size(), text.length());
            return text;
        } catch (IOException | software.amazon.awssdk.core.exception.SdkException exception) {
            throw new OcrException(exception);
        } finally {
            cleanup(directory);
        }
    }

    private int pageNumber(Path path) {
        String name = path.getFileName().toString();
        return Integer.parseInt(name.substring(5, name.length() - 4));
    }

    private void cleanup(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("ocr_temporary_cleanup_failed errorType={}", exception.getClass().getSimpleName());
        }
    }
}
