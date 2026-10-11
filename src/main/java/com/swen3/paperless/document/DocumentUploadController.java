package com.swen3.paperless.document;

import java.net.URI;
import com.swen3.paperless.document.dto.DocumentResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {
    private final DocumentUploadService service;
    public DocumentUploadController(DocumentUploadService service) { this.service = service; }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(required = false) String title,
                                                   @RequestParam(required = false) String description) {
        DocumentResponse response = service.upload(file, title, description);
        return ResponseEntity.created(URI.create("/api/documents/" + response.id())).body(response);
    }
}
