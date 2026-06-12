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
@ConditionalOnExpression("!'${app.google.api-key:}'.isEmpty() && '${app.anthropic.api-key:}'.isEmpty()")
public class GemmaAIService implements AIService {

    private static final String TOOL_NAME = "interpret_inventory";

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public GemmaAIService(
            @Value("${app.google.api-key}") String apiKey,
            @Value("${app.google.model:gemma-3-27b-it}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public AIInterpretation interpret(InterpretRequest request) {
        try {
            Map<String, Object> body = buildRequestBody(request);
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/" + model + ":generateContent")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("[Gemma] interpret failed: {}", e.getMessage());
            return new AIInterpretation("unknown", 0, false, null,
                    "No pude interpretar el mensaje. Intentá de nuevo.");
        }
    }

    private Map<String, Object> buildRequestBody(InterpretRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of(
                "parts", List.of(Map.of("text", buildSystemPrompt(request.catalog())))
        ));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.message()))
        )));
        body.put("tools", List.of(Map.of("function_declarations", List.of(buildFunctionDeclaration()))));
        body.put("tool_config", Map.of(
                "function_calling_config", Map.of(
                        "mode", "ANY",
                        "allowed_function_names", List.of(TOOL_NAME)
                )
        ));
        body.put("generationConfig", Map.of("maxOutputTokens", 1024));
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
                + "- Respondé en español rioplatense, de forma concisa y directa.\n\n"
                + "ESTRUCTURA DE DATA (obligatoria):\n"
                + "Siempre devolvé data.items[] con objetos. NUNCA pongas los campos directamente en el nivel raíz.\n"
                + "- purchase: {\"supplier_id\":\"uuid-o-null\",\"items\":[{\"product_id\":\"uuid\",\"quantity\":10,\"price_per_unit\":5000,\"expiration_date\":\"2026-12-31\"}]}\n"
                + "  → price_per_unit y product_id son OBLIGATORIOS para purchase.\n"
                + "  → expiration_date es opcional (formato YYYY-MM-DD). supplier_id es opcional.\n"
                + "- waste: {\"items\":[{\"product_id\":\"uuid\",\"quantity\":2,\"reason\":\"DAMAGED\"}]}\n"
                + "- sale:  {\"items\":[{\"product_id\":\"uuid\",\"quantity\":3}]}\n\n"
                + "REGLA DE PRECIOS (crítica):\n"
                + "- \"a [precio]\" con cantidad siempre es precio POR UNIDAD, no total.\n"
                + "  Ejemplos: \"5kg a 25mil\" → price_per_unit=25000 (total=125000).\n"
                + "            \"3 docenas a 1500\" → price_per_unit=1500 (total=4500).\n"
                + "- Solo interpretás como precio total si el usuario dice explícitamente\n"
                + "  \"en total\", \"todo\", \"la bolsa completa\", \"el lote\", o similar.\n"
                + "  Ejemplo: \"5kg, me salió 25mil en total\" → price_per_unit=5000.\n"
                + "- REGLA DE CONFIRMACIÓN POR PRECIO ALTO: si la cantidad es > 1 Y el\n"
                + "  precio_por_unidad implícito supera $100.000 ARS, SIEMPRE poné\n"
                + "  needs_confirmation=true y preguntá explícitamente si ese precio es\n"
                + "  por unidad o es el total de toda la compra. No importa si el precio\n"
                + "  parece razonable — la ambigüedad es lo que importa confirmar.";
    }

    private Map<String, Object> buildFunctionDeclaration() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("intent", Map.of(
                "type", "STRING",
                "enum", List.of("purchase", "waste", "sale", "stock_adjustment", "query", "multi", "unknown")));
        properties.put("confidence",         Map.of("type", "INTEGER", "minimum", 0, "maximum", 100));
        properties.put("needs_confirmation", Map.of("type", "BOOLEAN"));
        properties.put("response_to_user",   Map.of("type", "STRING"));
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("product_id",      Map.of("type", "STRING", "description", "UUID exacto del catálogo. null si no hay coincidencia."));
        itemProps.put("quantity",        Map.of("type", "NUMBER", "description", "Cantidad numérica."));
        itemProps.put("price_per_unit",  Map.of("type", "NUMBER", "description", "Precio por unidad en ARS. OBLIGATORIO para purchase. null para waste/sale."));
        itemProps.put("expiration_date", Map.of("type", "STRING", "description", "Fecha de vencimiento YYYY-MM-DD. Solo para purchase. Opcional."));
        itemProps.put("reason",          Map.of("type", "STRING", "enum", List.of("EXPIRED","DAMAGED","OVERPRODUCTION","THEFT","OTHER"),
                                                "description", "Motivo de merma. Solo para waste."));
        Map<String, Object> itemSchemaG = new LinkedHashMap<>();
        itemSchemaG.put("type", "OBJECT");
        itemSchemaG.put("properties", itemProps);

        Map<String, Object> dataProps = new LinkedHashMap<>();
        dataProps.put("items",       Map.of("type", "ARRAY", "description", "Array de productos. Siempre presente.", "items", itemSchemaG));
        dataProps.put("supplier_id", Map.of("type", "STRING", "description", "UUID del proveedor. Solo para purchase. Opcional."));
        Map<String, Object> dataSchemaG = new LinkedHashMap<>();
        dataSchemaG.put("type", "OBJECT");
        dataSchemaG.put("description", "Datos estructurados. Siempre usá items[] — nunca pongas los campos sueltos en el nivel raíz.");
        dataSchemaG.put("properties", dataProps);
        properties.put("data", dataSchemaG);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);
        parameters.put("required", List.of("intent", "confidence", "needs_confirmation", "response_to_user"));

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", TOOL_NAME);
        fn.put("description", "Interpreta un mensaje del equipo de cocina y extrae la operación de inventario");
        fn.put("parameters", parameters);
        return fn;
    }

    private AIInterpretation parseResponse(JsonNode response) {
        try {
            JsonNode parts = response.path("candidates").get(0)
                    .path("content").path("parts");
            for (JsonNode part : parts) {
                JsonNode functionCall = part.path("functionCall");
                if (!functionCall.isMissingNode()
                        && TOOL_NAME.equals(functionCall.path("name").asText())) {
                    JsonNode args         = functionCall.path("args");
                    String intent         = args.path("intent").asText("unknown");
                    int confidence        = args.path("confidence").asInt(0);
                    boolean needsConfirm  = args.path("needs_confirmation").asBoolean(true);
                    String responseToUser = args.path("response_to_user").asText("");
                    Object data           = args.has("data")
                            ? objectMapper.convertValue(args.get("data"), Object.class)
                            : null;
                    log.debug("[Gemma] intent={} confidence={}", intent, confidence);
                    return new AIInterpretation(intent, confidence, needsConfirm, data, responseToUser);
                }
            }
            log.warn("[Gemma] No functionCall block in response");
            return new AIInterpretation("unknown", 0, true, null, "No pude interpretar el mensaje.");
        } catch (Exception e) {
            log.warn("[Gemma] Failed to parse response: {}", e.getMessage());
            return new AIInterpretation("unknown", 0, true, null, "No pude interpretar el mensaje.");
        }
    }
}
