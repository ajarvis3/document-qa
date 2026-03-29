package com.learn.ai.document_qa.controller;

import com.learn.ai.document_qa.dto.AnswerResponse;
import com.learn.ai.document_qa.dto.UploadDocumentResponse;
import com.learn.ai.document_qa.service.DocumentQAService;
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
    public UploadDocumentResponse upload(@RequestParam("file") MultipartFile file) {
        return documentQAService.uploadDocument(file);
    }

    @PostMapping("/ask")
    public AnswerResponse askQuestion(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");
        String documentText = request.getOrDefault("fileUri", "");

        AnswerResponse response = documentQAService.askQuestion(question, documentText);
        return response;
    }
}

