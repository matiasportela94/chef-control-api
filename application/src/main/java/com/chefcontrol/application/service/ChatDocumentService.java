package com.chefcontrol.application.service;

import com.chefcontrol.application.attachment.AttachmentFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.ChatCatalogMenuItem;
import com.chefcontrol.application.port.AIService.DocumentPage;
import com.chefcontrol.application.port.AIService.DocumentReading;
import com.chefcontrol.application.port.ChatExchangeRepository.Kind;
import com.chefcontrol.application.service.ChatService.ChatCatalog;
import com.chefcontrol.application.service.ChatService.ChatResult;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.application.service.ChatService.Skipped;
import com.chefcontrol.domain.chat.ChatOperationType;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un archivo del chat (F19, tandas 7 y 8): el modelo dice qué es y lo lee; una factura o un remito va a la vista previa
 * de la compra (ChatInvoiceService), un cierre de caja a la de las ventas del día (ChatSalesReportService). Siempre
 * vista previa: nada se registra en el acto. Así quien manda la foto no tiene que decir qué es, y WhatsApp tampoco.
 */
@Service
@RequiredArgsConstructor
public class ChatDocumentService {

    static final int MAX_IMAGES = 5;
    private static final int MAX_CATALOG = 500;

    private final AIService aiService;
    private final ProductRepository productRepository;
    private final MenuItemRepository menuItemRepository;
    private final UnitRepository unitRepository;
    private final ChatInvoiceService chatInvoiceService;
    private final ChatSalesReportService chatSalesReportService;
    private final ChatHistoryService chatHistoryService;

    /**
     * @param files       las hojas (hasta 5 imágenes) o un PDF; el formato, por magic bytes
     * @param permissions una factura pide PURCHASES_CREATE y un cierre SALES_CREATE: se sabe recién después de leerlo
     */
    public ChatResult sendChatDocument(List<byte[]> files, String message, Set<Permission> permissions) {
        List<DocumentPage> pages = pages(files);
        UUID restaurantId = TenantContext.require();
        List<Product> products = productRepository
                .findByRestaurantIdAndIsActiveTrue(restaurantId, PageRequest.of(0, MAX_CATALOG)).content();
        List<MenuItem> menuItems = menuItemRepository
                .findByRestaurantIdAndActive(restaurantId, true, PageRequest.of(0, MAX_CATALOG)).content();
        ChatCatalog catalog = new ChatCatalog(products, menuItems, unitRepository.findAll(), Set.of());
        ChatResult result;
        try {
            DocumentReading reading = aiService.interpretChatDocument(new AIService.ChatDocumentRequest(pages, message,
                    catalog.chatProducts(), menuItems.stream()
                            .map(m -> new ChatCatalogMenuItem(m.getId(), m.getName(), m.getPrice())).toList(),
                    catalog.unitOptions()));
            result = switch (reading.kind()) {
                case INVOICE -> permissions.contains(Permission.PURCHASES_CREATE)
                        ? chatInvoiceService.previewInvoice(reading.invoice(), catalog)
                        : missing(ChatOperationType.PURCHASE, "es una factura y no tenés permiso para cargar compras");
                case SALES_REPORT -> permissions.contains(Permission.SALES_CREATE)
                        ? chatSalesReportService.previewSalesReport(reading.salesReport(), catalog)
                        : missing(ChatOperationType.SALE, "es un cierre de caja y no tenés permiso para cargar ventas");
                case OTHER -> notRecognized();
            };
        } catch (AppException e) {
            if (e.getErrorCode() != ErrorCode.AI_INTERPRETATION_FAILED) throw e;
            result = ChatResult.of(ChatStatus.NOT_UNDERSTOOD, ChatService.NOTHING_REGISTERED
                    + ": no pude leer el archivo. Probá de nuevo o con una foto más nítida.");
        }
        chatHistoryService.rememberChatExchange(Kind.MESSAGE, message == null || message.isBlank() ? "(archivo)" : message,
                result);
        return result;
    }

    private static List<DocumentPage> pages(List<byte[]> files) {
        if (files == null || files.isEmpty()) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR, "Send the photos or one PDF");
        }
        List<DocumentPage> pages = new ArrayList<>();
        for (byte[] f : files) {
            if (f.length == 0 || f.length > PurchaseAttachmentService.MAX_BYTES) {
                throw AppException.badRequest(ErrorCode.ATTACHMENT_TOO_LARGE, "Each file can be up to 4 MB");
            }
            AttachmentFormat format = AttachmentFormat.detect(f).orElseThrow(() -> AppException.badRequest(
                    ErrorCode.ATTACHMENT_FORMAT_NOT_SUPPORTED, "Only PDF, JPEG, PNG or WEBP"));
            pages.add(new DocumentPage(f, format.mediaType()));
        }
        boolean pdf = pages.stream().anyMatch(p -> "application/pdf".equals(p.mediaType()));
        if (pdf && pages.size() > 1 || pages.size() > MAX_IMAGES) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR, "Up to 5 photos or one PDF");
        }
        return pages;
    }

    private static ChatResult missing(ChatOperationType type, String why) {
        return new ChatResult(ChatStatus.NOT_REGISTERED, ChatService.NOTHING_REGISTERED + ": " + why + ".", List.of(),
                List.of(), List.of(new Skipped(type, null, ErrorCode.CHAT_PERMISSION_MISSING,
                        Character.toUpperCase(why.charAt(0)) + why.substring(1) + ".")));
    }

    private static ChatResult notRecognized() {
        String why = "No reconozco una factura, un remito ni un cierre de caja en el archivo.";
        return new ChatResult(ChatStatus.NOT_UNDERSTOOD, ChatService.NOTHING_REGISTERED
                + ": no reconozco una factura, un remito ni un cierre de caja en el archivo.", List.of(), List.of(),
                List.of(new Skipped(null, null, ErrorCode.CHAT_DOCUMENT_NOT_RECOGNIZED, why)));
    }
}
