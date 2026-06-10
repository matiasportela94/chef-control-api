package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.port.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnExpression("!'${app.anthropic.api-key:}'.isEmpty()")
public class ClaudeAIService implements AIService {

    private static final String TOOL_NAME = "interpret_inventory";

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public ClaudeAIService(
            @Value("${app.anthropic.api-key}") String apiKey,
            @Value("${app.anthropic.model:claude-haiku-4-5-20251001}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public AIInterpretation interpret(InterpretRequest request) {
        try {
            Map<String, Object> body = buildRequestBody(request);
            JsonNode response = restClient.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("[Claude] interpret failed: {}", e.getMessage());
            return new AIInterpretation("unknown", 0, false, null,
                    "No pude interpretar el mensaje. Intentá de nuevo.");
        }
    }

    private Map<String, Object> buildRequestBody(InterpretRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", buildSystemPrompt(request.catalog()));
        body.put("tools", List.of(buildTool()));
        body.put("tool_choice", Map.of("type", "tool", "name", TOOL_NAME));
        body.put("messages", List.of(Map.of("role", "user", "content", request.message())));
        return body;
    }

    private String buildSystemPrompt(String catalog) {
        String catalogSection = (catalog == null || catalog.isBlank())
                ? "(sin productos registrados)"
                : catalog;
        return "Sos el asistente operativo de un restaurante argentino. Interpretás mensajes del equipo "
                + "y los convertís en operaciones de inventario estructuradas.\n\n"
                + "CATÁLOGO DE PRODUCTOS (ID | Nombre | SKU | Unidad):\n"
                + catalogSection + "\n\n"
                + "INTENCIONES:\n"
                + "- purchase: compra de insumos (\"compramos 10kg harina a $2000\")\n"
                + "- waste: merma o descarte (\"se rompieron 3 botellas\", \"tiramos carne vencida\")\n"
                + "- sale: venta de platos (\"vendimos 5 milanesas\")\n"
                + "- stock_adjustment: corrección de stock (\"hay 8kg de harina\")\n"
                + "- query: consulta de datos o stock\n"
                + "- multi: el mensaje contiene múltiples operaciones distintas\n"
                + "- unknown: no se puede determinar\n\n"
                + "REGLAS:\n"
                + "- Usá los IDs exactos del catálogo si el producto coincide. Si no, product_id = null.\n"
                + "- Convertí cantidades textuales a números (\"tres\" → 3, \"media docena\" → 6).\n"
                + "- Si no se menciona precio, dejá null.\n"
                + "- needs_confirmation = true si confidence < 80 o hay ambigüedad.\n"
                + "- Motivos de merma: EXPIRED, DAMAGED, OVERPRODUCTION, THEFT, OTHER.\n"
                + "- Respondé en español rioplatense, de forma concisa y directa.";
    }

    private Map<String, Object> buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("intent", Map.of("type", "string",
                "enum", List.of("purchase", "waste", "sale", "stock_adjustment", "query", "multi", "unknown")));
        properties.put("confidence",         Map.of("type", "integer", "minimum", 0, "maximum", 100));
        properties.put("needs_confirmation", Map.of("type", "boolean"));
        properties.put("response_to_user",   Map.of("type", "string"));
        properties.put("data",               Map.of("type", "object",
                "description", "Datos estructurados según el intent. Estructura varía: items[] para purchase/waste/sale, producto+cantidad para stock_adjustment."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("intent", "confidence", "needs_confirmation", "response_to_user"));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", TOOL_NAME);
        tool.put("description", "Interpreta un mensaje del equipo de cocina y extrae la operación de inventario");
        tool.put("input_schema", schema);
        return tool;
    }

    private AIInterpretation parseResponse(JsonNode response) {
        JsonNode content = response.path("content");
        for (JsonNode block : content) {
            if ("tool_use".equals(block.path("type").asText())
                    && TOOL_NAME.equals(block.path("name").asText())) {
                JsonNode input         = block.path("input");
                String intent          = input.path("intent").asText("unknown");
                int confidence         = input.path("confidence").asInt(0);
                boolean needsConfirm   = input.path("needs_confirmation").asBoolean(true);
                String responseToUser  = input.path("response_to_user").asText("");
                Object data            = input.has("data")
                        ? objectMapper.convertValue(input.get("data"), Object.class)
                        : null;
                log.debug("[Claude] intent={} confidence={}", intent, confidence);
                return new AIInterpretation(intent, confidence, needsConfirm, data, responseToUser);
            }
        }
        log.warn("[Claude] No tool_use block in response");
        return new AIInterpretation("unknown", 0, true, null, "No pude interpretar el mensaje.");
    }
}
