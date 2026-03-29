package com.learn.ai.document_qa.service;

import com.google.genai.Client;
import com.google.genai.types.*;
import com.learn.ai.document_qa.dto.AnswerResponse;
import com.learn.ai.document_qa.dto.UploadDocumentResponse;
import com.learn.ai.document_qa.exception.UploadFailed;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Service
public class DocumentQAService {

    private static final String MODEL = "gemini-2.0-flash";

    private static final String SYSTEM_PROMPT =
        """
        Use only the document provided by the file URI to answer questions.
        If the answer cannot be found in the document, say you cannot find it in the provided text.
        If you are unsure of the answer, state that you are unsure.
        """;

    public UploadDocumentResponse uploadDocument(MultipartFile file) {
        try (Client client = new Client()) {
            File tempFile = File.createTempFile("upload-", "-" + file.getOriginalFilename());
            file.transferTo(tempFile);
            tempFile.deleteOnExit();
            UploadFileConfig config = UploadFileConfig.builder()
                    .mimeType("application/pdf")
                    .displayName(file.getOriginalFilename())
                    .build();
            com.google.genai.types.File result = client.files.upload(tempFile, config);
            if (result != null && result.uri().isPresent()) {
                return new UploadDocumentResponse(result.uri().get());
            } else {
                throw new UploadFailed("Failed to retrieve file uri");
            }
        } catch (IOException ex) {
            throw new UploadFailed("Failed to upload document: " + ex.getMessage(), ex);
        }
    }

    public AnswerResponse askQuestion(String question, String fileUri) {
        try (Client client = new Client()) {
            // Part 1 — reference the already-uploaded file by URI
            Part filePart = Part.builder()
                    .fileData(FileData.builder()
                            .fileUri(fileUri)
                            .mimeType("application/pdf")
                            .build())
                    .build();

            // Part 2 — the question
            Part questionPart = Part.builder()
                    .text(question)
                    .build();

            // Combine both parts into a single user message
            Content content = Content.builder()
                    .role("user")
                    .parts(filePart, questionPart)
                    .build();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(
                            Content.builder()
                                    .parts(Collections.singletonList(
                                            Part.builder().text(SYSTEM_PROMPT).build()
                                    ))
                                    .build()
                    )
                    .temperature(0.2f) // You can also set other configs here
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    MODEL,
                    List.of(content),
                    config
            );

            return AnswerResponse.fromGemini(response.text());
        }
    }
}
