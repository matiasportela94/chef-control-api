package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.ChatInterpretation;
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
import com.chefcontrol.domain.repository.StockMovementRepository;
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
    private final Product mayonesa = product("Mayonesa", kg);
    private final MenuItem milanesa = MenuItem.builder().id(UUID.randomUUID()).restaurantId(R).name("Milanesa")
            .price(new BigDecimal("12000")).active(true).build();
    private ChatService service;

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
        service = new ChatService(aiService, new ChatRecipeService(aiService, recipeRepository, accountService),
                productRepository, menuItemRepository, unitRepository,
                unitConversionService, purchaseService, wasteService, saleService, stockCountService,
                productionService, recipeRepository, accountService, stockService,
                stockMovementRepository, chatActionRepository, currentUserProvider,
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
        assertEquals(0, new BigDecimal("150000").compareTo(result.proposal().getFirst().items().getFirst().pricePerUnit()));
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void aTotalIsDividedByTheBackNeverByTheModelAndAsked() {
        // "5 kg de comino, 150 mil en total": el modelo dice "total" y devuelve 150000 tal cual.
        answer(95, false, op("purchase", new InterpretedItem(comino.getId(), null, "comino", new BigDecimal("5"), "kg",
                new BigDecimal("150000"), "total", null, null, null)));

        var result = service.sendChatMessage("5 kg de comino, 150 mil en total", ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status());
        assertEquals(0, new BigDecimal("30000").compareTo(result.proposal().getFirst().items().getFirst().pricePerUnit()));
        verify(purchaseService, never()).createPurchase(any());
    }

    @Test
    void aTotalOfOneUnitIsTheSameAsItsPriceAndIsRegistered() {
        answer(95, false, op("purchase", new InterpretedItem(comino.getId(), null, "comino", BigDecimal.ONE, "kg",
                new BigDecimal("3000"), "total", null, null, null)));

        assertEquals(ChatStatus.REGISTERED, service.sendChatMessage("1 kg de comino, 3000 todo", ALL).status());
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
        answer(95, false, op("stock_adjustment", item(comino, "800", "g", null, null), item(tomate, "0", "kg", null, null)));
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
    void undoingAnAdjustmentReversesEachMovementOfTheCountThatIsStillThere() {
        ChatAction action = stored(ChatOperationType.STOCK_ADJUSTMENT, USER, NOW);
        StockMovement live = StockMovement.builder().id(UUID.randomUUID()).type(MovementType.COUNT).build();
        StockMovement reversedElsewhere = StockMovement.builder().id(UUID.randomUUID()).type(MovementType.COUNT)
                .reversedBy(UUID.randomUUID()).build();
        when(stockMovementRepository.findByReferenceIdAndReferenceType(action.getEntityId(), "stock_count"))
                .thenReturn(List.of(live, reversedElsewhere));

        service.undoChatAction(action.getId(), Set.of());

        verify(stockService).reverseMovement(live.getId());
        verify(stockService, never()).reverseMovement(reversedElsewhere.getId());
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
