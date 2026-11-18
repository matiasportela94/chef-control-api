package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.InvoiceLineReading;
import com.chefcontrol.application.port.AIService.InvoiceReading;
import com.chefcontrol.application.port.ChatExchangeRepository;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.ChatInvoiceService.ConfirmedLine;
import com.chefcontrol.application.service.ChatInvoiceService.InvoiceConfirmation;
import com.chefcontrol.application.service.ChatInvoiceService.InvoiceWarning;
import com.chefcontrol.application.service.ChatService.ChatStatus;
import com.chefcontrol.domain.chat.ChatActionRepository;
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
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.supplier.Supplier;
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
class ChatInvoiceServiceTest {

    @Mock AIService aiService;
    @Mock ProductRepository productRepository;
    @Mock UnitRepository unitRepository;
    @Mock UnitConversionService unitConversionService;
    @Mock SupplierRepository supplierRepository;
    @Mock SupplierService supplierService;
    @Mock SupplierProductAliasRepository aliasRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock PurchaseService purchaseService;
    @Mock ProductService productService;
    @Mock ChatActionRepository chatActionRepository;
    @Mock ChatExchangeRepository chatExchangeRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock com.chefcontrol.domain.repository.MenuItemRepository menuItemRepository;
    @Mock com.chefcontrol.domain.repository.RecipeRepository recipeRepository;

    private static final UUID R = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-18T15:00:00Z");
    private static final Set<Permission> ALL = EnumSet.allOf(Permission.class);
    private static final byte[] PDF = "%PDF-1.4 factura".getBytes(StandardCharsets.US_ASCII);

    private final Unit kg = unit("Kilogramo", "kg");
    private final Unit g = unit("Gramo", "g");
    private final Unit bolsa = unit("Bolsa", "bol");
    private final Unit u = unit("Unidad", "u");
    private final Product tomate = product("Tomate", kg);
    private final Product harina = product("Harina", kg);
    private final Supplier verduleria = supplier("Verdulería Don José", "30712345671");
    private ChatInvoiceService service;
    private ChatDocumentService documents;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        when(unitRepository.findAll()).thenReturn(List.of(kg, g, bolsa, u));
        when(productRepository.findByRestaurantIdAndIsActiveTrue(eq(R), any()))
                .thenReturn(new Page<>(List.of(tomate, harina), 0, 2, 2));
        when(supplierRepository.findActiveByRestaurantIdAndCuit(R, "30712345671")).thenReturn(Optional.of(verduleria));
        when(supplierRepository.findAllByRestaurantIdAndIsActiveTrueOrderByName(R)).thenReturn(List.of(verduleria));
        when(unitConversionService.convert(any(), eq(bolsa.getId()), eq(kg.getId())))
                .thenThrow(AppException.badRequest(ErrorCode.UNIT_CONVERSION_NOT_FOUND, "no"));
        when(unitConversionService.convert(any(), eq(g.getId()), eq(kg.getId())))
                .thenAnswer(i -> i.<BigDecimal>getArgument(0).divide(new BigDecimal("1000")));
        when(unitConversionService.convertPrice(any(), eq(g.getId()), eq(kg.getId())))
                .thenAnswer(i -> i.<BigDecimal>getArgument(0).multiply(new BigDecimal("1000")));
        when(currentUserProvider.currentUserId()).thenReturn(USER);
        when(purchaseService.createPurchase(any())).thenAnswer(i -> Purchase.builder().id(UUID.randomUUID())
                .total(new BigDecimal("12100")).build());
        when(chatActionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var history = new ChatHistoryService(chatExchangeRepository, chatActionRepository, currentUserProvider, clock);
        service = new ChatInvoiceService(unitConversionService,
                supplierRepository, supplierService, aliasRepository, purchaseRepository, purchaseService,
                productService, chatActionRepository, history, currentUserProvider,
                TransactionOperations.withoutTransaction(), clock);
        when(menuItemRepository.findByRestaurantIdAndActive(eq(R), eq(true), any())).thenReturn(new Page<>(List.of(), 0, 0, 0));
        documents = new ChatDocumentService(aiService, productRepository, menuItemRepository, unitRepository, recipeRepository, service, null, null,
                history);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Vista previa ──

    @Test
    void aFacturaAIsReadAsItsPrintedNumbersAndTheBackDoesTheMath() {
        reads(reading("A", "30-71234567-1", "Verdulería Don José", new BigDecimal("12100"),
                line("TOMATE PERITA X KG", "5", "kg", "2000", "10000", "21", "Tomate", "Tomate")));

        var result = documents.sendChatDocument(List.of(PDF), null, ALL);

        assertEquals(ChatStatus.NEEDS_CONFIRMATION, result.status());
        var preview = result.invoice();
        assertEquals(verduleria.getId(), preview.supplier().supplierId());
        assertFalse(preview.pricesIncludeIva());                        // A: precios sin IVA
        var line = preview.lines().getFirst();
        assertEquals(tomate.getId(), line.productId());
        assertEquals("CATALOG", line.matchedBy());
        assertEquals(0, new BigDecimal("21").compareTo(line.ivaPercentage()));
        assertEquals(0, new BigDecimal("12100").compareTo(preview.computedTotal()));   // 10.000 + 21%: cierra
        assertTrue(preview.warnings().isEmpty(), preview.warnings().toString());
        verify(purchaseService, never()).createPurchase(any());          // vista previa: nada se registra
    }

    @Test
    void anAliasOfThatSupplierWinsAndItsLearnedFactorConvertsBags() {
        // "HARINA 000 X 25 KG" en bolsas: la factura anterior enseñó que una bolsa son 25 kg.
        when(aliasRepository.findBySupplierId(verduleria.getId())).thenReturn(List.of(new SupplierProductAlias(
                UUID.randomUUID(), R, verduleria.getId(), "harina 000 x 25 kg", harina.getId(), new BigDecimal("25"))));
        reads(reading("B", "30712345671", "Verdulería Don José", new BigDecimal("40000"),
                line("HARINA 000 X 25 KG", "2", "bol", "20000", "40000", null, "Tomate", "Harina 000")));

        var line = documents.sendChatDocument(List.of(PDF), null, ALL).invoice().lines().getFirst();

        assertEquals(harina.getId(), line.productId());                  // el alias gana sobre lo que sugirió la IA
        assertEquals("ALIAS", line.matchedBy());
        assertEquals(0, new BigDecimal("50").compareTo(line.quantity()));        // 2 bolsas × 25 kg
        assertEquals(0, new BigDecimal("800").compareTo(line.unitPrice()));      // $ 20.000 / 25 kg
        assertEquals(kg.getId(), line.unitId());
        assertTrue(line.warnings().isEmpty());
    }

    @Test
    void aUnitThatDoesNotConvertIsFlaggedAndTheGramsConvert() {
        reads(reading("B", "30712345671", "Verdulería Don José", null,
                line("HARINA BOLSA", "2", "bol", "20000", "40000", null, "Harina", "Harina"),
                line("TOMATE", "500", "g", "3", "1500", null, "Tomate", "Tomate")));

        var lines = documents.sendChatDocument(List.of(PDF), null, ALL).invoice().lines();

        assertTrue(lines.get(0).warnings().getFirst().startsWith("UNIT_NOT_CONVERTIBLE"), lines.get(0).warnings().toString());
        assertEquals(bolsa.getId(), lines.get(0).unitId());              // queda como está impreso, para que lo cargue
        assertEquals(0, new BigDecimal("0.5").compareTo(lines.get(1).quantity()));   // 500 g → 0,5 kg
        assertEquals(0, new BigDecimal("3000").compareTo(lines.get(1).unitPrice()));  // $ 3 el g → $ 3.000 el kg
    }

    @Test
    void anUnknownLineIsProposedAndAFacturaCGoesWithIvaZero() {
        reads(reading("C", null, "Almacén Sin CUIT", new BigDecimal("1000"),
                line("ACEITE GIRASOL 900 ML", "1", "u", "1000", "1000", null, null, "Aceite de girasol")));

        var preview = documents.sendChatDocument(List.of(PDF), null, ALL).invoice();

        assertTrue(preview.pricesIncludeIva());
        var line = preview.lines().getFirst();
        assertNull(line.productId());
        assertEquals("Aceite de girasol", line.proposedProduct().name());
        assertEquals(0, BigDecimal.ZERO.compareTo(line.ivaPercentage()));          // C: no discrimina IVA
        assertTrue(preview.supplier().isNew());
        assertNull(preview.supplier().cuit());
    }

    @Test
    void withoutACuitTheSupplierIsFoundByNameInsteadOfDuplicated() {
        reads(reading("REMITO", null, "VERDULERIA DON JOSE", null,
                line("TOMATE", "1", "kg", "1000", "1000", null, "Tomate", "Tomate")));

        var supplier = documents.sendChatDocument(List.of(PDF), null, ALL).invoice().supplier();

        assertEquals(verduleria.getId(), supplier.supplierId());
        assertFalse(supplier.isNew());
    }

    @Test
    void theWarningsSayWhatToReview() {
        when(purchaseRepository.findActiveBySupplierIdAndInvoiceNumber(R, verduleria.getId(), "0001-00001234"))
                .thenReturn(Optional.of(Purchase.builder().id(UUID.randomUUID()).purchasedAt(NOW.minusSeconds(86400)).build()));
        var reading = new InvoiceReading("Verdulería Don José", "30-71234567-9", "B", "0001-00001234",
                LocalDate.of(2026, 12, 1), new BigDecimal("9999"),
                List.of(line("TOMATE", "5", "kg", "2000", "9000", null, "Tomate", "Tomate")));
        reads(reading);

        var codes = documents.sendChatDocument(List.of(PDF), null, ALL).invoice().warnings().stream().map(InvoiceWarning::code).toList();

        assertTrue(codes.containsAll(List.of("INVALID_CUIT_READ", "TOTAL_MISMATCH", "FUTURE_DATE", "LINES_TO_REVIEW")), codes.toString());
        // El CUIT leído no vale, pero el nombre encontró el proveedor: y esa factura ya estaba cargada.
        assertTrue(codes.contains("ALREADY_LOADED"), codes.toString());
    }

    @Test
    void aFileThatIsNotAnInvoiceOrTooManyFilesIsSaid() {
        when(aiService.interpretChatDocument(any())).thenReturn(
                new AIService.DocumentReading(AIService.DocumentKind.OTHER, null, null));
        var result = documents.sendChatDocument(List.of(PDF), null, ALL);
        assertEquals(ChatStatus.NOT_UNDERSTOOD, result.status());
        assertEquals(ErrorCode.CHAT_DOCUMENT_NOT_RECOGNIZED, result.skipped().getFirst().errorCode());

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(AppException.class,
                () -> documents.sendChatDocument(List.of(PDF, PDF), null, ALL)).getErrorCode());
        assertEquals(ErrorCode.ATTACHMENT_FORMAT_NOT_SUPPORTED, assertThrows(AppException.class,
                () -> documents.sendChatDocument(List.of("<svg/>".getBytes(StandardCharsets.US_ASCII)), null, ALL)).getErrorCode());
    }

    // ── Confirmar ──

    @Test
    void confirmingCreatesWhatIsNewLearnsTheAliasesAndRegistersThePurchaseInItsDate() {
        Supplier created = supplier("Almacén Nuevo", null);
        when(supplierService.createSupplier(any())).thenReturn(created);
        Product aceite = product("Aceite de girasol", u);
        when(productService.createProduct(any())).thenReturn(aceite);

        var result = service.confirmChatInvoice(new InvoiceConfirmation(null, "Almacén Nuevo", "30-71234567-9", InvoiceType.C,
                "0002-00000077", LocalDate.of(2026, 11, 15), false, List.of(
                new ConfirmedLine("HARINA BOLSA", harina.getId(), null, null, new BigDecimal("50"), kg.getId(),
                        new BigDecimal("800"), new BigDecimal("21"), null, new BigDecimal("25")),
                new ConfirmedLine("ACEITE 900", null, "aceite de girasol", u.getId(), BigDecimal.ONE, u.getId(),
                        new BigDecimal("1000"), null, null, null))), ALL);

        assertEquals(ChatStatus.REGISTERED, result.status(), result.message());
        var cmd = ArgumentCaptor.forClass(PurchaseService.CreatePurchaseCommand.class);
        verify(purchaseService).createPurchase(cmd.capture());
        assertEquals(created.getId(), cmd.getValue().supplierId());
        assertEquals(InvoiceType.C, cmd.getValue().invoiceType());
        assertEquals("0002-00000077", cmd.getValue().invoiceNumber());
        assertEquals(Instant.parse("2026-11-15T03:00:00Z"), cmd.getValue().purchasedAt());   // el día de la factura, acá
        assertTrue(cmd.getValue().pricesIncludeIva());                                       // C: con IVA incluido...
        assertTrue(cmd.getValue().items().stream().allMatch(i -> BigDecimal.ZERO.compareTo(i.ivaPercentage()) == 0)); // ...y 0%
        var learned = ArgumentCaptor.forClass(SupplierProductAlias.class);
        verify(aliasRepository, times(2)).upsert(learned.capture());
        assertEquals("harina bolsa", learned.getAllValues().get(0).printedKey());
        assertEquals(0, new BigDecimal("25").compareTo(learned.getAllValues().get(0).unitsPerPrintedUnit()));
        assertEquals(result.actions().getFirst().getEntityId(), result.actions().getFirst().getEntityId());
        verify(chatExchangeRepository).saveChatExchange(argThat(x -> x.kind() == ChatExchangeRepository.Kind.CONFIRMATION));
    }

    @Test
    void withoutThePermissionsTheNewSupplierAndProductsDoNotGetIn() {
        Set<Permission> few = EnumSet.of(Permission.AI_USE, Permission.PURCHASES_CREATE);

        var result = service.confirmChatInvoice(new InvoiceConfirmation(null, "Almacén Nuevo", null, InvoiceType.B, null,
                LocalDate.of(2026, 11, 15), true, List.of(
                new ConfirmedLine("TOMATE", tomate.getId(), null, null, BigDecimal.ONE, kg.getId(), new BigDecimal("1000"),
                        null, null, null),
                new ConfirmedLine("ACEITE", null, "Aceite", u.getId(), BigDecimal.ONE, u.getId(), new BigDecimal("1000"),
                        null, null, null))), few);

        assertEquals(ChatStatus.REGISTERED, result.status());
        verify(supplierService, never()).createSupplier(any());
        verify(productService, never()).createProduct(any());
        verify(aliasRepository, never()).upsert(any());     // sin proveedor no hay de quién aprender
        assertEquals(2, result.skipped().size());
        var cmd = ArgumentCaptor.forClass(PurchaseService.CreatePurchaseCommand.class);
        verify(purchaseService).createPurchase(cmd.capture());
        assertNull(cmd.getValue().supplierId());
        assertEquals(1, cmd.getValue().items().size());
    }

    // ── fixtures ──

    private void reads(InvoiceReading reading) {
        when(aiService.interpretChatDocument(any())).thenReturn(
                new AIService.DocumentReading(AIService.DocumentKind.INVOICE, reading, null));
    }

    private static InvoiceReading reading(String type, String cuit, String supplier, BigDecimal total, InvoiceLineReading... lines) {
        return new InvoiceReading(supplier, cuit, type, "0001-00001234", LocalDate.of(2026, 11, 15), total, List.of(lines));
    }

    private static InvoiceLineReading line(String text, String qty, String unit, String price, String total, String iva,
                                           String catalogName, String suggested) {
        return new InvoiceLineReading(text, new BigDecimal(qty), unit, new BigDecimal(price),
                total == null ? null : new BigDecimal(total), iva == null ? null : new BigDecimal(iva), catalogName, suggested);
    }

    private static Unit unit(String name, String abbreviation) {
        Unit unit = new Unit();
        unit.setId(UUID.randomUUID());
        unit.setName(name);
        unit.setAbbreviation(abbreviation);
        return unit;
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

    private static Supplier supplier(String name, String cuit) {
        Supplier s = new Supplier();
        s.setId(UUID.randomUUID());
        s.setRestaurantId(R);
        s.setName(name);
        s.setCuit(cuit);
        return s;
    }
}
