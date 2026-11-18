package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.ChatDocumentRequest;
import com.chefcontrol.application.port.AIService.InvoiceLineReading;
import com.chefcontrol.application.port.AIService.DocumentKind;
import com.chefcontrol.application.port.AIService.DocumentReading;
import com.chefcontrol.application.port.AIService.InvoiceReading;
import com.chefcontrol.application.port.AIService.SalesReportLineReading;
import com.chefcontrol.application.port.AIService.SalesReportReading;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chefcontrol.infrastructure.ai.CatalogSpreadsheetPrompt.number;
import static com.chefcontrol.infrastructure.ai.CatalogSpreadsheetPrompt.text;

/**
 * Leer un archivo del chat, igual para Claude y Gemini: una factura o remito, o un cierre de caja. El modelo dice qué es,
 * copia lo impreso y sugiere el insumo o el plato; las cuentas, los alias y lo que se registra los deciden
 * ChatInvoiceService y ChatSalesReportService.
 */
final class DocumentPrompt {

    /** Una factura de 40 renglones o un cierre de 60 platos con nombres largos: unos 60 tokens por renglón. */
    static final int MAX_OUTPUT_TOKENS = 4096;

    private static final String RULES = """
            Leés un archivo que mandó alguien de un restaurante argentino (fotos o un PDF; varias fotos son las hojas de \
            un mismo comprobante), decís qué es y devolvés lo que tiene impreso. No registrás nada: una persona revisa \
            todo antes de cargarlo.

            SALIDA, con esta forma (solo los campos del tipo que corresponda):
            {"kind":"invoice","supplierName":"Verdulería Don José","supplierCuit":"30-71234567-1","invoiceType":"A",
             "invoiceNumber":"0001-00001234","date":"2026-11-15","printedTotal":12100,
             "lines":[{"printedText":"TOMATE PERITA X KG","quantity":5,"unit":"kg","unitPrice":2000,"lineTotal":10000,
                       "ivaPercentage":21,"catalogName":"Tomate perita","suggestedName":"Tomate perita"}]}
            {"kind":"recipe","dishes":[{"menuItem":"Ñoquis de papa","servings":4,"ingredients":[\
            {"ingredient":"papas","quantity":1,"unit":"kg","catalogName":"Papa"}]}],\
             "preparations":[{"preparation":"Salsa fileto","yieldQuantity":1.5,"yieldUnit":"l","ingredients":[...]}],\
             "proposedProducts":[{"name":"nuez moscada","unit":"g"}],"warnings":["..."]}
            {"kind":"sales_report","reportNumber":"0012","date":"2026-11-18","time":"23:45","printedTotal":356000,
             "salesLines":[{"printedText":"MILANESA NAPO","quantity":12,"unitPrice":9500,"lineTotal":114000,
                            "catalogName":"Milanesa napolitana"}]}

            REGLAS:
            1. kind: "invoice" si es una factura, un ticket de compra o un remito de un PROVEEDOR (le vende al \
            restaurante); "sales_report" si es un cierre de caja del propio restaurante (ticket Z, cierre de turno, \
            reporte de ventas por producto del sistema de caja); "recipe" si es una o varias recetas (una ficha, una \
            hoja de un cuaderno de recetas); "other" si no es ninguno: ahí lo demás vacío.
            2. Copiá los números tal como están impresos, SIN hacer cuentas: ni sumar, ni dividir, ni calcular IVA. \
            Como números, con punto decimal ("1.500,50" → 1500.5). date como YYYY-MM-DD; time como HH:MM, o null si no \
            está impresa.
            FACTURA O REMITO:
            3. supplierName y supplierCuit: los del EMISOR (el proveedor que vende), nunca los del cliente. El CUIT tal \
            cual, con o sin guiones; null si no se ve.
            4. invoiceType: la letra del comprobante (A, B o C), o REMITO si es un remito; null si no se ve. \
            invoiceNumber: el número completo, con el punto de venta. date: la fecha de emisión.
            5. lines: un renglón por producto, en orden. Sin subtotales, IVA, percepciones, descuentos generales ni \
            totales. printedText: la descripción exactamente como está impresa. quantity, unitPrice y lineTotal: los \
            de ese renglón. unit: una abreviatura de UNIDADES si el renglón la dice ("KG" → kg, "UN" → u, "BOLSA" → bol \
            si está en la lista); si no la dice, null. ivaPercentage: la alícuota del renglón si está impresa (factura \
            A); si no, null.
            6. catalogName: el nombre EXACTO de un insumo del CATÁLOGO DE INSUMOS si el renglón es claramente ese \
            insumo ("TOMATE PERITA X KG" → "Tomate perita"); si dudás, null. suggestedName: un nombre corto y limpio \
            para el insumo, con mayúscula inicial y sin marcas ni presentaciones ("Harina 000", no "HARINA 000 MOLINOS \
            X 25 KG").
            7. printedTotal: el total final impreso del comprobante.
            CIERRE DE CAJA:
            8. reportNumber: el número del Z o del cierre; null si no tiene. date y time: los del cierre, como están \
            impresos (no pases un cierre de la madrugada al día anterior: eso lo decide el sistema).
            9. salesLines: un renglón por producto vendido, en orden: printedText exactamente como está impreso, \
            quantity, unitPrice y lineTotal de ese renglón (null si no están). Sin formas de pago, propinas, \
            descuentos, cubiertos ni totales. catalogName: el nombre EXACTO de un plato del MENÚ si el renglón es \
            claramente ese plato ("MILANESA NAPO" → "Milanesa napolitana"); si dudás o no es un plato, null.
            10. printedTotal: el total de ventas impreso.
            RECETA:
            11. Una entrada en dishes o preparations por receta, con el nombre como está escrito. preparations: lo que \
            el restaurante hace en tandas y usa como ingrediente (salsas, masas, fondos, bases) o que dice cuánto rinde \
            en cantidad ("rinde 1,5 l"), con yieldQuantity y yieldUnit. dishes: lo que se vende; servings: para \
            cuántas porciones son las cantidades, solo si lo dice. price y section solo si están escritos.
            12. Cada ingrediente con su cantidad y su unidad tal como están, SIN hacer cuentas: ni dividir por \
            porciones ni pasar de una unidad a otra (fracciones a número: "½ kg" → 0.5 y kg). unit es una abreviatura \
            de UNIDADES; si la unidad no está en la lista (taza, cucharada), no la conviertas: dejá afuera el \
            ingrediente y avisalo. Un ingrediente sin cantidad ("sal c/n", "a gusto") queda afuera, con su aviso.
            13. catalogName: el nombre EXACTO de un insumo del CATÁLOGO DE INSUMOS si el ingrediente es claramente ese \
            ("papas" → "Papa"); si dudás, null. Un ingrediente que no está en el catálogo y no es una preparación de \
            la foto va además una vez a proposedProducts, con su nombre y la unidad típica de compra (g → kg, ml → l, \
            u → u).
            14. warnings: cortos, en castellano rioplatense, lo que no pudiste leer o dejaste afuera.
            """;

    private DocumentPrompt() {}

    /** @param output cómo devolver el resultado: el JSON en el texto (Gemini) o la herramienta (Claude) */
    static String systemPrompt(ChatDocumentRequest r, String output) {
        String products = r.products().isEmpty() ? "(sin insumos cargados)" : r.products().stream()
                .map(p -> p.name() + " | " + (p.unit() == null ? "-" : p.unit())).collect(Collectors.joining("\n"));
        String menu = r.menuItems().isEmpty() ? "(sin platos cargados)"
                : r.menuItems().stream().map(m -> m.name()).collect(Collectors.joining("\n"));
        return RULES + output + "\n\n"
                + "UNIDADES (abreviatura: nombre): " + r.units().stream()
                        .map(u -> u.abbreviation() + ": " + u.name()).collect(Collectors.joining("; ")) + "\n\n"
                + "CATÁLOGO DE INSUMOS (nombre | unidad):\n" + products + "\n\n"
                + "MENÚ (platos):\n" + menu;
    }

    static String userText(ChatDocumentRequest r) {
        return r.message() == null || r.message().isBlank() ? "Leé el archivo adjunto."
                : "Leé el archivo adjunto. El usuario escribió: " + r.message();
    }

    /** Para Claude: la forma de la salida como input_schema de la herramienta. */
    static Map<String, Object> jsonSchema() {
        Map<String, Object> line = Map.of("type", "object", "properties", Map.of(
                "printedText", type("string"), "quantity", type("number"), "unit", type("string"),
                "unitPrice", type("number"), "lineTotal", type("number"), "ivaPercentage", type("number"),
                "catalogName", type("string"), "suggestedName", type("string")), "required", List.of("printedText"));
        Map<String, Object> salesLine = Map.of("type", "object", "properties", Map.of(
                "printedText", type("string"), "quantity", type("number"), "unitPrice", type("number"),
                "lineTotal", type("number"), "catalogName", type("string")), "required", List.of("printedText"));
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("kind", Map.of("type", "string", "enum", List.of("invoice", "sales_report", "recipe", "other")));
        properties.put("supplierName", type("string"));
        properties.put("supplierCuit", type("string"));
        properties.put("invoiceType", Map.of("type", "string", "enum", List.of("A", "B", "C", "REMITO")));
        properties.put("invoiceNumber", type("string"));
        properties.put("reportNumber", type("string"));
        properties.put("date", type("string"));
        properties.put("time", type("string"));
        properties.put("printedTotal", type("number"));
        properties.put("lines", Map.of("type", "array", "items", line));
        properties.put("salesLines", Map.of("type", "array", "items", salesLine));
        Map<String, Object> ingredient = Map.of("type", "object", "properties", Map.of("ingredient", type("string"),
                "quantity", type("number"), "unit", type("string"), "catalogName", type("string")));
        properties.put("dishes", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                "menuItem", type("string"), "servings", type("number"), "price", type("number"), "section", type("string"),
                "ingredients", Map.of("type", "array", "items", ingredient)))));
        properties.put("preparations", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                "preparation", type("string"), "yieldQuantity", type("number"), "yieldUnit", type("string"),
                "ingredients", Map.of("type", "array", "items", ingredient)))));
        properties.put("proposedProducts", Map.of("type", "array", "items", Map.of("type", "object", "properties",
                Map.of("name", type("string"), "unit", type("string")))));
        properties.put("warnings", Map.of("type", "array", "items", type("string")));
        return Map.of("type", "object", "properties", properties, "required", List.of("kind"));
    }

    private static Map<String, Object> type(String t) {
        return Map.of("type", t);
    }

    static DocumentReading parse(JsonNode root) {
        return switch (root.path("kind").asText("other")) {
            case "invoice" -> new DocumentReading(DocumentKind.INVOICE, invoice(root), null);
            case "sales_report" -> new DocumentReading(DocumentKind.SALES_REPORT, null, salesReport(root));
            case "recipe" -> new DocumentReading(DocumentKind.RECIPE, null, null, recipes(root));
            default -> new DocumentReading(DocumentKind.OTHER, null, null);
        };
    }

    private static InvoiceReading invoice(JsonNode root) {
        List<InvoiceLineReading> lines = new ArrayList<>();
        for (JsonNode l : root.path("lines")) {
            lines.add(new InvoiceLineReading(text(l, "printedText"), number(l, "quantity"), text(l, "unit"),
                    number(l, "unitPrice"), number(l, "lineTotal"), number(l, "ivaPercentage"), text(l, "catalogName"),
                    text(l, "suggestedName")));
        }
        return new InvoiceReading(text(root, "supplierName"), text(root, "supplierCuit"), text(root, "invoiceType"),
                text(root, "invoiceNumber"), date(text(root, "date")), number(root, "printedTotal"), lines);
    }

    private static SalesReportReading salesReport(JsonNode root) {
        List<SalesReportLineReading> lines = new ArrayList<>();
        for (JsonNode l : root.path("salesLines")) {
            lines.add(new SalesReportLineReading(text(l, "printedText"), number(l, "quantity"), number(l, "unitPrice"),
                    number(l, "lineTotal"), text(l, "catalogName")));
        }
        return new SalesReportReading(text(root, "reportNumber"), date(text(root, "date")), time(text(root, "time")),
                number(root, "printedTotal"), lines);
    }

    /**
     * Las recetas, con la forma del Excel con IA: {@code row} = la posición del ingrediente en la foto (el modelo no la
     * numera), y un insumo propuesto, en la fila del primer ingrediente que lo usa.
     */
    private static com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation recipes(JsonNode root) {
        int row = 0;
        java.util.Map<String, Integer> rowOf = new java.util.HashMap<>();
        for (String list : List.of("dishes", "preparations")) {
            for (JsonNode recipe : root.path(list)) {
                for (JsonNode i : recipe.path("ingredients")) {
                    if (!(i instanceof com.fasterxml.jackson.databind.node.ObjectNode o)) continue;
                    o.put("row", ++row);
                    if (text(i, "ingredient") != null) rowOf.putIfAbsent(text(i, "ingredient").toLowerCase(java.util.Locale.ROOT), row);
                }
            }
        }
        for (JsonNode p : root.path("proposedProducts")) {
            if (p instanceof com.fasterxml.jackson.databind.node.ObjectNode o) {
                String name = text(p, "name");
                o.put("row", name == null ? 1 : rowOf.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), 1));
            }
        }
        if (root instanceof com.fasterxml.jackson.databind.node.ObjectNode o) o.remove("products");   // una foto no da de alta insumos sueltos
        return CatalogSpreadsheetPrompt.parse(root);
    }

    private static LocalTime time(String s) {
        try {
            return s == null ? null : LocalTime.parse(s.length() == 4 ? "0" + s : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate date(String s) {
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    static AppException failed(String why) {
        return new AppException(ErrorCode.AI_INTERPRETATION_FAILED, "Could not read the document: " + why);
    }
}
