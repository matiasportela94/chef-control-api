package com.chefcontrol.domain.purchase;

import com.chefcontrol.domain.shared.valueobject.IvaRates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.chefcontrol.domain.restaurant.TaxCondition.MONOTRIBUTO;
import static com.chefcontrol.domain.restaurant.TaxCondition.RESPONSABLE_INSCRIPTO;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla del IVA de una compra. La cuenta que importa es el costo que entra al stock: para el RI, el neto; para
 * el monotributista y quien no cargó la condición, neto + IVA, que con precios con IVA es lo de siempre.
 */
class PurchaseIvaTest {

    private static final BigDecimal TWENTY_ONE = new BigDecimal("21");

    @Test
    void responsableInscriptoWithPricesIncludingIvaCostsTheNet() {
        PurchaseItem line = line("121", "2", true, TWENTY_ONE);

        assertMoney(line.getNetAmount(), "200.00");
        assertMoney(line.getIvaAmount(), "42.00");
        assertThat(line.stockCostPerUnit(true, RESPONSABLE_INSCRIPTO)).isEqualByComparingTo("100");
    }

    @Test
    void responsableInscriptoWithPricesWithoutIvaCostsThePrice() {
        PurchaseItem line = line("100", "2", false, TWENTY_ONE);

        assertMoney(line.getNetAmount(), "200.00");
        assertMoney(line.getIvaAmount(), "42.00");
        assertThat(line.stockCostPerUnit(false, RESPONSABLE_INSCRIPTO)).isEqualByComparingTo("100");
    }

    @Test
    void monotributoWithPricesIncludingIvaCostsThePriceAsBefore() {
        PurchaseItem line = line("121", "2", true, TWENTY_ONE);

        assertThat(line.stockCostPerUnit(true, MONOTRIBUTO)).isEqualByComparingTo("121");
    }

    @Test
    void monotributoWithPricesWithoutIvaCostsTheNetPlusIva() {
        PurchaseItem line = line("100", "2", false, TWENTY_ONE);

        assertThat(line.stockCostPerUnit(false, MONOTRIBUTO)).isEqualByComparingTo("121");
    }

    @Test
    void withoutTaxConditionItBehavesLikeMonotributo() {
        assertThat(Purchase.pricesIncludeIvaOrDefault(null, null)).isTrue();
        assertThat(Purchase.pricesIncludeIvaOrDefault(null, MONOTRIBUTO)).isTrue();
        assertThat(Purchase.pricesIncludeIvaOrDefault(null, RESPONSABLE_INSCRIPTO)).isFalse();
        assertThat(Purchase.pricesIncludeIvaOrDefault(true, RESPONSABLE_INSCRIPTO)).isTrue();

        assertThat(line("100", "1", false, TWENTY_ONE).stockCostPerUnit(false, null)).isEqualByComparingTo("121");
        assertThat(line("121", "1", true, TWENTY_ONE).stockCostPerUnit(true, null)).isEqualByComparingTo("121");
    }

    @Test
    void theRateIsTheRequestedOneThenTheProductsThenZero() {
        assertThat(PurchaseItem.ivaPercentageOrDefault(new BigDecimal("10.5"), TWENTY_ONE)).isEqualByComparingTo("10.5");
        assertThat(PurchaseItem.ivaPercentageOrDefault(null, TWENTY_ONE)).isEqualByComparingTo("21");
        assertThat(PurchaseItem.ivaPercentageOrDefault(null, null)).isEqualByComparingTo("0");
        // Un 0 explícito es "sin IVA", aunque el insumo tenga tasa.
        assertThat(PurchaseItem.ivaPercentageOrDefault(BigDecimal.ZERO, TWENTY_ONE)).isEqualByComparingTo("0");
    }

    @Test
    void zeroRateLeavesNoIvaAndTheCostIsThePrice() {
        PurchaseItem line = line("100", "3", true, BigDecimal.ZERO);

        assertMoney(line.getNetAmount(), "300.00");
        assertMoney(line.getIvaAmount(), "0.00");
        assertThat(line.stockCostPerUnit(true, RESPONSABLE_INSCRIPTO)).isEqualByComparingTo("100");
    }

    @Test
    void roundingNeverLosesACentAndTheLinesAddUpToThePurchase() {
        // 30 / 1,21 = 24,7934 → neto 24,79 y el IVA es lo que falta: 5,21.
        PurchaseItem a = line("10", "3", true, TWENTY_ONE);
        assertMoney(a.getNetAmount(), "24.79");
        assertMoney(a.getIvaAmount(), "5.21");
        // 33,33 × 10,5% = 3,49965 → 3,50.
        PurchaseItem b = line("33.33", "1", true, new BigDecimal("10.5"));
        PurchaseItem c = line("33.33", "1", false, new BigDecimal("10.5"));
        assertMoney(c.getIvaAmount(), "3.50");

        Purchase purchase = Purchase.builder().pricesIncludeIva(true).build();
        purchase.totalize(List.of(a, b));

        assertThat(purchase.getNetTotal().add(purchase.getIvaTotal())).isEqualByComparingTo(purchase.getTotal());
        assertMoney(purchase.getTotal(), "63.33"); // 30 + 33,33: el importe de la factura, sin centavos de más
    }

    @Test
    void aPurchaseFromBeforeIvaKeepsItsOldTotalAndCost() {
        PurchaseItem legacy = PurchaseItem.builder().pricePerUnit(new BigDecimal("100.5")).quantity(new BigDecimal("3")).build();
        Purchase purchase = new Purchase();

        purchase.totalize(List.of(legacy));

        assertMoney(purchase.getTotal(), "301.50");
        assertThat(purchase.getNetTotal()).isNull();
        assertThat(purchase.getIvaTotal()).isNull();
        assertThat(legacy.stockCostPerUnit(true, RESPONSABLE_INSCRIPTO)).isEqualByComparingTo("100.5");
    }

    @Test
    void onlyArgentineRatesAreValid() {
        assertThat(IvaRates.isValid(new BigDecimal("10.50"))).isTrue();
        assertThat(IvaRates.isValid(new BigDecimal("21.0"))).isTrue();
        assertThat(IvaRates.isValid(BigDecimal.ZERO)).isTrue();
        assertThat(IvaRates.isValid(new BigDecimal("10"))).isFalse();
        assertThat(IvaRates.isValid(new BigDecimal("-21"))).isFalse();
        assertThat(IvaRates.isValid(null)).isFalse();
    }

    private static PurchaseItem line(String price, String quantity, boolean pricesIncludeIva, BigDecimal rate) {
        PurchaseItem line = PurchaseItem.builder()
                .pricePerUnit(new BigDecimal(price)).quantity(new BigDecimal(quantity)).build();
        line.applyIva(pricesIncludeIva, rate);
        return line;
    }

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualTo(new BigDecimal(expected)); // también la escala: plata a 2 decimales
    }
}
