package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.ChatInterpretation;
import com.chefcontrol.application.port.AIService.ChatMessageRequest;
import com.chefcontrol.application.port.AIService.InterpretedItem;
import com.chefcontrol.application.port.AIService.InterpretedOperation;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El prompt y la lectura de la respuesta del chat de la IA, iguales para Claude y Gemini: cada adaptador pone el
 * transporte. El system prompt queda igual entre mensajes mientras no cambie el catálogo (la fecha va en el mensaje):
 * es lo que Claude cachea.
 */
final class ChatPrompt {

    static final int MAX_OUTPUT_TOKENS = 2048;

    private static final String RULES = """
            Sos el asistente operativo de un restaurante argentino. Leés un mensaje del equipo (escrito o dictado) y \
            devolvés las operaciones de stock que pide. No registrás nada: el sistema valida lo que devuelvas y lo \
            registra.

            ENTRADA: "HOY: <fecha>" y después el mensaje.

            SALIDA, con esta forma:
            {"confidence":95,"needsConfirmation":false,"responseToUser":"...",
             "operations":[{"type":"purchase","items":[{"productId":"<id>","name":"harina","quantity":10,"unit":"kg","pricePerUnit":2000,"priceUnit":"kg","expirationDate":"2026-12-31"}]},
                           {"type":"waste","items":[{"productId":"<id>","name":"tomate","quantity":2,"unit":"kg","reason":"EXPIRED"}]},
                           {"type":"sale","items":[{"menuItemId":"<id>","name":"milanesa","quantity":5}]},
                           {"type":"stock_adjustment","items":[{"productId":"<id>","name":"harina","quantity":8,"unit":"kg"}]},
                           {"type":"production","items":[{"productId":"<id>","name":"mayonesa","quantity":5,"unit":"kg","expirationDate":"2026-12-01"}]},
                           {"type":"recipe","items":[{"menuItemId":"<id>","name":"milanesa napolitana"}]}]}

            TIPOS DE OPERACIÓN (type):
            - purchase: compra de insumos ("compramos 10 kg de harina a $2000").
            - waste: merma o descarte ("se rompieron 3 botellas", "tiramos carne vencida").
            - sale: venta de platos ("vendimos 5 milanesas").
            - stock_adjustment: cuánto hay ahora de un insumo, contado ("quedan 8 kg de harina").
            - production: una preparación hecha en el restaurante ("hice 5 kg de mayonesa").
            - recipe: crear o cambiar la receta de un plato ("la napolitana lleva 200 g de jamón", "sacale la crema \
            al flan").
            - preparation_recipe: crear o cambiar la receta de una preparación hecha en tandas ("la mayonesa base rinde \
            1 kg y lleva 6 huevos y 1 l de aceite").
            - query: una pregunta o consulta ("¿cuánta harina hay?"), sin items.
            Un mensaje puede pedir varias cosas: una operación por tipo, con todos sus items. Si no pide nada de esto, \
            operations va vacío.

            REGLAS:
            1. productId: el id exacto de CATÁLOGO DE INSUMOS si el insumo es claramente ese (plural o singular, \
            acentos, mayúsculas: "tomates" es "Tomate"). En una venta va menuItemId, el id exacto de PLATOS, y no \
            productId. Si no está, o dudás entre dos, null: nunca inventes un id ni elijas uno parecido ("Tomate" no es \
            "Tomate perita"). Aunque no esté, devolvé el ítem con el id en null: el sistema le avisa al usuario qué no \
            encontró. name: el insumo o el plato como lo dijo el usuario.
            2. quantity y unit: tal como los dijo el usuario, SIN convertir: "500 g" es quantity 500 y unit "g" aunque \
            el insumo vaya en kg; el sistema convierte. unit es una abreviatura de la lista de UNIDADES ("kilos" → kg, \
            "gramos" → g, "litros" → l, "unidades" → u). Si no dijo unidad, null. Números escritos a número ("tres" → \
            3, "media docena" → 6 y unit null, "medio kilo" → 0.5 y kg).
            3. pricePerUnit, en pesos y como número (sin "$" ni separador de miles: "25 mil" → 25000). "a [precio]" \
            con una cantidad es SIEMPRE por unidad: "5 kg a 25 mil" → 25000. Es el total solo si lo dice ("en total", \
            "todo", "me salió", "el lote"), y ahí dividilo por la cantidad: "5 kg, 25 mil en total" → 5000. Si no lo \
            dice, nunca lo dividas por tu cuenta, aunque te parezca caro: "5 kg de comino a 150 mil" → 150000 y \
            needsConfirmation = true (regla 10); el sistema le pregunta al usuario. \
            priceUnit: la unidad a la que se refiere el precio si la dice ("500 g a 3000 el kilo" → kg); si no, null. \
            Sin precio, null.
            4. expirationDate: solo en compras y producciones, y si lo dice, como YYYY-MM-DD, contando desde HOY.
            5. reason: solo en mermas. EXPIRED (vencido), DAMAGED (roto, quemado, en mal estado), OVERPRODUCTION \
            (sobró), THEFT (robo) u OTHER.
            6. En una venta, quantity son las porciones vendidas, sin unit y sin precio: el precio es el del plato.
            7. En un ajuste, quantity es lo que HAY AHORA, contado, no lo que se usó ni la diferencia: "quedan 8 kg de \
            harina" → 8 y kg; "no queda más aceite" → 0. Lo que se usó o se gastó no es un ajuste.
            8. En una producción, productId es una preparación: un insumo marcado "preparación" en el catálogo. \
            quantity y unit, lo que salió de la tanda, como lo dijo el usuario ("hice 5 kilos de mayonesa" → 5 y kg).
            9. En una receta, un solo ítem: el plato (menuItemId, de PLATOS) o la preparación (productId, de \
            CATÁLOGO DE INSUMOS) cuya receta se crea o cambia, y name; si es nueva, el id en null y name tal cual lo \
            dijo, sin el artículo ("la napolitana" → "napolitana"). Sin cantidades: los ingredientes los lee otro paso. Un insumo del catálogo \
            que pasa a tener receta de tanda es preparation_recipe.
            10. confidence: de 0 a 100, cuán seguro estás de haber entendido todo. needsConfirmation = true SOLO si \
            confidence < 80, si dudás entre dos insumos o platos, o si en una compra la cantidad es mayor a 1 y el \
            precio por unidad supera $100.000 (puede ser el total). Fuera de eso no preguntes ni objetes: devolvé la \
            operación como la pidió el usuario aunque te parezca rara ("hice 3 kilos de harina" es una production de \
            la harina). El sistema valida y le explica al usuario lo que no se puede.
            11. responseToUser, en castellano rioplatense, corto y sin muletillas (nada de "che", "dale", "genial"): \
            si needsConfirmation, la pregunta concreta (si el precio es por unidad o el total, cuál de dos insumos). \
            Si no, una frase corta y sin preguntas: el detalle de lo registrado o rechazado lo arma el sistema. Si el \
            mensaje no pide nada para registrar, decí en una frase qué podés registrar.
            """;

    private ChatPrompt() {}

    /** @param output cómo devolver el resultado: el JSON en el texto (Gemini) o la herramienta (Claude) */
    static String systemPrompt(ChatMessageRequest r, String output) {
        String products = r.products().isEmpty() ? "(sin insumos cargados)" : r.products().stream()
                .map(p -> p.id() + " | " + p.name() + " | " + (p.unit() == null ? "-" : p.unit())
                        + (p.preparation() ? " | preparación" : ""))
                .collect(Collectors.joining("\n"));
        String menuItems = r.menuItems().isEmpty() ? "(sin platos cargados)" : r.menuItems().stream()
                .map(m -> m.id() + " | " + m.name() + " | " + (m.price() == null ? "-" : m.price().toPlainString()))
                .collect(Collectors.joining("\n"));
        return RULES + output + "\n\n"
                + "UNIDADES (abreviatura: nombre): " + r.units().stream()
                        .map(u -> u.abbreviation() + ": " + u.name()).collect(Collectors.joining("; ")) + "\n\n"
                + "CATÁLOGO DE INSUMOS (id | nombre | unidad | \"preparación\" si tiene receta de tanda):\n" + products + "\n\n"
                + "PLATOS (id | nombre | precio):\n" + menuItems;
    }

    static String userMessage(ChatMessageRequest r) {
        return "HOY: " + r.today() + "\n" + r.message();
    }

    /** Para Claude: la forma de la salida como input_schema de la herramienta. */
    static Map<String, Object> jsonSchema() {
        Map<String, Object> item = Map.of("type", "object", "properties", Map.of(
                "productId", type("string"), "menuItemId", type("string"), "name", type("string"),
                "quantity", type("number"),
                "unit", type("string"), "pricePerUnit", type("number"), "priceUnit", type("string"),
                "expirationDate", type("string"),
                "reason", Map.of("type", "string", "enum", List.of("EXPIRED", "DAMAGED", "OVERPRODUCTION", "THEFT", "OTHER"))),
                "required", List.of("name"));
        Map<String, Object> operation = Map.of("type", "object", "properties", Map.of(
                "type", Map.of("type", "string",
                        "enum", List.of("purchase", "waste", "sale", "stock_adjustment", "production", "recipe",
                                "preparation_recipe", "query")),
                "items", Map.of("type", "array", "items", item)),
                "required", List.of("type", "items"));
        return Map.of("type", "object", "properties", Map.of(
                "confidence", Map.of("type", "integer", "minimum", 0, "maximum", 100),
                "needsConfirmation", type("boolean"),
                "responseToUser", type("string"),
                "operations", Map.of("type", "array", "items", operation)),
                "required", List.of("confidence", "needsConfirmation", "responseToUser", "operations"));
    }

    private static Map<String, Object> type(String t) {
        return Map.of("type", t);
    }

    static ChatInterpretation parse(JsonNode root) {
        List<InterpretedOperation> operations = new ArrayList<>();
        for (JsonNode op : root.path("operations")) {
            List<InterpretedItem> items = new ArrayList<>();
            for (JsonNode i : op.path("items")) {
                items.add(new InterpretedItem(uuid(text(i, "productId")), uuid(text(i, "menuItemId")), text(i, "name"),
                        number(i, "quantity"), text(i, "unit"), number(i, "pricePerUnit"), text(i, "priceUnit"),
                        date(text(i, "expirationDate")), text(i, "reason")));
            }
            operations.add(new InterpretedOperation(text(op, "type"), items));
        }
        return new ChatInterpretation(root.path("confidence").asInt(0),
                root.path("needsConfirmation").asBoolean(true), text(root, "responseToUser"), operations);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    private static BigDecimal number(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.decimalValue();
        try {
            return new BigDecimal(v.asText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Un id que no es un UUID es uno inventado: se trata como "no está en el catálogo". */
    private static UUID uuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate date(String s) {
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    static AppException failed(String why) {
        return new AppException(ErrorCode.AI_INTERPRETATION_FAILED, "Could not interpret the chat message: " + why);
    }
}
