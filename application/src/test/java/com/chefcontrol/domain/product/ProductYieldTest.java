package com.chefcontrol.domain.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ponytail: la única regla que puede estar mal escrita acá es si se divide o se suma.
 * Vive en el test de domain porque es el módulo que no tiene dependencias de test propias
 * (el árbol de tests es el de application).
 */
class ProductYieldTest {

    private Product product(String yield) {
        Product p = new Product();
        p.setYieldPercentage(new BigDecimal(yield));
        return p;
    }

    @Test
    void divideEntreElRendimiento_noSumaElPorcentaje() {
        // 200 g de papa pelada al 90% salen de 222,222 g comprados — no de 220.
        assertThat(product("90").grossQuantityFor(new BigDecimal("200")))
                .isEqualByComparingTo("222.222");
    }

    @Test
    void rendimientoMayorA100_devuelveMenosQueElNeto() {
        // El arroz absorbe agua: 300 g cocidos salen de 120 g crudos.
        assertThat(product("250").grossQuantityFor(new BigDecimal("300")))
                .isEqualByComparingTo("120.000");
    }

    @Test
    void rendimiento100_noToca_laCantidad() {
        assertThat(product("100").grossQuantityFor(new BigDecimal("200")))
                .isEqualByComparingTo("200");
    }

    @Test
    void sinRendimientoCargado_noToca_laCantidad() {
        Product p = new Product();
        p.setYieldPercentage(null);
        assertThat(p.grossQuantityFor(new BigDecimal("200"))).isEqualByComparingTo("200");
    }
}
