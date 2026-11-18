package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.ChatInterpretation;
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
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.waste.WasteEvent;
import com.chefcontrol.domain.waste.WasteReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock PurchaseService purchaseService;
    @Mock WasteService wasteService;
    @Mock StockService stockService;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock ChatActionRepository chatActionRepository;
    @Mock CurrentUserProvider currentUserProvider;

    private static final UUID R = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");
    private static final Set<Permission> ALL = EnumSet.allOf(Permission.class);

    private final Unit kg = unit("Kilogramo", "kg");
    private final Unit g = unit("Gramo", "g");
    private final Unit l = unit("Litro", "l");
    private final Product comino = product("Comino", kg);
    private final Product tomate = product("Tomate", kg);
    private ChatService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        when(unitRepository.findAll()).thenReturn(List.of(kg, g, l));
        when(productRepository.findByRestaurantIdAndIsActiveTrue(eq(R), any()))
                .thenReturn(new Page<>(List.of(comino, tomate), 0, 2, 2));
        for (Product p : List.of(comino, tomate)) {
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
        service = new ChatService(aiService, productRepository, unitRepository, unitConversionService, purchaseService,
                wasteService, stockService, stockMovementRepository, chatActionRepository, currentUserProvider,
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
    void aHighUnitPriceIsAskedBeforeRegistering() {
        answer(95, false, op("purchase", item(comino, "5", "kg", "150000", null)));

        var result = service.sendChatMessage("5 kg de comino a 150 mil", ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status());
        assertEquals(1, result.proposal().size());
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void lowConfidenceIsAskedEvenIfTheModelDidNotFlagIt() {
        answer(60, false, op("waste", item(tomate, "2", "kg", null, null)));

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, service.sendChatMessage("tomate 2", ALL).status());
        verify(wasteService, never()).createWasteEvent(any());
    }

    @Test
    void aProductOutsideTheCatalogIsNeverRegistered() {
        answer(95, false, op("purchase", new InterpretedItem(null, "tomate cherry", BigDecimal.ONE, "kg",
                new BigDecimal("4000"), null, null, null)));

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
        answer(95, false, op("sale", List.of()), op("query", List.of()));

        var result = service.sendChatMessage("vendimos 5 milanesas, ¿cuánta harina queda?", ALL);

        assertEquals(ChatStatus.NOT_SUPPORTED, result.status());
        assertEquals(2, result.skipped().size());
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

        var result = service.sendChatMessage("compré comino y tiré tomate", ALL);

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
        StockMovement movement = StockMovement.builder().id(UUID.randomUUID()).type(MovementType.WASTE).build();
        when(stockMovementRepository.findByReferenceIdAndReferenceType(action.getEntityId(), "waste_event"))
                .thenReturn(List.of(movement));

        var e = assertThrows(AppException.class,
                () -> service.undoChatAction(action.getId(), Set.of(Permission.PURCHASES_DELETE)));
        assertEquals(ErrorCode.CHAT_UNDO_NOT_ALLOWED, e.getErrorCode());

        service.undoChatAction(action.getId(), Set.of(Permission.STOCK_DELETE));
        verify(stockService).reverseMovement(movement.getId());
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
        return new InterpretedItem(p.getId(), p.getName().toLowerCase(), new BigDecimal(qty), unit,
                price == null ? null : new BigDecimal(price), isReason ? null : reasonOrPriceUnit, null,
                isReason ? reasonOrPriceUnit : null);
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
