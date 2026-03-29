package com.learn.ai.document_qa.controller;

import com.learn.ai.document_qa.model.AnswerResponse;
import com.learn.ai.document_qa.service.DocumentQAService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/document-qa")
public class DocumentQAController {

    private final DocumentQAService documentQAService;

    public DocumentQAController(DocumentQAService documentQAService) {
        this.documentQAService = documentQAService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        String fileUri = documentQAService.uploadDocument(file.getBytes());
        return ResponseEntity.ok(fileUri);  // client stores this
    }

    @PostMapping("/ask")
    public ResponseEntity<AnswerResponse> askQuestion(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");
        String documentText = request.getOrDefault("fileUri", "");

        AnswerResponse response = documentQAService.askQuestion(question, documentText);
        if (!response.success() && "Please provide a question.".equals(response.answer())) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }
}

