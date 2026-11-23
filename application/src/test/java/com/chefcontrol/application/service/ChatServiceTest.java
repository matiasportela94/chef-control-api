package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.ChatInterpretation;
import com.chefcontrol.application.port.AIService.HistoryAction;
import com.chefcontrol.application.port.AIService.InterpretedQuery;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.repository.PurchaseItemRepository;
import com.chefcontrol.domain.repository.PurchaseRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.repository.WasteEventRepository;
import java.time.LocalDate;
import com.chefcontrol.application.port.ChatExchangeRepository;
import com.chefcontrol.application.port.ChatExchangeRepository.ChatExchange;
import com.chefcontrol.application.port.ChatExchangeRepository.Kind;
import com.chefcontrol.application.port.ChatExchangeRepository.StoredReply;
import com.chefcontrol.application.port.AIService.IngredientMatch;
import com.chefcontrol.application.port.AIService.RecipeMessageRequest;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.chefcontrol.application.port.AIService.InterpretedItem;
import com.chefcontrol.application.port.AIService.InterpretedOperation;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.application.service.ChatService.Operation;
import com.chefcontrol.application.service.ChatService.OperationItem;
import com.chefcontrol.domain.chat.ChatAction;
import com.chefcontrol.domain.chat.ChatActionRepository;
import com.chefcontrol.domain.chat.ChatOperationType;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.stock.Production;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockCount;
import com.chefcontrol.domain.stock.StockMovement;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.waste.WasteEvent;
import com.chefcontrol.domain.waste.WasteReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock AIService aiService;
    @Mock ProductRepository productRepository;
    @Mock UnitRepository unitRepository;
    @Mock UnitConversionService unitConversionService;
    @Mock MenuItemRepository menuItemRepository;
    @Mock PurchaseService purchaseService;
    @Mock WasteService wasteService;
    @Mock SaleService saleService;
    @Mock StockCountService stockCountService;
    @Mock ProductionService productionService;
    @Mock RecipeRepository recipeRepository;
    @Mock AccountService accountService;
    @Mock StockService stockService;
    @Mock ChatActionRepository chatActionRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock ChatExchangeRepository chatExchangeRepository;
    @Mock StockBatchRepository stockBatchRepository;
    @Mock SaleRepository saleRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock PurchaseItemRepository purchaseItemRepository;
    @Mock WasteEventRepository wasteEventRepository;
    @Mock FoodCostService foodCostService;
    @Mock AlertRepository alertRepository;

    private static final UUID R = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");
    private static final Set<Permission> ALL = EnumSet.allOf(Permission.class);

    private final Unit kg = unit("Kilogramo", "kg");
    private final Unit g = unit("Gramo", "g");
    private final Unit l = unit("Litro", "l");
    private final Product comino = product("Comino", kg);
    private final Product tomate = product("Tomate", kg);
    private final Product mayonesa = product("Mayonesa", kg);
    private final MenuItem milanesa = MenuItem.builder().id(UUID.randomUUID()).restaurantId(R).name("Milanesa")
            .price(new BigDecimal("12000")).active(true).build();
    private ChatService service;
    private ChatHistoryService history;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        when(unitRepository.findAll()).thenReturn(List.of(kg, g, l));
        when(productRepository.findByRestaurantIdAndIsActiveTrue(eq(R), any()))
                .thenReturn(new Page<>(List.of(comino, tomate), 0, 2, 2));
        when(productRepository.findByRestaurantIdAndIsActiveTrue(eq(R), any()))
                .thenReturn(new Page<>(List.of(comino, tomate, mayonesa), 0, 3, 3));
        when(recipeRepository.findPreparationProductIds(R)).thenReturn(Set.of(mayonesa.getId()));
        when(productionService.recordProduction(any())).thenAnswer(i -> Production.builder().id(UUID.randomUUID()).build());
        for (Product p : List.of(comino, tomate, mayonesa)) {
            when(productRepository.findByIdAndRestaurantId(p.getId(), R)).thenReturn(Optional.of(p));
        }
        when(unitConversionService.convert(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(unitConversionService.convertPrice(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(currentUserProvider.currentUserId()).thenReturn(USER);
        when(chatActionRepository.save(any())).thenAnswer(i -> {
            ChatAction a = i.getArgument(0);
            return ChatAction.builder().id(a.getId() != null ? a.getId() : UUID.randomUUID())
                    .restaurantId(a.getRestaurantId()).userId(a.getUserId()).type(a.getType())
                    .entityId(a.getEntityId()).summary(a.getSummary()).createdAt(a.getCreatedAt())
                    .undoneAt(a.getUndoneAt()).build();
        });
        when(purchaseService.createPurchase(any())).thenReturn(Purchase.builder().id(UUID.randomUUID()).build());
        when(wasteService.createWasteEvent(any())).thenAnswer(i -> {
            WasteEvent e = new WasteEvent();
            e.setId(UUID.randomUUID());
            return e;
        });
        when(menuItemRepository.findByRestaurantIdAndActive(eq(R), eq(true), any()))
                .thenReturn(new Page<>(List.of(milanesa), 0, 1, 1));
        when(menuItemRepository.findByIdAndRestaurantId(milanesa.getId(), R)).thenReturn(Optional.of(milanesa));
        when(saleService.recordSale(any())).thenReturn(Sale.builder().id(UUID.randomUUID()).build());
        StockCount count = new StockCount();
        count.setId(UUID.randomUUID());
        when(stockCountService.createStockCount(any())).thenReturn(new StockCountService.StockCountResult(count, List.of()));
        history = new ChatHistoryService(chatExchangeRepository, chatActionRepository, currentUserProvider,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var queries = new ChatQueryService(stockService, stockBatchRepository, saleRepository, purchaseRepository,
                purchaseItemRepository, wasteEventRepository, foodCostService, alertRepository, accountService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new ChatService(aiService, new ChatRecipeService(aiService, recipeRepository, accountService), history,
                queries, productRepository, menuItemRepository, unitRepository,
                unitConversionService, purchaseService, wasteService, saleService, stockCountService,
                productionService, recipeRepository, accountService, chatActionRepository, currentUserProvider,
                TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void aPurchaseIsRegisteredRightAwayInTheProductsUnitWithItsUndo() {
        // "compré 500 g de comino a 3000 el kilo": la IA no convierte, el back sí.
        answer(95, false, op("purchase", item(comino, "500", "g", "3000", "kg")));
        when(unitConversionService.convert(new BigDecimal("500"), g.getId(), kg.getId())).thenReturn(new BigDecimal("0.500000"));

        var result = service.sendChatMessage("compré 500 g de comino a 3000 el kilo", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        var cmd = ArgumentCaptor.forClass(PurchaseService.CreatePurchaseCommand.class);
        verify(purchaseService).createPurchase(cmd.capture());
        var line = cmd.getValue().items().getFirst();
        assertEquals(0, new BigDecimal("0.5").compareTo(line.quantity()));
        assertEquals(kg.getId(), line.unitId());
        assertEquals(0, new BigDecimal("3000").compareTo(line.pricePerUnit()));
        verify(unitConversionService).convertPrice(new BigDecimal("3000"), kg.getId(), kg.getId());

        ChatAction action = result.actions().getFirst();
        assertEquals(ChatOperationType.PURCHASE, action.getType());
        assertEquals(USER, action.getUserId());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), action.undoableUntil());
        assertEquals("Compra: 0,5 kg Comino a $ 3.000", action.getSummary());
    }

    @Test
    void eachOperationNeedsThePermissionOfItsScreen() {
        answer(95, false, op("purchase", item(comino, "1", "kg", "3000", null)),
                op("waste", item(tomate, "2", null, null, null)));

        var result = service.sendChatMessage("compré comino y se pudrieron 2 kg de tomate", Set.of(Permission.WASTE_CREATE));

        assertEquals(ChatStatus.REGISTERED, result.status());
        assertEquals(List.of(ChatOperationType.WASTE), result.actions().stream().map(ChatAction::getType).toList());
        assertEquals(ErrorCode.CHAT_PERMISSION_MISSING, result.skipped().getFirst().errorCode());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void aHighUnitPriceIsAskedWithBothReadingsComputedByTheBack() {
        answer(95, false, op("purchase", item(comino, "5", "kg", "150000", null)));

        var result = service.sendChatMessage("5 kg de comino a 150 mil", ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status());
        assertEquals("«Comino»: ¿$ 150.000 es por kg o el total de los 5 kg? Por kg serían $ 750.000; "
                + "si es el total, $ 30.000 por kg.", result.message());
        // Un botón por lectura: confirmar contesta la pregunta. Primero la del modelo.
        assertEquals(List.of("Por kg · $ 750.000", "Total · $ 30.000 por kg"),
                result.proposals().stream().map(ChatService.Proposal::label).toList());
        assertEquals(0, new BigDecimal("150000").compareTo(priceOf(result.proposals().get(0))));
        assertEquals(0, new BigDecimal("30000").compareTo(priceOf(result.proposals().get(1))));
        verify(purchaseService, never()).createPurchase(any());
    }

    private static BigDecimal priceOf(ChatService.Proposal p) {
        return p.operations().getFirst().items().getFirst().pricePerUnit();
    }

    // ── Consultas (tanda 6) ──

    @Test
    void aQueryIsAnsweredByTheBackWithItsOwnNumbersForWholeArgentineDays() {
        // HOY es el 17/11 en Argentina: "ayer" es el 16/11 entero, de 00:00 a 23:59:59.999 de acá.
        Instant from = Instant.parse("2026-11-16T03:00:00Z");
        Instant to = Instant.parse("2026-11-17T02:59:59.999Z");
        when(saleRepository.countByRestaurantIdAndSoldAtBetween(R, from, to)).thenReturn(14L);
        when(saleRepository.sumTotalAmountByRestaurantIdAndSoldAtBetween(R, from, to)).thenReturn(new BigDecimal("120000"));
        answer(95, false, query("sales", "yesterday"));

        var result = service.sendChatMessage("¿cuánto vendimos ayer?", ALL);

        assertEquals(ChatStatus.ANSWERED, result.status());
        assertEquals("Ayer vendiste $ 120.000 en 14 ventas.", result.message());
    }

    @Test
    void periodsHaveNamesAndTheBackCountsTheDays() {
        LocalDate wednesday = LocalDate.of(2026, 11, 18);
        assertPeriod("this_week", null, wednesday, "2026-11-16", "2026-11-18");    // de lunes a hoy
        assertPeriod("last_week", null, wednesday, "2026-11-09", "2026-11-15");    // lunes a domingo
        assertPeriod("this_month", null, wednesday, "2026-11-01", "2026-11-18");
        assertPeriod("last_n_days", 10, wednesday, "2026-11-09", "2026-11-18");
        assertPeriod("last_month", null, LocalDate.of(2027, 1, 5), "2026-12-01", "2026-12-31");   // año nuevo
        assertPeriod("last_week", null, LocalDate.of(2026, 11, 16), "2026-11-09", "2026-11-15");  // el lunes mismo
        assertThrows(ChatQueryService.InvalidPeriod.class, () -> ChatQueryService.period(new InterpretedQuery("sales",
                "range", null, wednesday, wednesday.plusDays(1)), wednesday));                    // futuro
        assertThrows(ChatQueryService.InvalidPeriod.class, () -> ChatQueryService.period(new InterpretedQuery("sales",
                "range", null, LocalDate.of(2025, 1, 1), wednesday), wednesday));                 // más de un año
        assertThrows(ChatQueryService.InvalidPeriod.class, () -> ChatQueryService.period(new InterpretedQuery("sales",
                "range", null, wednesday, wednesday.minusDays(3)), wednesday));                   // desde > hasta
        for (String future : List.of("tomorrow", "next_week", "next_month")) {   // "¿cuánto vendimos mañana?"
            assertThrows(ChatQueryService.InvalidPeriod.class, () -> ChatQueryService.period(
                    new InterpretedQuery("sales", future, null, null, null), wednesday), future);
        }
        assertEquals(wednesday.plusDays(1), ChatQueryService.expirationLimit(
                new InterpretedQuery("expirations", "tomorrow", null, null, null), wednesday));     // "¿qué vence mañana?"
        assertEquals(LocalDate.of(2026, 11, 29), ChatQueryService.expirationLimit(
                new InterpretedQuery("expirations", "next_week", null, null, null), wednesday));
        assertEquals(wednesday.plusDays(7), ChatQueryService.expirationLimit(
                new InterpretedQuery("expirations", null, null, null, null), wednesday));
        assertEquals(LocalDate.of(2026, 11, 22), ChatQueryService.expirationLimit(
                new InterpretedQuery("expirations", "this_week", null, null, null), wednesday));   // hasta el domingo
    }

    private static void assertPeriod(String name, Integer days, LocalDate today, String from, String to) {
        var p = ChatQueryService.period(new InterpretedQuery("sales", name, days, null, null), today);
        assertEquals(LocalDate.parse(from), p.from(), name);
        assertEquals(LocalDate.parse(to), p.to(), name);
    }

    @Test
    void aQueryNeedsThePermissionToSeeItsScreen() {
        answer(95, false, query("sales", "today"));
        Set<Permission> noSales = EnumSet.allOf(Permission.class);
        noSales.remove(Permission.SALES_VIEW);

        var result = service.sendChatMessage("¿cuánto vendimos hoy?", noSales);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals("No tenés permiso para ver las ventas.", result.message());
        assertEquals(ErrorCode.CHAT_PERMISSION_MISSING, result.skipped().getFirst().errorCode());
        verify(saleRepository, never()).sumTotalAmountByRestaurantIdAndSoldAtBetween(any(), any(), any());
    }

    @Test
    void theFoodCostNeedsItsPlan() {
        answer(95, false, query("food_cost", "this_month"));
        doThrow(AppException.forbidden(ErrorCode.PLAN_FEATURE_REQUIRED, "no"))
                .when(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);

        var result = service.sendChatMessage("¿cómo viene el food cost?", ALL);

        assertEquals(ErrorCode.PLAN_FEATURE_REQUIRED, result.skipped().getFirst().errorCode());
        assertEquals("El food cost no está incluido en tu plan.", result.message());
        verify(foodCostService, never()).calculate(any(), any());
    }

    @Test
    void theLastPurchasePriceIsAsLoadedAndSaysWhenItHasNoIva() {
        when(purchaseItemRepository.findLastActiveLineOfProduct(comino.getId(), R)).thenReturn(Optional.of(
                new PurchaseItemRepository.LastPurchaseLine(new BigDecimal("0.5"), kg.getId(), new BigDecimal("3000"),
                        Instant.parse("2026-11-15T15:00:00Z"), false)));
        answer(95, false, query("last_purchase_price", null, item(comino, "1", null, null, null)));

        var result = service.sendChatMessage("¿a cuánto compramos el comino?", ALL);

        assertEquals("La última compra de Comino: 0,5 kg a $ 3.000 el kg (sin IVA), el 15/11.", result.message());
    }

    @Test
    void aMixedMessageRegistersAndAnswersWithTheStatusOfTheOperation() {
        tomate.setMinStock(new BigDecimal("2"));
        when(stockService.getCurrentStock(tomate.getId())).thenReturn(new BigDecimal("1.5"));
        answer(95, false, op("purchase", item(comino, "1", "kg", "3000", null)),
                query("stock", null, item(tomate, "1", null, null, null)));

        var result = service.sendChatMessage("compré 1 kg de comino a 3000, ¿cuánto tomate queda?", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        assertTrue(result.message().endsWith("Hay 1,5 kg de Tomate. Está por debajo del mínimo (2 kg)."), result.message());
    }

    @Test
    void aQueryOutsideTheListOrWithABadPeriodSaysSo() {
        answer(95, false, new InterpretedOperation("query", List.of(), null, null,
                new InterpretedQuery(null, null, null, null, null)));
        var result = service.sendChatMessage("¿quién es el mejor proveedor?", ALL);
        assertEquals(ChatStatus.NOT_SUPPORTED, result.status());
        assertEquals(ErrorCode.CHAT_OPERATION_NOT_SUPPORTED, result.skipped().getFirst().errorCode());

        answer(95, false, new InterpretedOperation("query", List.of(), null, null, new InterpretedQuery("sales", "range",
                null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))));
        assertEquals(ErrorCode.CHAT_INVALID_PERIOD,
                service.sendChatMessage("¿cuánto vendimos en diciembre?", ALL).skipped().getFirst().errorCode());
    }

    private static InterpretedOperation query(String name, String period, InterpretedItem... items) {
        return new InterpretedOperation("query", List.of(items), null, null,
                new InterpretedQuery(name, period, null, null, null));
    }

    // ── Memoria (tanda 5) ──

    @Test
    void thePromptGetsTodaysConversationWithItsActionsNumberedAndTheExchangeIsRemembered() {
        ChatAction bought = stored(ChatOperationType.PURCHASE, USER, NOW.minusSeconds(60));
        conversation(exchange("viejo", List.of()), newConversation(),
                exchange("compré 3 kg de comino a 3000", List.of(bought.getId())));
        answer(95, false);

        var result = service.sendChatMessage("gracias", ALL);

        var request = ArgumentCaptor.forClass(AIService.ChatMessageRequest.class);
        verify(aiService).interpretChatMessage(request.capture());
        var turns = request.getValue().history();
        assertEquals(1, turns.size());   // "Nueva conversación" cortó lo anterior
        assertEquals("compré 3 kg de comino a 3000", turns.getFirst().userMessage());
        assertEquals(List.of(new HistoryAction(1, "x", false)), turns.getFirst().actions());
        verify(chatExchangeRepository).saveChatExchange(argThat(x -> x.kind() == Kind.MESSAGE
                && "gracias".equals(x.userMessage()) && x.status() == result.status() && x.userId().equals(USER)));
    }

    @Test
    void aCorrectionUndoesTheActionAndRegistersTheNewOne() {
        ChatAction bought = stored(ChatOperationType.PURCHASE, USER, NOW.minusSeconds(60));
        conversation(exchange("compré 3 kg de comino a 3000", List.of(bought.getId())));
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "3000", null)),
                "no, eran 5 kg", 1));

        var result = service.sendChatMessage("no, eran 5 kg", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        verify(purchaseService).reversePurchase(bought.getEntityId());
        verify(purchaseService).createPurchase(any());
        assertEquals(List.of(bought.getId()), result.undone().stream().map(ChatAction::getId).toList());
        assertTrue(result.message().startsWith("Corregí: deshice «x». Listo: Compra: 5 kg Comino"), result.message());
    }

    @Test
    void aCorrectionThatCannotUndoRegistersNothing() {
        ChatAction someoneElses = stored(ChatOperationType.PURCHASE, UUID.randomUUID(), NOW.minusSeconds(60));
        conversation(exchange("compré 3 kg de comino a 3000", List.of(someoneElses.getId())));
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "3000", null)),
                "no, eran 5 kg", 1));
        Set<Permission> noDelete = EnumSet.allOf(Permission.class);
        noDelete.remove(Permission.PURCHASES_DELETE);

        var result = service.sendChatMessage("no, eran 5 kg", noDelete);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals(ErrorCode.CHAT_UNDO_NOT_ALLOWED, result.skipped().getFirst().errorCode());
        assertTrue(result.message().contains("No corregí nada"), result.message());
        verify(purchaseService, never()).reversePurchase(any());
        verify(purchaseService, never()).createPurchase(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no, eran 5 kg", "me equivoqué, eran 5 kg", "perdón, eran 5 kg", "eran 5 kg", "era 5 kg"})
    void aCorrectionTheModelMissedIsAskedInsteadOfRegisteredTwice(String said) {
        // Una por cada palabra de CORRECTION_WORDS. Pasó con gemini-3.1-flash-lite: sin corrects, quedaban las dos compras.
        ChatAction bought = storedWithSummary(ChatOperationType.PURCHASE, "Compra: 3 kg Comino a $ 3.000");
        conversation(exchange("compré 3 kg de comino a 3000", List.of(bought.getId())));
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "3000", null)), said));

        var result = service.sendChatMessage(said, ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status(), result.message());
        assertEquals("¿Corrijo «Compra: 3 kg Comino a $ 3.000» o lo registro aparte?", result.message());
        assertEquals(List.of("Corregir «Compra: 3 kg Comino a $ 3.000»", "Registrar otra"),
                result.proposals().stream().map(ChatService.Proposal::label).toList());
        assertEquals(bought.getId(), result.proposals().get(0).operations().getFirst().correctsActionId());
        assertNull(result.proposals().get(1).operations().getFirst().correctsActionId());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void everyCorrectionWordHasItsCase() {
        assertEquals(5, ChatService.CORRECTION_WORDS.size(), "una palabra nueva necesita su caso en el test de arriba");
    }

    @Test
    void noAloneCancelsThePendingQuestionButNoWithMoreIsACorrection() {
        // Del lado de CANCEL_WORDS: "no" solo, con una pregunta pendiente, cancela sin llamar al modelo.
        conversation(pending());
        assertEquals("Listo, no registré nada.", service.sendChatMessage("no", ALL).message());
        verify(aiService, never()).interpretChatMessage(any());
        // Del lado del resguardo: "no, eran 5 kg" va al modelo y, si no marca la corrección, se pregunta.
        ChatAction bought = storedWithSummary(ChatOperationType.PURCHASE, "Compra: 3 kg Comino a $ 3.000");
        conversation(exchange("compré 3 kg de comino a 3000", List.of(bought.getId())));
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "3000", null)),
                "no, eran 5 kg"));
        assertEquals(ChatStatus.NEEDS_CONFIRMATION, service.sendChatMessage("no, eran 5 kg", ALL).status());
    }

    @Test
    void aMessageThatStartsWithNoButIsAboutSomethingElseRegisters() {
        // Sin una acción del mismo insumo en la conversación, no hay qué corregir.
        ChatAction other = storedWithSummary(ChatOperationType.PURCHASE, "Compra: 3 kg Tomate a $ 1.500");
        conversation(exchange("compré 3 kg de tomate a 1500", List.of(other.getId())));
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "1", "kg", "3000", null)),
                "no hay más comino, compré 1 kg a 3000"));

        assertEquals(ChatStatus.REGISTERED, service.sendChatMessage("no hay más comino, compré 1 kg a 3000", ALL).status());
    }

    @Test
    void answeringTheGuardByTextPicksTheRightAlternative() {
        ChatAction bought = storedWithSummary(ChatOperationType.PURCHASE, "Compra: 3 kg Comino a $ 3.000");
        conversation(exchange("compré 3 kg de comino a 3000", List.of(bought.getId())));
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "3000", null)),
                "no, eran 5 kg"));
        service.sendChatMessage("no, eran 5 kg", ALL);
        var asked = ArgumentCaptor.forClass(ChatExchange.class);
        verify(chatExchangeRepository).saveChatExchange(asked.capture());
        conversation(exchange("compré 3 kg de comino a 3000", List.of(bought.getId())), asked.getValue());
        // "Es otra": la misma operación sin corrects → "Registrar otra", sin deshacer nada.
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "3000", null)), "es otra"));

        var result = service.sendChatMessage("es otra", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status(), result.message());
        verify(purchaseService, never()).reversePurchase(any());
        assertTrue(result.undone().isEmpty());
    }

    private ChatAction storedWithSummary(ChatOperationType type, String summary) {
        ChatAction a = ChatAction.builder().id(UUID.randomUUID()).restaurantId(R).userId(USER).type(type)
                .entityId(UUID.randomUUID()).summary(summary).createdAt(NOW.minusSeconds(60)).build();
        when(chatActionRepository.findByIdAndRestaurantId(a.getId(), R)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void onlyAnActionShownToTheModelCanBeCorrected() {
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "3000", null)),
                "no, eran 5 kg", 7));

        var result = service.sendChatMessage("no, eran 5 kg", ALL);

        assertEquals(ErrorCode.CHAT_ACTION_NOT_FOUND, result.skipped().getFirst().errorCode());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void answeringThePriceQuestionByTextRegistersThatReadingWithoutAskingAgain() {
        answer(95, false, op("purchase", item(comino, "5", "kg", "150000", null)));
        assertEquals(ChatStatus.NEEDS_CONFIRMATION, service.sendChatMessage("5 kg de comino a 150 mil", ALL).status());
        var asked = ArgumentCaptor.forClass(ChatExchange.class);
        verify(chatExchangeRepository).saveChatExchange(asked.capture());
        conversation(asked.getValue());
        // "Es por kilo": el mismo número, por unidad. Sin la memoria, la regla de los $100.000 volvería a preguntar.
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "150000", null)),
                "es por kilo"));

        var result = service.sendChatMessage("es por kilo", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status(), result.message());
        var cmd = ArgumentCaptor.forClass(PurchaseService.CreatePurchaseCommand.class);
        verify(purchaseService).createPurchase(cmd.capture());
        assertEquals(0, new BigDecimal("150000").compareTo(cmd.getValue().items().getFirst().pricePerUnit()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "No.", "cancelá", "cancelalo", "cancelar", "dejá", "dejalo", "olvidalo", "olvidate",
            "nada", "mejor no", "dejalo así", "no, gracias"})
    void aNoToThePendingQuestionCancelsItWithoutCallingTheModel(String said) {
        // Una por cada palabra de CANCEL_WORDS.
        conversation(pending());

        var result = service.sendChatMessage(said, ALL);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals("Listo, no registré nada.", result.message());
        verify(aiService, never()).interpretChatMessage(any());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void everyCancelWordHasItsCase() {
        assertEquals(12, ChatService.CANCEL_WORDS.size(), "una palabra nueva necesita su caso en el test de arriba");
    }

    @Test
    void aNoWithMoreIsNotACancellation() {
        conversation(pending());
        answer(95, false);

        service.sendChatMessage("no, eran 5 kg", ALL);

        verify(aiService).interpretChatMessage(any());
    }

    @Test
    void afterCancellingAYesRegistersNothing() {
        conversation(pending());
        assertEquals("Listo, no registré nada.", history.cancelChatProposal().message());
        var cancelled = ArgumentCaptor.forClass(ChatExchange.class);
        verify(chatExchangeRepository).saveChatExchange(cancelled.capture());
        assertEquals(Kind.CANCELLATION, cancelled.getValue().kind());
        conversation(pending(), cancelled.getValue());
        answer(95, false, op("purchase", item(comino, "5", "kg", "150000", null)));

        var result = service.sendChatMessage("sí", ALL);

        // Ya no contesta nada y no va al modelo: con la pregunta cancelada en el historial, el "sí" la revivía.
        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals("No tengo nada pendiente para confirmar.", result.message());
        verify(aiService, never()).interpretChatMessage(any());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void aCancelledQuestionDoesNotGoBackToThePrompt() {
        conversation(pending());
        var cancelled = ArgumentCaptor.forClass(ChatExchange.class);
        history.cancelChatProposal();
        verify(chatExchangeRepository).saveChatExchange(cancelled.capture());
        conversation(exchange("compré 3 kg de tomate a 3000", List.of()), pending(), cancelled.getValue());
        answer(95, false);

        service.sendChatMessage("es por kilo", ALL);

        var request = ArgumentCaptor.forClass(AIService.ChatMessageRequest.class);
        verify(aiService).interpretChatMessage(request.capture());
        assertEquals(List.of("compré 3 kg de tomate a 3000"),
                request.getValue().history().stream().map(AIService.ChatHistoryTurn::userMessage).toList());
    }

    @Test
    void aPriceThatIsNotInAnyTextIsNotRegistered() {
        // Lo que pasó después de un "Cancelar": el modelo devolvió $ 40.000 (120 mil / 3 kg), un número que nadie escribió.
        answer(95, false, op("purchase", item(comino, "3", "kg", "40000", null)));

        var result = service.sendChatMessage("compré 3 kg de comino a 120 mil", ALL);

        assertEquals(ErrorCode.CHAT_MISSING_PRICE, result.skipped().getFirst().errorCode());
        verify(purchaseService, never()).createPurchase(any());
        assertTrue(ChatService.numberSaid("a $120.000", new BigDecimal("120000")));
        assertTrue(ChatService.numberSaid("a 120 mil", new BigDecimal("120000")));
        assertTrue(ChatService.numberSaid("a 1500,50 el kilo", new BigDecimal("1500.5")));
        assertFalse(ChatService.numberSaid("sí", new BigDecimal("40000")));
    }

    @Test
    void aPriceIsComparedAgainstEachWrittenNumber() {
        // Con los dígitos de todo el texto juntos quedaba "101000", y un 100 calculado por el modelo pasaba.
        assertFalse(ChatService.numberSaid("10 kg de harina, 1000 en total", new BigDecimal("100")));
        assertTrue(ChatService.numberSaid("10 kg de harina, 1000 en total", new BigDecimal("1000")));
        assertTrue(ChatService.numberSaid("a 9.500", new BigDecimal("9500")));
        assertTrue(ChatService.numberSaid("a $9500", new BigDecimal("9500")));
        assertTrue(ChatService.numberSaid("a 9500,50", new BigDecimal("9500")));
        assertTrue(ChatService.numberSaid("a 1.200.000 el lote", new BigDecimal("1200000")));
        assertTrue(ChatService.numberSaid("a 12,5 mil", new BigDecimal("12500")));
        assertTrue(ChatService.numberSaid("a 120 MIL", new BigDecimal("120000")));
        assertFalse(ChatService.numberSaid("a 120 mil", new BigDecimal("120")));
        assertFalse(ChatService.numberSaid("2 milanesas a 9", new BigDecimal("2000")));
    }

    @Test
    void aYesAloneIsAnAffirmationAndOnlyThat() {
        assertTrue(ChatService.isAffirmation("Sí, dale"));
        assertTrue(ChatService.isAffirmation("ok confirmo"));
        assertFalse(ChatService.isAffirmation("sí, 3 kg de tomate"));
        assertFalse(ChatService.isAffirmation("gracias"));
    }

    @Test
    void aNotUnderstoodDoesNotLeaveThePendingQuestionBehind() {
        var failed = new ChatExchange(UUID.randomUUID(), R, USER, Kind.MESSAGE, "es el", ChatStatus.NOT_UNDERSTOOD,
                new StoredReply("No registré nada: no pude interpretar el mensaje.", List.of(), List.of(), List.of(),
                        List.of(), null), NOW.minusSeconds(10));
        conversation(pending(), failed);
        answer(95, false, new InterpretedOperation("purchase", List.of(item(comino, "5", "kg", "150000", null)),
                "es por kilo"));

        assertEquals(ChatStatus.REGISTERED, service.sendChatMessage("es por kilo", ALL).status());
    }

    @Test
    void notUnderstoodAlwaysSaysNothingWasRegistered() {
        // El modelo contestó "Registrado." sin ninguna operación: el texto no depende de él.
        when(aiService.interpretChatMessage(any())).thenReturn(new ChatInterpretation(90, false, "Registrado.", List.of()));
        assertEquals("No registré nada: no entendí el mensaje. Podés registrar algo («compré 10 kg de harina a $2.000») "
                + "o preguntar («¿cuánto vendimos ayer?»).", service.sendChatMessage("registralo", ALL).message());

        when(aiService.interpretChatMessage(any())).thenReturn(new ChatInterpretation(90, false, "¿De qué plato hablás?",
                List.of()));
        assertEquals("No registré nada. ¿De qué plato hablás?", service.sendChatMessage("la de siempre", ALL).message());
    }

    @Test
    void aPriceWithASaysPerUnitEvenIfTheModelDoubted() {
        answer(95, false, new InterpretedOperation("purchase", List.of(new InterpretedItem(tomate.getId(), null, "tomate",
                new BigDecimal("3"), "kg", new BigDecimal("1500"), "unclear", null, null, null)), "compré 3 kg de tomate a 1500"));

        var result = service.sendChatMessage("compré 3 kg de tomate a 1500", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status(), result.message());
        assertTrue(ChatService.saysPerUnit("5 kg a $ 25 mil"));
        assertFalse(ChatService.saysPerUnit("5 kg, 25 mil"));
        assertFalse(ChatService.saysPerUnit("lata 400 g"));
    }

    @Test
    void cancellingWithNothingPendingSavesNothing() {
        assertEquals("No había nada para cancelar.", history.cancelChatProposal().message());
        verify(chatExchangeRepository, never()).saveChatExchange(any());
    }

    /** "5 kg de comino a 150 mil" preguntado: dos alternativas, por kg y total. */
    private ChatExchange pending() {
        var item = new OperationItem(comino.getId(), null, "Comino", new BigDecimal("5"), "kg", new BigDecimal("150000"),
                null, null);
        return new ChatExchange(UUID.randomUUID(), R, USER, Kind.MESSAGE, "compré 5 kg de comino a 150 mil",
                ChatStatus.NEEDS_CONFIRMATION, new StoredReply("¿Por kg o el total?", List.of(), List.of(),
                List.of(new ChatService.Proposal("Por kg · $ 750.000", List.of(new Operation(ChatOperationType.PURCHASE,
                        List.of(item))))), List.of(), null), NOW.minusSeconds(20));
    }

    @Test
    void theHistoryShowsEachActionAsItIsNow() {
        ChatAction bought = stored(ChatOperationType.PURCHASE, USER, NOW.minusSeconds(60));
        bought.markUndone(NOW);
        conversation(exchange("compré 3 kg de comino a 3000", List.of(bought.getId())));

        var shown = history.listChatMessages();

        assertEquals(1, shown.size());
        assertEquals("compré 3 kg de comino a 3000", shown.getFirst().userMessage());
        assertNotNull(shown.getFirst().response().actions().getFirst().getUndoneAt());
    }

    @Test
    void aNewConversationIsAMarkerThatDeletesNothing() {
        history.startChatConversation();

        verify(chatExchangeRepository).saveChatExchange(argThat(x -> x.kind() == Kind.NEW_CONVERSATION
                && x.userMessage() == null && x.reply() == null));
        verify(chatExchangeRepository, never()).deleteChatExchangesBefore(any());
    }

    @Test
    void thePurgeDeletesWhatIsOlderThanTheRetention() {
        history.purgeChatHistory(30);

        verify(chatExchangeRepository).deleteChatExchangesBefore(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void aConfirmationIsRememberedToo() {
        service.confirmChatOperations(List.of(new Operation(ChatOperationType.WASTE, List.of(new OperationItem(
                tomate.getId(), null, "Tomate", BigDecimal.ONE, "kg", null, null, WasteReason.DAMAGED)))), ALL);

        verify(chatExchangeRepository).saveChatExchange(argThat(x -> x.kind() == Kind.CONFIRMATION
                && x.userMessage() == null && x.status() == ChatStatus.REGISTERED));
    }

    private void conversation(ChatExchange... exchanges) {
        when(chatExchangeRepository.findChatExchangesSince(eq(USER), eq(R), any())).thenReturn(List.of(exchanges));
        when(chatActionRepository.findAllByIdInAndRestaurantId(any(), eq(R))).thenAnswer(i -> {
            java.util.Collection<UUID> ids = i.getArgument(0);
            return ids.stream().map(id -> chatActionRepository.findByIdAndRestaurantId(id, R).orElse(null))
                    .filter(java.util.Objects::nonNull).toList();
        });
    }

    private static ChatExchange exchange(String said, List<UUID> actionIds) {
        return new ChatExchange(UUID.randomUUID(), R, USER, Kind.MESSAGE, said, ChatStatus.REGISTERED,
                new StoredReply("Listo.", actionIds, List.of(), List.of(), List.of(), null), NOW.minusSeconds(30));
    }

    private static ChatExchange newConversation() {
        return new ChatExchange(UUID.randomUUID(), R, USER, Kind.NEW_CONVERSATION, null, null, null, NOW.minusSeconds(40));
    }

    @Test
    void aUnitNobodySaidIsAskedBeforeRegistering() {
        // Pasó con la IA real: "100 g de harina" volvió como 100 kg una vez de tres.
        answer(95, false, op("purchase", item(comino, "100", "kg", "3000", null)));

        var result = service.sendChatMessage("compré 200 g de tomate y 100 g de comino a 3000", ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status());
        assertTrue(result.message().contains("«Comino»: entendí 100 kg, ¿es así?"), result.message());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void unitsAreRecognizedTheWayTheyAreSaid() {
        assertTrue(ChatService.unitSaid("medio kilo de comino", kg));
        assertTrue(ChatService.unitSaid("500g de comino", g));
        assertTrue(ChatService.unitSaid("2 litros de leche", l));
        assertTrue(ChatService.unitSaid("3 huevos", unit("Unidad", "u")));
        assertFalse(ChatService.unitSaid("100 g de harina", kg));
    }

    @Test
    void aTotalIsDividedByTheBackNeverByTheModel() {
        // "5 kg de comino, 150 mil en total": el modelo dice "total" y devuelve 150000 tal cual; divide el back.
        answer(95, false, op("purchase", new InterpretedItem(comino.getId(), null, "comino", new BigDecimal("5"), "kg",
                new BigDecimal("150000"), "total", null, null, null)));

        var result = service.sendChatMessage("5 kg de comino, 150 mil en total", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        var cmd = ArgumentCaptor.forClass(PurchaseService.CreatePurchaseCommand.class);
        verify(purchaseService).createPurchase(cmd.capture());
        assertEquals(0, new BigDecimal("30000").compareTo(cmd.getValue().items().getFirst().pricePerUnit()));
    }

    @Test
    void aNumberWithoutAUnitIsAskedUnlessTheProductGoesInUnits() {
        // Pasó en el front: "se tiraron 100 de harina" → "Merma: 100 kg Harina" y el stock en −90.
        answer(95, false, op("waste", item(tomate, "100", null, null, "DAMAGED")));

        var result = service.sendChatMessage("se tiraron 100 de tomate", ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status());
        assertTrue(result.message().contains("«Tomate»: entendí 100 kg, ¿es así?"), result.message());
        verify(wasteService, never()).createWasteEvent(any());
    }

    @Test
    void aTotalOfOneUnitIsTheSameAsItsPriceAndIsRegistered() {
        answer(95, false, op("purchase", new InterpretedItem(comino.getId(), null, "comino", BigDecimal.ONE, "kg",
                new BigDecimal("3000"), "total", null, null, null)));

        assertEquals(ChatStatus.REGISTERED, service.sendChatMessage("1 kg de comino, 3000 todo", ALL).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"5 kg de comino, 7500 en total", "5 kg de comino a 7500 todo", "5 kg de comino, me salió 7500",
            "5 kg de comino, me costó 7500", "pagué 7500 por 5 kg de comino", "la bolsa de 5 kg de comino a 7500",
            "el lote de 5 kg de comino a 7500"})
    void aTotalTheTextBacksIsDividedWithoutAsking(String said) {
        // Una por cada palabra de TOTAL_WORDS: la clasificación del modelo se acepta si el texto la respalda.
        answer(95, false, new InterpretedOperation("purchase", List.of(new InterpretedItem(comino.getId(), null, "comino",
                new BigDecimal("5"), "kg", new BigDecimal("7500"), "total", null, null, null)), said));

        var result = service.sendChatMessage(said, ALL);

        assertEquals(ChatStatus.REGISTERED, result.status(), result.message());
        var cmd = ArgumentCaptor.forClass(PurchaseService.CreatePurchaseCommand.class);
        verify(purchaseService).createPurchase(cmd.capture());
        assertEquals(0, new BigDecimal("1500").compareTo(cmd.getValue().items().getFirst().pricePerUnit()));
    }

    @Test
    void everyTotalWordHasItsCase() {
        assertEquals(7, ChatService.TOTAL_WORDS.size(), "una palabra nueva necesita su caso en el test de arriba");
    }

    @Test
    void aTotalTheTextDoesNotBackIsAsked() {
        // "a 1.500" que el modelo clasificó como total: dividirlo en silencio entraría a $300 el kg.
        answer(95, false, new InterpretedOperation("purchase", List.of(new InterpretedItem(comino.getId(), null, "comino",
                new BigDecimal("5"), "kg", new BigDecimal("1500"), "total", null, null, null)), "5 kg de comino a 1500"));

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, service.sendChatMessage("5 kg de comino a 1500", ALL).status());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void anUnclearPriceIsAsked() {
        answer(95, false, op("purchase", new InterpretedItem(comino.getId(), null, "comino", new BigDecimal("2"), "kg",
                new BigDecimal("6000"), "unclear", null, null, null)));

        var result = service.sendChatMessage("2 kg de comino, 6000", ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status());
        assertTrue(result.message().startsWith("«Comino»: ¿$ 6.000 es por kg"), result.message());
    }

    @Test
    void lowConfidenceIsAskedEvenIfTheModelDidNotFlagIt() {
        answer(60, false, op("waste", item(tomate, "2", "kg", null, null)));

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, service.sendChatMessage("tomate 2", ALL).status());
        verify(wasteService, never()).createWasteEvent(any());
    }

    @Test
    void aProductOutsideTheCatalogIsNeverRegistered() {
        answer(95, false, op("purchase", new InterpretedItem(null, null, "tomate cherry", BigDecimal.ONE, "kg",
                new BigDecimal("4000"), "unit", null, null, null)));

        var result = service.sendChatMessage("1 kg de tomate cherry a 4000", ALL);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals(ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG, result.skipped().getFirst().errorCode());
        assertEquals("tomate cherry", result.skipped().getFirst().name());
    }

    @Test
    void aPurchaseWithoutPriceIsNotRegistered() {
        answer(95, false, op("purchase", item(comino, "1", "kg", null, null)));

        var result = service.sendChatMessage("compré 1 kg de comino", ALL);

        assertEquals(ErrorCode.CHAT_MISSING_PRICE, result.skipped().getFirst().errorCode());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void aUnitThatDoesNotConvertIsSkipped() {
        answer(95, false, op("waste", item(tomate, "2", "l", null, null)));
        when(unitConversionService.convert(any(), eq(l.getId()), eq(kg.getId())))
                .thenThrow(AppException.badRequest(ErrorCode.UNIT_CONVERSION_NOT_FOUND, "no"));

        var result = service.sendChatMessage("se tiraron 2 litros de tomate", ALL);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals(ErrorCode.UNIT_CONVERSION_NOT_FOUND, result.skipped().getFirst().errorCode());
    }

    @Test
    void operationsWithoutExecutionYetAreNotSupported() {
        answer(95, false, op("query", List.of()));

        var result = service.sendChatMessage("¿cuánta harina queda?", ALL);

        assertEquals(ChatStatus.NOT_SUPPORTED, result.status());
        assertEquals(1, result.skipped().size());
    }

    // ── Recetas ──

    @Test
    void editingTheRecipeOfADishGoesToThePreviewPerPortionAndMarkedToReplace() {
        // La receta de hoy es para 2 porciones: el modelo la recibe por porción, como la guarda el import.
        answer(95, false, op("recipe", dish(milanesa, "1")));
        when(recipeRepository.findByMenuItemIdAndRestaurantId(milanesa.getId(), R)).thenReturn(Optional.of(
                Recipe.builder().menuItemId(milanesa.getId()).restaurantId(R).servings(2).items(List.of(
                        RecipeItem.builder().productId(tomate.getId()).quantity(new BigDecimal("400")).unitId(g.getId())
                                .build())).build()));
        when(aiService.interpretRecipeMessage(any())).thenReturn(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(), List.of(new RecipeRow(1, "Milanesa", null, null, "tomates", new BigDecimal("300"), "g")),
                List.of(), List.of(new IngredientMatch(1, "tomates", "Tomate")), List.of()));

        var result = service.sendChatMessage("subí el tomate de la milanesa a 300 g", ALL);

        var request = ArgumentCaptor.forClass(RecipeMessageRequest.class);
        verify(aiService).interpretRecipeMessage(request.capture());
        assertEquals("Milanesa", request.getValue().recipeName());
        assertFalse(request.getValue().preparation());
        var line = request.getValue().current().ingredients().getFirst();
        assertEquals("Tomate", line.ingredient());
        assertEquals(0, new BigDecimal("200").compareTo(line.quantity()));
        assertEquals("g", line.unit());

        assertEquals(ChatStatus.RECIPE_PREVIEW, result.status());
        assertTrue(result.message().contains("«Milanesa»"), result.message());
        assertEquals(List.of(milanesa.getId()), result.recipe().replaceRecipeMenuItemIds());
        // El back aplica lo que reconoció el modelo: la fila va con el nombre del catálogo.
        assertEquals("Tomate", result.recipe().catalogImport().recipes().getFirst().ingredient());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void aMixedMessageRegistersTheOperationsAndBringsTheRecipe() {
        answer(95, false, op("purchase", item(comino, "1", "kg", "3000", null)),
                op("recipe", new InterpretedItem(null, null, "napolitana", null, null, null, null, null, null, null)));
        when(aiService.interpretRecipeMessage(any())).thenReturn(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(), List.of(new RecipeRow(1, "Napolitana", null, null, "Tomate", new BigDecimal("0.1"), "kg")),
                List.of(), List.of(), List.of()));

        var result = service.sendChatMessage("compré 1 kg de comino a 3000 y la napolitana lleva 100 g de tomate", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        assertEquals(1, result.actions().size());
        assertEquals("Napolitana", result.recipe().name());
        assertTrue(result.recipe().replaceRecipeMenuItemIds().isEmpty());
        assertTrue(result.message().contains("Armé la receta de «Napolitana»"), result.message());
        var request = ArgumentCaptor.forClass(RecipeMessageRequest.class);
        verify(aiService).interpretRecipeMessage(request.capture());
        assertNull(request.getValue().current());
    }

    @Test
    void aRecipeNeedsTheSamePermissionsAsTheImport() {
        answer(95, false, op("recipe", dish(milanesa, "1")));
        Set<Permission> noMenuUpdate = EnumSet.allOf(Permission.class);
        noMenuUpdate.remove(Permission.MENU_UPDATE);

        var result = service.sendChatMessage("la milanesa lleva 200 g de tomate", noMenuUpdate);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals(ErrorCode.CHAT_PERMISSION_MISSING, result.skipped().getFirst().errorCode());
        assertNull(result.skipped().getFirst().type());
        assertNull(result.recipe());
        verify(aiService, never()).interpretRecipeMessage(any());
    }

    @Test
    void aRecipeNeedsThePlanWithRecipes() {
        answer(95, false, op("recipe", dish(milanesa, "1")));
        doThrow(AppException.forbidden(ErrorCode.PLAN_FEATURE_REQUIRED, "no"))
                .when(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);

        var result = service.sendChatMessage("la milanesa lleva 200 g de tomate", ALL);

        assertEquals(ErrorCode.PLAN_FEATURE_REQUIRED, result.skipped().getFirst().errorCode());
        verify(aiService, never()).interpretRecipeMessage(any());
    }

    @Test
    void oneRecipePerMessage() {
        answer(95, false, op("recipe", dish(milanesa, "1"),
                new InterpretedItem(null, null, "flan", null, null, null, null, null, null, null)));
        when(aiService.interpretRecipeMessage(any())).thenReturn(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(), List.of(new RecipeRow(1, "Milanesa", null, null, "Tomate", BigDecimal.ONE, "kg")),
                List.of(), List.of(), List.of()));

        var result = service.sendChatMessage("la milanesa lleva 1 kg de tomate y el flan 2 huevos", ALL);

        assertEquals(ChatStatus.RECIPE_PREVIEW, result.status());
        assertEquals("Milanesa", result.recipe().name());
        assertEquals(ErrorCode.CHAT_OPERATION_NOT_SUPPORTED, result.skipped().getFirst().errorCode());
        assertTrue(result.message().contains("Una receta por mensaje"), result.message());
        verify(aiService, times(1)).interpretRecipeMessage(any());
    }

    @Test
    void anExistingPreparationKeepsItsYieldWhenTheModelDoesNotRepeatIt() {
        answer(95, false, op("preparation_recipe", item(mayonesa, "1", null, null, null)));
        when(recipeRepository.findByProductIdAndRestaurantId(mayonesa.getId(), R)).thenReturn(Optional.of(
                Recipe.builder().productId(mayonesa.getId()).restaurantId(R).yieldQuantity(new BigDecimal("1.5"))
                        .items(List.of(RecipeItem.builder().productId(tomate.getId()).quantity(new BigDecimal("2"))
                                .unitId(kg.getId()).build())).build()));
        when(aiService.interpretRecipeMessage(any())).thenReturn(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(new PreparationRow(1, "Mayonesa", null, null, "Tomate", new BigDecimal("3"), "kg")),
                List.of(), List.of(), List.of(), List.of()));

        var result = service.sendChatMessage("a la mayonesa ponele 3 kg de tomate", ALL);

        var request = ArgumentCaptor.forClass(RecipeMessageRequest.class);
        verify(aiService).interpretRecipeMessage(request.capture());
        assertTrue(request.getValue().preparation());
        assertEquals(0, new BigDecimal("2").compareTo(request.getValue().current().ingredients().getFirst().quantity()));
        var row = result.recipe().catalogImport().preparations().getFirst();
        assertEquals(0, new BigDecimal("1.5").compareTo(row.yieldQuantity()));
        assertEquals("kg", row.yieldUnit());
        assertEquals(List.of(mayonesa.getId()), result.recipe().replacePreparationProductIds());
    }

    @Test
    void whatTheModelRecognizedDecidesTheRecipeNotTheTypeItChose() {
        // Pasó en el e2e: receta "de plato" con el id de una preparación. Por el tipo, salía un plato nuevo duplicado.
        answer(95, false, op("recipe", item(mayonesa, "1", null, null, null)));
        when(aiService.interpretRecipeMessage(any())).thenReturn(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(new PreparationRow(1, "Mayonesa", BigDecimal.ONE, "kg", "Huevo", new BigDecimal("6"), "u")),
                List.of(), List.of(), List.of(), List.of()));

        var result = service.sendChatMessage("a la mayonesa agregale 6 huevos", ALL);

        var request = ArgumentCaptor.forClass(RecipeMessageRequest.class);
        verify(aiService).interpretRecipeMessage(request.capture());
        assertTrue(request.getValue().preparation());
        assertEquals(List.of(mayonesa.getId()), result.recipe().replacePreparationProductIds());
        assertTrue(result.recipe().replaceRecipeMenuItemIds().isEmpty());
    }

    @Test
    void withoutIdsTheNameFindsTheExistingRecipe() {
        answer(95, false, op("recipe", new InterpretedItem(null, null, "MILANESA", null, null, null, null, null, null, null)),
                op("recipe", new InterpretedItem(null, null, "comino", null, null, null, null, null, null, null)));
        when(aiService.interpretRecipeMessage(any())).thenReturn(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(), List.of(new RecipeRow(1, "Milanesa", null, null, "Tomate", BigDecimal.ONE, "kg")),
                List.of(), List.of(), List.of()));

        var result = service.sendChatMessage("la milanesa lleva 1 kg de tomate", ALL);

        assertEquals("Milanesa", result.recipe().name());
        assertEquals(List.of(milanesa.getId()), result.recipe().replaceRecipeMenuItemIds());
    }

    @Test
    void theRecipeCallGetsOnlyItsPartOfTheMessageAndAWrongUnitIsWarned() {
        // En un mensaje mixto, la compra se colaba como ingrediente de la receta.
        answer(95, false, op("purchase", item(comino, "2", "kg", "1500", null)),
                new InterpretedOperation("recipe", List.of(dish(milanesa, "1")), "la milanesa lleva 100 g de tomate"));
        when(aiService.interpretRecipeMessage(any())).thenReturn(new CatalogSpreadsheetInterpretation(List.of(),
                List.of(), List.of(new RecipeRow(1, "Milanesa", null, null, "tomate", new BigDecimal("100"), "kg")),
                List.of(), List.of(), List.of()));

        var result = service.sendChatMessage("compré 2 kg de comino a 1500 y la milanesa lleva 100 g de tomate", ALL);

        var request = ArgumentCaptor.forClass(RecipeMessageRequest.class);
        verify(aiService).interpretRecipeMessage(request.capture());
        assertEquals("la milanesa lleva 100 g de tomate", request.getValue().message());
        var catalogImport = result.recipe().catalogImport();
        assertEquals("Tomate", catalogImport.recipes().getFirst().ingredient());   // como en el catálogo
        // "kg" está en el mensaje entero (la compra), pero no en el pedazo de la receta: se avisa.
        assertTrue(catalogImport.warnings().contains(
                "Revisá la unidad de «Tomate»: entendí 100 kg y en el mensaje no dijiste kg."), catalogImport.warnings().toString());
    }

    @Test
    void aRecipeTheModelCannotWriteIsSkippedAndTheRestStillRuns() {
        answer(95, false, op("waste", item(tomate, "1", "kg", null, "DAMAGED")), op("recipe", dish(milanesa, "1")));
        when(aiService.interpretRecipeMessage(any()))
                .thenThrow(new AppException(ErrorCode.AI_INTERPRETATION_FAILED, "down"));

        var result = service.sendChatMessage("se rompió 1 kg de tomate y la milanesa lleva queso", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        assertNull(result.recipe());
        assertEquals(ErrorCode.AI_INTERPRETATION_FAILED, result.skipped().getFirst().errorCode());
    }

    // ── Producción ──

    @Test
    void aProductionOfAPreparationIsRegisteredInItsUnitWithItsExpiration() {
        // "hice 1500 g de mayonesa, vence el 1/12": una producción por tanda, convertida a kg.
        var said = new InterpretedItem(mayonesa.getId(), null, "mayonesa", new BigDecimal("1500"), "g", null, null, null,
                java.time.LocalDate.of(2026, 12, 1), null);
        answer(95, false, op("production", said));
        when(unitConversionService.convert(new BigDecimal("1500"), g.getId(), kg.getId())).thenReturn(new BigDecimal("1.5"));

        var result = service.sendChatMessage("hice 1500 g de mayonesa, vence el 1/12", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        var cmd = ArgumentCaptor.forClass(ProductionService.RecordProductionCommand.class);
        verify(productionService).recordProduction(cmd.capture());
        assertEquals(mayonesa.getId(), cmd.getValue().productId());
        assertEquals(0, new BigDecimal("1.5").compareTo(cmd.getValue().quantity()));
        assertEquals(kg.getId(), cmd.getValue().unitId());
        assertEquals(java.time.LocalDate.of(2026, 12, 1), cmd.getValue().expirationDate());
        verify(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);
        assertEquals("Producción: 1,5 kg Mayonesa", result.actions().getFirst().getSummary());
    }

    @Test
    void onlyAPreparationCanBeProduced() {
        answer(95, false, op("production", item(tomate, "2", "kg", null, null)));

        var result = service.sendChatMessage("hice 2 kg de tomate", ALL);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals(ErrorCode.PRODUCT_NOT_A_PREPARATION, result.skipped().getFirst().errorCode());
        verify(productionService, never()).recordProduction(any());
    }

    @Test
    void producingNeedsThePlanWithRecipes() {
        answer(95, false, op("production", item(mayonesa, "1", "kg", null, null)));
        doThrow(AppException.forbidden(ErrorCode.PLAN_FEATURE_REQUIRED, "no"))
                .when(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);

        var result = service.sendChatMessage("hice 1 kg de mayonesa", ALL);

        assertEquals(ErrorCode.PLAN_FEATURE_REQUIRED, result.skipped().getFirst().errorCode());
        verify(productionService, never()).recordProduction(any());
    }

    @Test
    void confirmingAProductionRechecksThatItIsAPreparation() {
        var ops = List.of(new Operation(ChatOperationType.PRODUCTION, List.of(
                new OperationItem(tomate.getId(), null, "Tomate", BigDecimal.ONE, "kg", null, null, null))));

        var result = service.confirmChatOperations(ops, ALL);

        assertEquals(ErrorCode.PRODUCT_NOT_A_PREPARATION, result.skipped().getFirst().errorCode());
    }

    @Test
    void undoingAProductionReversesIt() {
        ChatAction action = stored(ChatOperationType.PRODUCTION, USER, NOW);

        service.undoChatAction(action.getId(), Set.of());

        verify(productionService).reverseProduction(action.getEntityId());
    }

    // ── Ventas y ajustes ──

    @Test
    void aSaleIsRegisteredWithTheDishesAndItsPortions() {
        answer(95, false, op("sale", dish(milanesa, "5")));

        var result = service.sendChatMessage("vendimos 5 milanesas", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        var cmd = ArgumentCaptor.forClass(SaleService.CreateSaleCommand.class);
        verify(saleService).recordSale(cmd.capture());
        assertEquals(new SaleService.SaleItemCommand(milanesa.getId(), 5), cmd.getValue().items().getFirst());
        assertEquals("Venta: 5 Milanesa", result.actions().getFirst().getSummary());
    }

    @Test
    void aSaleNeedsPortionsAndADishOfTheMenu() {
        answer(95, false, op("sale", dish(milanesa, "2.5"),
                new InterpretedItem(null, null, "pizza", BigDecimal.ONE, null, null, null, null, null, null)));

        var result = service.sendChatMessage("vendimos dos milanesas y media y una pizza", ALL);

        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        assertEquals(List.of(ErrorCode.CHAT_INVALID_QUANTITY, ErrorCode.CHAT_MENU_ITEM_NOT_IN_CATALOG),
                result.skipped().stream().map(ChatService.Skipped::errorCode).toList());
        verify(saleService, never()).recordSale(any());
    }

    @Test
    void aSaleThatTheServiceRejectsSaysWhyInSpanish() {
        // El texto es lo único que se lee (y lo que va a leer WhatsApp): el motivo tiene que estar ahí.
        answer(95, false, op("sale", dish(milanesa, "1")));
        when(saleService.recordSale(any())).thenThrow(AppException.badRequest(ErrorCode.MISSING_PURCHASE_COST,
                "Sin historial de compras para: Queso, Harina"));

        var result = service.sendChatMessage("vendimos una milanesa", ALL);

        assertEquals(ErrorCode.MISSING_PURCHASE_COST, result.skipped().getFirst().errorCode());
        assertEquals("No registré nada: «Queso, Harina» no tiene compras cargadas, así que la venta no tiene costo. "
                + "Cargá una compra y probá de nuevo.", result.message());
    }

    @Test
    void aProductionOutsideThePlanSaysSo() {
        answer(95, false, op("production", item(mayonesa, "1", "kg", null, null)));
        doThrow(AppException.forbidden(ErrorCode.PLAN_FEATURE_REQUIRED, "Your plan does not include RECIPES"))
                .when(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);

        var result = service.sendChatMessage("hice 1 kg de mayonesa", ALL);

        assertEquals("No registré nada: Tu plan no incluye producciones.", result.message());
    }

    @Test
    void anAdjustmentIsACountOfOnlyThoseProductsInTheirUnit() {
        // "quedan 800 g de comino y no queda tomate": lo contado, convertido; 0 vale.
        answer(95, false, op("stock_adjustment", item(comino, "800", "g", null, null), item(tomate, "0", null, null, null)));
        when(unitConversionService.convert(new BigDecimal("800"), g.getId(), kg.getId())).thenReturn(new BigDecimal("0.8"));

        var result = service.sendChatMessage("quedan 800 g de comino y no queda tomate", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        var cmd = ArgumentCaptor.forClass(StockCountService.CreateStockCountCommand.class);
        verify(stockCountService).createStockCount(cmd.capture());
        assertEquals(List.of(new StockCountService.StockCountItemCommand(comino.getId(), kg.getId(), new BigDecimal("0.8")),
                        new StockCountService.StockCountItemCommand(tomate.getId(), kg.getId(), BigDecimal.ZERO)),
                cmd.getValue().items());
        assertEquals("Ajuste: hay 0,8 kg Comino, 0 kg Tomate", result.actions().getFirst().getSummary());
    }

    @Test
    void undoingASaleReversesIt() {
        ChatAction action = stored(ChatOperationType.SALE, USER, NOW);

        service.undoChatAction(action.getId(), Set.of());

        verify(saleService).reverseSale(action.getEntityId());
    }

    @Test
    void undoingAnAdjustmentReversesTheCountLikeTheStockCountsScreen() {
        ChatAction action = stored(ChatOperationType.STOCK_ADJUSTMENT, USER, NOW);

        service.undoChatAction(action.getId(), Set.of());

        verify(stockCountService).reverseStockCount(action.getEntityId());
    }

    @Test
    void aModelFailureIsNotUnderstoodInsteadOfAnError() {
        when(aiService.interpretChatMessage(any())).thenThrow(new AppException(ErrorCode.AI_INTERPRETATION_FAILED, "x"));

        assertEquals(ChatStatus.NOT_UNDERSTOOD, service.sendChatMessage("hola", ALL).status());
    }

    @Test
    void whenTheSecondOperationFailsTheFirstStaysRegistered() {
        answer(95, false, op("purchase", item(comino, "1", "kg", "3000", null)),
                op("waste", item(tomate, "2", "kg", null, null)));
        when(wasteService.createWasteEvent(any())).thenThrow(AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "gone"));

        var result = service.sendChatMessage("compré 1 kg de comino a 3000 y tiré 2 kg de tomate", ALL);

        assertEquals(ChatStatus.REGISTERED, result.status());
        assertEquals(1, result.actions().size());
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, result.skipped().getFirst().errorCode());
    }

    @Test
    void eachWasteIsItsOwnActionWithItsReason() {
        answer(95, false, op("waste", item(tomate, "2", "kg", null, "EXPIRED"), item(comino, "0.1", "kg", null, null)));

        var result = service.sendChatMessage("tiramos 2 kg de tomate vencido y 100 g de comino", ALL);

        assertEquals(2, result.actions().size());
        assertEquals("Merma: 2 kg Tomate (vencido)", result.actions().getFirst().getSummary());
    }

    @Test
    void confirmingRevalidatesThePermissionAndTheCatalog() {
        var ops = List.of(
                new Operation(ChatOperationType.PURCHASE, List.of(new OperationItem(comino.getId(), null, "Comino",
                        BigDecimal.ONE, "kg", new BigDecimal("150000"), null, null))),
                new Operation(ChatOperationType.WASTE, List.of(new OperationItem(UUID.randomUUID(), null, "Otro",
                        BigDecimal.ONE, "kg", null, null, WasteReason.DAMAGED))));

        var result = service.confirmChatOperations(ops, Set.of(Permission.PURCHASES_CREATE, Permission.WASTE_CREATE));

        assertEquals(ChatStatus.REGISTERED, result.status());
        assertEquals(1, result.actions().size());
        assertEquals(ErrorCode.CHAT_PRODUCT_NOT_IN_CATALOG, result.skipped().getFirst().errorCode());
        assertEquals(ErrorCode.CHAT_PERMISSION_MISSING, service.confirmChatOperations(ops.subList(0, 1),
                Set.of(Permission.WASTE_CREATE)).skipped().getFirst().errorCode());
    }

    // ── Deshacer ──

    @Test
    void whoLoadedItUndoesWithinFifteenMinutesWithoutTheDeletePermission() {
        ChatAction action = stored(ChatOperationType.PURCHASE, USER, NOW.minus(Duration.ofMinutes(14)));

        ChatAction undone = service.undoChatAction(action.getId(), Set.of());

        verify(purchaseService).reversePurchase(action.getEntityId());
        assertEquals(NOW, undone.getUndoneAt());
    }

    @Test
    void afterFifteenMinutesItNeedsTheDeletePermission() {
        ChatAction action = stored(ChatOperationType.PURCHASE, USER, NOW.minus(Duration.ofMinutes(16)));

        var e = assertThrows(AppException.class, () -> service.undoChatAction(action.getId(), Set.of()));
        assertEquals(ErrorCode.CHAT_UNDO_NOT_ALLOWED, e.getErrorCode());

        service.undoChatAction(action.getId(), Set.of(Permission.PURCHASES_DELETE));
        verify(purchaseService).reversePurchase(action.getEntityId());
    }

    @Test
    void someoneElsesNeedsTheDeletePermissionEvenRightAway() {
        ChatAction action = stored(ChatOperationType.WASTE, UUID.randomUUID(), NOW);

        var e = assertThrows(AppException.class,
                () -> service.undoChatAction(action.getId(), Set.of(Permission.PURCHASES_DELETE)));
        assertEquals(ErrorCode.CHAT_UNDO_NOT_ALLOWED, e.getErrorCode());

        service.undoChatAction(action.getId(), Set.of(Permission.STOCK_DELETE));
        verify(wasteService).reverseWasteEvent(action.getEntityId());
    }

    @Test
    void anUndoneActionCannotBeUndoneAgain() {
        ChatAction action = stored(ChatOperationType.PURCHASE, USER, NOW);
        action.markUndone(NOW);

        var e = assertThrows(AppException.class, () -> service.undoChatAction(action.getId(), ALL));
        assertEquals(ErrorCode.CHAT_ACTION_ALREADY_UNDONE, e.getErrorCode());
        verify(purchaseService, never()).reversePurchase(any());
    }

    // ── helpers ──

    private ChatAction stored(ChatOperationType type, UUID user, Instant createdAt) {
        ChatAction a = ChatAction.builder().id(UUID.randomUUID()).restaurantId(R).userId(user).type(type)
                .entityId(UUID.randomUUID()).summary("x").createdAt(createdAt).build();
        when(chatActionRepository.findByIdAndRestaurantId(a.getId(), R)).thenReturn(Optional.of(a));
        return a;
    }

    private void answer(int confidence, boolean needsConfirmation, InterpretedOperation... ops) {
        when(aiService.interpretChatMessage(any()))
                .thenReturn(new ChatInterpretation(confidence, needsConfirmation, "ok", List.of(ops)));
    }

    private static InterpretedOperation op(String type, InterpretedItem... items) {
        return new InterpretedOperation(type, List.of(items));
    }

    private static InterpretedOperation op(String type, List<InterpretedItem> items) {
        return new InterpretedOperation(type, items);
    }

    private static InterpretedItem item(Product p, String qty, String unit, String price, String reasonOrPriceUnit) {
        boolean isReason = reasonOrPriceUnit != null && reasonOrPriceUnit.equals(reasonOrPriceUnit.toUpperCase());
        return new InterpretedItem(p.getId(), null, p.getName().toLowerCase(), new BigDecimal(qty), unit,
                price == null ? null : new BigDecimal(price), price == null ? null : "unit",
                isReason ? null : reasonOrPriceUnit, null, isReason ? reasonOrPriceUnit : null);
    }

    private static InterpretedItem dish(MenuItem m, String portions) {
        return new InterpretedItem(null, m.getId(), m.getName().toLowerCase(), new BigDecimal(portions), null, null, null,
                null, null, null);
    }

    private static Unit unit(String name, String abbreviation) {
        Unit u = new Unit();
        u.setId(UUID.randomUUID());
        u.setName(name);
        u.setAbbreviation(abbreviation);
        return u;
    }

    private static Product product(String name, Unit unit) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setRestaurantId(R);
        p.setName(name);
        p.setDefaultUnitId(unit.getId());
        p.setDefaultUnitAbbreviation(unit.getAbbreviation());
        return p;
    }
}
