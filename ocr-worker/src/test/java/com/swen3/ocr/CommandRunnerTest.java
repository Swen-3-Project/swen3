package com.swen3.ocr;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CommandRunnerTest {
    @TempDir Path directory;
    CommandRunner commands = new CommandRunner();

    private String java() {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    }

    @Test void handlesSuccessfulFailedMissingAndTimedOutCommands() {
        assertDoesNotThrow(() -> commands.run(List.of(java(), "-version"), directory, Duration.ofSeconds(10)));
        assertThrows(OcrException.class, () -> commands.run(List.of(java(), "-invalid-option"), directory, Duration.ofSeconds(10)));
        assertThrows(OcrException.class, () -> commands.run(List.of(directory.resolve("missing-command").toString()), directory, Duration.ofSeconds(1)));
        assertThrows(OcrException.class, () -> commands.run(List.of(java(), "-version"), directory, Duration.ofNanos(1)));
    }
}
