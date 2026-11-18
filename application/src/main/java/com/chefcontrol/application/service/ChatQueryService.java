package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.InterpretedItem;
import com.chefcontrol.application.port.AIService.InterpretedOperation;
import com.chefcontrol.application.port.AIService.InterpretedQuery;
import com.chefcontrol.application.service.ChatService.ChatCatalog;
import com.chefcontrol.application.service.ChatService.Skipped;
import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.repository.PurchaseItemRepository;
import com.chefcontrol.domain.repository.PurchaseRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.repository.WasteEventRepository;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.stock.StockBatch;
import com.chefcontrol.domain.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Las consultas del chat (F19, tanda 6). La IA solo elige una de la lista fija y su período con nombre; los números
 * los saca este servicio de los repositorios y servicios de siempre, y el texto lo arma acá: el modelo nunca da un
 * número ni una fecha, y nunca hay SQL libre. Cada consulta pide el permiso de ver su pantalla (y el food cost, su
 * plan); sin él va a skipped, como una operación.
 */
@Service
@RequiredArgsConstructor
public class ChatQueryService {

    /** "¿Qué vence?" sin fecha: la semana que viene. Las alertas avisan a 3 días. */
    static final int EXPIRATIONS_DEFAULT_DAYS = 7;
    /** Un período no puede pasar de un año: es una pregunta de chat, no un reporte. */
    static final int MAX_PERIOD_DAYS = 366;
    private static final int MAX_LISTED = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final String UNSUPPORTED = "Esa consulta todavía no la respondo. Puedo decirte el stock de un insumo, "
            + "qué vence, cuánto se vendió, se compró o se tiró en un período, a cuánto se compró un insumo, el food cost "
            + "y las alertas abiertas.";

    private final StockService stockService;
    private final StockBatchRepository stockBatchRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final WasteEventRepository wasteEventRepository;
    private final FoodCostService foodCostService;
    private final AlertRepository alertRepository;
    private final AccountService accountService;
    private final Clock clock;

    /** La lista fija, cada una con el permiso de ver su pantalla y, si la tiene, la feature de su plan. */
    enum Query {
        STOCK(Permission.PRODUCTS_VIEW, null, "el stock"),
        EXPIRATIONS(Permission.PRODUCTS_VIEW, null, "los vencimientos"),
        SALES(Permission.SALES_VIEW, null, "las ventas"),
        PURCHASES(Permission.PURCHASES_VIEW, null, "las compras"),
        WASTE(Permission.WASTE_VIEW, null, "las mermas"),
        LAST_PURCHASE_PRICE(Permission.PURCHASES_VIEW, null, "las compras"),
        FOOD_COST(Permission.FOOD_COST_VIEW, Feature.RECIPES_AND_FOOD_COST, "el food cost"),
        OPEN_ALERTS(Permission.ALERTS_VIEW, null, "las alertas");

        final Permission permission;
        final Feature feature;
        final String what;

        Query(Permission permission, Feature feature, String what) {
            this.permission = permission;
            this.feature = feature;
            this.what = what;
        }

        static Query of(String name) {
            if (name == null) return null;
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Las respuestas, en el orden de las consultas; lo que no se pudo responder va a {@code skipped}. */
    List<String> answerChatQueries(List<InterpretedOperation> queries, ChatCatalog catalog, Set<Permission> permissions,
                                   List<Skipped> skipped) {
        List<String> answers = new ArrayList<>();
        for (InterpretedOperation op : queries) {
            String answer = answer(op, catalog, permissions, skipped);
            if (answer != null) answers.add(answer);
        }
        return answers;
    }

    private String answer(InterpretedOperation op, ChatCatalog catalog, Set<Permission> permissions, List<Skipped> skipped) {
        InterpretedQuery q = op.query();
        Query query = q == null ? null : Query.of(q.name());
        if (query == null) {
            skipped.add(new Skipped(null, null, ErrorCode.CHAT_OPERATION_NOT_SUPPORTED, UNSUPPORTED));
            return null;
        }
        if (!permissions.contains(query.permission)) {
            skipped.add(new Skipped(null, null, ErrorCode.CHAT_PERMISSION_MISSING, "No tenés permiso para ver " + query.what + "."));
            return null;
        }
        try {
            if (query.feature != null) accountService.requireFeature(query.feature);
        } catch (AppException e) {
            skipped.add(new Skipped(null, null, e.getErrorCode(), capitalized(query.what) + " no está incluido en tu plan."));
            return null;
        }
        InterpretedItem item = op.items().isEmpty() ? null : op.items().getFirst();
        LocalDate today = LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE));
        try {
            return switch (query) {
                case STOCK -> withProduct(item, catalog, skipped, p -> stock(p, catalog));
                case LAST_PURCHASE_PRICE -> withProduct(item, catalog, skipped, p -> lastPurchasePrice(p, catalog));
                case EXPIRATIONS -> expirations(expirationLimit(q, today), today, catalog);
                case SALES -> sales(period(q, today));
                case PURCHASES -> purchases(period(q, today));
                case WASTE -> waste(period(q, today));
                case FOOD_COST -> item != null && (item.menuItemId() != null || item.name() != null)
                        ? withDish(item, catalog, skipped) : foodCostOf(period(q, today));
                case OPEN_ALERTS -> openAlerts();
            };
        } catch (InvalidPeriod e) {
            skipped.add(new Skipped(null, null, ErrorCode.CHAT_INVALID_PERIOD, e.getMessage()));
            return null;
        } catch (AppException e) {
            skipped.add(new Skipped(null, item == null ? null : item.name(), e.getErrorCode(),
                    e.getErrorCode() == ErrorCode.RECIPE_NOT_FOUND
                            ? "«" + (item == null ? "?" : item.name()) + "» no tiene receta: no tiene food cost."
                            : "No pude responder sobre " + query.what + "."));
            return null;
        }
    }

    // ── Las consultas ──────────────────────────────────────────────────────────

    private String stock(Product p, ChatCatalog catalog) {
        String unit = ChatService.unitLabel(p);
        BigDecimal stock = stockService.getCurrentStock(p.getId());
        if (stock.signum() < 0) {
            return p.getName() + " da " + qty(stock, unit) + ": se registró más salida que entrada. Conviene contarlo.";
        }
        String text = "Hay " + qty(stock, unit) + " de " + p.getName() + ".";
        if (p.getMinStock() != null && stock.compareTo(p.getMinStock()) < 0) {
            text += " Está por debajo del mínimo (" + qty(p.getMinStock(), unit) + ").";
        }
        return text;
    }

    private String lastPurchasePrice(Product p, ChatCatalog catalog) {
        return purchaseItemRepository.findLastActiveLineOfProduct(p.getId(), TenantContext.require())
                .map(line -> {
                    Unit u = catalog.unit(line.unitId());
                    String unit = u == null ? ChatService.unitLabel(p) : u.getAbbreviation();
                    // Un RI que carga sin IVA lee $ 3.000 y no sabe si es neto: se dice.
                    String iva = Boolean.FALSE.equals(line.pricesIncludeIva()) ? " (sin IVA)" : "";
                    return "La última compra de " + p.getName() + ": " + qty(line.quantity(), unit) + " a "
                            + ChatService.money(line.pricePerUnit()) + " el " + unit + iva + ", el " + day(line.purchasedAt()) + ".";
                })
                .orElse("No hay compras de " + p.getName() + " cargadas.");
    }

    private String expirations(LocalDate limit, LocalDate today, ChatCatalog catalog) {
        List<StockBatch> batches = stockBatchRepository.findExpiringByRestaurant(TenantContext.require(), limit);
        if (batches.isEmpty()) return "No vence nada hasta el " + limit.format(DAY) + ".";
        List<String> soon = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        for (StockBatch b : batches) {
            Product p = catalog.product(b.getProductId());
            String line = (p == null ? "Un insumo" : p.getName()) + " " + qty(b.getQuantityRemaining(),
                    p == null ? "" : ChatService.unitLabel(p)) + " (" + b.getExpirationDate().format(DAY) + ")";
            (b.getExpirationDate().isBefore(today) ? expired : soon).add(line);
        }
        List<String> parts = new ArrayList<>();
        if (!soon.isEmpty()) parts.add("Vence hasta el " + limit.format(DAY) + ": " + listed(soon) + ".");
        if (!expired.isEmpty()) parts.add("Ya vencido, con stock: " + listed(expired) + ".");
        return String.join(" ", parts);
    }

    private String sales(Period period) {
        UUID restaurantId = TenantContext.require();
        long count = saleRepository.countByRestaurantIdAndSoldAtBetween(restaurantId, period.start(), period.end());
        if (count == 0) return period.label() + " no hubo ventas.";
        BigDecimal total = saleRepository.sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, period.start(), period.end());
        return period.label() + " vendiste " + ChatService.money(total) + " en " + count + (count == 1 ? " venta." : " ventas.");
    }

    private String purchases(Period period) {
        UUID restaurantId = TenantContext.require();
        long count = purchaseRepository.countByRestaurantIdAndPurchasedAtBetween(restaurantId, period.start(), period.end());
        if (count == 0) return period.label() + " no hubo compras.";
        BigDecimal total = purchaseRepository.sumTotalByRestaurantIdAndPurchasedAtBetween(restaurantId, period.start(), period.end());
        return period.label() + " compraste " + ChatService.money(total) + " en " + count + (count == 1 ? " compra." : " compras.");
    }

    /** La misma suma del tablero: sin las mermas deshechas. */
    private String waste(Period period) {
        UUID restaurantId = TenantContext.require();
        long count = wasteEventRepository.countByRestaurantIdAndCreatedAtBetween(restaurantId, period.start(), period.end());
        if (count == 0) return period.label() + " no hubo mermas.";
        BigDecimal cost = wasteEventRepository.sumCostByRestaurantIdAndCreatedAtBetween(restaurantId, period.start(), period.end());
        return period.label() + " hubo " + count + (count == 1 ? " merma" : " mermas") + " por " + ChatService.money(cost) + ".";
    }

    private String foodCostOf(Period period) {
        var r = foodCostService.calculate(period.start(), period.end());
        if (r.netRevenue().signum() == 0) return period.label() + " no hubo ventas: no hay food cost.";
        boolean net = r.netRevenue().compareTo(r.revenue()) != 0;
        return period.label() + " el food cost fue " + percent(r.foodCostPercentage()) + ": "
                + ChatService.money(r.theoreticalCost()) + " de costo sobre " + ChatService.money(r.netRevenue())
                + (net ? " vendidos sin IVA." : " vendidos.");
    }

    private String withDish(InterpretedItem item, ChatCatalog catalog, List<Skipped> skipped) {
        MenuItem dish = item.menuItemId() == null ? null : catalog.menuItem(item.menuItemId());
        if (dish == null && item.name() != null) dish = catalog.menuItemNamed(item.name());
        if (dish == null) {
            skipped.add(new Skipped(null, item.name(), ErrorCode.CHAT_MENU_ITEM_NOT_IN_CATALOG,
                    "«" + (item.name() == null ? "?" : item.name()) + "» no es un plato del menú."));
            return null;
        }
        var r = foodCostService.calculateRecipeCost(dish.getId());
        if (r.foodCostPercentage() == null || r.menuPrice() == null || r.menuPrice().signum() == 0) {
            return "«" + dish.getName() + "» cuesta " + ChatService.money(r.costPerServing()) + " por porción, pero no tiene "
                    + "precio: no hay food cost.";
        }
        boolean net = r.netPrice() != null && r.netPrice().compareTo(r.menuPrice()) != 0;
        return "El food cost de " + dish.getName() + " es " + percent(r.foodCostPercentage()) + ": "
                + ChatService.money(r.costPerServing()) + " por porción sobre " + ChatService.money(net ? r.netPrice() : r.menuPrice())
                + (net ? " sin IVA (precio " + ChatService.money(r.menuPrice()) + ")." : ".");
    }

    private String openAlerts() {
        var page = alertRepository.findByRestaurantIdAndResolvedAtIsNullOrderByCreatedAtDesc(
                TenantContext.require(), PageRequest.of(0, MAX_LISTED));
        if (page.totalElements() == 0) return "No hay alertas abiertas.";
        String listed = page.content().stream().map(Alert::getMessage).collect(Collectors.joining("; "));
        long more = page.totalElements() - page.content().size();
        return (page.totalElements() == 1 ? "Hay 1 alerta abierta: " : "Hay " + page.totalElements() + " alertas abiertas: ")
                + listed + (more > 0 ? "; y " + more + " más." : ".");
    }

    // ── Insumo y plato: los que nombra la consulta, del catálogo ───────────────

    private interface ProductAnswer {
        String answer(Product p);
    }

    private static String withProduct(InterpretedItem item, ChatCatalog catalog, List<Skipped> skipped, ProductAnswer answer) {
        Product p = item == null || item.productId() == null ? null : catalog.product(item.productId());
        if (p == null && item != null && item.name() != null) p = catalog.productNamed(item.name());
        if (p == null) {
            String name = item == null ? null : item.name();
            skipped.add(new Skipped(null, name, ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG,
                    "«" + (name == null ? "?" : name) + "» no está en el catálogo."));
            return null;
        }
        return answer.answer(p);
    }

    // ── El período: con nombre, y las fechas las calcula el back ───────────────

    /** Días argentinos completos, extremos incluidos (como /iva). */
    record Period(LocalDate from, LocalDate to, String label) {
        Instant start() {
            return from.atStartOfDay(ChefControlTime.DEFAULT_ZONE).toInstant();
        }

        Instant end() {
            return to.plusDays(1).atStartOfDay(ChefControlTime.DEFAULT_ZONE).toInstant().minusMillis(1);
        }
    }

    static final class InvalidPeriod extends RuntimeException {
        InvalidPeriod(String message) {
            super(message);
        }
    }

    /**
     * El período que nombró el modelo, pasado a días. Semana de lunes a domingo; "esta semana" y "este mes" van hasta hoy.
     * Sin período, hoy. Nada en el futuro, desde ≤ hasta, a lo sumo un año.
     */
    static Period period(InterpretedQuery q, LocalDate today) {
        String name = q.period() == null ? "today" : q.period().trim().toLowerCase(Locale.ROOT);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Period p = switch (name) {
            case "today" -> new Period(today, today, "Hoy");
            case "yesterday" -> new Period(today.minusDays(1), today.minusDays(1), "Ayer");
            case "this_week" -> new Period(monday, today, "Esta semana");
            case "last_week" -> new Period(monday.minusWeeks(1), monday.minusDays(1), "La semana pasada");
            case "this_month" -> new Period(today.withDayOfMonth(1), today, "Este mes");
            // Para adelante: el modelo los nombra y la validación de abajo los rechaza ("¿cuánto vendimos mañana?").
            case "tomorrow" -> new Period(today.plusDays(1), today.plusDays(1), "Mañana");
            case "next_week" -> new Period(monday.plusWeeks(1), monday.plusWeeks(1).plusDays(6), "La semana que viene");
            case "next_month" -> {
                LocalDate first = today.withDayOfMonth(1).plusMonths(1);
                yield new Period(first, first.with(TemporalAdjusters.lastDayOfMonth()), "El mes que viene");
            }
            case "last_month" -> {
                LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                yield new Period(first, first.with(TemporalAdjusters.lastDayOfMonth()), "El mes pasado");
            }
            case "last_n_days" -> {
                if (q.days() == null || q.days() < 1 || q.days() > MAX_PERIOD_DAYS) {
                    throw new InvalidPeriod("Decime de cuántos días (hasta un año).");
                }
                yield new Period(today.minusDays(q.days() - 1), today,
                        q.days() == 1 ? "Hoy" : "En los últimos " + q.days() + " días");
            }
            case "range" -> {
                if (q.from() == null) throw new InvalidPeriod("No entendí las fechas.");
                LocalDate to = q.to() == null ? q.from() : q.to();
                yield new Period(q.from(), to, q.from().equals(to) ? "El " + q.from().format(DAY)
                        : "Del " + q.from().format(DAY) + " al " + to.format(DAY));
            }
            default -> throw new InvalidPeriod("No entendí el período.");
        };
        if (p.from().isAfter(p.to())) throw new InvalidPeriod("El período empieza después de terminar.");
        if (p.to().isAfter(today)) throw new InvalidPeriod("No hay datos del futuro: el período termina después de hoy.");
        if (ChronoUnit.DAYS.between(p.from(), p.to()) + 1 > MAX_PERIOD_DAYS) {
            throw new InvalidPeriod("El período es de más de un año: pedilo más corto.");
        }
        return p;
    }

    /** Hasta cuándo mirar los vencimientos: hacia adelante. Sin período, los próximos 7 días. */
    static LocalDate expirationLimit(InterpretedQuery q, LocalDate today) {
        String name = q.period() == null ? "" : q.period().trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "today" -> today;
            case "tomorrow" -> today.plusDays(1);
            case "this_week" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case "next_week" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(1);
            case "next_month" -> today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            case "this_month" -> today.with(TemporalAdjusters.lastDayOfMonth());
            case "last_n_days" -> q.days() == null || q.days() < 1 ? today.plusDays(EXPIRATIONS_DEFAULT_DAYS)
                    : today.plusDays(Math.min(q.days(), MAX_PERIOD_DAYS));
            case "range" -> q.to() != null ? q.to() : q.from() != null ? q.from() : today.plusDays(EXPIRATIONS_DEFAULT_DAYS);
            default -> today.plusDays(EXPIRATIONS_DEFAULT_DAYS);
        };
    }

    // ── Texto ──────────────────────────────────────────────────────────────────

    private static String qty(BigDecimal n, String unit) {
        return ChatService.num(n) + (unit == null || unit.isBlank() ? "" : " " + unit);
    }

    private static String percent(BigDecimal p) {
        return ChatService.num(p) + "%";
    }

    private static String day(Instant at) {
        return at.atZone(ChefControlTime.DEFAULT_ZONE).toLocalDate().format(DAY);
    }

    private static String listed(List<String> lines) {
        return lines.size() <= MAX_LISTED ? String.join("; ", lines)
                : String.join("; ", lines.subList(0, MAX_LISTED)) + "; y " + (lines.size() - MAX_LISTED) + " más";
    }

    private static String capitalized(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
