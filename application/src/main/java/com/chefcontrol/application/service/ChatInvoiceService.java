package com.chefcontrol.application.service;

import com.chefcontrol.application.attachment.AttachmentFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.DocumentPage;
import com.chefcontrol.application.port.AIService.InvoiceLineReading;
import com.chefcontrol.application.port.AIService.InvoiceReading;
import com.chefcontrol.application.port.ChatExchangeRepository.Kind;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.ChatService.ChatCatalog;
import com.chefcontrol.application.service.ChatService.ChatResult;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.application.service.ChatService.Skipped;
import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatActionRepository;
import com.chefcontrol.domain.chat.ChatOperationType;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.purchase.InvoiceType;
import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.SupplierProductAlias;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.PurchaseRepository;
import com.chefcontrol.domain.repository.SupplierProductAliasRepository;
import com.chefcontrol.domain.repository.SupplierRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.supplier.Cuit;
import com.chefcontrol.domain.supplier.Supplier;
import com.chefcontrol.domain.user.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Una factura o un remito por foto o PDF → la compra (F19, tanda 7). Siempre vista previa y confirmar: nada se registra
 * en el acto. La IA lee lo impreso; acá se decide todo lo demás: el proveedor (por CUIT y, sin CUIT, por nombre), qué
 * insumo es cada renglón (el alias aprendido de ese proveedor gana; después, lo que sugiere la IA si existe; si no, se
 * propone uno nuevo), las cuentas, la unidad y los avisos. Al confirmar, una sola transacción: proveedor nuevo,
 * insumos nuevos, alias aprendidos y la compra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInvoiceService {

    static final int MAX_IMAGES = 5;
    private static final int MAX_CATALOG = 500;
    /** Una diferencia de hasta $1 o 1% es redondeo de la factura, no un error de lectura. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final AIService aiService;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final UnitConversionService unitConversionService;
    private final SupplierRepository supplierRepository;
    private final SupplierService supplierService;
    private final SupplierProductAliasRepository supplierProductAliasRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseService purchaseService;
    private final ProductService productService;
    private final ChatActionRepository chatActionRepository;
    private final ChatHistoryService chatHistoryService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactions;
    private final Clock clock;

    // ── Vista previa ───────────────────────────────────────────────────────────

    /** @param files las hojas (hasta 5 imágenes) o un PDF; el formato, por magic bytes */
    public ChatResult sendChatDocument(List<byte[]> files, String message) {
        List<DocumentPage> pages = pages(files);
        UUID restaurantId = TenantContext.require();
        List<Product> products = productRepository
                .findByRestaurantIdAndIsActiveTrue(restaurantId, PageRequest.of(0, MAX_CATALOG)).content();
        ChatCatalog catalog = new ChatCatalog(products, List.of(), unitRepository.findAll(), Set.of());
        ChatResult result;
        try {
            InvoiceReading reading = aiService.interpretInvoiceDocument(new AIService.InvoiceDocumentRequest(pages, message,
                    catalog.chatProducts(), catalog.unitOptions()));
            result = reading.invoice() ? preview(reading, catalog) : notAnInvoice();
        } catch (AppException e) {
            if (e.getErrorCode() != ErrorCode.AI_INTERPRETATION_FAILED) throw e;
            result = ChatResult.of(ChatStatus.NOT_UNDERSTOOD, ChatService.NOTHING_REGISTERED
                    + ": no pude leer el archivo. Probá de nuevo o con una foto más nítida.");
        }
        chatHistoryService.rememberChatExchange(Kind.MESSAGE, message == null || message.isBlank() ? "(factura)" : message,
                result);
        return result;
    }

    private static List<DocumentPage> pages(List<byte[]> files) {
        if (files == null || files.isEmpty()) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR, "Send the photos of the invoice or one PDF");
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

    private ChatResult notAnInvoice() {
        return new ChatResult(ChatStatus.NOT_UNDERSTOOD, ChatService.NOTHING_REGISTERED
                + ": no reconozco una factura o un remito en el archivo.", List.of(), List.of(),
                List.of(new Skipped(null, null, ErrorCode.CHAT_DOCUMENT_NOT_AN_INVOICE,
                        "No reconozco una factura o un remito en el archivo.")));
    }

    ChatResult preview(InvoiceReading r, ChatCatalog catalog) {
        UUID restaurantId = TenantContext.require();
        LocalDate today = LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE));
        List<InvoiceWarning> warnings = new ArrayList<>();
        InvoiceType type = invoiceType(r.invoiceType());

        // El proveedor: por CUIT; sin CUIT (los remitos), por nombre con la comparación del import, para no duplicarlo.
        String cuit = r.supplierCuit() == null ? null : Cuit.normalize(r.supplierCuit()).orElse(null);
        if (r.supplierCuit() != null && cuit == null) {
            warnings.add(new InvoiceWarning("INVALID_CUIT_READ", "El CUIT leído (" + r.supplierCuit()
                    + ") no es válido: revisalo.", null));
        }
        Supplier supplier = cuit == null ? null : supplierRepository.findActiveByRestaurantIdAndCuit(restaurantId, cuit).orElse(null);
        if (supplier == null && r.supplierName() != null) {
            String key = CatalogImportService.key(r.supplierName());
            supplier = supplierRepository.findAllByRestaurantIdAndIsActiveTrueOrderByName(restaurantId).stream()
                    .filter(s -> CatalogImportService.key(s.getName()).equals(key)).findFirst().orElse(null);
        }
        InvoiceSupplier invoiceSupplier = supplier != null
                ? new InvoiceSupplier(supplier.getId(), supplier.getName(), supplier.getCuit(), false)
                : new InvoiceSupplier(null, r.supplierName(), cuit, r.supplierName() != null);

        Map<String, SupplierProductAlias> aliases = new HashMap<>();
        if (supplier != null) {
            supplierProductAliasRepository.findBySupplierId(supplier.getId()).forEach(a -> aliases.put(a.printedKey(), a));
        }

        List<InvoicePreviewLine> lines = new ArrayList<>();
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        for (InvoiceLineReading l : r.lines()) {
            InvoicePreviewLine line = line(l, type, catalog, aliases);
            lines.add(line);
            BigDecimal amount = l.lineTotal() != null ? l.lineTotal()
                    : l.quantity() != null && l.unitPrice() != null ? l.quantity().multiply(l.unitPrice()) : BigDecimal.ZERO;
            base = base.add(amount);
            if (type == InvoiceType.A && l.ivaPercentage() != null) {
                iva = iva.add(amount.multiply(l.ivaPercentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
        }
        BigDecimal computed = base.add(iva).setScale(2, RoundingMode.HALF_UP);
        if (r.printedTotal() != null && differ(computed, r.printedTotal())) {
            warnings.add(new InvoiceWarning("TOTAL_MISMATCH", "La suma de los renglones (" + ChatService.money(computed)
                    + ") no da el total impreso (" + ChatService.money(r.printedTotal()) + "): revisá la lectura"
                    + (type == InvoiceType.A ? " o si hay percepciones." : "."), null));
        }
        String number = r.invoiceNumber() == null ? null : r.invoiceNumber().trim();
        if (supplier != null && number != null) {
            purchaseRepository.findActiveBySupplierIdAndInvoiceNumber(restaurantId, supplier.getId(), number)
                    .ifPresent(p -> warnings.add(new InvoiceWarning("ALREADY_LOADED", "Esa factura ya está cargada (compra del "
                            + p.getPurchasedAt().atZone(ChefControlTime.DEFAULT_ZONE).toLocalDate().format(DAY) + ").", p.getId())));
        }
        if (r.issuedOn() != null && r.issuedOn().isAfter(today)) {
            warnings.add(new InvoiceWarning("FUTURE_DATE", "La fecha leída (" + r.issuedOn().format(DAY)
                    + ") es futura: revisala.", null));
        }
        if (lines.stream().anyMatch(l -> !l.warnings().isEmpty())) {
            warnings.add(new InvoiceWarning("LINES_TO_REVIEW", "Hay renglones para revisar.", null));
        }

        boolean includeIva = type == null || type.pricesIncludeIva();
        InvoicePreview preview = new InvoicePreview(invoiceSupplier, type, number, r.issuedOn(), includeIva, lines,
                r.printedTotal(), computed, warnings);
        String what = (type == null ? "el comprobante" : type == InvoiceType.REMITO ? "el remito" : "la factura " + type)
                + (number == null ? "" : " " + number)
                + (invoiceSupplier.name() == null ? "" : " de «" + invoiceSupplier.name() + "»")
                + (r.issuedOn() == null ? "" : " del " + r.issuedOn().format(DAY));
        String message = "Leí " + what + ": " + lines.size() + (lines.size() == 1 ? " renglón" : " renglones")
                + (r.printedTotal() == null ? "" : ", total " + ChatService.money(r.printedTotal()))
                + ". Revisala y confirmala."
                + (warnings.isEmpty() ? "" : " Ojo: " + String.join(" ", warnings.stream().map(InvoiceWarning::message).toList()));
        return new ChatResult(ChatStatus.NEEDS_CONFIRMATION, message, List.of(), List.of(), List.of(), List.of(), null,
                preview);
    }

    /** Un renglón: qué insumo es y su cantidad y precio en la unidad del insumo, calculados acá. */
    private InvoicePreviewLine line(InvoiceLineReading l, InvoiceType type, ChatCatalog catalog,
                                    Map<String, SupplierProductAlias> aliases) {
        List<String> warnings = new ArrayList<>();
        SupplierProductAlias alias = l.printedText() == null ? null : aliases.get(CatalogImportService.key(l.printedText()));
        Product product = alias == null ? null : catalog.product(alias.productId());
        String matchedBy = product != null ? "ALIAS" : null;
        if (product == null && l.catalogName() != null) {
            product = catalog.productNamed(l.catalogName());
            if (product == null) {
                String name = CatalogSpreadsheetService.singularMatch(l.catalogName(),
                        catalog.products().stream().map(Product::getName).toList());
                product = name == null ? null : catalog.productNamed(name);
            }
            if (product != null) matchedBy = "CATALOG";
        }
        Unit printedUnit = l.unit() == null ? null : catalog.unitNamed(l.unit());
        BigDecimal quantity = l.quantity();
        BigDecimal unitPrice = l.unitPrice();
        Unit unit = printedUnit;
        ProposedProduct proposed = null;

        if (product != null) {
            Unit target = catalog.unit(product.getDefaultUnitId());
            BigDecimal factor = alias == null ? null : alias.unitsPerPrintedUnit();
            unit = target;
            if (factor != null && (printedUnit == null || !converts(printedUnit, target))) {
                // Lo aprendido en una factura anterior de este proveedor: "BOLSA X 25 KG" son 25 kg.
                quantity = quantity == null ? null : quantity.multiply(factor).setScale(3, RoundingMode.HALF_UP);
                unitPrice = unitPrice == null ? null : unitPrice.divide(factor, 4, RoundingMode.HALF_UP);
            } else if (printedUnit != null && target != null && !printedUnit.getId().equals(target.getId())) {
                try {
                    if (quantity != null) quantity = unitConversionService.convert(quantity, printedUnit.getId(), target.getId())
                            .setScale(3, RoundingMode.HALF_UP);
                    if (unitPrice != null) unitPrice = unitConversionService.convertPrice(unitPrice, printedUnit.getId(),
                            target.getId()).setScale(4, RoundingMode.HALF_UP);
                } catch (AppException e) {
                    unit = printedUnit;
                    warnings.add("UNIT_NOT_CONVERTIBLE: «" + product.getName() + "» va en " + ChatService.unitLabel(product)
                            + " y el renglón dice " + printedUnit.getAbbreviation() + ": cargá cuántos "
                            + ChatService.unitLabel(product) + " son.");
                }
            } else if (printedUnit == null && target != null && !"u".equalsIgnoreCase(target.getAbbreviation())) {
                warnings.add("UNIT_UNCLEAR: el renglón no dice la unidad: revisá que sean "
                        + ChatService.unitLabel(product) + ".");
            }
        } else {
            String name = l.suggestedName() != null ? l.suggestedName() : l.printedText();
            Unit u = printedUnit != null ? printedUnit : catalog.unitNamed("u");
            proposed = name == null ? null : new ProposedProduct(CatalogSpreadsheetService.capitalized(name),
                    u == null ? null : u.getId(), u == null ? null : u.getAbbreviation());
        }
        if (l.quantity() != null && l.unitPrice() != null && l.lineTotal() != null
                && differ(l.quantity().multiply(l.unitPrice()), l.lineTotal())) {
            warnings.add("LINE_TOTAL_MISMATCH: cantidad × precio (" + ChatService.money(l.quantity().multiply(l.unitPrice()))
                    + ") no da el total del renglón (" + ChatService.money(l.lineTotal()) + ").");
        }
        // Factura C: el emisor no discrimina IVA; con IVA incluido y alícuota 0, en un RI el costo es el total.
        BigDecimal ivaPercentage = type == InvoiceType.C ? BigDecimal.ZERO : type == InvoiceType.A ? l.ivaPercentage() : null;
        return new InvoicePreviewLine(l.printedText(), product == null ? null : product.getId(),
                product == null ? null : product.getName(), matchedBy, proposed, quantity, unit == null ? null : unit.getAbbreviation(),
                unit == null ? null : unit.getId(), unitPrice, ivaPercentage, l.lineTotal(), l.quantity(),
                printedUnit == null ? l.unit() : printedUnit.getAbbreviation(), warnings);
    }

    private boolean converts(Unit from, Unit to) {
        if (to == null || from.getId().equals(to.getId())) return true;
        try {
            unitConversionService.convert(BigDecimal.ONE, from.getId(), to.getId());
            return true;
        } catch (AppException e) {
            return false;
        }
    }

    private static boolean differ(BigDecimal a, BigDecimal b) {
        BigDecimal diff = a.subtract(b).abs();
        return diff.compareTo(BigDecimal.ONE) > 0 && diff.compareTo(b.abs().multiply(TOLERANCE)) > 0;
    }

    private static InvoiceType invoiceType(String t) {
        if (t == null) return null;
        try {
            return InvoiceType.valueOf(t.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Confirmar ──────────────────────────────────────────────────────────────

    /**
     * La vista previa, tal cual o editada. Todo en una transacción: el proveedor nuevo (con SUPPLIERS_CREATE; si falta,
     * la compra va sin proveedor), los insumos nuevos (con PRODUCTS_CREATE; si falta, ese renglón no entra), los alias
     * aprendidos y la compra. Queda en la conversación y con su deshacer, como cualquier compra del chat.
     */
    public ChatResult confirmChatInvoice(InvoiceConfirmation c, Set<Permission> permissions) {
        List<Skipped> skipped = new ArrayList<>();
        ChatResult result;
        try {
            result = transactions.execute(status -> register(c, permissions, skipped));
        } catch (AppException e) {
            skipped.add(ChatService.rejected(ChatOperationType.PURCHASE, "la factura", e));
            result = new ChatResult(ChatStatus.NOT_REGISTERED, ChatService.NOTHING_REGISTERED + ": "
                    + skipped.getLast().message(), List.of(), List.of(), skipped);
        }
        chatHistoryService.rememberChatExchange(Kind.CONFIRMATION, null, result);
        return result;
    }

    private ChatResult register(InvoiceConfirmation c, Set<Permission> permissions, List<Skipped> skipped) {
        UUID restaurantId = TenantContext.require();
        Supplier supplier = null;
        if (c.supplierId() != null) {
            supplier = supplierRepository.findByIdAndRestaurantId(c.supplierId(), restaurantId)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.SUPPLIER_NOT_FOUND, "Supplier not found"));
        } else if (c.newSupplierName() != null && !c.newSupplierName().isBlank()) {
            // Un CUIT que ya tiene un proveedor activo es ese proveedor; uno inválido no frena la compra.
            String cuit = c.newSupplierCuit() == null ? null : Cuit.normalize(c.newSupplierCuit()).orElse(null);
            Supplier same = cuit == null ? null
                    : supplierRepository.findActiveByRestaurantIdAndCuit(restaurantId, cuit).orElse(null);
            if (same != null) {
                supplier = same;
            } else if (permissions.contains(Permission.SUPPLIERS_CREATE)) {
                supplier = supplierService.createSupplier(new SupplierService.SupplierCommand(c.newSupplierName().trim(),
                        null, null, null, null, cuit));
            } else {
                skipped.add(new Skipped(null, c.newSupplierName(), ErrorCode.CHAT_PERMISSION_MISSING,
                        "No tenés permiso para dar de alta proveedores: la compra va sin proveedor."));
            }
        }

        boolean forcedC = c.invoiceType() == InvoiceType.C;
        List<PurchaseService.PurchaseItemCommand> items = new ArrayList<>();
        List<SupplierProductAlias> learned = new ArrayList<>();
        int lines = 0;
        for (ConfirmedLine l : c.lines()) {
            UUID productId = l.productId();
            if (productId == null && l.newProductName() != null) {
                if (!permissions.contains(Permission.PRODUCTS_CREATE)) {
                    skipped.add(new Skipped(ChatOperationType.PURCHASE, l.newProductName(), ErrorCode.CHAT_PERMISSION_MISSING,
                            "No tenés permiso para dar de alta insumos: «" + l.newProductName() + "» no entró."));
                    continue;
                }
                productId = productService.createProduct(new ProductService.CreateProductCommand(
                        CatalogSpreadsheetService.capitalized(l.newProductName()), null, l.newProductUnitId(),
                        null, null, null, null, null)).getId();
            }
            if (productId == null) continue;
            items.add(new PurchaseService.PurchaseItemCommand(productId, l.unitId(), l.quantity(), l.unitPrice(),
                    l.expirationDate(), forcedC ? BigDecimal.ZERO : l.ivaPercentage()));
            lines++;
            if (supplier != null && l.printedText() != null && !l.printedText().isBlank()) {
                learned.add(new SupplierProductAlias(null, restaurantId, supplier.getId(),
                        CatalogImportService.key(l.printedText()), productId, l.unitsPerPrintedUnit()));
            }
        }
        if (items.isEmpty()) {
            return new ChatResult(ChatStatus.NOT_REGISTERED, ChatService.NOTHING_REGISTERED + ": no quedó ningún renglón.",
                    List.of(), List.of(), skipped);
        }
        LocalDate issued = c.issuedOn() == null || c.issuedOn().isAfter(LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE)))
                ? LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE)) : c.issuedOn();
        Purchase purchase = purchaseService.createPurchase(new PurchaseService.CreatePurchaseCommand(
                supplier == null ? null : supplier.getId(), null,
                issued.atStartOfDay(ChefControlTime.DEFAULT_ZONE).toInstant(),
                forcedC || c.pricesIncludeIva(), items, c.invoiceType(), c.invoiceNumber(), null));
        learned.forEach(supplierProductAliasRepository::upsert);

        String summary = "Compra: " + (c.invoiceType() == null ? "comprobante" : c.invoiceType() == InvoiceType.REMITO
                ? "remito" : "factura " + c.invoiceType()) + (c.invoiceNumber() == null ? "" : " " + c.invoiceNumber())
                + (supplier == null ? "" : " de " + supplier.getName()) + " (" + lines + (lines == 1 ? " renglón" : " renglones")
                + ") por " + ChatService.money(purchase.getTotal());
        ChatAction action = chatActionRepository.save(ChatAction.builder().restaurantId(restaurantId)
                .userId(currentUserProvider.currentUserId()).type(ChatOperationType.PURCHASE).entityId(purchase.getId())
                .summary(summary.length() > 500 ? summary.substring(0, 499) + "…" : summary).createdAt(clock.instant()).build());
        String message = "Listo: " + summary + "." + (skipped.isEmpty() ? "" : " No registré: "
                + String.join(" ", skipped.stream().map(Skipped::message).toList()));
        return new ChatResult(ChatStatus.REGISTERED, message, List.of(action), List.of(), skipped);
    }

    // ── Tipos ──────────────────────────────────────────────────────────────────

    /**
     * Lo que se leyó, ya resuelto. {@code pricesIncludeIva} sale del tipo (A sin IVA; B, C y remito con IVA) y es
     * editable; en una C las alícuotas van en 0.
     */
    public record InvoicePreview(InvoiceSupplier supplier, InvoiceType invoiceType, String invoiceNumber, LocalDate issuedOn,
                                 boolean pricesIncludeIva, List<InvoicePreviewLine> lines, BigDecimal printedTotal,
                                 BigDecimal computedTotal, List<InvoiceWarning> warnings) {}

    /** @param isNew no está cargado: se da de alta al confirmar (con SUPPLIERS_CREATE) */
    public record InvoiceSupplier(UUID supplierId, String name, String cuit, boolean isNew) {}

    /**
     * Un renglón. {@code quantity}, {@code unitId} y {@code unitPrice} van en la unidad del insumo si se pudo convertir
     * (o con el factor aprendido); si no, como están impresos y con UNIT_NOT_CONVERTIBLE en {@code warnings}.
     *
     * @param matchedBy ALIAS (aprendido de este proveedor), CATALOG (lo sugirió la IA y existe) o null (propuesto)
     */
    public record InvoicePreviewLine(String printedText, UUID productId, String productName, String matchedBy,
                                     ProposedProduct proposedProduct, BigDecimal quantity, String unit, UUID unitId,
                                     BigDecimal unitPrice, BigDecimal ivaPercentage, BigDecimal lineTotal,
                                     BigDecimal printedQuantity, String printedUnit, List<String> warnings) {}

    public record ProposedProduct(String name, UUID unitId, String unit) {}

    /** @param code TOTAL_MISMATCH, ALREADY_LOADED (con purchaseId), FUTURE_DATE, INVALID_CUIT_READ o LINES_TO_REVIEW */
    public record InvoiceWarning(String code, String message, UUID purchaseId) {}

    /** @param supplierId el proveedor que ya existe; si es null y viene {@code newSupplierName}, se da de alta */
    public record InvoiceConfirmation(UUID supplierId, String newSupplierName, String newSupplierCuit, InvoiceType invoiceType,
                                      String invoiceNumber, LocalDate issuedOn, boolean pricesIncludeIva,
                                      List<ConfirmedLine> lines) {}

    /**
     * @param productId           el insumo; si es null y viene {@code newProductName}, se da de alta (en {@code newProductUnitId})
     * @param unitsPerPrintedUnit cuántas unidades del insumo trae la unidad impresa, si no se convertía: lo aprende el alias
     */
    public record ConfirmedLine(String printedText, UUID productId, String newProductName, UUID newProductUnitId,
                                BigDecimal quantity, UUID unitId, BigDecimal unitPrice, BigDecimal ivaPercentage,
                                LocalDate expirationDate, BigDecimal unitsPerPrintedUnit) {}
}
