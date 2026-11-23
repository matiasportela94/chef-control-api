package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.ChatInterpretation;
import com.chefcontrol.application.port.AIService.ChatMessageRequest;
import com.chefcontrol.application.port.AIService.RecipeMessageRequest;
import com.chefcontrol.application.port.AIService.DocumentPage;
import com.chefcontrol.application.port.AIService.ChatDocumentRequest;
import com.chefcontrol.application.port.AIService.DocumentReading;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnExpression("!'${app.google.api-key:}'.isEmpty() && '${app.anthropic.api-key:}'.isEmpty()")
public class GemmaAIService implements AIService {


    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    /** El de las fotos y PDF de facturas; vacío = el de texto. */
    @Value("${app.google.vision-model:}")
    private String visionModel;

    public GemmaAIService(
            @Value("${app.google.api-key}") String apiKey,
            @Value("${app.google.model:gemini-2.5-flash}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(AiHttp.requestFactory())
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public ChatInterpretation interpretChatMessage(ChatMessageRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", ChatPrompt.systemPrompt(request,
                "Respondé solo con ese JSON, compacto y sin texto alrededor.")))));
        body.put("contents", List.of(Map.of("role", "user",
                "parts", List.of(Map.of("text", ChatPrompt.userMessage(request))))));
        body.put("generationConfig", generationConfig(ChatPrompt.MAX_OUTPUT_TOKENS));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Gemma", BUSY, () -> restClient.post()
                    .uri("/v1beta/models/" + model + ":generateContent").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Gemma] interpretChatMessage failed: {}", e.getMessage());
            throw ChatPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usageMetadata");
        log.info("[Gemma] interpretChatMessage model={} products={} input_tokens={} output_tokens={} "
                        + "thoughts_tokens={} cached_tokens={} ms={}", model, request.products().size(),
                usage.path("promptTokenCount").asInt(), usage.path("candidatesTokenCount").asInt(),
                usage.path("thoughtsTokenCount").asInt(), usage.path("cachedContentTokenCount").asInt(),
                System.currentTimeMillis() - start);
        return ChatPrompt.parse(CatalogSpreadsheetPrompt.extractJson(answerText(response), objectMapper));
    }

    @Override
    public DocumentReading interpretChatDocument(ChatDocumentRequest request) {
        String vision = visionModel == null || visionModel.isBlank() ? model : visionModel;
        List<Map<String, Object>> parts = new java.util.ArrayList<>();
        for (DocumentPage page : request.pages()) {
            parts.add(Map.of("inline_data", Map.of("mime_type", page.mediaType(),
                    "data", java.util.Base64.getEncoder().encodeToString(page.bytes()))));
        }
        parts.add(Map.of("text", DocumentPrompt.userText(request)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", DocumentPrompt.systemPrompt(request,
                "Respondé solo con ese JSON, compacto y sin texto alrededor.")))));
        body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        body.put("generationConfig", generationConfig(DocumentPrompt.MAX_OUTPUT_TOKENS));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Gemma", BUSY, () -> restClient.post()
                    .uri("/v1beta/models/" + vision + ":generateContent").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Gemma] interpretChatDocument failed: {}", e.getMessage());
            throw DocumentPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usageMetadata");
        log.info("[Gemma] interpretChatDocument model={} pages={} input_tokens={} output_tokens={} ms={}", vision,
                request.pages().size(), usage.path("promptTokenCount").asInt(), usage.path("candidatesTokenCount").asInt(),
                System.currentTimeMillis() - start);
        return DocumentPrompt.parse(CatalogSpreadsheetPrompt.extractJson(answerText(response), objectMapper));
    }

    @Override
    public CatalogSpreadsheetInterpretation interpretRecipeMessage(RecipeMessageRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", RecipeChatPrompt.systemPrompt(request,
                "Respondé solo con ese JSON, compacto y sin texto alrededor.")))));
        body.put("contents", List.of(Map.of("role", "user",
                "parts", List.of(Map.of("text", RecipeChatPrompt.userMessage(request))))));
        body.put("generationConfig", generationConfig(RecipeChatPrompt.MAX_OUTPUT_TOKENS));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Gemma", BUSY, () -> restClient.post()
                    .uri("/v1beta/models/" + model + ":generateContent").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Gemma] interpretRecipeMessage failed: {}", e.getMessage());
            throw RecipeChatPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usageMetadata");
        log.info("[Gemma] interpretRecipeMessage model={} products={} input_tokens={} output_tokens={} "
                        + "thoughts_tokens={} cached_tokens={} ms={}", model, request.products().size(),
                usage.path("promptTokenCount").asInt(), usage.path("candidatesTokenCount").asInt(),
                usage.path("thoughtsTokenCount").asInt(), usage.path("cachedContentTokenCount").asInt(),
                System.currentTimeMillis() - start);
        return RecipeChatPrompt.parse(CatalogSpreadsheetPrompt.extractJson(answerText(response), objectMapper), request);
    }

    /** "Ocupado" en Google: límite por minuto (429) y "high demand" (503). */
    private static final java.util.Set<Integer> BUSY = java.util.Set.of(429, 503);

    /** JSON, sin azar y, en 2.5 Flash, sin razonamiento: gasta tokens de salida y traducir no lo necesita. */
    private Map<String, Object> generationConfig(int maxOutputTokens) {
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("maxOutputTokens", maxOutputTokens);
        generation.put("temperature", 0);
        generation.put("responseMimeType", "application/json");
        // ponytail: otros modelos no aceptan apagar el razonamiento, por eso va por nombre.
        if (model.startsWith("gemini-2.5-flash")) {
            generation.put("thinkingConfig", Map.of("thinkingBudget", 0));
        }
        return generation;
    }

    private static String answerText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
        }
        return text.toString();
    }

    /**
     * Probado el 2026-10-23: gemini-2.5-flash (el del .env local) responde bien en ~20 s para 300 filas.
     * gemma-4-31b-it devolvió 500 de Google y gemma-4-26b-a4b-it tardó 109 s y mezcló su razonamiento con el texto.
     */
    @Override
    public CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(CatalogSpreadsheetRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", CatalogSpreadsheetPrompt.systemPrompt(request,
                "Respondé solo con ese JSON, compacto (en una sola línea, sin sangría) y sin texto alrededor.")))));
        body.put("contents", List.of(Map.of("role", "user",
                "parts", List.of(Map.of("text", CatalogSpreadsheetPrompt.rows(request))))));
        body.put("generationConfig", generationConfig(CatalogSpreadsheetPrompt.MAX_OUTPUT_TOKENS));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Gemma", BUSY, () -> restClient.post()
                    .uri("/v1beta/models/" + model + ":generateContent").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Gemma] interpretCatalogSpreadsheet failed: {}", e.getMessage());
            throw CatalogSpreadsheetPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usageMetadata");
        log.info("[Gemma] interpretCatalogSpreadsheet model={} rows={} input_tokens={} output_tokens={} "
                        + "thoughts_tokens={} cached_tokens={} ms={}", model, CatalogSpreadsheetPrompt.rowCount(request),
                usage.path("promptTokenCount").asInt(), usage.path("candidatesTokenCount").asInt(),
                usage.path("thoughtsTokenCount").asInt(), usage.path("cachedContentTokenCount").asInt(),
                System.currentTimeMillis() - start);

        JsonNode candidate = response.path("candidates").path(0);
        if ("MAX_TOKENS".equals(candidate.path("finishReason").asText())) {
            throw CatalogSpreadsheetPrompt.tooLarge();
        }
        return CatalogSpreadsheetPrompt.parse(CatalogSpreadsheetPrompt.extractJson(answerText(response), objectMapper));
    }
}
