package com.swen3.ocr;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OcrServiceTest {
    S3Client storage = mock(S3Client.class);
    CommandRunner commands = mock(CommandRunner.class);
    OcrService service = new OcrService(storage, commands, "documents");

    @Test void downloadsRendersAndRecognizesPagesInNumericOrderAndCleansUp() throws Exception {
        download();
        doAnswer(call -> {
            List<String> args = call.getArgument(0);
            Path directory = call.getArgument(1);
            if (args.getFirst().equals("pdftoppm")) {
                Files.write(directory.resolve("page-10.png"), new byte[0]);
                Files.write(directory.resolve("page-2.png"), new byte[0]);
            } else {
                Files.writeString(Path.of(args.get(2) + ".txt"), args.get(1).endsWith("page-2.png") ? "Hello\n" : "World\n");
            }
            return null;
        }).when(commands).run(anyList(), any(), any());
        assertEquals("Hello\n\nWorld", service.extract("key.pdf"));
        verify(storage).getObject(GetObjectRequest.builder().bucket("documents").key("key.pdf").build());
        var directories = ArgumentCaptor.forClass(Path.class);
        verify(commands, times(3)).run(anyList(), directories.capture(), any(Duration.class));
        assertFalse(Files.exists(directories.getValue()));
    }

    @Test void rejectsPdfWithoutPagesAndWrapsDownloadFailures() {
        download();
        assertThrows(OcrException.class, () -> service.extract("key.pdf"));
        when(storage.getObject(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertThrows(OcrException.class, () -> service.extract("key.pdf"));
    }

    private void download() {
        when(storage.getObject(any(GetObjectRequest.class))).thenAnswer(call ->
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream("%PDF-".getBytes()))));
    }
}
