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
import com.chefcontrol.application.port.ChatExchangeRepository.Kind;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.ChatRecipeService.RecipePreview;
import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatActionRepository;
import com.chefcontrol.domain.chat.ChatOperationType;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
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
import java.util.IdentityHashMap;
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

    /** Todo lo operativo. Las recetas no van acá: terminan en la vista previa del import ({@link ChatRecipeService}). */
    static final Set<ChatOperationType> SUPPORTED = EnumSet.allOf(ChatOperationType.class);

    static final int MIN_CONFIDENCE = 80;
    /** La regla de la confirmación por precio alto: cantidad mayor a 1 y precio unitario mayor a esto. */
    static final BigDecimal HIGH_UNIT_PRICE = new BigDecimal("100000");
    private static final int MAX_CATALOG = 500;
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private final AIService aiService;
    private final ChatRecipeService chatRecipeService;
    private final ChatHistoryService chatHistoryService;
    private final ProductRepository productRepository;
    private final MenuItemRepository menuItemRepository;
    private final UnitRepository unitRepository;
    private final UnitConversionService unitConversionService;
    private final PurchaseService purchaseService;
    private final WasteService wasteService;
    private final SaleService saleService;
    private final StockCountService stockCountService;
    private final ProductionService productionService;
    private final RecipeRepository recipeRepository;
    private final AccountService accountService;
    private final StockService stockService;
    private final StockMovementRepository stockMovementRepository;
    private final ChatActionRepository chatActionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionOperations transactions;
    private final Clock clock;

    // ── Mensaje ────────────────────────────────────────────────────────────────

    /** @param permissions los del usuario en el local activo */
    public ChatResult sendChatMessage(String message, Set<Permission> permissions) {
        ChatHistoryService.Conversation conversation = chatHistoryService.currentConversation();
        ChatResult result = answerChatMessage(message, permissions, conversation);
        chatHistoryService.rememberChatExchange(Kind.MESSAGE, message, result);
        return result;
    }

    private ChatResult answerChatMessage(String message, Set<Permission> permissions,
                                         ChatHistoryService.Conversation conversation) {
        // "No", "cancelá", "dejalo" contra una pregunta pendiente: se cancela sin llamar al modelo (lo que va a usar
        // WhatsApp, sin botones). Queda guardado, así la pregunta deja de estar pendiente.
        if (!conversation.pendingProposals().isEmpty() && isCancellation(message)) {
            return ChatResult.of(ChatStatus.NOT_REGISTERED, ChatHistoryService.CANCELLED);
        }
        UUID restaurantId = TenantContext.require();
        List<Product> products = productRepository
                .findByRestaurantIdAndIsActiveTrue(restaurantId, PageRequest.of(0, MAX_CATALOG)).content();
        List<MenuItem> menuItems = menuItemRepository
                .findByRestaurantIdAndActive(restaurantId, true, PageRequest.of(0, MAX_CATALOG)).content();
        List<Unit> units = unitRepository.findAll();
        Set<UUID> preparations = recipeRepository.findPreparationProductIds(restaurantId);
        ChatCatalog catalog = new ChatCatalog(products, menuItems, units, preparations);
        ChatHistoryService.PromptHistory history = conversation.promptHistory();

        ChatInterpretation interpretation;
        try {
            interpretation = aiService.interpretChatMessage(new AIService.ChatMessageRequest(message,
                    LocalDate.now(clock.withZone(ChefControlTime.DEFAULT_ZONE)), catalog.chatProducts(),
                    menuItems.stream().map(m -> new ChatCatalogMenuItem(m.getId(), m.getName(), m.getPrice())).toList(),
                    catalog.unitOptions(), history.turns()));
        } catch (AppException e) {
            if (e.getErrorCode() != ErrorCode.AI_INTERPRETATION_FAILED) throw e;
            return ChatResult.of(ChatStatus.NOT_UNDERSTOOD, "No pude interpretar el mensaje. Probá de nuevo.");
        }

        Resolution resolution = new Resolution(products, menuItems, units, preparations);
        List<Skipped> skipped = new ArrayList<>();
        List<Operation> operations = new ArrayList<>();
        List<ChatRecipeService.RecipeRequest> recipes = new ArrayList<>();
        for (InterpretedOperation op : interpretation.operations()) {
            if (isRecipe(op.type())) {
                boolean preparation = "preparation_recipe".equalsIgnoreCase(op.type().trim());
                op.items().forEach(i -> recipes.add(new ChatRecipeService.RecipeRequest(preparation, i, op.text())));
                continue;
            }
            ChatOperationType type = typeOf(op.type());
            if (type == null || !SUPPORTED.contains(type)) {
                skipped.add(notSupported(type));
                continue;
            }
            if (!permissions.contains(type.createPermission())) {
                skipped.add(permissionMissing(type));
                continue;
            }
            // "No, eran 5 kg": la acción se nombra por el número que se le mostró al modelo; solo esas se corrigen.
            UUID corrects = op.corrects() == null ? null : history.actionNumbers().get(op.corrects());
            if (op.corrects() != null && corrects == null) {
                skipped.add(new Skipped(type, null, ErrorCode.CHAT_ACTION_NOT_FOUND,
                        "No encontré la acción " + op.corrects() + " para corregir: no registré la corrección."));
                continue;
            }
            List<OperationItem> items = new ArrayList<>();
            // El pedazo del mensaje de esta operación: contra eso se verifica lo que el modelo clasificó.
            String said = op.text() == null || op.text().isBlank() ? message : op.text();
            for (InterpretedItem item : op.items()) {
                OperationItem resolved = resolution.resolve(type, item, said, skipped);
                if (resolved == null) continue;
                items.add(resolved);
            }
            if (!items.isEmpty()) operations.add(new Operation(type, items, corrects));
        }

        // La receta antes que lo operativo: sus motivos de "no" tienen que estar en skipped cuando se arma el texto.
        RecipePreview recipe = chatRecipeService.previewChatRecipe(message, recipes, catalog, permissions, skipped);

        if (operations.isEmpty()) return withRecipe(nothingToRegister(interpretation, skipped), recipe);

        // Contesta la pregunta anterior ("sí", "es el total"): se registra la alternativa tal como se mostró.
        Proposal answered = answeredProposal(conversation.pendingProposals(), operations);
        if (answered != null) return withRecipe(register(answered.operations(), skipped, permissions), recipe);

        boolean doubtful = interpretation.needsConfirmation() || interpretation.confidence() < MIN_CONFIDENCE;
        if (doubtful || !resolution.questions.isEmpty()) {
            String question = !resolution.questions.isEmpty() ? String.join(" ", resolution.questions)
                    : interpretation.needsConfirmation() && interpretation.responseToUser() != null
                    ? interpretation.responseToUser()
                    : "No estoy seguro de haber entendido. ¿Lo registro así?";
            return withRecipe(new ChatResult(ChatStatus.NEEDS_CONFIRMATION, question, List.of(),
                    proposals(operations, resolution.readings), skipped), recipe);
        }
        return withRecipe(register(operations, skipped, permissions), recipe);
    }

    /**
     * Un mensaje mixto resuelve cada parte: el status es el de lo operativo y la receta viene aparte. RECIPE_PREVIEW
     * queda para cuando la receta es lo único para hacer (no se registró nada ni hay nada que confirmar).
     */
    private static ChatResult withRecipe(ChatResult r, RecipePreview recipe) {
        if (recipe == null) return r;
        String preview = "Armé la receta de «" + recipe.name() + "»: revisala y confirmala.";
        if (r.actions().isEmpty() && r.proposals().isEmpty()) {
            String message = r.skipped().isEmpty() ? preview : preview + " No registré: " + skipMessages(r.skipped());
            return new ChatResult(ChatStatus.RECIPE_PREVIEW, message, List.of(), List.of(), List.of(), r.skipped(), recipe);
        }
        return new ChatResult(r.status(), r.message() + " " + preview, r.actions(), r.undone(), r.proposals(),
                r.skipped(), recipe);
    }

    // ── Confirmar ──────────────────────────────────────────────────────────────

    /**
     * Registra una alternativa de un NEEDS_CONFIRMATION ({@code proposals}), tal cual o editada. La cantidad ya viene
     * en la unidad del insumo. Viene del cliente: se revalida todo como si fuera nueva, y el deshacer de una corrección
     * con sus reglas de siempre. Queda en la conversación.
     */
    public ChatResult confirmChatOperations(List<Operation> operations, Set<Permission> permissions) {
        UUID restaurantId = TenantContext.require();
        List<Skipped> skipped = new ArrayList<>();
        List<Operation> valid = new ArrayList<>();
        Set<UUID> preparations = recipeRepository.findPreparationProductIds(restaurantId);
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
                        : confirmedProductItem(op.type(), item, restaurantId, preparations, skipped);
                if (checked != null) items.add(checked);
            }
            if (!items.isEmpty()) valid.add(new Operation(op.type(), items, op.correctsActionId()));
        }
        ChatResult result = valid.isEmpty()
                ? new ChatResult(ChatStatus.NOT_REGISTERED, nothingRegistered(skipped), List.of(), List.of(), skipped)
                : register(valid, skipped, permissions);
        chatHistoryService.rememberChatExchange(Kind.CONFIRMATION, null, result);
        return result;
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
                                               Set<UUID> preparations, List<Skipped> skipped) {
        Product product = item.productId() == null ? null : productRepository
                .findByIdAndRestaurantId(item.productId(), restaurantId).filter(Product::isActive).orElse(null);
        if (product == null) {
            skipped.add(skip(type, item.name(), ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG));
        } else if (type == ChatOperationType.PRODUCTION && !preparations.contains(product.getId())) {
            skipped.add(skip(type, product.getName(), ErrorCode.PRODUCT_NOT_A_PREPARATION));
        } else if (!validQuantity(type, item.quantity())) {
            skipped.add(skip(type, product.getName(), ErrorCode.CHAT_INVALID_QUANTITY));
        } else if (type == ChatOperationType.PURCHASE && !positive(item.pricePerUnit())) {
            skipped.add(skip(type, product.getName(), ErrorCode.CHAT_MISSING_PRICE));
        } else {
            return new OperationItem(product.getId(), null, product.getName(), item.quantity(), unitLabel(product),
                    type == ChatOperationType.PURCHASE ? item.pricePerUnit() : null,
                    datedByUser(type) ? item.expirationDate() : null,
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
        ChatAction action = undoable(actionId, permissions);
        return transactions.execute(status -> revert(action));
    }

    /** La acción, si este usuario la puede deshacer ahora; si no, por qué (404, 409 o CHAT_UNDO_NOT_ALLOWED). */
    private ChatAction undoable(UUID actionId, Set<Permission> permissions) {
        ChatAction action = chatActionRepository.findByIdAndRestaurantId(actionId, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.CHAT_ACTION_NOT_FOUND, "Chat action not found"));
        if (action.isUndone()) {
            throw AppException.conflict(ErrorCode.CHAT_ACTION_ALREADY_UNDONE, "Chat action already undone");
        }
        if (!action.undoableWithoutPermissionBy(currentUserProvider.currentUserId(), clock.instant())
                && !permissions.contains(action.getType().undoPermission())) {
            throw AppException.forbidden(ErrorCode.CHAT_UNDO_NOT_ALLOWED,
                    "Undoing this needs " + action.getType().undoPermission());
        }
        return action;
    }

    /** Con los métodos de siempre: queda auditado como cualquier reversión. Sin transacción propia: la pone quien llama. */
    private ChatAction revert(ChatAction action) {
        switch (action.getType()) {
            case PURCHASE -> purchaseService.reversePurchase(action.getEntityId());
            case WASTE -> stockService.reverseMovement(wasteMovementOf(action.getEntityId()).getId());
            case SALE -> saleService.reverseSale(action.getEntityId());
            case PRODUCTION -> productionService.reverseProduction(action.getEntityId());
            // Un conteo sin diferencias no movió nada: no hay qué revertir, pero queda deshecho igual.
            case STOCK_ADJUSTMENT -> stockMovementRepository
                    .findByReferenceIdAndReferenceType(action.getEntityId(), "stock_count").stream()
                    .filter(m -> m.getType() != MovementType.REVERSAL && m.getReversedBy() == null)
                    .forEach(m -> stockService.reverseMovement(m.getId()));
        }
        action.markUndone(clock.instant());
        return chatActionRepository.save(action);
    }

    private StockMovement wasteMovementOf(UUID wasteEventId) {
        return stockMovementRepository.findByReferenceIdAndReferenceType(wasteEventId, "waste_event").stream()
                .filter(m -> m.getType() == MovementType.WASTE)
                .findFirst()
                .orElseThrow(() -> AppException.notFound(ErrorCode.MOVEMENT_NOT_FOUND, "Waste movement not found"));
    }

    // ── Registrar ──────────────────────────────────────────────────────────────

    private ChatResult register(List<Operation> operations, List<Skipped> skipped, Set<Permission> permissions) {
        List<ChatAction> actions = new ArrayList<>();
        List<ChatAction> undone = new ArrayList<>();
        for (Operation op : operations) {
            if (op.correctsActionId() == null) {
                // Una acción por merma y por producción: cada una es su entidad, y así se deshace solo la equivocada.
                List<Operation> parts = op.type() == ChatOperationType.WASTE || op.type() == ChatOperationType.PRODUCTION
                        ? op.items().stream().map(i -> new Operation(op.type(), List.of(i))).toList()
                        : List.of(op);
                parts.forEach(part -> inOwnTransaction(part.type(), names(part.items()), skipped,
                        () -> actions.addAll(record(part))));
                continue;
            }
            // Una corrección: deshacer la vieja y registrar la nueva, juntas. Si la nueva falla, la vieja queda como
            // estaba; si la vieja no se puede deshacer, la nueva no se registra.
            ChatAction corrected;
            try {
                corrected = undoable(op.correctsActionId(), permissions);
            } catch (AppException e) {
                skipped.add(notCorrected(op, e));
                continue;
            }
            inOwnTransaction(op.type(), names(op.items()), skipped, () -> {
                ChatAction reverted = revert(corrected);
                List<ChatAction> recorded = record(op);
                undone.add(reverted);
                actions.addAll(recorded);
            });
        }
        if (actions.isEmpty()) {
            return new ChatResult(ChatStatus.NOT_REGISTERED, nothingRegistered(skipped), List.of(), List.of(), skipped);
        }
        String message = (undone.isEmpty() ? "" : "Corregí: deshice " + undone.stream()
                .map(a -> "«" + a.getSummary() + "»").collect(Collectors.joining(", ")) + ". ")
                + "Listo: " + actions.stream().map(ChatAction::getSummary).collect(Collectors.joining("; ")) + ".";
        if (!skipped.isEmpty()) message += " No registré: " + skipMessages(skipped);
        return new ChatResult(ChatStatus.REGISTERED, message, actions, undone, List.of(), skipped, null);
    }

    /** Registra una operación y devuelve sus acciones. Sin transacción propia: la pone quien llama. */
    private List<ChatAction> record(Operation op) {
        List<ChatAction> recorded = new ArrayList<>();
        switch (op.type()) {
            case PURCHASE -> {
                var purchase = purchaseService.createPurchase(new PurchaseService.CreatePurchaseCommand(
                        null, null, clock.instant(), null, op.items().stream().map(this::purchaseItem).toList()));
                recorded.add(saveAction(ChatOperationType.PURCHASE, purchase.getId(), purchaseSummary(op.items())));
            }
            case WASTE -> op.items().forEach(item -> {
                var event = wasteService.createWasteEvent(new WasteService.CreateWasteEventCommand(
                        item.productId(), defaultUnitOf(item.productId()), item.quantity(), item.wasteReason()));
                recorded.add(saveAction(ChatOperationType.WASTE, event.getId(), wasteSummary(item)));
            });
            // Una venta con todos los platos, como el formulario. Descuenta con la receta (y produce lo que falte).
            case SALE -> {
                var sale = saleService.recordSale(new SaleService.CreateSaleCommand(null, clock.instant(),
                        op.items().stream().map(i -> new SaleService.SaleItemCommand(
                                i.menuItemId(), i.quantity().intValueExact())).toList()));
                recorded.add(saveAction(ChatOperationType.SALE, sale.getId(), saleSummary(op.items())));
            }
            // Un conteo de solo esos insumos: "quedan 8 kg de harina" ajusta la harina y nada más.
            case STOCK_ADJUSTMENT -> {
                var count = stockCountService.createStockCount(new StockCountService.CreateStockCountCommand(null,
                        op.items().stream().map(i -> new StockCountService.StockCountItemCommand(
                                i.productId(), defaultUnitOf(i.productId()), i.quantity())).toList()));
                recorded.add(saveAction(ChatOperationType.STOCK_ADJUSTMENT, count.count().getId(),
                        adjustmentSummary(op.items())));
            }
            case PRODUCTION -> op.items().forEach(item -> {
                accountService.requireFeature(Feature.RECIPES_AND_FOOD_COST);   // como POST /productions
                var production = productionService.recordProduction(new ProductionService.RecordProductionCommand(
                        item.productId(), item.quantity(), defaultUnitOf(item.productId()), item.expirationDate(),
                        clock.instant(), null));
                recorded.add(saveAction(ChatOperationType.PRODUCTION, production.getId(), productionSummary(item)));
            });
        }
        return recorded;
    }

    /** Por qué una corrección no se hizo: no se registró la nueva, y se dice. */
    private Skipped notCorrected(Operation op, AppException e) {
        String what = chatActionRepository.findByIdAndRestaurantId(op.correctsActionId(), TenantContext.require())
                .map(a -> "«" + a.getSummary() + "»").orElse("lo que querés corregir");
        String why = switch (e.getErrorCode()) {
            case CHAT_ACTION_ALREADY_UNDONE -> what + " ya estaba deshecha";
            case CHAT_UNDO_NOT_ALLOWED -> "para deshacer " + what
                    + " hace falta el permiso de borrar (pasaron más de 15 minutos o no la cargaste vos)";
            default -> "no encontré " + what;
        };
        return new Skipped(op.type(), names(op.items()), e.getErrorCode(), "No corregí nada: " + why + ".");
    }

    /**
     * Qué se puede confirmar. Con un precio de dos lecturas, una alternativa por lectura (primero la del modelo), así el
     * botón contesta la pregunta; si no, una sola, "Confirmar".
     */
    static List<Proposal> proposals(List<Operation> operations, Map<OperationItem, Readings> readings) {
        if (readings.isEmpty()) return List.of(new Proposal("Confirmar", operations));
        if (readings.size() > 1) {
            return List.of(new Proposal("Precios por unidad", priced(operations, readings, true)),
                    new Proposal("Precios totales", priced(operations, readings, false)));
        }
        var only = readings.entrySet().iterator().next();
        OperationItem item = only.getKey();
        Readings r = only.getValue();
        Proposal perUnit = new Proposal("Por " + item.unitName() + " · " + money(r.perUnit().multiply(item.quantity())),
                priced(operations, readings, true));
        Proposal total = new Proposal("Total · " + money(r.ofTotal()) + " por " + item.unitName(),
                priced(operations, readings, false));
        return r.modelSaidTotal() ? List.of(total, perUnit) : List.of(perUnit, total);
    }

    private static List<Operation> priced(List<Operation> operations, Map<OperationItem, Readings> readings,
                                          boolean perUnit) {
        return operations.stream().map(op -> new Operation(op.type(), op.items().stream().map(i -> {
            Readings r = readings.get(i);
            return r == null ? i : i.withPrice(perUnit ? r.perUnit() : r.ofTotal());
        }).toList(), op.correctsActionId())).toList();
    }

    /**
     * La alternativa que contesta la pregunta anterior ("sí", "es el total", "por kilo"): lo interpretado ahora tiene
     * los mismos insumos o platos, cantidades y precios que una de las que se mostraron. Sin esto, "es por kilo" volvería
     * a caer en la regla de los $100.000 y preguntaría para siempre.
     */
    static Proposal answeredProposal(List<Proposal> pending, List<Operation> operations) {
        List<String> now = keys(operations);
        return pending.stream().filter(p -> keys(p.operations()).equals(now)).findFirst().orElse(null);
    }

    /** Las palabras de un "no" a la pregunta pendiente: el mensaje entero tiene que ser solo esto ("mejor no", "dejalo"). */
    static final Set<String> CANCEL_WORDS = Set.of("no", "cancela", "cancelalo", "cancelar", "deja", "dejalo",
            "olvidalo", "olvidate", "nada", "mejor", "asi", "gracias");

    /** "No, eran 5 kg" no es cancelar: trae algo más, y lo interpreta el modelo como siempre. */
    static boolean isCancellation(String message) {
        List<String> words = java.util.Arrays.stream(CatalogImportService.key(message).split("[^a-z]+"))
                .filter(w -> !w.isEmpty()).toList();
        return !words.isEmpty() && CANCEL_WORDS.containsAll(words);
    }

    private static List<String> keys(List<Operation> operations) {
        return operations.stream().flatMap(op -> op.items().stream().map(i -> op.type() + "|" + i.productId() + "|"
                + i.menuItemId() + "|" + plain(i.quantity()) + "|" + plain(i.pricePerUnit()))).sorted().toList();
    }

    private static String plain(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    /** La operación y su fila de chat_actions juntas: no queda una compra sin deshacer ni un deshacer sin compra. */
    private void inOwnTransaction(ChatOperationType type, String name, List<Skipped> skipped, Runnable work) {
        try {
            transactions.executeWithoutResult(status -> work.run());
        } catch (AppException e) {
            skipped.add(rejected(type, name, e));
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

        final Set<UUID> preparations;
        /** Lo que hay que preguntar antes de registrar nada: un precio con dos lecturas, una unidad que nadie dijo. */
        final List<String> questions = new ArrayList<>();
        /** Los ítems con un precio de dos lecturas (por identidad): cada lectura es una alternativa para confirmar. */
        final Map<OperationItem, Readings> readings = new IdentityHashMap<>();

        Resolution(List<Product> catalog, List<MenuItem> dishes, List<Unit> all, Set<UUID> preparations) {
            this.preparations = preparations;
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
        OperationItem resolve(ChatOperationType type, InterpretedItem item, String said, List<Skipped> skipped) {
            if (type == ChatOperationType.SALE) return resolveSale(item, skipped);
            Product product = item.productId() == null ? null : products.get(item.productId());
            if (product == null) {
                skipped.add(skip(type, item.name(), ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG));
                return null;
            }
            if (type == ChatOperationType.PRODUCTION && !preparations.contains(product.getId())) {
                skipped.add(skip(type, product.getName(), ErrorCode.PRODUCT_NOT_A_PREPARATION));
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
            // Sin unidad dicha también: "se tiraron 100 de harina" entraba como 100 kg (la del insumo) sin preguntar.
            // Un 0 es 0 en cualquier unidad ("no queda más aceite").
            if (item.quantity().signum() != 0 && !unitSaid(said, unit)) {
                questions.add("«" + product.getName() + "»: entendí " + num(item.quantity()) + " " + unit.getAbbreviation()
                        + ", ¿es así?");
            }
            try {
                BigDecimal quantity = convert(() -> unitConversionService.convert(
                        item.quantity(), unit.getId(), product.getDefaultUnitId()), 3);
                BigDecimal price = null;
                Readings twoReadings = null;
                if (!validQuantity(type, quantity)) {   // 0,0001 g de algo en kg: redondeado no queda nada
                    skipped.add(skip(type, product.getName(), ErrorCode.CHAT_INVALID_QUANTITY));
                    return null;
                }
                if (type == ChatOperationType.PURCHASE) {
                    if (!positive(item.price())) {
                        skipped.add(skip(type, product.getName(), ErrorCode.CHAT_MISSING_PRICE));
                        return null;
                    }
                    Unit priceUnit = item.priceUnit() == null ? unit : unitNamed(item.priceUnit());
                    if (priceUnit == null) {
                        skipped.add(skip(type, product.getName(), ErrorCode.UNIT_NOT_FOUND));
                        return null;
                    }
                    if (item.priceUnit() != null && !unitSaid(said, priceUnit)) {
                        questions.add("«" + product.getName() + "»: ¿" + money(item.price()) + " por "
                                + priceUnit.getAbbreviation() + "?");
                    }
                    // Las dos lecturas del número, calculadas acá: el modelo solo dice cuál le parece.
                    BigDecimal perUnit = convert(() -> unitConversionService.convertPrice(
                            item.price(), priceUnit.getId(), product.getDefaultUnitId()), 4);
                    BigDecimal ofTotal = convert(() -> item.price().divide(quantity, 6, RoundingMode.HALF_UP), 4);
                    PriceBasis basis = PriceBasis.of(item.priceBasis());
                    price = basis == PriceBasis.TOTAL ? ofTotal : perUnit;
                    if (basis.isAmbiguous(quantity, perUnit, said)) {
                        questions.add(priceQuestion(product, item.price(), priceUnit, quantity, perUnit, ofTotal));
                        twoReadings = new Readings(perUnit, ofTotal, basis == PriceBasis.TOTAL);
                    }
                }
                OperationItem resolved = new OperationItem(product.getId(), null, product.getName(), quantity,
                        unitLabel(product), price, datedByUser(type) ? item.expirationDate() : null,
                        type == ChatOperationType.WASTE ? reasonOf(item.wasteReason()) : null);
                if (twoReadings != null) readings.put(resolved, twoReadings);
                return resolved;
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

    /** El vencimiento lo dice quien carga en una compra o una producción; en lo demás no hay lote nuevo. */
    private static boolean datedByUser(ChatOperationType type) {
        return type == ChatOperationType.PURCHASE || type == ChatOperationType.PRODUCTION;
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

    /** A qué se refiere el precio que dijo el usuario. Lo clasifica la IA; las cuentas, ChatService. */
    enum PriceBasis {
        UNIT, TOTAL, UNCLEAR;

        static PriceBasis of(String basis) {
            if (basis == null) return UNCLEAR;
            return switch (basis.trim().toLowerCase(Locale.ROOT)) {
                case "unit" -> UNIT;
                case "total" -> TOTAL;
                default -> UNCLEAR;
            };
        }

        /**
         * Cuándo las dos lecturas pueden ser la que no es. Por unidad: la regla de siempre (más de una unidad y más de
         * $100.000 cada una: puede ser el total). Total: con más de una unidad, salvo que el texto lo diga ("en total",
         * "me salió"…): la clasificación del modelo se acepta solo si el texto la respalda, así un "a 1.500" mal
         * clasificado nunca se divide en silencio. Sin saber: si la cantidad no es 1, porque las dos lecturas difieren.
         *
         * @param quantity en la unidad del insumo
         * @param perUnit  la lectura "por unidad", por unidad del insumo
         * @param said     el pedazo del mensaje de la operación
         */
        boolean isAmbiguous(BigDecimal quantity, BigDecimal perUnit, String said) {
            return switch (this) {
                case UNIT -> quantity.compareTo(BigDecimal.ONE) > 0 && perUnit.compareTo(HIGH_UNIT_PRICE) > 0;
                case TOTAL -> quantity.compareTo(BigDecimal.ONE) > 0 && !saysTotal(said);
                case UNCLEAR -> quantity.compareTo(BigDecimal.ONE) != 0;
            };
        }
    }

    /** Lo que, dicho en el mensaje, respalda que un precio es el total. Comparado sin mayúsculas ni acentos. */
    static final List<String> TOTAL_WORDS = List.of("total", "todo", "me salio", "me costo", "pague", "la bolsa", "el lote");

    static boolean saysTotal(String said) {
        String words = " " + String.join(" ", CatalogImportService.key(said == null ? "" : said).split("[^a-z]+")) + " ";
        return TOTAL_WORDS.stream().anyMatch(w -> words.contains(" " + w + " "));
    }

    /** "«Comino»: ¿$ 150.000 es por kg o el total de los 5 kg? Por kg serían $ 750.000; si es el total, $ 30.000 por kg." */
    static String priceQuestion(Product product, BigDecimal said, Unit priceUnit, BigDecimal quantity,
                                BigDecimal perUnit, BigDecimal ofTotal) {
        String u = unitLabel(product);
        return "«" + product.getName() + "»: ¿" + money(said) + " es por " + priceUnit.getAbbreviation()
                + " o el total de los " + num(quantity) + " " + u + "? Por " + priceUnit.getAbbreviation() + " serían "
                + money(perUnit.multiply(quantity)) + "; si es el total, " + money(ofTotal) + " por " + u + ".";
    }

    /**
     * Si el usuario nombró la unidad en el mensaje ("100 g", "medio kilo", "2 litros", "3 bolsas"). El modelo a veces la
     * cambia: con "200 g de acelga y 100 g de harina" devolvió 100 kg de harina una vez de tres. Una unidad que nadie
     * dijo se pregunta (o se avisa, en una receta). "u" no hace falta decirla ("3 huevos").
     */
    static boolean unitSaid(String message, Unit unit) {
        if (unit == null || "u".equalsIgnoreCase(unit.getAbbreviation())) return true;
        Set<String> words = Set.copyOf(List.of(CatalogImportService.key(message).split("[^a-z]+")));   // "500g": el número separa
        Set<String> spellings = new java.util.HashSet<>(UNIT_SYNONYMS.getOrDefault(
                unit.getAbbreviation() == null ? "" : CatalogImportService.key(unit.getAbbreviation()), Set.of()));
        for (String n : new String[] {unit.getAbbreviation(), unit.getName()}) {
            if (n == null) continue;
            String k = CatalogImportService.key(n);
            spellings.addAll(List.of(k, k + "s", k + "es"));
        }
        return spellings.stream().anyMatch(words::contains);
    }

    /** Cómo se dicen las unidades del seed además de su nombre y abreviatura. */
    private static final Map<String, Set<String>> UNIT_SYNONYMS = Map.of(
            "kg", Set.of("kilo", "kilos", "kgs"),
            "g", Set.of("gr", "grs", "gramo", "gramos"),
            "l", Set.of("lt", "lts", "litro", "litros"),
            "ml", Set.of("mililitro", "mililitros", "cc"),
            "cc", Set.of("ml"),
            "doc", Set.of("docena", "docenas"));

    static String num(BigDecimal n) {
        NumberFormat f = NumberFormat.getNumberInstance(ES_AR);
        f.setMaximumFractionDigits(3);
        return f.format(n);
    }

    private static boolean isRecipe(String type) {
        return type != null && Set.of("recipe", "preparation_recipe").contains(type.trim().toLowerCase(Locale.ROOT));
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

    static String unitLabel(Product p) {
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
            case PRODUCT_NOT_A_PREPARATION -> who + " no tiene receta de preparación: no se puede producir.";
            case CHAT_INVALID_QUANTITY -> type == ChatOperationType.SALE
                    ? "La venta de " + who + " tiene que ser en porciones enteras."
                    : "Falta la cantidad de " + who + ".";
            case CHAT_MISSING_PRICE -> "Falta el precio de " + who + ".";
            case UNIT_NOT_FOUND -> "No reconozco la unidad de " + who + ".";
            default -> "No se pudo registrar " + who + ".";
        };
        return new Skipped(type, name, code, message);
    }

    /**
     * Por qué un servicio rechazó lo que el chat le pidió, en castellano: el texto del chat es lo único que se lee (y lo
     * que va a leer WhatsApp, que no tiene front que traduzca el errorCode). Los mensajes de AppException van en inglés.
     */
    static Skipped rejected(ChatOperationType type, String name, AppException e) {
        String who = "«" + (name == null ? "?" : name) + "»";
        String message = switch (e.getErrorCode()) {
            case MISSING_PURCHASE_COST -> {
                // ponytail: los insumos salen del texto de SaleService/ProductionService ("…para: Queso, Harina").
                // Si ese texto cambia, queda el nombre de la operación; un campo en AppException si hace falta más.
                String m = e.getMessage() == null ? "" : e.getMessage();
                String missing = m.contains(": ") ? m.substring(m.indexOf(": ") + 2) : name;
                yield "«" + missing + "» no tiene compras cargadas, así que " + label(type) + " no tiene costo. "
                        + "Cargá una compra y probá de nuevo.";
            }
            case UNIT_CONVERSION_NOT_FOUND -> "La receta de " + who
                    + " usa una unidad que no se puede pasar a la del insumo: corregila y probá de nuevo.";
            case PLAN_FEATURE_REQUIRED -> "Tu plan no incluye " + plural(type) + ".";
            case PREPARATION_CYCLE -> "Las recetas de las preparaciones de " + who + " se usan entre sí en círculo.";
            case PRODUCT_NOT_FOUND, MENU_ITEM_NOT_FOUND, UNIT_NOT_FOUND -> who + " ya no está en el catálogo.";
            case INVALID_EXPIRATION_DATE -> "El vencimiento de " + who + " tiene que ser posterior a la fecha de la compra.";
            case PRODUCT_NOT_A_PREPARATION, CHAT_INVALID_QUANTITY, CHAT_MISSING_PRICE ->
                    skip(type, name, e.getErrorCode()).message();
            default -> "No se pudo registrar " + label(type) + " de " + who + ".";
        };
        return new Skipped(type, name, e.getErrorCode(), message);
    }

    private static Skipped notSupported(ChatOperationType type) {
        String message = type == null
                ? "Por ahora no respondo consultas: registro compras, mermas, ventas, ajustes de stock y producciones, y armo recetas."
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

    static String productionSummary(OperationItem item) {
        return "Producción: " + quantity(item);
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

    /** @param correctsActionId la acción que esta operación corrige (se deshace en la misma transacción); null si no */
    public record Operation(ChatOperationType type, List<OperationItem> items, UUID correctsActionId) {
        public Operation(ChatOperationType type, List<OperationItem> items) {
            this(type, items, null);
        }
    }

    /** Una alternativa de un NEEDS_CONFIRMATION: lo que confirma su botón. El label lo arma el back. */
    public record Proposal(String label, List<Operation> operations) {}

    /** Las dos lecturas de un precio, calculadas por el back, por unidad del insumo. */
    record Readings(BigDecimal perUnit, BigDecimal ofTotal, boolean modelSaidTotal) {}

    /**
     * @param quantity     en la unidad por defecto del insumo (ya convertida); en ventas, porciones
     * @param unitName     la abreviatura de esa unidad
     * @param pricePerUnit solo compras: pesos por {@code unitName}
     */
    public record OperationItem(UUID productId, UUID menuItemId, String name, BigDecimal quantity, String unitName,
                                    BigDecimal pricePerUnit, LocalDate expirationDate, WasteReason wasteReason) {
        OperationItem withPrice(BigDecimal price) {
            return new OperationItem(productId, menuItemId, name, quantity, unitName, price, expirationDate, wasteReason);
        }
    }

    /** @param name como lo dijo el usuario, o el nombre del catálogo si ya se lo reconoció */
    public record Skipped(ChatOperationType type, String name, ErrorCode errorCode, String message) {}

    /**
     * @param undone    lo que deshizo una corrección en este mensaje
     * @param proposals las alternativas de un NEEDS_CONFIRMATION, cada una con su botón
     * @param recipe    la receta para la vista previa del import; null si el mensaje no trae receta
     */
    public record ChatResult(ChatStatus status, String message, List<ChatAction> actions, List<ChatAction> undone,
                             List<Proposal> proposals, List<Skipped> skipped, RecipePreview recipe) {
        public ChatResult(ChatStatus status, String message, List<ChatAction> actions, List<Proposal> proposals,
                          List<Skipped> skipped) {
            this(status, message, actions, List.of(), proposals, skipped, null);
        }

        static ChatResult of(ChatStatus status, String message) {
            return new ChatResult(status, message, List.of(), List.of(), List.of());
        }
    }

    /** Lo que hay en el local cuando llega el mensaje: lo que la IA nombra se busca acá, nunca se crea. */
    record ChatCatalog(List<Product> products, List<MenuItem> menuItems, List<Unit> units, Set<UUID> preparations) {
        // ponytail: búsqueda lineal; son a lo sumo 500 y se busca un par de veces por mensaje.
        Product product(UUID id) {
            return products.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
        }

        MenuItem menuItem(UUID id) {
            return menuItems.stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
        }

        Unit unit(UUID id) {
            return units.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        }

        /** Por abreviatura o nombre, como el import. */
        Unit unitNamed(String name) {
            String k = CatalogImportService.key(name);
            return units.stream().filter(u -> (u.getAbbreviation() != null && CatalogImportService.key(u.getAbbreviation()).equals(k))
                    || (u.getName() != null && CatalogImportService.key(u.getName()).equals(k))).findFirst().orElse(null);
        }

        /** Con la comparación del import (sin mayúsculas, acentos ni espacios de más); null si no hay uno solo. */
        MenuItem menuItemNamed(String name) {
            List<MenuItem> found = menuItems.stream()
                    .filter(m -> CatalogImportService.key(m.getName()).equals(CatalogImportService.key(name))).toList();
            return found.size() == 1 ? found.getFirst() : null;
        }

        Product productNamed(String name) {
            List<Product> found = products.stream()
                    .filter(p -> CatalogImportService.key(p.getName()).equals(CatalogImportService.key(name))).toList();
            return found.size() == 1 ? found.getFirst() : null;
        }

        List<ChatCatalogProduct> chatProducts() {
            return products.stream().map(p -> new ChatCatalogProduct(p.getId(), p.getName(), unitLabel(p),
                    preparations.contains(p.getId()))).toList();
        }

        List<UnitOption> unitOptions() {
            return units.stream().map(u -> new UnitOption(u.getName(), u.getAbbreviation())).toList();
        }
    }
}
