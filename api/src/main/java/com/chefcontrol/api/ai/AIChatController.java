package com.chefcontrol.api.ai;

import com.chefcontrol.api.ai.dto.ChatActionResponse;
import com.chefcontrol.api.ai.dto.ChatExchangeResponse;
import com.chefcontrol.api.ai.dto.ChatOperation;
import com.chefcontrol.api.ai.dto.ChatResponse;
import com.chefcontrol.api.plan.RequiresFeature;
import com.chefcontrol.application.service.ChatHistoryService;
import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.domain.plan.Feature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.chefcontrol.api.shared.Authorities.permissionsOf;

/**
 * El chat de la IA (F19). Además de AI_USE, cada operación pide el permiso de su pantalla, pero eso lo decide
 * {@link ChatService} por operación: sin el permiso esa parte no se registra y la respuesta lo dice, en vez de un 403
 * de todo el mensaje.
 */
@RestController
@RequestMapping("/ai/chat")
@RequiresFeature(Feature.AI_INTERPRETER)
@RequiredArgsConstructor
public class AIChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;
    private final com.chefcontrol.application.service.ChatInvoiceService chatInvoiceService;
    private final com.chefcontrol.application.service.ChatDocumentService chatDocumentService;
    private final com.chefcontrol.application.service.ChatSalesReportService chatSalesReportService;

    /** Interpreta y resuelve: registra en el acto (con deshacer), pide confirmación o dice por qué no. */
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ChatResponse> sendChatMessage(@Valid @RequestBody SendChatMessageRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(ChatResponse.from(
                chatService.sendChatMessage(request.message().trim(), permissionsOf(authentication))));
    }

    /**
     * La conversación de hoy (desde la medianoche argentina o el último "Nueva conversación"): lo mismo que recuerda la
     * IA, con las acciones como están ahora.
     */
    @GetMapping("/messages")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<List<ChatExchangeResponse>> listChatMessages() {
        return ResponseEntity.ok(chatHistoryService.listChatMessages().stream().map(ChatExchangeResponse::from).toList());
    }

    /**
     * "Cancelar" la pregunta pendiente (la última respuesta, si es un NEEDS_CONFIRMATION): queda en la conversación, así
     * un "sí" después no registra lo cancelado. Sin nada pendiente, no guarda nada.
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ChatResponse> cancelChatProposal() {
        return ResponseEntity.ok(ChatResponse.from(chatHistoryService.cancelChatProposal()));
    }

    /** "Nueva conversación": la memoria arranca de cero. No borra nada (los mensajes se guardan 30 días). */
    @PostMapping("/conversations")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<Void> startChatConversation() {
        chatHistoryService.startChatConversation();
        return ResponseEntity.noContent().build();
    }

    /** Registra una alternativa de un NEEDS_CONFIRMATION, tal cual o editada. Se revalida todo. */
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ChatResponse> confirmChatOperations(@Valid @RequestBody ConfirmChatOperationsRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.ok(ChatResponse.from(chatService.confirmChatOperations(
                request.operations().stream().map(ChatOperation::toCommand).toList(), permissionsOf(authentication))));
    }

    /**
     * Quien lo cargó, durante 15 minutos, sin el permiso de borrar; después, o lo de otro, con el permiso de borrar de
     * esa operación (CHAT_UNDO_NOT_ALLOWED si falta).
     */
    @PostMapping("/actions/{actionId}/undo")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ChatActionResponse> undoChatAction(@PathVariable UUID actionId,
                                                             Authentication authentication) {
        return ResponseEntity.ok(ChatActionResponse.from(
                chatService.undoChatAction(actionId, permissionsOf(authentication))));
    }

    /**
     * Una factura o un remito, un cierre de caja o una o varias recetas: hasta 5 fotos (sus hojas) o un PDF, por magic bytes. El modelo dice
     * qué es. Siempre vuelve como vista previa (NEEDS_CONFIRMATION con {@code invoice} o {@code salesReport}): nada se
     * registra en el acto. Los archivos los guarda el front y los sube a /purchases/{id}/attachments o
     * /sales/{id}/attachments después de confirmar. La factura pide PURCHASES_CREATE y el cierre SALES_CREATE: sin el
     * que corresponde, NOT_REGISTERED con CHAT_PERMISSION_MISSING.
     */
    @PostMapping(value = "/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_AI_USE') and (hasAuthority('PERM_PURCHASES_CREATE') or hasAuthority('PERM_SALES_CREATE')"
            + " or hasAuthority('PERM_MENU_CREATE') or hasAuthority('PERM_PRODUCTS_CREATE'))")
    public ResponseEntity<ChatResponse> sendChatDocument(
            @RequestParam("files") List<org.springframework.web.multipart.MultipartFile> files,
            @RequestParam(value = "message", required = false) @Size(max = 2000) String message,
            Authentication authentication) throws java.io.IOException {
        List<byte[]> bytes = new java.util.ArrayList<>();
        for (var f : files) bytes.add(f.getBytes());
        return ResponseEntity.ok(ChatResponse.from(chatDocumentService.sendChatDocument(bytes, message,
                permissionsOf(authentication))));
    }

    /**
     * La vista previa de un cierre de caja, tal cual o editada: revierte las ventas que ya tenía ese día si
     * {@code existingSales} es REVERSE (con SALES_DELETE; todo o nada), registra una venta con todos los platos al precio
     * del ticket y aprende lo que imprime el POS ({@code ignore: true} = no es un plato). La venta es
     * {@code actions[0].entityId}.
     */
    @PostMapping("/sales-report/confirm")
    @PreAuthorize("hasAuthority('PERM_AI_USE') and hasAuthority('PERM_SALES_CREATE')")
    public ResponseEntity<ChatResponse> confirmChatSalesReport(@Valid @RequestBody ConfirmChatSalesReportRequest r,
                                                               Authentication authentication) {
        return ResponseEntity.ok(ChatResponse.from(chatSalesReportService.confirmChatSalesReport(
                new com.chefcontrol.application.service.ChatSalesReportService.SalesReportConfirmation(r.reportNumber(),
                        r.soldOn(), r.existingSales(), r.lines().stream().map(l ->
                        new com.chefcontrol.application.service.ChatSalesReportService.ConfirmedSalesLine(l.printedText(),
                                l.menuItemId(), l.quantity(), l.unitPrice(), Boolean.TRUE.equals(l.ignore()))).toList()),
                permissionsOf(authentication))));
    }

    record ConfirmChatSalesReportRequest(@Size(max = 30) String reportNumber, java.time.LocalDate soldOn,
                                         com.chefcontrol.application.service.ChatSalesReportService.ExistingSalesChoice existingSales,
                                         @NotNull @Size(min = 1, max = 200) List<@Valid @NotNull ConfirmedSalesReportLine> lines) {}

    /** Un plato ({@code menuItemId} + {@code quantity} + {@code unitPrice}) o {@code ignore: true}. */
    record ConfirmedSalesReportLine(@Size(max = 300) String printedText, UUID menuItemId,
                                    @jakarta.validation.constraints.Positive Integer quantity,
                                    @jakarta.validation.constraints.Positive java.math.BigDecimal unitPrice, Boolean ignore) {}

    /**
     * La vista previa de una factura, tal cual o editada: da de alta el proveedor y los insumos nuevos (con sus permisos),
     * aprende los alias del proveedor y registra la compra, en una transacción. La compra es {@code actions[0].entityId}.
     */
    @PostMapping("/invoice/confirm")
    @PreAuthorize("hasAuthority('PERM_AI_USE') and hasAuthority('PERM_PURCHASES_CREATE')")
    public ResponseEntity<ChatResponse> confirmChatInvoice(@Valid @RequestBody ConfirmChatInvoiceRequest r,
                                                           Authentication authentication) {
        return ResponseEntity.ok(ChatResponse.from(chatInvoiceService.confirmChatInvoice(
                new com.chefcontrol.application.service.ChatInvoiceService.InvoiceConfirmation(r.supplierId(),
                        r.newSupplierName(), r.newSupplierCuit(), r.invoiceType(), r.invoiceNumber(), r.issuedOn(),
                        r.pricesIncludeIva(), r.lines().stream().map(l ->
                        new com.chefcontrol.application.service.ChatInvoiceService.ConfirmedLine(l.printedText(),
                                l.productId(), l.newProductName(), l.newProductUnitId(), l.quantity(), l.unitId(),
                                l.unitPrice(), l.ivaPercentage(), l.expirationDate(), l.unitsPerPrintedUnit())).toList()),
                permissionsOf(authentication))));
    }

    record ConfirmChatInvoiceRequest(UUID supplierId, @Size(max = 255) String newSupplierName,
                                     @Size(max = 13) String newSupplierCuit,
                                     com.chefcontrol.domain.purchase.InvoiceType invoiceType,
                                     @Size(max = 30) String invoiceNumber, java.time.LocalDate issuedOn,
                                     boolean pricesIncludeIva,
                                     @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull ConfirmedInvoiceLine> lines) {}

    /**
     * @param productId           el insumo; si es null, {@code newProductName} y {@code newProductUnitId} lo dan de alta
     * @param unitsPerPrintedUnit si la unidad impresa no se convertía: cuántas unidades del insumo trae. Con él, cantidad,
     *                            precio y unidad van como están impresos y el back los pasa a la unidad del insumo; y lo
     *                            aprende el alias
     */
    record ConfirmedInvoiceLine(@Size(max = 300) String printedText, UUID productId, @Size(max = 255) String newProductName,
                                UUID newProductUnitId,
                                @NotNull @jakarta.validation.constraints.Positive java.math.BigDecimal quantity,
                                @NotNull UUID unitId,
                                @NotNull @jakarta.validation.constraints.Positive java.math.BigDecimal unitPrice,
                                java.math.BigDecimal ivaPercentage, java.time.LocalDate expirationDate,
                                @jakarta.validation.constraints.Positive java.math.BigDecimal unitsPerPrintedUnit) {}

    /** 2000: un audio de 2 minutos transcripto pasa los 500 caracteres. */
    record SendChatMessageRequest(@NotBlank @Size(max = 2000) String message) {}

    record ConfirmChatOperationsRequest(@NotNull @Size(min = 1, max = 20) List<@Valid @NotNull ChatOperation> operations) {}
}
