package com.swen3.ocr;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class CommandRunner {
    public void run(List<String> command, Path directory, Duration timeout) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("command.log").toFile()).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new OcrException("OCR command timed out");
            }
            if (process.exitValue() != 0) {
                throw new OcrException("OCR command failed with exit code " + process.exitValue());
            }
        } catch (IOException exception) {
            throw new OcrException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrException(exception);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
