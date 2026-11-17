package com.chefcontrol.domain.sale;

import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.restaurant.TaxCondition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El IVA de una venta. Lo que importa es el ingreso: para el RI, lo cobrado sin el IVA (que es del fisco); para el
 * monotributista y quien no cargó la condición, todo lo cobrado, que es lo de siempre.
 */
class SaleIvaTest {

    @Test
    void onlyAResponsableInscriptoSeparatesTheIvaOfWhatItSells() {
        Restaurant restaurant = new Restaurant();
        assertThat(restaurant.salesIvaRate()).isEqualByComparingTo("0"); // sin condición: como monotributo

        restaurant.setTaxCondition(TaxCondition.MONOTRIBUTO);
        assertThat(restaurant.salesIvaRate()).isEqualByComparingTo("0");

        restaurant.setTaxCondition(TaxCondition.RESPONSABLE_INSCRIPTO);
        assertThat(restaurant.salesIvaRate()).isEqualByComparingTo("21"); // el default de la alícuota de venta

        restaurant.setSalesIvaPercentage(new BigDecimal("10.5"));
        assertThat(restaurant.salesIvaRate()).isEqualByComparingTo("10.5");
    }

    @Test
    void theNetIsSeparatedLineByLineAndNeverLosesACent() {
        // 3 × 1210 = 3630 → neto 3000, IVA 630. 1 × 100 = 100 → 100 / 1,21 = 82,6446 → 82,64 y el IVA es lo que falta.
        Sale sale = new Sale();
        List<SaleItem> lines = List.of(line(3, "1210"), line(1, "100"));

        sale.totalize(new BigDecimal("21"), lines);

        assertMoney(lines.get(0).getNetAmount(), "3000.00");
        assertMoney(lines.get(1).getNetAmount(), "82.64");
        assertMoney(sale.getTotalAmount(), "3730.00");
        assertMoney(sale.getNetTotal(), "3082.64");
        assertMoney(sale.getIvaTotal(), "647.36");
        assertThat(sale.getIvaPercentage()).isEqualByComparingTo("21");
        assertThat(sale.hasIvaData()).isTrue();
    }

    @Test
    void zeroRateMeansTheWholePriceIsIncome() {
        Sale sale = new Sale();
        sale.totalize(BigDecimal.ZERO, List.of(line(2, "4500.50")));

        assertMoney(sale.getNetTotal(), "9001.00");
        assertMoney(sale.getIvaTotal(), "0.00");
        assertThat(sale.hasIvaData()).isTrue(); // 0 es un dato: "no tenía IVA", no "anterior al IVA"
    }

    @Test
    void aSaleFromBeforeIvaHasNoIvaData() {
        Sale legacy = Sale.builder().totalAmount(new BigDecimal("5000.00")).build();

        assertThat(legacy.hasIvaData()).isFalse();
        assertThat(legacy.getIvaTotal()).isNull();
    }

    private static SaleItem line(int quantity, String unitPrice) {
        return SaleItem.builder().quantity(quantity).unitPrice(new BigDecimal(unitPrice)).build();
    }

    private static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualTo(new BigDecimal(expected)); // también la escala: plata a 2 decimales
    }
}
