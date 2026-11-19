package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.SalesReportLineReading;
import com.chefcontrol.application.port.AIService.SalesReportReading;
import com.chefcontrol.application.port.ChatExchangeRepository;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.ChatSalesReportService.ConfirmedSalesLine;
import com.chefcontrol.application.service.ChatSalesReportService.ExistingSalesChoice;
import com.chefcontrol.application.service.ChatSalesReportService.SalesReportConfirmation;
import com.chefcontrol.application.service.ChatSalesReportService.SalesReportLine;
import com.chefcontrol.application.service.ChatSalesReportService.SalesReportWarning;
import com.chefcontrol.application.service.ChatService.ChatCatalog;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.domain.chat.ChatActionRepository;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.repository.MenuItemAliasRepository;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.sale.MenuItemAlias;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.user.Permission;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
class ChatSalesReportServiceTest {

    @Mock MenuItemAliasRepository aliasRepository;
    @Mock SaleRepository saleRepository;
    @Mock SaleService saleService;
    @Mock ChatActionRepository chatActionRepository;
    @Mock ChatExchangeRepository chatExchangeRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock AIService aiService;
    @Mock ProductRepository productRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock UnitRepository unitRepository;
    @Mock com.chefcontrol.domain.repository.RecipeRepository recipeRepository;

    private static final UUID R = UUID.randomUUID();
    /** Miércoles 18/11, 15:00 en Argentina. */
    private static final Instant NOW = Instant.parse("2026-11-18T18:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final Set<Permission> ALL = EnumSet.allOf(Permission.class);

    private final MenuItem napolitana = dish("Milanesa napolitana", "12000");
    private final MenuItem flan = dish("Flan casero", "5000");
    private final ChatCatalog catalog = new ChatCatalog(List.of(), List.of(napolitana, flan), List.of(), Set.of());
    private ChatSalesReportService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        when(saleRepository.findActiveByRestaurantIdAndSoldAtBetween(eq(R), any(), any())).thenReturn(List.of());
        when(saleService.recordSale(any())).thenAnswer(i -> Sale.builder().id(UUID.randomUUID())
                .totalAmount(new BigDecimal("33000")).build());
        when(chatActionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ChatSalesReportService(aliasRepository, saleRepository, saleService, chatActionRepository,
                new ChatHistoryService(chatExchangeRepository, chatActionRepository, currentUserProvider, clock),
                currentUserProvider, TransactionOperations.withoutTransaction(), clock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Vista previa ──

    @Test
    void eachLineIsTheLearnedDishTheSuggestedOneOrIgnoredAndTheTicketPriceIsKept() {
        when(aliasRepository.findByRestaurantId(R)).thenReturn(List.of(
                new MenuItemAlias(UUID.randomUUID(), R, "mila napo", napolitana.getId()),
                new MenuItemAlias(UUID.randomUUID(), R, "cubierto", null)));

        var preview = service.previewSalesReport(report(TODAY, LocalTime.of(23, 40), "33500",
                line("MILA NAPO", "2", "11000", "22000", null),
                line("FLANES CASEROS", "2", "5000", "10000", "Flanes caseros"),   // el plural lo iguala el back
                line("CUBIERTO", "3", "500", "1500", null),
                line("GASEOSA", "1", "2500", "2500", null)), catalog).salesReport();   // no está, no se inventa

        List<SalesReportLine> l = preview.lines();
        assertEquals("ALIAS", l.get(0).matchedBy());
        assertEquals(napolitana.getId(), l.get(0).menuItemId());
        assertEquals(0, new BigDecimal("11000").compareTo(l.get(0).unitPrice()));   // lo cobrado, no la carta
        assertTrue(l.get(0).priceDiffers());
        assertEquals("CATALOG", l.get(1).matchedBy());
        assertEquals(flan.getId(), l.get(1).menuItemId());
        assertFalse(l.get(1).priceDiffers());
        assertEquals("IGNORED", l.get(2).matchedBy());
        assertTrue(l.get(2).warnings().isEmpty());
        assertNull(l.get(3).menuItemId());
        assertTrue(l.get(3).warnings().getFirst().startsWith("NOT_ON_MENU"));
        assertEquals(0, new BigDecimal("32000").compareTo(preview.saleTotal()));    // solo lo que tiene plato
        assertEquals(0, new BigDecimal("36000").compareTo(preview.readTotal()));
        assertEquals(TODAY, preview.soldOn());
        assertTrue(codes(preview.warnings()).containsAll(List.of("TOTAL_MISMATCH", "LINES_TO_REVIEW")));
    }

    @Test
    void aCloseAfterMidnightGoesToThePreviousDayAndTheSalesAlreadyThereAreShown() {
        LocalDate yesterday = TODAY.minusDays(1);
        Sale loaded = Sale.builder().id(UUID.randomUUID()).totalAmount(new BigDecimal("45000")).build();
        when(saleRepository.findActiveByRestaurantIdAndSoldAtBetween(R, Instant.parse("2026-11-17T03:00:00Z"),
                Instant.parse("2026-11-18T02:59:59.999999999Z"))).thenReturn(List.of(loaded));
        when(saleRepository.findActiveByReportNumber(R, "0012")).thenReturn(Optional.of(Sale.builder()
                .id(loaded.getId()).soldAt(Instant.parse("2026-11-17T15:00:00Z")).build()));

        var preview = service.previewSalesReport(report(TODAY, LocalTime.of(1, 30), "24000",
                line("MILANESA NAPOLITANA", "2", "12000", "24000", "Milanesa napolitana")), catalog).salesReport();

        assertEquals(yesterday, preview.soldOn());
        var warnings = codes(preview.warnings());
        assertTrue(warnings.containsAll(List.of("AFTER_MIDNIGHT", "ALREADY_LOADED")), warnings.toString());
        assertEquals(loaded.getId(), preview.warnings().stream().filter(w -> w.code().equals("ALREADY_LOADED"))
                .findFirst().orElseThrow().saleId());
        assertEquals(1, preview.existingSales().count());
        assertEquals(0, new BigDecimal("45000").compareTo(preview.existingSales().total()));
    }

    @Test
    void theQuantityAndPriceAreComputedByTheBackAndAFractionIsFlagged() {
        var lines = service.previewSalesReport(report(TODAY, null, null,
                line("FLAN", null, "5000", "15000", "Flan casero"),          // la cantidad sale del total
                line("MILANESA", "2", null, "24000", "Milanesa napolitana"), // el precio sale del total
                line("FLAN", "1.5", "5000", "7500", "Flan casero")), catalog).salesReport().lines();

        assertEquals(3, lines.get(0).quantity());
        assertEquals(0, new BigDecimal("12000").compareTo(lines.get(1).unitPrice()));
        assertNull(lines.get(2).quantity());
        assertTrue(lines.get(2).warnings().getFirst().startsWith("INVALID_QUANTITY"));
    }

    // ── Confirmar ──

    @Test
    void confirmingRegistersOneSaleAtTheTicketPricesOnThatDayAndLearnsThePos() {
        LocalDate yesterday = TODAY.minusDays(1);
        var result = service.confirmChatSalesReport(new SalesReportConfirmation("0012", yesterday, null, List.of(
                new ConfirmedSalesLine("MILA NAPO", napolitana.getId(), 2, new BigDecimal("11000"), false),
                new ConfirmedSalesLine("FLAN", flan.getId(), 2, new BigDecimal("5500"), false),
                new ConfirmedSalesLine("CUBIERTO", null, null, null, true))), ALL);

        assertEquals(ChatStatus.REGISTERED, result.status(), result.message());
        var cmd = ArgumentCaptor.forClass(SaleService.CreateSaleCommand.class);
        verify(saleService).recordSale(cmd.capture());
        assertEquals("0012", cmd.getValue().reportNumber());
        assertEquals(Instant.parse("2026-11-17T15:00:00Z"), cmd.getValue().soldAt());   // mediodía del día del servicio
        assertEquals(2, cmd.getValue().items().size());
        assertEquals(0, new BigDecimal("11000").compareTo(cmd.getValue().items().getFirst().unitPrice()));
        var learned = ArgumentCaptor.forClass(MenuItemAlias.class);
        verify(aliasRepository, times(3)).upsert(learned.capture());
        assertNull(learned.getAllValues().get(2).menuItemId());                          // "no es un plato"
        assertEquals("cubierto", learned.getAllValues().get(2).printedKey());
        assertTrue(result.message().contains("4 platos"), result.message());
        verify(saleService, never()).reverseSale(any());
        assertNull(result.actions().getFirst().getUndoNote());
    }

    @Test
    void replacingTheDaysSalesReversesThemAllOrAsksForThePermission() {
        Sale a = Sale.builder().id(UUID.randomUUID()).totalAmount(BigDecimal.TEN).build();
        Sale b = Sale.builder().id(UUID.randomUUID()).totalAmount(BigDecimal.TEN).build();
        when(saleRepository.findActiveByRestaurantIdAndSoldAtBetween(eq(R), any(), any())).thenReturn(List.of(a, b));
        var lines = List.of(new ConfirmedSalesLine("FLAN", flan.getId(), 1, new BigDecimal("5000"), false));

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(AppException.class, () -> service.confirmChatSalesReport(
                new SalesReportConfirmation(null, TODAY, null, lines), ALL)).getErrorCode());   // hay que elegir

        var denied = service.confirmChatSalesReport(new SalesReportConfirmation(null, TODAY, ExistingSalesChoice.REVERSE,
                lines), EnumSet.of(Permission.AI_USE, Permission.SALES_CREATE));
        assertEquals(ChatStatus.NOT_REGISTERED, denied.status());
        assertEquals(ErrorCode.CHAT_PERMISSION_MISSING, denied.skipped().getFirst().errorCode());
        verify(saleService, never()).recordSale(any());

        var replaced = service.confirmChatSalesReport(new SalesReportConfirmation(null, TODAY, ExistingSalesChoice.REVERSE,
                lines), ALL);
        assertEquals(ChatStatus.REGISTERED, replaced.status());
        verify(saleService).reverseSale(a.getId());
        verify(saleService).reverseSale(b.getId());
        // Deshacer el cierre no las restaura: la acción lo dice.
        assertEquals("Las 2 ventas que reemplazó siguen revertidas: cargalas de nuevo si hace falta.",
                replaced.actions().getFirst().getUndoNote());
        assertEquals(Instant.parse("2026-11-18T15:00:00Z"), captured().soldAt());          // hoy, ya pasó el mediodía
    }

    @Test
    void aLineWithoutADishThatIsNotIgnoredIsRejectedAndAFailedSaleRegistersNothing() {
        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(AppException.class, () -> service.confirmChatSalesReport(
                new SalesReportConfirmation(null, TODAY, null, List.of(
                        new ConfirmedSalesLine("GASEOSA", null, 1, new BigDecimal("2500"), false))), ALL)).getErrorCode());

        when(saleService.recordSale(any())).thenThrow(AppException.badRequest(ErrorCode.MISSING_PURCHASE_COST, "Queso"));
        var result = service.confirmChatSalesReport(new SalesReportConfirmation(null, TODAY, null, List.of(
                new ConfirmedSalesLine("FLAN", flan.getId(), 1, new BigDecimal("5000"), false))), ALL);
        assertEquals(ChatStatus.NOT_REGISTERED, result.status());
        verify(aliasRepository, never()).upsert(any());
    }

    // ── El archivo: un cierre pide el permiso de ventas ──

    @Test
    void aSalesReportWithoutSalesCreateIsNotPreviewed() {
        when(productRepository.findByRestaurantIdAndIsActiveTrue(eq(R), any())).thenReturn(new Page<>(List.of(), 0, 0, 0));
        when(menuItemRepository.findByRestaurantIdAndActive(eq(R), eq(true), any()))
                .thenReturn(new Page<>(List.of(napolitana, flan), 0, 2, 2));
        when(aiService.interpretChatDocument(any())).thenReturn(new AIService.DocumentReading(
                AIService.DocumentKind.SALES_REPORT, null, report(TODAY, null, null,
                line("FLAN", "1", "5000", "5000", "Flan casero"))));
        var documents = new ChatDocumentService(aiService, productRepository, menuItemRepository, unitRepository, recipeRepository, null,
                service, null, new ChatHistoryService(chatExchangeRepository, chatActionRepository, currentUserProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)));
        byte[] pdf = "%PDF-1.4 z".getBytes(StandardCharsets.US_ASCII);

        var denied = documents.sendChatDocument(List.of(pdf), null, EnumSet.of(Permission.AI_USE, Permission.PURCHASES_CREATE));
        assertEquals(ChatStatus.NOT_REGISTERED, denied.status());
        assertEquals(ErrorCode.CHAT_PERMISSION_MISSING, denied.skipped().getFirst().errorCode());

        var ok = documents.sendChatDocument(List.of(pdf), null, EnumSet.of(Permission.AI_USE, Permission.SALES_CREATE));
        assertEquals(ChatStatus.NEEDS_CONFIRMATION, ok.status());
        assertEquals(flan.getId(), ok.salesReport().lines().getFirst().menuItemId());
    }

    // ── fixtures ──

    private SaleService.CreateSaleCommand captured() {
        var cmd = ArgumentCaptor.forClass(SaleService.CreateSaleCommand.class);
        verify(saleService, atLeastOnce()).recordSale(cmd.capture());
        return cmd.getValue();
    }

    private static List<String> codes(List<SalesReportWarning> warnings) {
        return warnings.stream().map(SalesReportWarning::code).toList();
    }

    private static SalesReportReading report(LocalDate date, LocalTime time, String total, SalesReportLineReading... lines) {
        return new SalesReportReading("0012", date, time, total == null ? null : new BigDecimal(total), List.of(lines));
    }

    private static SalesReportLineReading line(String text, String qty, String price, String total, String catalogName) {
        return new SalesReportLineReading(text, qty == null ? null : new BigDecimal(qty),
                price == null ? null : new BigDecimal(price), total == null ? null : new BigDecimal(total), catalogName);
    }

    private static MenuItem dish(String name, String price) {
        return MenuItem.builder().id(UUID.randomUUID()).restaurantId(R).name(name).price(new BigDecimal(price))
                .active(true).build();
    }
}
