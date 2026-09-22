package com.chefcontrol.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * La merma de un insumo en un período, partida en las dos que el ledger sabe distinguir:
 *
 * <ul>
 *   <li>{@code standard} — la de limpieza, que el sistema descuenta solo al vender según el
 *       rendimiento del producto. Cuelga del {@code sale_item}.</li>
 *   <li>{@code registered} — la que alguien cargó a mano: vencido, dañado, robo, sobreproducción.
 *       Cuelga del {@code waste_event}.</li>
 * </ul>
 *
 * <p>La segunda contra la primera es la resta que responde "¿se está tirando más de lo que el
 * producto pierde por naturaleza?". Antes de que la merma estándar fuera un movimiento propio
 * estaba escondida adentro del consumo y esta comparación no se podía hacer.
 */
public record ProductWasteSummary(
        UUID restaurantId,
        UUID productId,
        BigDecimal standard,
        BigDecimal registered
) {
    /** Cuánto se pasó la merma registrada por encima de la estándar, como fracción (0,35 = 35%). */
    public BigDecimal excessRatio() {
        if (standard == null || standard.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return registered.divide(standard, 4, java.math.RoundingMode.HALF_UP);
    }
}
