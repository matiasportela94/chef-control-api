package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.ChatCatalogMenuItem;
import com.chefcontrol.application.port.AIService.ChatCatalogProduct;
import com.chefcontrol.application.port.AIService.ChatInterpretation;
import com.chefcontrol.application.port.AIService.InterpretedItem;
import com.chefcontrol.application.port.AIService.InterpretedOperation;
import com.chefcontrol.application.port.AIService.UnitOption;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatActionRepository;
import com.chefcontrol.domain.chat.ChatOperationType;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.waste.WasteReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * El chat de la IA (F19): interpreta un mensaje y decide, en un solo lugar, qué se registra en el acto, qué se pregunta
 * y qué no se puede. Lo usa la Entrada rápida y lo va a usar WhatsApp (F20), que no tiene front: la regla no puede
 * vivir en otro lado.
 *
 * <ul>
 *   <li>Lo operativo se registra en el acto, con deshacer ({@link ChatAction}): el ledger lo revierte.</li>
 *   <li>Se pregunta antes (NEEDS_CONFIRMATION) si la IA duda, si la confianza es menor a 80 o si una compra de más
 *       de una unidad trae un precio unitario de más de $100.000 (puede ser el total). Lo aplica el back también:
 *       no alcanza con que lo diga el modelo.</li>
 *   <li>Cada operación pide el permiso de su pantalla. Sin él no se registra y la respuesta lo dice: un mensaje con
 *       una compra y una merma, de alguien que solo puede cargar mermas, registra la merma.</li>
 *   <li>Las cantidades llegan como las dijo el usuario y se convierten acá a la unidad del insumo.</li>
 *   <li>Cada operación va en su propia transacción, con su fila de chat_actions: si falla la segunda, la primera
 *       queda registrada y deshacible.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** La producción llega en la tanda siguiente. */
    static final Set<ChatOperationType> SUPPORTED = EnumSet.of(ChatOperationType.PURCHASE, ChatOperationType.WASTE,
            ChatOperationType.SALE, ChatOperationType.STOCK_ADJUSTMENT);

    static final int MIN_CONFIDENCE = 80;
    /** La regla de la confirmación por precio alto: cantidad mayor a 1 y precio unitario mayor a esto. */
    static final BigDecimal HIGH_UNIT_PRICE = new BigDecimal("100000");
    private static final int MAX_CATALOG = 500;
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private final AIService aiService;
    private final ProductRepository productRepository;
    private final MenuItemRepository menuItemRepository;
    private final UnitRepository unitRepository;
    private final UnitConversionService unitConversionService;
    private final PurchaseService purchaseService;
    private final WasteService wasteService;
    private final SaleService saleService;
    private final StockCountService stockCountService;
    private final StockService stockService;
    private final StockMovementRepository stockMovementRepository;
    private final ChatActionRepository chatActionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactions;
    private final Clock clock;

    // ── Mensaje ────────────────────────────────────────────────────────────────

    /** @param permissions los del usuario en el local activo */
    public ChatResult sendChatMessage(String message, Set<Permission> permissions) {
        UUID restaurantId = TenantContext.require();
        List<Product> products = productRepository
                .findByRestaurantIdAndIsActiveTrue(restaurantId, PageRequest.of(0, MAX_CATALOG)).content();
        List<MenuItem> menuItems = menuItemRepository
                .findByRestaurantIdAndActive(restaurantId, true, PageRequest.of(0, MAX_CATALOG)).content();
        List<Unit> units = unitRepository.findAll();

        ChatInterpretation interpretation;
        try {
            interpretation = aiService.interpretChatMessage(new AIService.ChatMessageRequest(message,
                    LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE)),
                    products.stream().map(p -> new ChatCatalogProduct(p.getId(), p.getName(), unitLabel(p))).toList(),
                    menuItems.stream().map(m -> new ChatCatalogMenuItem(m.getId(), m.getName(), m.getPrice())).toList(),
                    units.stream().map(u -> new UnitOption(u.getName(), u.getAbbreviation())).toList()));
        } catch (AppException e) {
            if (e.getErrorCode() != ErrorCode.AI_INTERPRETATION_FAILED) throw e;
            return ChatResult.of(ChatStatus.NOT_UNDERSTOOD, "No pude interpretar el mensaje. Probá de nuevo.");
        }

        Resolution resolution = new Resolution(products, menuItems, units);
        List<Skipped> skipped = new ArrayList<>();
        List<Operation> operations = new ArrayList<>();
        boolean highPrice = false;
        for (InterpretedOperation op : interpretation.operations()) {
            ChatOperationType type = typeOf(op.type());
            if (type == null || !SUPPORTED.contains(type)) {
                skipped.add(notSupported(type));
                continue;
            }
            if (!permissions.contains(type.createPermission())) {
                skipped.add(permissionMissing(type));
                continue;
            }
            List<OperationItem> items = new ArrayList<>();
            for (InterpretedItem item : op.items()) {
                OperationItem resolved = resolution.resolve(type, item, skipped);
                if (resolved == null) continue;
                items.add(resolved);
                highPrice |= type == ChatOperationType.PURCHASE && isHighPrice(item);
            }
            if (!items.isEmpty()) operations.add(new Operation(type, items));
        }

        if (operations.isEmpty()) return nothingToRegister(interpretation, skipped);

        boolean doubtful = interpretation.needsConfirmation() || interpretation.confidence() < MIN_CONFIDENCE;
        if (doubtful || highPrice) {
            String question = interpretation.needsConfirmation() && interpretation.responseToUser() != null
                    ? interpretation.responseToUser()
                    : highPrice ? "¿El precio es por unidad o el total de la compra?"
                    : "No estoy seguro de haber entendido. ¿Lo registro así?";
            return new ChatResult(ChatStatus.NEEDS_CONFIRMATION, question, List.of(), operations, skipped);
        }
        return register(operations, skipped);
    }

    // ── Confirmar ──────────────────────────────────────────────────────────────

    /**
     * Registra una propuesta (la {@code proposal} de un NEEDS_CONFIRMATION, tal cual o editada). La cantidad ya viene en
     * la unidad del insumo. Viene del cliente: se revalida todo como si fuera nueva.
     */
    public ChatResult confirmChatOperations(List<Operation> operations, Set<Permission> permissions) {
        UUID restaurantId = TenantContext.require();
        List<Skipped> skipped = new ArrayList<>();
        List<Operation> valid = new ArrayList<>();
        for (Operation op : operations) {
            if (op.type() == null || !SUPPORTED.contains(op.type())) {
                skipped.add(notSupported(op.type()));
                continue;
            }
            if (!permissions.contains(op.type().createPermission())) {
                skipped.add(permissionMissing(op.type()));
                continue;
            }
            List<OperationItem> items = new ArrayList<>();
            for (OperationItem item : op.items()) {
                OperationItem checked = op.type() == ChatOperationType.SALE
                        ? confirmedSaleItem(item, restaurantId, skipped)
                        : confirmedProductItem(op.type(), item, restaurantId, skipped);
                if (checked != null) items.add(checked);
            }
            if (!items.isEmpty()) valid.add(new Operation(op.type(), items));
        }
        if (valid.isEmpty()) {
            return new ChatResult(ChatStatus.NOT_REGISTERED, nothingRegistered(skipped), List.of(), List.of(), skipped);
        }
        return register(valid, skipped);
    }

    private OperationItem confirmedSaleItem(OperationItem item, UUID restaurantId, List<Skipped> skipped) {
        MenuItem dish = item.menuItemId() == null ? null : menuItemRepository
                .findByIdAndRestaurantId(item.menuItemId(), restaurantId).filter(MenuItem::isActive).orElse(null);
        if (dish == null) {
            skipped.add(skip(ChatOperationType.SALE, item.name(), ErrorCode.CHAT_MENU_ITEM_NOT_IN_CATALOG));
            return null;
        }
        if (!isPortions(item.quantity())) {
            skipped.add(skip(ChatOperationType.SALE, dish.getName(), ErrorCode.CHAT_INVALID_QUANTITY));
            return null;
        }
        return saleItem(dish, item.quantity());
    }

    private OperationItem confirmedProductItem(ChatOperationType type, OperationItem item, UUID restaurantId,
                                               List<Skipped> skipped) {
        Product product = item.productId() == null ? null : productRepository
                .findByIdAndRestaurantId(item.productId(), restaurantId).filter(Product::isActive).orElse(null);
        if (product == null) {
            skipped.add(skip(type, item.name(), ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG));
        } else if (!validQuantity(type, item.quantity())) {
            skipped.add(skip(type, product.getName(), ErrorCode.CHAT_INVALID_QUANTITY));
        } else if (type == ChatOperationType.PURCHASE && !positive(item.pricePerUnit())) {
            skipped.add(skip(type, product.getName(), ErrorCode.CHAT_MISSING_PRICE));
        } else {
            return new OperationItem(product.getId(), null, product.getName(), item.quantity(), unitLabel(product),
                    type == ChatOperationType.PURCHASE ? item.pricePerUnit() : null,
                    type == ChatOperationType.PURCHASE ? item.expirationDate() : null,
                    type == ChatOperationType.WASTE ? orOther(item.wasteReason()) : null);
        }
        return null;
    }

    // ── Deshacer ───────────────────────────────────────────────────────────────

    /**
     * Revierte lo que registró el chat con los métodos de siempre, así que queda auditado como cualquier reversión.
     * Quien lo cargó puede durante 15 minutos sin el permiso de borrar; después, o lo de otro, con ese permiso.
     */
    public ChatAction undoChatAction(UUID actionId, Set<Permission> permissions) {
        UUID restaurantId = TenantContext.require();
        ChatAction action = chatActionRepository.findByIdAndRestaurantId(actionId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.CHAT_ACTION_NOT_FOUND, "Chat action not found"));
        if (action.isUndone()) {
            throw AppException.conflict(ErrorCode.CHAT_ACTION_ALREADY_UNDONE, "Chat action already undone");
        }
        Instant now = clock.instant();
        if (!action.undoableWithoutPermissionBy(currentUserProvider.currentUserId(), now)
                && !permissions.contains(action.getType().undoPermission())) {
            throw AppException.forbidden(ErrorCode.CHAT_UNDO_NOT_ALLOWED,
                    "Undoing this needs " + action.getType().undoPermission());
        }
        return transactions.execute(status -> {
            switch (action.getType()) {
                case PURCHASE -> purchaseService.reversePurchase(action.getEntityId());
                case WASTE -> stockService.reverseMovement(wasteMovementOf(action.getEntityId()).getId());
                case SALE -> saleService.reverseSale(action.getEntityId());
                // Un conteo sin diferencias no movió nada: no hay qué revertir, pero queda deshecho igual.
                case STOCK_ADJUSTMENT -> stockMovementRepository
                        .findByReferenceIdAndReferenceType(action.getEntityId(), "stock_count").stream()
                        .filter(m -> m.getType() != MovementType.REVERSAL && m.getReversedBy() == null)
                        .forEach(m -> stockService.reverseMovement(m.getId()));
                default -> throw AppException.badRequest(ErrorCode.CHAT_OPERATION_NOT_SUPPORTED,
                        "Undo of " + action.getType() + " is not supported yet");
            }
            action.markUndone(now);
            return chatActionRepository.save(action);
        });
    }

    private StockMovement wasteMovementOf(UUID wasteEventId) {
        return stockMovementRepository.findByReferenceIdAndReferenceType(wasteEventId, "waste_event").stream()
                .filter(m -> m.getType() == MovementType.WASTE)
                .findFirst()
                .orElseThrow(() -> AppException.notFound(ErrorCode.MOVEMENT_NOT_FOUND, "Waste movement not found"));
    }

    // ── Registrar ──────────────────────────────────────────────────────────────

    private ChatResult register(List<Operation> operations, List<Skipped> skipped) {
        List<ChatAction> actions = new ArrayList<>();
        for (Operation op : operations) {
            switch (op.type()) {
                case PURCHASE -> inOwnTransaction(op.type(), names(op.items()), skipped, () -> {
                    var purchase = purchaseService.createPurchase(new PurchaseService.CreatePurchaseCommand(
                            null, null, clock.instant(), null, op.items().stream().map(this::purchaseItem).toList()));
                    actions.add(saveAction(ChatOperationType.PURCHASE, purchase.getId(), purchaseSummary(op.items())));
                });
                // Una acción por merma: cada waste_event es de un insumo, y así se deshace solo la equivocada.
                case WASTE -> op.items().forEach(item -> inOwnTransaction(op.type(), item.name(), skipped, () -> {
                    var event = wasteService.createWasteEvent(new WasteService.CreateWasteEventCommand(
                            item.productId(), defaultUnitOf(item.productId()), item.quantity(), item.wasteReason()));
                    actions.add(saveAction(ChatOperationType.WASTE, event.getId(), wasteSummary(item)));
                }));
                // Una venta con todos los platos, como el formulario. Descuenta con la receta (y produce lo que falte).
                case SALE -> inOwnTransaction(op.type(), names(op.items()), skipped, () -> {
                    var sale = saleService.recordSale(new SaleService.CreateSaleCommand(null, clock.instant(),
                            op.items().stream().map(i -> new SaleService.SaleItemCommand(
                                    i.menuItemId(), i.quantity().intValueExact())).toList()));
                    actions.add(saveAction(ChatOperationType.SALE, sale.getId(), saleSummary(op.items())));
                });
                // Un conteo de solo esos insumos: "quedan 8 kg de harina" ajusta la harina y nada más.
                case STOCK_ADJUSTMENT -> inOwnTransaction(op.type(), names(op.items()), skipped, () -> {
                    var count = stockCountService.createStockCount(new StockCountService.CreateStockCountCommand(null,
                            op.items().stream().map(i -> new StockCountService.StockCountItemCommand(
                                    i.productId(), defaultUnitOf(i.productId()), i.quantity())).toList()));
                    actions.add(saveAction(ChatOperationType.STOCK_ADJUSTMENT, count.count().getId(),
                            adjustmentSummary(op.items())));
                });
                default -> skipped.add(notSupported(op.type()));
            }
        }
        if (actions.isEmpty()) {
            return new ChatResult(ChatStatus.NOT_REGISTERED, nothingRegistered(skipped), List.of(), List.of(), skipped);
        }
        String message = "Listo: " + actions.stream().map(ChatAction::getSummary).collect(Collectors.joining("; ")) + ".";
        if (!skipped.isEmpty()) message += " No registré: " + skipMessages(skipped);
        return new ChatResult(ChatStatus.REGISTERED, message, actions, List.of(), skipped);
    }

    /** La operación y su fila de chat_actions juntas: no queda una compra sin deshacer ni un deshacer sin compra. */
    private void inOwnTransaction(ChatOperationType type, String name, List<Skipped> skipped, Runnable work) {
        try {
            transactions.executeWithoutResult(status -> work.run());
        } catch (AppException e) {
            skipped.add(new Skipped(type, name, e.getErrorCode(), "No se pudo registrar " + label(type) + "."));
        } catch (RuntimeException e) {
            log.error("[Chat] {} failed: {}", type, e.getMessage(), e);
            skipped.add(new Skipped(type, name, ErrorCode.INTERNAL_ERROR, "No se pudo registrar " + label(type) + "."));
        }
    }

    private PurchaseService.PurchaseItemCommand purchaseItem(OperationItem item) {
        return new PurchaseService.PurchaseItemCommand(item.productId(), defaultUnitOf(item.productId()),
                item.quantity(), item.pricePerUnit(), item.expirationDate(), null);
    }

    private UUID defaultUnitOf(UUID productId) {
        return productRepository.findByIdAndRestaurantId(productId, TenantContext.require())
                .map(Product::getDefaultUnitId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));
    }

    private ChatAction saveAction(ChatOperationType type, UUID entityId, String summary) {
        return chatActionRepository.save(ChatAction.builder()
                .restaurantId(TenantContext.require())
                .userId(currentUserProvider.currentUserId())
                .type(type)
                .entityId(entityId)
                .summary(summary.length() > 500 ? summary.substring(0, 499) + "…" : summary)
                .createdAt(clock.instant())
                .build());
    }

    // ── Lo que dijo la IA → lo que se registra ─────────────────────────────────

    /** Los insumos, platos y unidades de un mensaje: lo que la IA nombra se busca acá, nunca se cree. */
    private class Resolution {
        final Map<UUID, Product> products = new HashMap<>();
        final Map<UUID, MenuItem> menuItems = new HashMap<>();
        final Map<String, Unit> units = new HashMap<>();
        final Map<UUID, Unit> unitsById = new HashMap<>();

        Resolution(List<Product> catalog, List<MenuItem> dishes, List<Unit> all) {
            catalog.forEach(p -> products.put(p.getId(), p));
            dishes.forEach(m -> menuItems.put(m.getId(), m));
            all.forEach(u -> {
                unitsById.put(u.getId(), u);
                units.putIfAbsent(CatalogImportService.key(u.getName()), u);
            });
            all.stream().filter(u -> u.getAbbreviation() != null)
                    .forEach(u -> units.putIfAbsent(CatalogImportService.key(u.getAbbreviation()), u));
        }

        /** null (y su motivo en skipped) si el ítem no se puede registrar. */
        OperationItem resolve(ChatOperationType type, InterpretedItem item, List<Skipped> skipped) {
            if (type == ChatOperationType.SALE) return resolveSale(item, skipped);
            Product product = item.productId() == null ? null : products.get(item.productId());
            if (product == null) {
                skipped.add(skip(type, item.name(), ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG));
                return null;
            }
            if (!validQuantity(type, item.quantity())) {
                skipped.add(skip(type, product.getName(), ErrorCode.CHAT_INVALID_QUANTITY));
                return null;
            }
            Unit unit = item.unit() == null ? unitsById.get(product.getDefaultUnitId()) : unitNamed(item.unit());
            if (unit == null || product.getDefaultUnitId() == null) {
                skipped.add(skip(type, product.getName(), ErrorCode.UNIT_NOT_FOUND));
                return null;
            }
            try {
                BigDecimal quantity = convert(() -> unitConversionService.convert(
                        item.quantity(), unit.getId(), product.getDefaultUnitId()), 3);
                BigDecimal price = null;
                if (type == ChatOperationType.PURCHASE) {
                    if (!positive(item.pricePerUnit())) {
                        skipped.add(skip(type, product.getName(), ErrorCode.CHAT_MISSING_PRICE));
                        return null;
                    }
                    Unit priceUnit = item.priceUnit() == null ? unit : unitNamed(item.priceUnit());
                    if (priceUnit == null) {
                        skipped.add(skip(type, product.getName(), ErrorCode.UNIT_NOT_FOUND));
                        return null;
                    }
                    price = convert(() -> unitConversionService.convertPrice(
                            item.pricePerUnit(), priceUnit.getId(), product.getDefaultUnitId()), 4);
                }
                if (!validQuantity(type, quantity)) {   // 0,0001 g de algo en kg: redondeado no queda nada
                    skipped.add(skip(type, product.getName(), ErrorCode.CHAT_INVALID_QUANTITY));
                    return null;
                }
                return new OperationItem(product.getId(), null, product.getName(), quantity, unitLabel(product),
                        price, type == ChatOperationType.PURCHASE ? item.expirationDate() : null,
                        type == ChatOperationType.WASTE ? reasonOf(item.wasteReason()) : null);
            } catch (AppException e) {   // UNIT_CONVERSION_NOT_FOUND: litros de un insumo en kg
                skipped.add(new Skipped(type, product.getName(), e.getErrorCode(),
                        "«" + product.getName() + "» va en " + unitLabel(product) + " y no se puede pasar de "
                                + unit.getAbbreviation() + "."));
                return null;
            }
        }

        private OperationItem resolveSale(InterpretedItem item, List<Skipped> skipped) {
            MenuItem dish = item.menuItemId() == null ? null : menuItems.get(item.menuItemId());
            if (dish == null) {
                skipped.add(skip(ChatOperationType.SALE, item.name(), ErrorCode.CHAT_MENU_ITEM_NOT_IN_CATALOG));
                return null;
            }
            if (!isPortions(item.quantity())) {   // "2,5 pizzas": una venta son porciones enteras
                skipped.add(skip(ChatOperationType.SALE, dish.getName(), ErrorCode.CHAT_INVALID_QUANTITY));
                return null;
            }
            return saleItem(dish, item.quantity());
        }

        private Unit unitNamed(String name) {
            return units.get(CatalogImportService.key(name));
        }
    }

    private static OperationItem saleItem(MenuItem dish, BigDecimal portions) {
        return new OperationItem(null, dish.getId(), dish.getName(), portions.stripTrailingZeros().setScale(0),
                null, null, null, null);
    }

    private static boolean isPortions(BigDecimal n) {
        return positive(n) && n.stripTrailingZeros().scale() <= 0;
    }

    /** Un ajuste puede ser 0 ("no queda más aceite"); lo demás, más de 0. */
    private static boolean validQuantity(ChatOperationType type, BigDecimal n) {
        return type == ChatOperationType.STOCK_ADJUSTMENT ? n != null && n.signum() >= 0 : positive(n);
    }

    private static BigDecimal convert(Supplier<BigDecimal> conversion, int scale) {
        // Sin ceros de más, pero tampoco 3E+3: Jackson escribe el BigDecimal tal cual.
        BigDecimal n = conversion.get().setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
        return n.scale() < 0 ? n.setScale(0) : n;
    }

    private static boolean isHighPrice(InterpretedItem item) {
        return item.quantity() != null && item.quantity().compareTo(BigDecimal.ONE) > 0
                && item.pricePerUnit() != null && item.pricePerUnit().compareTo(HIGH_UNIT_PRICE) > 0;
    }

    private static ChatOperationType typeOf(String type) {
        if (type == null) return null;
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "purchase" -> ChatOperationType.PURCHASE;
            case "waste" -> ChatOperationType.WASTE;
            case "sale" -> ChatOperationType.SALE;
            case "stock_adjustment" -> ChatOperationType.STOCK_ADJUSTMENT;
            case "production" -> ChatOperationType.PRODUCTION;
            default -> null;   // query y lo que venga: no tiene ejecución
        };
    }

    private static WasteReason reasonOf(String reason) {
        if (reason == null) return WasteReason.OTHER;
        try {
            return WasteReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return WasteReason.OTHER;
        }
    }

    private static WasteReason orOther(WasteReason reason) {
        return reason == null ? WasteReason.OTHER : reason;
    }

    private static boolean positive(BigDecimal n) {
        return n != null && n.signum() > 0;
    }

    private static String unitLabel(Product p) {
        return p.getDefaultUnitAbbreviation() != null ? p.getDefaultUnitAbbreviation() : p.getDefaultUnitName();
    }

    // ── Textos ─────────────────────────────────────────────────────────────────

    private static ChatResult nothingToRegister(ChatInterpretation interpretation, List<Skipped> skipped) {
        if (skipped.isEmpty()) {
            String text = interpretation.responseToUser() != null ? interpretation.responseToUser()
                    : "No entendí qué registrar. Probá con algo como «compré 10 kg de harina a $2.000».";
            return new ChatResult(ChatStatus.NOT_UNDERSTOOD, text, List.of(), List.of(), skipped);
        }
        boolean onlyUnsupported = skipped.stream().allMatch(s -> s.errorCode() == ErrorCode.CHAT_OPERATION_NOT_SUPPORTED);
        return new ChatResult(onlyUnsupported ? ChatStatus.NOT_SUPPORTED : ChatStatus.NOT_REGISTERED,
                onlyUnsupported ? skipMessages(skipped) : nothingRegistered(skipped), List.of(), List.of(), skipped);
    }

    private static String nothingRegistered(List<Skipped> skipped) {
        return "No registré nada: " + skipMessages(skipped);
    }

    private static String skipMessages(List<Skipped> skipped) {
        return skipped.stream().map(Skipped::message).distinct().collect(Collectors.joining(" "));
    }

    private static Skipped skip(ChatOperationType type, String name, ErrorCode code) {
        String who = "«" + (name == null ? "?" : name) + "»";
        String message = switch (code) {
            case CHAT_PRODUCT_NOT_IN_CATALOG -> who + " no está en el catálogo.";
            case CHAT_MENU_ITEM_NOT_IN_CATALOG -> who + " no es un plato del menú.";
            case CHAT_INVALID_QUANTITY -> type == ChatOperationType.SALE
                    ? "La venta de " + who + " tiene que ser en porciones enteras."
                    : "Falta la cantidad de " + who + ".";
            case CHAT_MISSING_PRICE -> "Falta el precio de " + who + ".";
            case UNIT_NOT_FOUND -> "No reconozco la unidad de " + who + ".";
            default -> "No se pudo registrar " + who + ".";
        };
        return new Skipped(type, name, code, message);
    }

    private static Skipped notSupported(ChatOperationType type) {
        String message = type == null
                ? "Por ahora no respondo consultas: solo registro compras, mermas, ventas y ajustes de stock."
                : "Todavía no registro " + plural(type) + " por acá: usá el formulario.";
        return new Skipped(type, null, ErrorCode.CHAT_OPERATION_NOT_SUPPORTED, message);
    }

    private static Skipped permissionMissing(ChatOperationType type) {
        return new Skipped(type, null, ErrorCode.CHAT_PERMISSION_MISSING,
                "No tenés permiso para registrar " + plural(type) + ".");
    }

    private static String label(ChatOperationType type) {
        return switch (type) {
            case PURCHASE -> "la compra";
            case WASTE -> "la merma";
            case SALE -> "la venta";
            case STOCK_ADJUSTMENT -> "el ajuste";
            case PRODUCTION -> "la producción";
        };
    }

    private static String plural(ChatOperationType type) {
        return switch (type) {
            case PURCHASE -> "compras";
            case WASTE -> "mermas";
            case SALE -> "ventas";
            case STOCK_ADJUSTMENT -> "ajustes de stock";
            case PRODUCTION -> "producciones";
        };
    }

    private static String names(List<OperationItem> items) {
        return items.stream().map(OperationItem::name).collect(Collectors.joining(", "));
    }

    static String purchaseSummary(List<OperationItem> items) {
        return "Compra: " + items.stream()
                .map(i -> quantity(i) + " a " + money(i.pricePerUnit()))
                .collect(Collectors.joining(", "));
    }

    static String wasteSummary(OperationItem item) {
        String reason = switch (item.wasteReason() == null ? WasteReason.OTHER : item.wasteReason()) {
            case EXPIRED -> " (vencido)";
            case DAMAGED -> " (en mal estado)";
            case OVERPRODUCTION -> " (sobrante)";
            case THEFT -> " (robo)";
            default -> "";
        };
        return "Merma: " + quantity(item) + reason;
    }

    static String saleSummary(List<OperationItem> items) {
        return "Venta: " + items.stream().map(ChatService::quantity).collect(Collectors.joining(", "));
    }

    static String adjustmentSummary(List<OperationItem> items) {
        return "Ajuste: hay " + items.stream().map(ChatService::quantity).collect(Collectors.joining(", "));
    }

    private static String quantity(OperationItem i) {
        NumberFormat f = NumberFormat.getNumberInstance(ES_AR);
        f.setMaximumFractionDigits(3);
        return f.format(i.quantity()) + " " + (i.unitName() == null ? "" : i.unitName() + " ") + i.name();
    }

    private static String money(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(ES_AR);
        boolean cents = amount.stripTrailingZeros().scale() > 0;
        f.setMinimumFractionDigits(cents ? 2 : 0);
        f.setMaximumFractionDigits(cents ? 2 : 0);
        return "$ " + f.format(amount);
    }

    // ── Tipos ──────────────────────────────────────────────────────────────────

    public enum ChatStatus { REGISTERED, NEEDS_CONFIRMATION, RECIPE_PREVIEW, NOT_REGISTERED, NOT_SUPPORTED, NOT_UNDERSTOOD }

    public record Operation(ChatOperationType type, List<OperationItem> items) {}

    /**
     * @param quantity     en la unidad por defecto del insumo (ya convertida); en ventas, porciones
     * @param unitName     la abreviatura de esa unidad
     * @param pricePerUnit solo compras: pesos por {@code unitName}
     */
    public record OperationItem(UUID productId, UUID menuItemId, String name, BigDecimal quantity, String unitName,
                                    BigDecimal pricePerUnit, LocalDate expirationDate, WasteReason wasteReason) {}

    /** @param name como lo dijo el usuario, o el nombre del catálogo si ya se lo reconoció */
    public record Skipped(ChatOperationType type, String name, ErrorCode errorCode, String message) {}

    public record ChatResult(ChatStatus status, String message, List<ChatAction> actions,
                             List<Operation> proposal, List<Skipped> skipped) {
        static ChatResult of(ChatStatus status, String message) {
            return new ChatResult(status, message, List.of(), List.of(), List.of());
        }
    }
}
