package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.ChatExchangeRepository.StoredReply;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.chefcontrol.application.service.ChatRecipeService.RecipePreview;
import com.chefcontrol.application.service.ChatService.Operation;
import com.chefcontrol.application.service.ChatService.OperationItem;
import com.chefcontrol.application.service.ChatService.Proposal;
import com.chefcontrol.application.service.ChatService.Skipped;
import com.chefcontrol.domain.chat.ChatOperationType;
import com.chefcontrol.domain.waste.WasteReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La respuesta guardada vuelve igual: de eso dependen el historial, los botones después de recargar y contestar una
 * pregunta por texto (se compara con las alternativas guardadas). Records con más de un constructor, enums y fechas.
 */
class ChatExchangeRepositoryAdapterTest {

    private final ChatExchangeRepositoryAdapter adapter =
            new ChatExchangeRepositoryAdapter(null, new ObjectMapper().findAndRegisterModules());

    @Test
    void theStoredReplyRoundTrips() {
        UUID product = UUID.randomUUID();
        var item = new OperationItem(product, null, "Comino", new BigDecimal("5"), "kg", new BigDecimal("30000.5"),
                LocalDate.of(2026, 12, 1), WasteReason.DAMAGED);
        var reply = new StoredReply("¿Por kg o total?", List.of(UUID.randomUUID()), List.of(),
                List.of(new Proposal("Por kg · $ 750.000", List.of(new Operation(ChatOperationType.PURCHASE, List.of(item),
                        UUID.randomUUID())))),
                List.of(new Skipped(null, "caviar", ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG, "«caviar» no está.")),
                new RecipePreview("Napolitana", new CatalogSpreadsheetInterpretation(List.of(),
                        List.of(), List.of(new RecipeRow(1, "Napolitana", null, null, "Jamón", new BigDecimal("0.1"), "kg")),
                        List.of(new ProductRow(1, "Jamón", "kg", null, null, null)), List.of(), List.of("aviso")),
                        List.of(), List.of()),
                new com.chefcontrol.application.service.ChatInvoiceService.InvoicePreview(
                        new com.chefcontrol.application.service.ChatInvoiceService.InvoiceSupplier(null, "Almacén", null, true),
                        com.chefcontrol.domain.purchase.InvoiceType.A, "0001-00000001", LocalDate.of(2026, 11, 15), false,
                        List.of(new com.chefcontrol.application.service.ChatInvoiceService.InvoicePreviewLine("TOMATE X KG",
                                product, "Tomate", "CATALOG", null, new BigDecimal("5"), "kg", UUID.randomUUID(),
                                new BigDecimal("2000"), new BigDecimal("21"), new BigDecimal("10000"), new BigDecimal("5"),
                                "kg", List.of())),
                        new BigDecimal("12100"), new BigDecimal("12100"),
                        List.of(new com.chefcontrol.application.service.ChatInvoiceService.InvoiceWarning("ALREADY_LOADED",
                                "Ya está.", UUID.randomUUID()))),
                new com.chefcontrol.application.service.ChatSalesReportService.SalesReportPreview("0012",
                        LocalDate.of(2026, 11, 17), List.of(new com.chefcontrol.application.service.ChatSalesReportService
                        .SalesReportLine("MILA NAPO", product, "Milanesa napolitana", "ALIAS", 2, new BigDecimal("11000"),
                        new BigDecimal("22000"), new BigDecimal("12000"), true, List.of())),
                        new BigDecimal("22000"), new BigDecimal("22000"), new BigDecimal("22000"),
                        new com.chefcontrol.application.service.ChatSalesReportService.ExistingSales(1, new BigDecimal("45000")),
                        List.of(new com.chefcontrol.application.service.ChatSalesReportService.SalesReportWarning(
                                "AFTER_MIDNIGHT", "Lo puse en el 17/11.", null))));

        assertEquals(reply, adapter.read(adapter.write(reply)));
    }
}
