package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.InvoiceDocumentRequest;
import com.chefcontrol.application.port.AIService.InvoiceLineReading;
import com.chefcontrol.application.port.AIService.InvoiceReading;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chefcontrol.infrastructure.ai.CatalogSpreadsheetPrompt.number;
import static com.chefcontrol.infrastructure.ai.CatalogSpreadsheetPrompt.text;

/**
 * Leer una factura o un remito, igual para Claude y Gemini. El modelo copia lo impreso y sugiere el insumo; las cuentas,
 * el proveedor, los alias y lo que se registra los decide ChatInvoiceService.
 */
final class InvoicePrompt {

    /** Una factura de 40 renglones con nombres largos: unos 60 tokens por renglón. */
    static final int MAX_OUTPUT_TOKENS = 4096;

    private static final String RULES = """
            Leés una factura, un ticket de compra o un remito de un proveedor de un restaurante argentino (fotos o un \
            PDF; varias fotos son las hojas de un mismo comprobante) y devolvés lo que tiene impreso. No registrás \
            nada: una persona revisa todo antes de cargarlo.

            SALIDA, con esta forma:
            {"invoice":true,"supplierName":"Verdulería Don José","supplierCuit":"30-71234567-1","invoiceType":"A",
             "invoiceNumber":"0001-00001234","issuedOn":"2026-11-15","printedTotal":12100,
             "lines":[{"printedText":"TOMATE PERITA X KG","quantity":5,"unit":"kg","unitPrice":2000,"lineTotal":10000,
                       "ivaPercentage":21,"catalogName":"Tomate perita","suggestedName":"Tomate perita"}]}

            REGLAS:
            1. invoice: false si no es una factura, un ticket de compra o un remito de un proveedor; ahí lo demás vacío.
            2. Copiá los números tal como están impresos, SIN hacer cuentas: ni sumar, ni dividir, ni calcular IVA. \
            Como números, con punto decimal ("1.500,50" → 1500.5).
            3. supplierName y supplierCuit: los del EMISOR (el proveedor que vende), nunca los del cliente. El CUIT tal \
            cual, con o sin guiones; null si no se ve.
            4. invoiceType: la letra del comprobante (A, B o C), o REMITO si es un remito; null si no se ve. \
            invoiceNumber: el número completo, con el punto de venta. issuedOn: la fecha de emisión, como YYYY-MM-DD.
            5. lines: un renglón por producto, en orden. Sin subtotales, IVA, percepciones, descuentos generales ni \
            totales. printedText: la descripción exactamente como está impresa. quantity, unitPrice y lineTotal: los \
            de ese renglón. unit: una abreviatura de UNIDADES si el renglón la dice ("KG" → kg, "UN" → u, "BOLSA" → bol \
            si está en la lista); si no la dice, null. ivaPercentage: la alícuota del renglón si está impresa (factura \
            A); si no, null.
            6. catalogName: el nombre EXACTO de un insumo del CATÁLOGO si el renglón es claramente ese insumo \
            ("TOMATE PERITA X KG" → "Tomate perita"); si dudás, null. suggestedName: un nombre corto y limpio para el \
            insumo, con mayúscula inicial y sin marcas ni presentaciones ("Harina 000", no "HARINA 000 MOLINOS X 25 KG").
            7. printedTotal: el total final impreso del comprobante.
            """;

    private InvoicePrompt() {}

    /** @param output cómo devolver el resultado: el JSON en el texto (Gemini) o la herramienta (Claude) */
    static String systemPrompt(InvoiceDocumentRequest r, String output) {
        String products = r.products().isEmpty() ? "(sin insumos cargados)" : r.products().stream()
                .map(p -> p.name() + " | " + (p.unit() == null ? "-" : p.unit())).collect(Collectors.joining("\n"));
        return RULES + output + "\n\n"
                + "UNIDADES (abreviatura: nombre): " + r.units().stream()
                        .map(u -> u.abbreviation() + ": " + u.name()).collect(Collectors.joining("; ")) + "\n\n"
                + "CATÁLOGO DE INSUMOS (nombre | unidad):\n" + products;
    }

    static String userText(InvoiceDocumentRequest r) {
        return r.message() == null || r.message().isBlank() ? "Leé el comprobante adjunto."
                : "Leé el comprobante adjunto. El usuario escribió: " + r.message();
    }

    /** Para Claude: la forma de la salida como input_schema de la herramienta. */
    static Map<String, Object> jsonSchema() {
        Map<String, Object> line = Map.of("type", "object", "properties", Map.of(
                "printedText", type("string"), "quantity", type("number"), "unit", type("string"),
                "unitPrice", type("number"), "lineTotal", type("number"), "ivaPercentage", type("number"),
                "catalogName", type("string"), "suggestedName", type("string")), "required", List.of("printedText"));
        return Map.of("type", "object", "properties", Map.of(
                "invoice", type("boolean"), "supplierName", type("string"), "supplierCuit", type("string"),
                "invoiceType", Map.of("type", "string", "enum", List.of("A", "B", "C", "REMITO")),
                "invoiceNumber", type("string"), "issuedOn", type("string"), "printedTotal", type("number"),
                "lines", Map.of("type", "array", "items", line)), "required", List.of("invoice", "lines"));
    }

    private static Map<String, Object> type(String t) {
        return Map.of("type", t);
    }

    static InvoiceReading parse(JsonNode root) {
        List<InvoiceLineReading> lines = new ArrayList<>();
        for (JsonNode l : root.path("lines")) {
            lines.add(new InvoiceLineReading(text(l, "printedText"), number(l, "quantity"), text(l, "unit"),
                    number(l, "unitPrice"), number(l, "lineTotal"), number(l, "ivaPercentage"), text(l, "catalogName"),
                    text(l, "suggestedName")));
        }
        return new InvoiceReading(root.path("invoice").asBoolean(false), text(root, "supplierName"),
                text(root, "supplierCuit"), text(root, "invoiceType"), text(root, "invoiceNumber"),
                date(text(root, "issuedOn")), number(root, "printedTotal"), lines);
    }

    private static LocalDate date(String s) {
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    static AppException failed(String why) {
        return new AppException(ErrorCode.AI_INTERPRETATION_FAILED, "Could not read the invoice: " + why);
    }
}
