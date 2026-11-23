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
import org.springframework.beans.factory.annotation.Autowired;
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
@ConditionalOnExpression("!'${app.anthropic.api-key:}'.isEmpty()")
public class ClaudeAIService implements AIService {

    private static final String CATALOG_TOOL = "import_catalog";
    private static final String CHAT_TOOL = "record_operations";
    /** "Ocupado" en Anthropic: límite (429), no disponible (503) y sobrecargado (529). */
    private static final java.util.Set<Integer> BUSY = java.util.Set.of(429, 503, 529);
    private static final String RECIPE_TOOL = "write_recipe";
    private static final String DOCUMENT_TOOL = "read_document";

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    /** El de las fotos y PDF de facturas; vacío = el de texto. Se cambia sin deploy si Haiku no lee bien. */
    @Value("${app.anthropic.vision-model:}")
    private String visionModel;

    // Dos constructores: sin @Autowired Spring busca uno sin argumentos y no arranca (staging, 2026-11-16).
    @Autowired
    public ClaudeAIService(
            @Value("${app.anthropic.api-key}") String apiKey,
            @Value("${app.anthropic.model:claude-haiku-4-5-20251001}") String model,
            ObjectMapper objectMapper) {
        this(RestClient.builder().requestFactory(AiHttp.requestFactory()), apiKey, model, objectMapper);
    }

    /** Para los tests: un builder atado a un servidor simulado. */
    ClaudeAIService(RestClient.Builder builder, String apiKey, String model, ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    /**
     * El system prompt (reglas + catálogo) va con cache_control: es casi todo el pedido y se repite en cada mensaje.
     * Las herramientas van antes del system, así que el corte cachea las dos. Con Haiku 4.5 Anthropic no cachea menos
     * de 4096 tokens: un catálogo chico no llega y cache_read_input_tokens queda en 0, sin error.
     */
    @Override
    public ChatInterpretation interpretChatMessage(ChatMessageRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", ChatPrompt.MAX_OUTPUT_TOKENS);
        body.put("temperature", 0);
        body.put("system", List.of(Map.of("type", "text",
                "text", ChatPrompt.systemPrompt(request, "Devolvé el resultado con la herramienta " + CHAT_TOOL + "."),
                "cache_control", Map.of("type", "ephemeral"))));
        body.put("tools", List.of(Map.of(
                "name", CHAT_TOOL,
                "description", "Devuelve las operaciones de stock que pide el mensaje",
                "input_schema", ChatPrompt.jsonSchema())));
        body.put("tool_choice", Map.of("type", "tool", "name", CHAT_TOOL));
        body.put("messages", List.of(Map.of("role", "user", "content", ChatPrompt.userMessage(request))));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Claude", BUSY,
                    () -> restClient.post().uri("/v1/messages").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Claude] interpretChatMessage failed: {}", e.getMessage());
            throw ChatPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usage");
        log.info("[Claude] interpretChatMessage model={} products={} input_tokens={} output_tokens={} "
                        + "cache_read_input_tokens={} cache_creation_input_tokens={} ms={}", model,
                request.products().size(), usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(),
                usage.path("cache_read_input_tokens").asInt(), usage.path("cache_creation_input_tokens").asInt(),
                System.currentTimeMillis() - start);

        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && CHAT_TOOL.equals(block.path("name").asText())) {
                return ChatPrompt.parse(block.path("input"));
            }
        }
        throw ChatPrompt.failed("no " + CHAT_TOOL + " block in the answer (stop_reason "
                + response.path("stop_reason").asText() + ")");
    }

    @Override
    public DocumentReading interpretChatDocument(ChatDocumentRequest request) {
        String vision = visionModel == null || visionModel.isBlank() ? model : visionModel;
        List<Map<String, Object>> content = new java.util.ArrayList<>();
        for (DocumentPage page : request.pages()) {
            Map<String, Object> source = Map.of("type", "base64", "media_type", page.mediaType(),
                    "data", java.util.Base64.getEncoder().encodeToString(page.bytes()));
            content.add(Map.of("type", "application/pdf".equals(page.mediaType()) ? "document" : "image", "source", source));
        }
        content.add(Map.of("type", "text", "text", DocumentPrompt.userText(request)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", vision);
        body.put("max_tokens", DocumentPrompt.MAX_OUTPUT_TOKENS);
        body.put("temperature", 0);
        body.put("system", List.of(Map.of("type", "text",
                "text", DocumentPrompt.systemPrompt(request, "Devolvé el resultado con la herramienta " + DOCUMENT_TOOL + "."),
                "cache_control", Map.of("type", "ephemeral"))));
        body.put("tools", List.of(Map.of(
                "name", DOCUMENT_TOOL,
                "description", "Devuelve lo impreso en la factura o el remito",
                "input_schema", DocumentPrompt.jsonSchema())));
        body.put("tool_choice", Map.of("type", "tool", "name", DOCUMENT_TOOL));
        body.put("messages", List.of(Map.of("role", "user", "content", content)));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Claude", BUSY,
                    () -> restClient.post().uri("/v1/messages").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Claude] interpretChatDocument failed: {}", e.getMessage());
            throw DocumentPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usage");
        log.info("[Claude] interpretChatDocument model={} pages={} input_tokens={} output_tokens={} "
                        + "cache_read_input_tokens={} ms={}", vision, request.pages().size(),
                usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(),
                usage.path("cache_read_input_tokens").asInt(), System.currentTimeMillis() - start);
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && DOCUMENT_TOOL.equals(block.path("name").asText())) {
                return DocumentPrompt.parse(block.path("input"));
            }
        }
        throw DocumentPrompt.failed("no " + DOCUMENT_TOOL + " block in the answer (stop_reason "
                + response.path("stop_reason").asText() + ")");
    }

    @Override
    public CatalogSpreadsheetInterpretation interpretRecipeMessage(RecipeMessageRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", RecipeChatPrompt.MAX_OUTPUT_TOKENS);
        body.put("temperature", 0);
        body.put("system", List.of(Map.of("type", "text",
                "text", RecipeChatPrompt.systemPrompt(request, "Devolvé el resultado con la herramienta " + RECIPE_TOOL + "."),
                "cache_control", Map.of("type", "ephemeral"))));
        body.put("tools", List.of(Map.of(
                "name", RECIPE_TOOL,
                "description", "Devuelve la receta completa como queda con el cambio del mensaje",
                "input_schema", RecipeChatPrompt.jsonSchema())));
        body.put("tool_choice", Map.of("type", "tool", "name", RECIPE_TOOL));
        body.put("messages", List.of(Map.of("role", "user", "content", RecipeChatPrompt.userMessage(request))));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Claude", BUSY,
                    () -> restClient.post().uri("/v1/messages").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Claude] interpretRecipeMessage failed: {}", e.getMessage());
            throw RecipeChatPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usage");
        log.info("[Claude] interpretRecipeMessage model={} products={} input_tokens={} output_tokens={} "
                        + "cache_read_input_tokens={} cache_creation_input_tokens={} ms={}", model,
                request.products().size(), usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(),
                usage.path("cache_read_input_tokens").asInt(), usage.path("cache_creation_input_tokens").asInt(),
                System.currentTimeMillis() - start);

        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && RECIPE_TOOL.equals(block.path("name").asText())) {
                return RecipeChatPrompt.parse(block.path("input"), request);
            }
        }
        throw RecipeChatPrompt.failed("no " + RECIPE_TOOL + " block in the answer (stop_reason "
                + response.path("stop_reason").asText() + ")");
    }

    @Override
    public CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(CatalogSpreadsheetRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", CatalogSpreadsheetPrompt.MAX_OUTPUT_TOKENS);
        body.put("temperature", 0);
        body.put("system", CatalogSpreadsheetPrompt.systemPrompt(request,
                "Devolvé el resultado con la herramienta " + CATALOG_TOOL + "."));
        body.put("tools", List.of(Map.of(
                "name", CATALOG_TOOL,
                "description", "Devuelve los insumos y las recetas leídos de la planilla",
                "input_schema", CatalogSpreadsheetPrompt.jsonSchema())));
        body.put("tool_choice", Map.of("type", "tool", "name", CATALOG_TOOL));
        body.put("messages", List.of(Map.of("role", "user", "content", CatalogSpreadsheetPrompt.rows(request))));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = ProviderRetry.once("Claude", BUSY,
                    () -> restClient.post().uri("/v1/messages").body(body).retrieve().body(JsonNode.class));
        } catch (RestClientException e) {
            log.error("[Claude] interpretCatalogSpreadsheet failed: {}", e.getMessage());
            throw CatalogSpreadsheetPrompt.failed(e.getMessage());
        }
        JsonNode usage = response.path("usage");
        log.info("[Claude] interpretCatalogSpreadsheet model={} rows={} input_tokens={} output_tokens={} "
                        + "cache_read_input_tokens={} ms={}", model, CatalogSpreadsheetPrompt.rowCount(request),
                usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(),
                usage.path("cache_read_input_tokens").asInt(), System.currentTimeMillis() - start);

        if ("max_tokens".equals(response.path("stop_reason").asText())) {
            throw CatalogSpreadsheetPrompt.tooLarge();
        }
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && CATALOG_TOOL.equals(block.path("name").asText())) {
                return CatalogSpreadsheetPrompt.parse(block.path("input"));
            }
        }
        throw CatalogSpreadsheetPrompt.failed("no " + CATALOG_TOOL + " block in the answer");
    }
}
