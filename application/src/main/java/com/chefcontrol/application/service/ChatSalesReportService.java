package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.SalesReportLineReading;
import com.chefcontrol.application.port.AIService.SalesReportReading;
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
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.repository.MenuItemAliasRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.sale.MenuItemAlias;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * F19, tanda 8: un cierre de caja (ticket Z o reporte del POS) → las ventas del día. Siempre con vista previa. El modelo
 * copia lo impreso; acá se decide qué plato es cada renglón (lo aprendido del POS, después el que sugiere el modelo si
 * existe), las cuentas, el día del servicio y los avisos. Un cierre no da de alta platos: son datos maestros.
 */
@Service
@RequiredArgsConstructor
public class ChatSalesReportService {

    /** Una diferencia de hasta $1 o 1% es redondeo del POS, no un error de lectura. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    /** Un cierre impreso antes de esta hora es el servicio de la noche anterior. */
    private static final LocalTime SERVICE_DAY_STARTS = LocalTime.of(6, 0);
    /** La venta de un cierre cae a esta hora del día del servicio: adentro de ese día en todos los reportes. */
    private static final LocalTime SALE_TIME = LocalTime.NOON;

    private final MenuItemAliasRepository menuItemAliasRepository;
    private final SaleRepository saleRepository;
    private final SaleService saleService;
    private final ChatActionRepository chatActionRepository;
    private final ChatHistoryService chatHistoryService;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactions;
    private final Clock clock;

    // ── Vista previa ───────────────────────────────────────────────────────────

    ChatResult previewSalesReport(SalesReportReading r, ChatCatalog catalog) {
        UUID restaurantId = TenantContext.require();
        LocalDate today = today();
        List<SalesReportWarning> warnings = new ArrayList<>();

        LocalDate soldOn = r.printedDate();
        if (soldOn != null && r.printedTime() != null && r.printedTime().isBefore(SERVICE_DAY_STARTS)) {
            soldOn = soldOn.minusDays(1);
            warnings.add(new SalesReportWarning("AFTER_MIDNIGHT", "El cierre es de las " + r.printedTime()
                    + " del " + r.printedDate().format(DAY) + ": lo puse en el " + soldOn.format(DAY)
                    + "; cambialo si no corresponde.", null));
        }
        if (soldOn == null) {
            soldOn = today;
            warnings.add(new SalesReportWarning("DATE_NOT_READ", "No leí la fecha del cierre: lo puse en hoy ("
                    + today.format(DAY) + "); cambiala si no corresponde.", null));
        } else if (soldOn.isAfter(today)) {
            warnings.add(new SalesReportWarning("FUTURE_DATE", "La fecha leída (" + soldOn.format(DAY)
                    + ") es futura: revisala.", null));
        }

        Map<String, MenuItemAlias> aliases = new HashMap<>();
        menuItemAliasRepository.findByRestaurantId(restaurantId).forEach(a -> aliases.put(a.printedKey(), a));
        List<SalesReportLine> lines = new ArrayList<>();
        BigDecimal readTotal = BigDecimal.ZERO;
        BigDecimal saleTotal = BigDecimal.ZERO;
        for (SalesReportLineReading l : r.lines()) {
            SalesReportLine line = line(l, catalog, aliases);
            lines.add(line);
            BigDecimal amount = l.lineTotal() != null ? l.lineTotal() : line.quantity() != null && line.unitPrice() != null
                    ? line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())) : BigDecimal.ZERO;
            readTotal = readTotal.add(amount);
            if (line.menuItemId() != null && line.quantity() != null && line.unitPrice() != null) {
                saleTotal = saleTotal.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            }
        }
        readTotal = readTotal.setScale(2, RoundingMode.HALF_UP);
        saleTotal = saleTotal.setScale(2, RoundingMode.HALF_UP);
        if (r.printedTotal() != null && differ(readTotal, r.printedTotal())) {
            warnings.add(new SalesReportWarning("TOTAL_MISMATCH", "La suma de los renglones (" + ChatService.money(readTotal)
                    + ") no da el total impreso (" + ChatService.money(r.printedTotal())
                    + "): revisá la lectura; puede ser por descuentos o propinas.", null));
        }
        String number = r.reportNumber() == null || r.reportNumber().isBlank() ? null : r.reportNumber().trim();
        if (number != null) {
            saleRepository.findActiveByReportNumber(restaurantId, number).ifPresent(s -> warnings.add(new SalesReportWarning(
                    "ALREADY_LOADED", "Ese cierre ya está cargado (venta del " + day(s.getSoldAt()).format(DAY) + ").",
                    s.getId())));
        }
        if (lines.stream().anyMatch(l -> !l.warnings().isEmpty())) {
            warnings.add(new SalesReportWarning("LINES_TO_REVIEW", "Hay renglones para revisar.", null));
        }
        List<Sale> sameDay = salesOf(restaurantId, soldOn);
        ExistingSales existing = sameDay.isEmpty() ? null : new ExistingSales(sameDay.size(),
                sameDay.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        SalesReportPreview preview = new SalesReportPreview(number, soldOn, lines, r.printedTotal(), readTotal, saleTotal,
                existing, warnings);
        long ignored = lines.stream().filter(l -> IGNORED.equals(l.matchedBy())).count();
        String message = "Leí el cierre" + (number == null ? "" : " Z " + number) + " del " + soldOn.format(DAY) + ": "
                + lines.size() + (lines.size() == 1 ? " renglón" : " renglones")
                + (r.printedTotal() == null ? "" : ", total " + ChatService.money(r.printedTotal()))
                + (ignored == 0 ? "" : " (" + ignored + " que no " + (ignored == 1 ? "es un plato" : "son platos") + ", ignorados)")
                + ". Revisalo y confirmalo."
                + (existing == null ? "" : " Ese día ya tiene " + existing.count() + (existing.count() == 1 ? " venta" : " ventas")
                        + " por " + ChatService.money(existing.total()) + ": elegí si " + (existing.count() == 1 ? "la" : "las")
                        + " reemplazo o le sumo el cierre.")
                + (warnings.isEmpty() ? "" : " Ojo: " + String.join(" ", warnings.stream().map(SalesReportWarning::message).toList()));
        return new ChatResult(ChatStatus.NEEDS_CONFIRMATION, message, List.of(), List.of(), List.of(), List.of(), null,
                null, preview);
    }

    static final String ALIAS = "ALIAS";
    static final String CATALOG = "CATALOG";
    static final String IGNORED = "IGNORED";

    /** Un renglón: qué plato es, la cantidad entera y el precio cobrado (el del ticket, no el de la carta). */
    private static SalesReportLine line(SalesReportLineReading l, ChatCatalog catalog, Map<String, MenuItemAlias> aliases) {
        MenuItemAlias alias = l.printedText() == null ? null : aliases.get(CatalogImportService.key(l.printedText()));
        if (alias != null && alias.ignored()) {
            return new SalesReportLine(l.printedText(), null, null, IGNORED, whole(l.quantity()), l.unitPrice(),
                    l.lineTotal(), null, false, List.of());
        }
        MenuItem item = alias == null ? null : catalog.menuItem(alias.menuItemId());
        String matchedBy = item != null ? ALIAS : null;
        if (item == null && l.catalogName() != null) {
            item = catalog.menuItemNamed(l.catalogName());
            if (item == null) {
                String name = CatalogSpreadsheetService.singularMatch(l.catalogName(),
                        catalog.menuItems().stream().map(MenuItem::getName).toList());
                item = name == null ? null : catalog.menuItemNamed(name);
            }
            if (item != null) matchedBy = CATALOG;
        }

        List<String> warnings = new ArrayList<>();
        BigDecimal printedQty = l.quantity();
        if (printedQty == null && l.unitPrice() != null && l.unitPrice().signum() > 0 && l.lineTotal() != null) {
            printedQty = l.lineTotal().divide(l.unitPrice(), 3, RoundingMode.HALF_UP);
        }
        Integer quantity = whole(printedQty);
        if (quantity == null) {
            warnings.add("INVALID_QUANTITY: no leí una cantidad entera de platos; cargala.");
        }
        BigDecimal unitPrice = l.unitPrice();
        if (unitPrice == null && l.lineTotal() != null && quantity != null) {
            unitPrice = l.lineTotal().divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }
        if (unitPrice == null) {
            warnings.add("PRICE_NOT_READ: no leí el precio; cargalo.");
        } else if (l.quantity() != null && l.lineTotal() != null && differ(l.quantity().multiply(unitPrice), l.lineTotal())) {
            warnings.add("LINE_TOTAL_MISMATCH: " + l.quantity().stripTrailingZeros().toPlainString() + " × "
                    + ChatService.money(unitPrice) + " no da " + ChatService.money(l.lineTotal()) + ": revisá la lectura.");
        }
        if (item == null) {
            warnings.add("NOT_ON_MENU: no es un plato del menú; elegí cuál es o marcalo como que no es un plato.");
        }
        BigDecimal menuPrice = item == null ? null : item.getPrice();
        boolean priceDiffers = menuPrice != null && unitPrice != null && menuPrice.compareTo(unitPrice) != 0;
        return new SalesReportLine(l.printedText(), item == null ? null : item.getId(), item == null ? null : item.getName(),
                matchedBy, quantity, unitPrice, l.lineTotal(), menuPrice, priceDiffers, warnings);
    }

    /** La cantidad, si es un entero positivo; si no, null. */
    private static Integer whole(BigDecimal q) {
        if (q == null || q.signum() <= 0 || q.stripTrailingZeros().scale() > 0) return null;
        try {
            return q.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static boolean differ(BigDecimal a, BigDecimal b) {
        BigDecimal diff = a.subtract(b).abs();
        return diff.compareTo(BigDecimal.ONE) > 0 && diff.compareTo(b.abs().multiply(TOLERANCE)) > 0;
    }

    // ── Confirmar ──────────────────────────────────────────────────────────────

    /**
     * La vista previa, tal cual o editada. Todo en una transacción: revierte las ventas que ya tenía ese día si se eligió
     * reemplazarlas (con SALES_DELETE; todo o nada), registra una venta con todos los platos al precio del ticket y con
     * la fecha del cierre, y aprende lo que imprime el POS (también lo que no es un plato). Queda en la conversación y
     * con su deshacer, como cualquier venta del chat.
     */
    public ChatResult confirmChatSalesReport(SalesReportConfirmation c, Set<Permission> permissions) {
        for (ConfirmedSalesLine l : c.lines()) {
            if (!l.ignore() && (l.menuItemId() == null || l.quantity() == null || l.unitPrice() == null)) {
                throw AppException.badRequest(ErrorCode.VALIDATION_ERROR,
                        "Each line needs a menu item, a quantity and a price, or ignore: true");
            }
        }
        UUID restaurantId = TenantContext.require();
        LocalDate soldOn = c.soldOn() == null || c.soldOn().isAfter(today()) ? today() : c.soldOn();
        List<Sale> sameDay = salesOf(restaurantId, soldOn);
        if (!sameDay.isEmpty() && c.existingSales() == null) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR,
                    "That day already has sales: existingSales must be REVERSE or ADD");
        }
        ChatResult result;
        if (!sameDay.isEmpty() && c.existingSales() == ExistingSalesChoice.REVERSE
                && !permissions.contains(Permission.SALES_DELETE)) {
            result = new ChatResult(ChatStatus.NOT_REGISTERED, ChatService.NOTHING_REGISTERED
                    + ": no tenés permiso para revertir ventas, así que no reemplacé las de ese día.", List.of(), List.of(),
                    List.of(new Skipped(ChatOperationType.SALE, null, ErrorCode.CHAT_PERMISSION_MISSING,
                            "No tenés permiso para revertir ventas.")));
        } else {
            try {
                List<Sale> toReverse = c.existingSales() == ExistingSalesChoice.REVERSE ? sameDay : List.of();
                result = transactions.execute(status -> register(c, soldOn, toReverse));
            } catch (AppException e) {
                Skipped why = ChatService.rejected(ChatOperationType.SALE, "el cierre", e);
                result = new ChatResult(ChatStatus.NOT_REGISTERED, ChatService.NOTHING_REGISTERED + ": " + why.message(),
                        List.of(), List.of(), List.of(why));
            }
        }
        chatHistoryService.rememberChatExchange(Kind.CONFIRMATION, null, result);
        return result;
    }

    private ChatResult register(SalesReportConfirmation c, LocalDate soldOn, List<Sale> toReverse) {
        UUID restaurantId = TenantContext.require();
        List<SaleService.SaleItemCommand> items = c.lines().stream().filter(l -> !l.ignore())
                .map(l -> new SaleService.SaleItemCommand(l.menuItemId(), l.quantity(), l.unitPrice())).toList();
        if (items.isEmpty()) {
            return ChatResult.of(ChatStatus.NOT_REGISTERED, ChatService.NOTHING_REGISTERED
                    + ": no quedó ningún plato para registrar.");
        }
        toReverse.forEach(s -> saleService.reverseSale(s.getId()));
        String number = c.reportNumber() == null || c.reportNumber().isBlank() ? null : c.reportNumber().trim();
        String what = "cierre" + (number == null ? "" : " Z " + number) + " del " + soldOn.format(DAY);
        Sale sale = saleService.recordSale(new SaleService.CreateSaleCommand(
                "Cierre" + (number == null ? " de caja" : " Z " + number), soldAt(soldOn), items, number));
        for (ConfirmedSalesLine l : c.lines()) {
            if (l.printedText() != null && !l.printedText().isBlank()) {
                menuItemAliasRepository.upsert(new MenuItemAlias(null, restaurantId, CatalogImportService.key(l.printedText()),
                        l.ignore() ? null : l.menuItemId()));
            }
        }

        int dishes = items.stream().mapToInt(SaleService.SaleItemCommand::quantity).sum();
        String summary = "Venta: " + what + " (" + dishes + (dishes == 1 ? " plato" : " platos") + ") por "
                + ChatService.money(sale.getTotalAmount());
        ChatAction action = chatActionRepository.save(ChatAction.builder().restaurantId(restaurantId)
                .userId(currentUserProvider.currentUserId()).type(ChatOperationType.SALE).entityId(sale.getId())
                .summary(summary.length() > 500 ? summary.substring(0, 499) + "…" : summary).createdAt(clock.instant()).build());
        String message = "Listo: " + summary + "." + (toReverse.isEmpty() ? "" : " Revertí "
                + (toReverse.size() == 1 ? "la venta que ya había" : "las " + toReverse.size() + " ventas que ya había")
                + " ese día.");
        return new ChatResult(ChatStatus.REGISTERED, message, List.of(action), List.of(), List.of());
    }

    /** El mediodía del día del servicio; si es hoy y todavía no es mediodía, ahora (una venta no va al futuro). */
    private Instant soldAt(LocalDate soldOn) {
        Instant noon = soldOn.atTime(SALE_TIME).atZone(ChefControlTime.DEFAULT_ZONE).toInstant();
        return noon.isAfter(clock.instant()) ? clock.instant() : noon;
    }

    private List<Sale> salesOf(UUID restaurantId, LocalDate day) {
        return saleRepository.findActiveByRestaurantIdAndSoldAtBetween(restaurantId,
                day.atStartOfDay(ChefControlTime.DEFAULT_ZONE).toInstant(),
                day.plusDays(1).atStartOfDay(ChefControlTime.DEFAULT_ZONE).toInstant().minusNanos(1));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE));
    }

    private static LocalDate day(Instant at) {
        return at.atZone(ChefControlTime.DEFAULT_ZONE).toLocalDate();
    }

    // ── Tipos ──────────────────────────────────────────────────────────────────

    /**
     * Lo que se leyó, ya resuelto.
     *
     * @param soldOn     el día del servicio (un cierre de la madrugada va al día anterior, con AFTER_MIDNIGHT)
     * @param readTotal  la suma de todos los renglones leídos, para compararla con el total impreso
     * @param saleTotal  lo que se registraría: los renglones con plato, al precio del ticket
     * @param existingSales las ventas que ya tiene ese día; null si no tiene
     */
    public record SalesReportPreview(String reportNumber, LocalDate soldOn, List<SalesReportLine> lines,
                                     BigDecimal printedTotal, BigDecimal readTotal, BigDecimal saleTotal,
                                     ExistingSales existingSales, List<SalesReportWarning> warnings) {}

    /**
     * @param matchedBy    ALIAS (lo aprendido del POS), CATALOG (el plato que sugirió el modelo) o IGNORED (marcado como
     *                     que no es un plato: no se vende); null si no es un plato del menú (NOT_ON_MENU)
     * @param unitPrice    el precio cobrado, el del ticket
     * @param priceDiffers el precio del ticket no es el de la carta
     * @param warnings     cada uno empieza con su código: INVALID_QUANTITY, PRICE_NOT_READ, LINE_TOTAL_MISMATCH, NOT_ON_MENU
     */
    public record SalesReportLine(String printedText, UUID menuItemId, String menuItemName, String matchedBy,
                                  Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal, BigDecimal menuPrice,
                                  boolean priceDiffers, List<String> warnings) {}

    public record ExistingSales(int count, BigDecimal total) {}

    /** @param saleId en ALREADY_LOADED, la venta de ese cierre */
    public record SalesReportWarning(String code, String message, UUID saleId) {}

    public enum ExistingSalesChoice { REVERSE, ADD }

    /** @param existingSales qué hacer con las ventas que ya tiene ese día; obligatorio si tiene */
    public record SalesReportConfirmation(String reportNumber, LocalDate soldOn, ExistingSalesChoice existingSales,
                                          List<ConfirmedSalesLine> lines) {}

    /** @param ignore no es un plato: no se vende y el próximo cierre lo ignora solo */
    public record ConfirmedSalesLine(String printedText, UUID menuItemId, Integer quantity, BigDecimal unitPrice,
                                     boolean ignore) {}
}
