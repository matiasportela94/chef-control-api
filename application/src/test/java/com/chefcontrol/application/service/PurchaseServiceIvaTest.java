package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.PurchaseService.CreatePurchaseCommand;
import com.chefcontrol.application.service.PurchaseService.ItemPriceUpdate;
import com.chefcontrol.application.service.PurchaseService.PurchaseItemCommand;
import com.chefcontrol.application.service.PurchaseService.UpdatePurchaseCommand;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.PurchaseItem;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.restaurant.TaxCondition;
import com.chefcontrol.domain.stock.StockMovement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Alta y corrección de compras con IVA: qué se guarda en la línea y qué costo entra al stock. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseServiceIvaTest {

    @Mock PurchaseRepository purchaseRepository;
    @Mock PurchaseItemRepository purchaseItemRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock StockBatchRepository stockBatchRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock ProductRepository productRepository;
    @Mock UnitRepository unitRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock StockBatchService stockBatchService;
    @Mock UnitConversionService unitConversionService;
    @Mock AlertEvaluationService alertEvaluationService;
    @Mock AuditService auditService;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock com.chefcontrol.domain.repository.PurchaseAttachmentRepository purchaseAttachmentRepository;
    @InjectMocks PurchaseService service;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final Product tomato = product("21");

    @BeforeEach
    void setUp() {
        TenantContext.set(restaurantId);
        Unit kg = new Unit();
        kg.setId(unitId);
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(kg));
        when(productRepository.findByIdAndRestaurantId(tomato.getId(), restaurantId)).thenReturn(Optional.of(tomato));
        when(unitConversionService.convert(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(unitConversionService.convertPrice(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.getCurrentStock(any(), any())).thenReturn(BigDecimal.ZERO);
        when(purchaseRepository.save(any())).thenAnswer(inv -> withId(inv.<Purchase>getArgument(0)));
        when(purchaseItemRepository.save(any())).thenAnswer(inv -> {
            PurchaseItem item = inv.getArgument(0);
            if (item.getId() == null) item.setId(UUID.randomUUID());
            return item;
        });
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void responsableInscriptoDefaultsToPricesWithoutIvaAndStocksTheNet() {
        restaurantIs(TaxCondition.RESPONSABLE_INSCRIPTO);

        Purchase purchase = service.createPurchase(purchaseOf(null, item("1000", null)));

        assertThat(purchase.getPricesIncludeIva()).isFalse();
        assertThat(purchase.getNetTotal()).isEqualByComparingTo("2000");
        assertThat(purchase.getIvaTotal()).isEqualByComparingTo("420");
        assertThat(purchase.getTotal()).isEqualByComparingTo("2420");
        assertThat(savedLine().getIvaPercentage()).isEqualByComparingTo("21"); // la del insumo
        assertThat(stockCost()).isEqualByComparingTo("1000");
    }

    @Test
    void monotributoWithPricesWithoutIvaStocksNetPlusIva() {
        restaurantIs(TaxCondition.MONOTRIBUTO);

        service.createPurchase(purchaseOf(false, item("1000", null)));

        assertThat(stockCost()).isEqualByComparingTo("1210");
    }

    @Test
    void withoutTaxConditionPricesIncludeIvaAndTheCostIsThePriceAsBefore() {
        restaurantIs(null);

        Purchase purchase = service.createPurchase(purchaseOf(null, item("1210", null)));

        assertThat(purchase.getPricesIncludeIva()).isTrue();
        assertThat(purchase.getNetTotal()).isEqualByComparingTo("2000");
        assertThat(purchase.getTotal()).isEqualByComparingTo("2420");
        assertThat(stockCost()).isEqualByComparingTo("1210");
    }

    @Test
    void anExplicitZeroRateIsNoIvaEvenIfTheProductHasOne() {
        restaurantIs(TaxCondition.RESPONSABLE_INSCRIPTO);

        Purchase purchase = service.createPurchase(purchaseOf(true, item("1000", BigDecimal.ZERO)));

        assertThat(purchase.getIvaTotal()).isEqualByComparingTo("0");
        assertThat(stockCost()).isEqualByComparingTo("1000");
    }

    @Test
    void aProductWithoutRateBuysWithZero() {
        restaurantIs(TaxCondition.RESPONSABLE_INSCRIPTO);
        tomato.setIvaPercentage(null);

        Purchase purchase = service.createPurchase(purchaseOf(true, item("1000", null)));

        assertThat(savedLine().getIvaPercentage()).isEqualByComparingTo("0");
        assertThat(purchase.getIvaTotal()).isEqualByComparingTo("0");
    }

    @Test
    void anInvalidRateIsRejectedBeforeWritingAnything() {
        restaurantIs(TaxCondition.RESPONSABLE_INSCRIPTO);

        assertThatThrownBy(() -> service.createPurchase(purchaseOf(true, item("1000", new BigDecimal("19")))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IVA_PERCENTAGE);
        verifyNoInteractions(purchaseRepository, purchaseItemRepository, stockMovementRepository);
    }

    @Test
    void correctingAPriceRecalculatesNetIvaCostAndTotals() {
        restaurantIs(TaxCondition.RESPONSABLE_INSCRIPTO);
        Purchase purchase = existingPurchase(true);
        PurchaseItem line = existingLine(purchase.getId(), "1210", "21");
        when(purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchase.getId())).thenReturn(List.of(line));

        Purchase corrected = service.updatePurchase(purchase.getId(), correction(line.getId(), "2420"));

        assertThat(line.getNetAmount()).isEqualByComparingTo("4000");
        assertThat(line.getIvaAmount()).isEqualByComparingTo("840");
        assertThat(corrected.getTotal()).isEqualByComparingTo("4840");
        assertThat(corrected.getNetTotal()).isEqualByComparingTo("4000");
        verify(stockMovementRepository).updatePurchaseCostPerUnit(eq(line.getId()), argThat(c -> c.compareTo(new BigDecimal("2000")) == 0));
        verify(stockBatchRepository).updateCostPerUnitByPurchaseItemId(eq(line.getId()), argThat(c -> c.compareTo(new BigDecimal("2000")) == 0));
    }

    @Test
    void correctingAPurchaseFromBeforeIvaKeepsTheOldRule() {
        restaurantIs(TaxCondition.RESPONSABLE_INSCRIPTO);
        Purchase purchase = existingPurchase(null);
        PurchaseItem line = existingLine(purchase.getId(), "1210", null);
        when(purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchase.getId())).thenReturn(List.of(line));

        Purchase corrected = service.updatePurchase(purchase.getId(), correction(line.getId(), "1500"));

        assertThat(corrected.getTotal()).isEqualByComparingTo("3000");
        assertThat(corrected.getNetTotal()).isNull();
        verify(stockMovementRepository).updatePurchaseCostPerUnit(eq(line.getId()), argThat(c -> c.compareTo(new BigDecimal("1500")) == 0));
    }

    @Test
    void aLineFromAnotherPurchaseCannotBeCorrected() {
        restaurantIs(TaxCondition.RESPONSABLE_INSCRIPTO);
        Purchase purchase = existingPurchase(true);
        PurchaseItem foreign = existingLine(UUID.randomUUID(), "1210", "21");

        assertThatThrownBy(() -> service.updatePurchase(purchase.getId(), correction(foreign.getId(), "1")))
                .isInstanceOf(AppException.class);
        verify(stockMovementRepository, never()).updatePurchaseCostPerUnit(any(), any());
    }

    @Test
    void theIvaReportSplitsByRateAndKeepsPurchasesFromBeforeIvaApart() {
        Purchase a = reportPurchase(true, "1000", "210", "1210");
        Purchase b = reportPurchase(false, "300", "31.50", "331.50");
        Purchase legacy = reportPurchase(null, null, null, "500");
        Instant from = Instant.parse("2026-11-01T03:00:00Z"), to = Instant.parse("2026-12-01T02:59:59Z");
        when(purchaseRepository.findActiveByRestaurantIdAndPurchasedAtBetween(restaurantId, from, to))
                .thenReturn(List.of(a, b, legacy));
        when(purchaseItemRepository.findByPurchaseIdIn(List.of(a.getId(), b.getId()))).thenReturn(List.of(
                reportLine("21", "600", "126"), reportLine("21.00", "400", "84"), reportLine("10.5", "300", "31.50")));

        var report = service.getPurchaseIvaReport(from, to);

        assertThat(report.netTotal()).isEqualByComparingTo("1300");
        assertThat(report.ivaTotal()).isEqualByComparingTo("241.50");
        assertThat(report.total()).isEqualByComparingTo("1541.50");
        assertThat(report.byRate()).hasSize(2); // 21 y 21.00 son la misma alícuota
        assertThat(report.byRate().get(0).ivaPercentage()).isEqualByComparingTo("10.5");
        assertThat(report.byRate().get(1).netAmount()).isEqualByComparingTo("1000");
        assertThat(report.byRate().get(1).ivaAmount()).isEqualByComparingTo("210");
        assertThat(report.purchases()).containsExactly(a, b, legacy);
        assertThat(report.purchasesWithoutIvaData()).isEqualTo(1);
        assertThat(report.totalWithoutIvaData()).isEqualByComparingTo("500");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void restaurantIs(TaxCondition condition) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setTaxCondition(condition);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    }

    private Product product(String ivaPercentage) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setRestaurantId(restaurantId);
        p.setName("Tomate");
        p.setDefaultUnitId(unitId);
        p.setIvaPercentage(new BigDecimal(ivaPercentage));
        return p;
    }

    // ── Factura (F19, tanda 7) ──

    @org.junit.jupiter.api.Test
    void correctingAPurchaseMovesTheInvoiceOnlyFromAReversedOne() {
        java.util.UUID old = java.util.UUID.randomUUID();
        Purchase reversed = Purchase.builder().id(old).restaurantId(restaurantId)
                .status(com.chefcontrol.domain.purchase.PurchaseStatus.REVERSED).build();
        when(purchaseRepository.findByIdAndRestaurantId(old, restaurantId)).thenReturn(java.util.Optional.of(reversed));

        Purchase created = service.createPurchase(new CreatePurchaseCommand(null, null, Instant.parse("2026-11-16T12:00:00Z"),
                false, List.of(item("1000", null)), com.chefcontrol.domain.purchase.InvoiceType.A, " 0001-00001234 ", old));

        assertThat(created.getInvoiceType()).isEqualTo(com.chefcontrol.domain.purchase.InvoiceType.A);
        assertThat(created.getInvoiceNumber()).isEqualTo("0001-00001234");
        org.mockito.Mockito.verify(purchaseAttachmentRepository).moveAll(old, created.getId());

        reversed.setStatus(com.chefcontrol.domain.purchase.PurchaseStatus.ACTIVE);   // una compra viva no se queda sin factura
        assertThatThrownBy(() -> service.createPurchase(new CreatePurchaseCommand(null, null,
                Instant.parse("2026-11-16T12:00:00Z"), false, List.of(item("1000", null)), null, null, old)))
                .isInstanceOf(com.chefcontrol.application.exception.AppException.class)
                .extracting("errorCode").isEqualTo(com.chefcontrol.application.exception.ErrorCode.ATTACHMENTS_NOT_MOVABLE);
    }

    private PurchaseItemCommand item(String price, BigDecimal ivaPercentage) {
        return new PurchaseItemCommand(tomato.getId(), unitId, new BigDecimal("2"), new BigDecimal(price), null, ivaPercentage);
    }

    private static CreatePurchaseCommand purchaseOf(Boolean pricesIncludeIva, PurchaseItemCommand item) {
        return new CreatePurchaseCommand(null, null, Instant.parse("2026-11-16T12:00:00Z"), pricesIncludeIva, List.of(item));
    }

    private PurchaseItem savedLine() {
        var captor = ArgumentCaptor.forClass(PurchaseItem.class);
        verify(purchaseItemRepository).save(captor.capture());
        return captor.getValue();
    }

    /** El costo del movimiento de stock, que tiene que ser el mismo del lote. */
    private BigDecimal stockCost() {
        var captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        BigDecimal cost = captor.getValue().getCostPerUnit();
        verify(stockBatchService).createBatch(any(), any(), any(), any(), any(), eq(cost), any());
        return cost;
    }

    private Purchase existingPurchase(Boolean pricesIncludeIva) {
        Purchase p = withId(Purchase.builder().restaurantId(restaurantId).pricesIncludeIva(pricesIncludeIva).total(BigDecimal.ONE).build());
        when(purchaseRepository.findByIdAndRestaurantId(p.getId(), restaurantId)).thenReturn(Optional.of(p));
        return p;
    }

    private PurchaseItem existingLine(UUID purchaseId, String price, String ivaPercentage) {
        PurchaseItem line = PurchaseItem.builder().id(UUID.randomUUID()).purchaseId(purchaseId)
                .quantity(new BigDecimal("2")).pricePerUnit(new BigDecimal(price))
                .ivaPercentage(ivaPercentage == null ? null : new BigDecimal(ivaPercentage)).build();
        when(purchaseItemRepository.findById(line.getId())).thenReturn(Optional.of(line));
        return line;
    }

    private static UpdatePurchaseCommand correction(UUID itemId, String price) {
        return new UpdatePurchaseCommand(null, null, null, List.of(new ItemPriceUpdate(itemId, new BigDecimal(price))));
    }

    private static Purchase reportPurchase(Boolean pricesIncludeIva, String net, String iva, String total) {
        return withId(Purchase.builder().pricesIncludeIva(pricesIncludeIva)
                .netTotal(net == null ? null : new BigDecimal(net)).ivaTotal(iva == null ? null : new BigDecimal(iva))
                .total(new BigDecimal(total)).build());
    }

    private static PurchaseItem reportLine(String rate, String net, String iva) {
        return PurchaseItem.builder().ivaPercentage(new BigDecimal(rate))
                .netAmount(new BigDecimal(net)).ivaAmount(new BigDecimal(iva)).build();
    }

    private static Purchase withId(Purchase p) {
        if (p.getId() == null) p.setId(UUID.randomUUID());
        return p;
    }
}
