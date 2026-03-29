package com.learn.ai.document_qa.service;

import com.google.genai.Client;
import com.google.genai.types.*;
import com.learn.ai.document_qa.dto.AnswerResponse;
import com.learn.ai.document_qa.dto.UploadDocumentResponse;
import com.learn.ai.document_qa.exception.AskQuestionFailed;
import com.learn.ai.document_qa.exception.UploadFailed;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DocumentQAService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentQAService.class);

    private static final String CHAT_MODEL = "gemini-2.5-flash";
    private static final String EMBEDDING_MODEL_NAME = "gemini-embedding-001";
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final int MAX_RESULTS = 5;

    private static final String SYSTEM_PROMPT =
        """
        Use only the document context provided to answer questions.
        If the answer cannot be found in the context, say you cannot find it in the provided text.
        If you are unsure of the answer, state that you are unsure.
        """;

    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter splitter;
    // TODO: Replace with persistent storage to survive application restarts
    private final Map<String, InMemoryEmbeddingStore<TextSegment>> documentStores = new ConcurrentHashMap<>();

    public DocumentQAService(@Value("${GEMINI_KEY}") String apiKey) {
        this.embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(EMBEDDING_MODEL_NAME)
                .build();
        this.splitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
    }

    public UploadDocumentResponse uploadDocument(MultipartFile file) {
        try {
            // Parse the PDF into a LangChain4j Document
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            Document document = parser.parse(file.getInputStream());

            // Chunk the document into overlapping text segments
            List<TextSegment> segments = splitter.split(document);
            if (segments.isEmpty()) {
                throw new UploadFailed("No text could be extracted from the document");
            }

            // Embed all segments using gemini-embedding-001
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // Store embeddings in a per-document in-memory store
            InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
            store.addAll(embeddings, segments);

            String documentId = UUID.randomUUID().toString();
            documentStores.put(documentId, store);

            LOG.info("Stored {} chunks for documentId={}", segments.size(), documentId);
            return new UploadDocumentResponse(documentId);
        } catch (UploadFailed ex) {
            throw ex;
        } catch (IOException ex) {
            throw new UploadFailed("Failed to read document: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new UploadFailed("Failed to process document: " + ex.getMessage(), ex);
        }
    }

    public AnswerResponse askQuestion(String question, String documentId) {
        InMemoryEmbeddingStore<TextSegment> store = documentStores.get(documentId);
        if (store == null) {
            throw new AskQuestionFailed("Document not found: " + documentId);
        }

        // Embed the question and retrieve the most relevant chunks before opening the Gemini client
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchResult<TextSegment> searchResult = store.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(MAX_RESULTS)
                        .build()
        );
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        if (matches.isEmpty()) {
            throw new AskQuestionFailed("No relevant content found for the question");
        }

        // Build the context from retrieved chunks
        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

        // Augment the prompt with the retrieved context
        String augmentedPrompt = String.format("""
                Context from document:
                %s

                Question: %s
                """, context, question);

        try (Client client = new Client()) {

            Part contentPart = Part.builder().text(augmentedPrompt).build();
            Content content = Content.builder()
                    .role("user")
                    .parts(contentPart)
                    .build();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(
                            Content.builder()
                                    .parts(Collections.singletonList(
                                            Part.builder().text(SYSTEM_PROMPT).build()
                                    ))
                                    .build()
                    )
                    .temperature(0.2f)
                    .build();

            LOG.info("Sending question to Gemini with {} context chunks: {}", matches.size(), question);

            GenerateContentResponse response = client.models.generateContent(
                    CHAT_MODEL,
                    List.of(content),
                    config
            );

            String answer = response != null ? response.text() : null;
            if (answer == null || answer.isBlank()) {
                throw new AskQuestionFailed("Gemini returned an empty answer");
            }
            LOG.info(answer);
            return AnswerResponse.fromGemini(answer);
        } catch (AskQuestionFailed ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Gemini request failed: {} class={} cause={}", ex.getMessage(),
                    ex.getClass().getName(), ex.getCause());
            throw new AskQuestionFailed("Failed to get answer from Gemini", ex);
        }
    }
}
