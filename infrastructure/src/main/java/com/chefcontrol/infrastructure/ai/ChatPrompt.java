package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.ChatInterpretation;
import com.chefcontrol.application.port.AIService.ChatHistoryTurn;
import com.chefcontrol.application.port.AIService.ChatMessageRequest;
import com.chefcontrol.application.port.AIService.HistoryAction;
import com.chefcontrol.application.port.AIService.InterpretedItem;
import com.chefcontrol.application.port.AIService.InterpretedOperation;
import com.chefcontrol.application.port.AIService.InterpretedQuery;
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

            ENTRADA: si hay, la CONVERSACIÓN DE HOY (lo anterior, con lo registrado numerado: "[acción 2] …"); \
            después "HOY: <fecha>" y el mensaje nuevo.

            SALIDA, con esta forma:
            {"confidence":95,"needsConfirmation":false,"responseToUser":"...",
             "operations":[{"type":"purchase","text":"compramos 10 kg de harina a 2000","items":[{"productId":"<id>","name":"harina","quantity":10,"unit":"kg","price":2000,"priceBasis":"unit","priceUnit":"kg","expirationDate":"2026-12-31"}]},
                           {"type":"waste","items":[{"productId":"<id>","name":"tomate","quantity":2,"unit":"kg","reason":"EXPIRED"}]},
                           {"type":"sale","items":[{"menuItemId":"<id>","name":"milanesa","quantity":5}]},
                           {"type":"stock_adjustment","items":[{"productId":"<id>","name":"harina","quantity":8,"unit":"kg"}]},
                           {"type":"production","items":[{"productId":"<id>","name":"mayonesa","quantity":5,"unit":"kg","expirationDate":"2026-12-01"}]},
                           {"type":"recipe","text":"la napolitana lleva 200 g de jamón","items":[{"menuItemId":"<id>","name":"napolitana"}]},
                           {"type":"query","text":"¿cuánto vendimos ayer?","query":"sales","period":"yesterday","items":[]}]}

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
            - query: una pregunta ("¿cuánta harina hay?", "¿cuánto vendimos ayer?"): ver la regla 13.
            Un mensaje puede pedir varias cosas: una operación por tipo, con todos sus items y en text el pedazo del \
            mensaje que la pide, copiado tal cual. Si no pide nada de esto, \
            operations va vacío.

            REGLAS:
            1. productId: el id exacto de CATÁLOGO DE INSUMOS si el insumo es claramente ese (plural o singular, \
            acentos, mayúsculas: "tomates" es "Tomate"). En una venta va menuItemId, el id exacto de PLATOS, y no \
            productId. Si no está, o dudás entre dos, null: nunca inventes un id ni elijas uno parecido ("Tomate" no es \
            "Tomate perita"). Aunque no esté, devolvé el ítem con el id en null: el sistema le avisa al usuario qué no \
            encontró. name: el insumo o el plato como lo dijo el usuario.
            2. quantity y unit: tal como los dijo el usuario, SIN convertir: "500 g" es quantity 500 y unit "g" aunque \
            el insumo vaya en kg; el sistema convierte. Cada ítem lleva la unidad que dijo para ese ítem, nunca la de \
            otro ni la del catálogo ("200 g de acelga y 100 g de harina" → harina 100 y g). unit es una abreviatura de la lista de UNIDADES ("kilos" → kg, \
            "gramos" → g, "litros" → l, "unidades" → u). Si no dijo unidad, null. Números escritos a número ("tres" → \
            3, "media docena" → 6 y unit null, "medio kilo" → 0.5 y kg).
            3. price: el precio tal como lo dijo, en pesos y como número (sin "$" ni separador de miles: "25 mil" → \
            25000). Nunca hagas cuentas con el precio, ni aunque te parezca caro o barato: no lo dividas ni lo \
            multipliques; la cuenta la hace el sistema. priceBasis, a qué se refiere ese número: "unit" si es por \
            unidad ("a [precio]" con una cantidad es por unidad: "5 kg a 25 mil", "a 3000 el kilo", "a 500 cada uno"); \
            "total" solo si lo dice ("en total", "todo", "me salió", "el lote", "pagué 25 mil por los 5 kg"); "unclear" \
            si no se puede saber ("5 kg de harina, 25 mil"). priceUnit: la unidad de un precio por unidad si la dice \
            ("500 g a 3000 el kilo" → kg); si no, null. Sin precio, price y priceBasis en null.
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
            dijo, sin el artículo ("la napolitana" → "napolitana"). Sin cantidades: los ingredientes los lee otro paso, \
            con text: el pedazo del mensaje que habla de esa receta, copiado tal cual y sin lo demás ("compré tomate y \
            la napolitana lleva 200 g de jamón" → "la napolitana lleva 200 g de jamón"). Un insumo del catálogo \
            que pasa a tener receta de tanda es preparation_recipe.
            10. confidence: de 0 a 100, cuán seguro estás de haber entendido todo. needsConfirmation = true SOLO si \
            confidence < 80 o si dudás entre dos insumos o platos. Por el precio no: si puede ser el total, el sistema \
            pregunta. Fuera de eso no preguntes ni objetes: devolvé la operación como la pidió el usuario aunque te \
            parezca rara ("hice 3 kilos de harina" es una production de la harina). El sistema valida y le explica al \
            usuario lo que no se puede.
            13. CONSULTAS (type query), una operación por pregunta, con query, uno de: stock (cuánto hay de un insumo: el \
            insumo en items, con productId), expirations (qué vence), sales (cuánto se vendió), purchases (cuánto se \
            compró), waste (cuánta merma hubo), last_purchase_price (a cuánto se compró por última vez un insumo: el \
            insumo en items), food_cost (de un plato: el plato en items, con menuItemId; o del período, sin items) u \
            open_alerts (alertas abiertas). Si pregunta otra cosa, query null. period, si dice uno: today, yesterday, \
            this_week, last_week, this_month, last_month, last_n_days (con days: "los últimos 10 días" → 10) o range \
            (solo si dice fechas: "del 1 al 15 de noviembre" → from y to, YYYY-MM-DD). NUNCA calcules fechas: "ayer" es \
            yesterday, "la semana pasada" es last_week; el sistema cuenta los días. Sin período dicho, period null. No \
            respondas la consulta ni des números: los pone el sistema. Con la CONVERSACIÓN, "¿y ayer?" después de \
            "¿cuánto vendimos hoy?" es la misma consulta con yesterday.
            12. CONVERSACIÓN: es para entender a qué se refiere el mensaje nuevo ("eran 5", "sí", "el total", "el \
            de recién"). Devolvé solo lo que pide el mensaje nuevo: nunca repitas una operación ya registrada. Si \
            corrige algo registrado ("no, eran 5 kg", "me equivoqué, era tomate"), devolvé la operación corregida \
            completa, con todos sus datos (los que no cambian, como estaban), y en corrects el número de esa acción. Si \
            contesta una pregunta tuya ("sí", "es el total", "por kilo"), devolvé la operación de la pregunta como \
            queda con la respuesta, sin corrects.
            11. responseToUser, en castellano rioplatense, corto y sin muletillas (nada de "che", "dale", "genial"): \
            si needsConfirmation, la pregunta concreta (cuál de dos insumos). \
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
        StringBuilder sb = new StringBuilder();
        if (!r.history().isEmpty()) {
            sb.append("CONVERSACIÓN DE HOY:\n");
            for (ChatHistoryTurn t : r.history()) {
                if (t.userMessage() != null) sb.append("Usuario: ").append(t.userMessage()).append('\n');
                sb.append("Respondí: ").append(t.reply()).append('\n');
                for (HistoryAction a : t.actions()) {
                    sb.append("  [acción ").append(a.number()).append("] ").append(a.summary())
                            .append(a.undone() ? " (deshecha)" : "").append('\n');
                }
            }
            sb.append('\n');
        }
        return sb.append("HOY: ").append(r.today()).append('\n').append(r.message()).toString();
    }

    /** Para Claude: la forma de la salida como input_schema de la herramienta. */
    static Map<String, Object> jsonSchema() {
        Map<String, Object> item = Map.of("type", "object", "properties", Map.of(
                "productId", type("string"), "menuItemId", type("string"), "name", type("string"),
                "quantity", type("number"),
                "unit", type("string"), "price", type("number"),
                "priceBasis", Map.of("type", "string", "enum", List.of("unit", "total", "unclear")),
                "priceUnit", type("string"),
                "expirationDate", type("string"),
                "reason", Map.of("type", "string", "enum", List.of("EXPIRED", "DAMAGED", "OVERPRODUCTION", "THEFT", "OTHER"))),
                "required", List.of("name"));
        Map<String, Object> operation = Map.of("type", "object", "properties", Map.of(
                "type", Map.of("type", "string",
                        "enum", List.of("purchase", "waste", "sale", "stock_adjustment", "production", "recipe",
                                "preparation_recipe", "query")),
                "items", Map.of("type", "array", "items", item), "text", type("string"),
                "corrects", type("integer"),
                "query", Map.of("type", "string", "enum", List.of("stock", "expirations", "sales", "purchases", "waste",
                        "last_purchase_price", "food_cost", "open_alerts")),
                "period", Map.of("type", "string", "enum", List.of("today", "yesterday", "this_week", "last_week",
                        "this_month", "last_month", "last_n_days", "range")),
                "days", type("integer"), "from", type("string"), "to", type("string")),
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
                        number(i, "quantity"), text(i, "unit"), number(i, "price"), text(i, "priceBasis"), text(i, "priceUnit"),
                        date(text(i, "expirationDate")), text(i, "reason")));
            }
            JsonNode corrects = op.get("corrects");
            JsonNode days = op.get("days");
            InterpretedQuery query = text(op, "query") == null ? null : new InterpretedQuery(text(op, "query"),
                    text(op, "period"), days == null || days.isNull() || days.asInt(0) <= 0 ? null : days.asInt(),
                    date(text(op, "from")), date(text(op, "to")));
            operations.add(new InterpretedOperation(text(op, "type"), items, text(op, "text"),
                    corrects == null || corrects.isNull() || corrects.asInt(0) <= 0 ? null : corrects.asInt(), query));
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
