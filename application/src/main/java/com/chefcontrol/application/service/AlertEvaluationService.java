package com.chefcontrol.application.service;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertSeverity;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductWasteSummary;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.stock.StockBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    /** Genera/actualiza la alerta de vencimiento cuando falten esta cantidad de días o menos. */
    private static final int EXPIRATION_WARNING_DAYS = 3;

    private final AlertRepository alertRepository;
    private final ProductRepository productRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * Cuánto puede superar la merma registrada a mano a la estándar antes de avisar, como
     * fracción. Arranca en 0,20 (20%) — es un punto de partida, no un número medido: sale de
     * elegirlo, no de mirar datos. Se ajusta con la property cuando haya semanas reales encima.
     */
    @Value("${app.alerts.waste-above-standard-threshold:0.20}")
    private BigDecimal wasteThreshold;

    /** Ventana del barrido. Una merma puntual no dice nada; la de la semana sí. */
    private static final int WASTE_SWEEP_DAYS = 7;

    /**
     * Los mensajes de alerta los lee una persona en Argentina: coma decimal y punto de miles.
     *
     * <p>Va explícito porque {@code String.format} sin locale usa el del JVM, y entonces el mismo
     * mensaje sale "6,50" en una máquina y "6.50" en otra según dónde se despliegue. Un texto que
     * cambia según el servidor es peor que uno equivocado: no se reproduce.
     */
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    @Transactional
    public void evaluate(UUID productId, UUID restaurantId, BigDecimal stockAfter) {
        Product product = productRepository.findByIdAndRestaurantId(productId, restaurantId)
                .orElse(null);
        if (product == null) return;

        evaluateLowStock(product, restaurantId, stockAfter);
        evaluateOverstock(product, restaurantId, stockAfter);
    }

    private void evaluateLowStock(Product product, UUID restaurantId, BigDecimal stockAfter) {
        if (product.getMinStock() == null) return;

        if (product.isLowStock(stockAfter)) {
            String msg = String.format(ES_AR, "Stock de '%s' (%.2f) está por debajo del mínimo (%.2f)",
                    product.getName(), stockAfter, product.getMinStock());
            AlertSeverity severity = product.lowStockSeverity(stockAfter);
            alertRepository.findByProductIdAndTypeAndResolvedAtIsNull(product.getId(), AlertType.LOW_STOCK)
                    .ifPresentOrElse(existing -> {
                        existing.setMessage(msg);
                        existing.setSeverity(severity);
                        alertRepository.save(existing);
                    }, () -> createAlert(restaurantId, product.getId(), AlertType.LOW_STOCK, severity, msg));
        } else {
            alertRepository.resolveByProductAndType(product.getId(), AlertType.LOW_STOCK, Instant.now());
        }
    }

    private void evaluateOverstock(Product product, UUID restaurantId, BigDecimal stockAfter) {
        if (product.getMaxStock() == null) return;

        if (product.isOverstock(stockAfter)) {
            String msg = String.format(ES_AR, "Stock de '%s' (%.2f) supera el máximo (%.2f)",
                    product.getName(), stockAfter, product.getMaxStock());
            alertRepository.findByProductIdAndTypeAndResolvedAtIsNull(product.getId(), AlertType.OVERSTOCK)
                    .ifPresentOrElse(existing -> {
                        existing.setMessage(msg);
                        alertRepository.save(existing);
                    }, () -> createAlert(restaurantId, product.getId(), AlertType.OVERSTOCK,
                            AlertSeverity.WARNING, msg));
        } else {
            alertRepository.resolveByProductAndType(product.getId(), AlertType.OVERSTOCK, Instant.now());
        }
    }

    /**
     * Barrido nocturno: revisa todos los lotes con stock remanente y genera/actualiza una
     * alerta EXPIRATION por producto cuando el lote más próximo a vencer entra en la ventana
     * de aviso (o ya venció). Resuelve las alertas de productos que dejaron de estar en riesgo.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void evaluateExpirations() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(EXPIRATION_WARNING_DAYS);

        Map<UUID, StockBatch> earliestPerProduct = new HashMap<>();
        for (StockBatch batch : stockBatchRepository.findExpiringSoon(threshold)) {
            earliestPerProduct.merge(batch.getProductId(), batch,
                    (a, b) -> a.getExpirationDate().isBefore(b.getExpirationDate()) ? a : b);
        }

        for (StockBatch batch : earliestPerProduct.values()) {
            Product product = productRepository.findByIdAndRestaurantId(batch.getProductId(), batch.getRestaurantId())
                    .orElse(null);
            if (product == null) continue;

            long daysLeft = ChronoUnit.DAYS.between(today, batch.getExpirationDate());
            AlertSeverity severity = daysLeft <= 0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            String msg = daysLeft < 0
                    ? String.format(ES_AR, "'%s' venció hace %d día(s) (%.2f unidades sin usar)",
                            product.getName(), -daysLeft, batch.getQuantityRemaining())
                    : daysLeft == 0
                    ? String.format(ES_AR, "'%s' vence hoy (%.2f unidades sin usar)",
                            product.getName(), batch.getQuantityRemaining())
                    : String.format(ES_AR, "'%s' vence en %d día(s) (%.2f unidades sin usar)",
                            product.getName(), daysLeft, batch.getQuantityRemaining());

            alertRepository.findByProductIdAndTypeAndResolvedAtIsNull(product.getId(), AlertType.EXPIRATION)
                    .ifPresentOrElse(existing -> {
                        existing.setMessage(msg);
                        existing.setSeverity(severity);
                        alertRepository.save(existing);
                    }, () -> createAlert(batch.getRestaurantId(), product.getId(), AlertType.EXPIRATION, severity, msg));
        }

        resolveStaleExpirationAlerts(earliestPerProduct.keySet());
    }

    /**
     * Barrido semanal: compara, por insumo, la merma que alguien registró a mano contra la
     * estándar de limpieza que el sistema descontó solo al vender.
     *
     * <p>Semanal y no por evento a propósito: una merma puntual no dice nada —se cayó una caja—
     * y avisar por cada una entrena a la gente a ignorar las alertas. La del período sí es señal.
     *
     * <p>Solo entran los insumos que tuvieron merma estándar en la semana (lo garantiza el
     * HAVING de la query): sin rendimiento cargado el esperado es cero, cualquier merma lo
     * supera y la alerta dispararía para todo el catálogo.
     */
    @Scheduled(cron = "${app.alerts.waste-sweep-cron:0 0 7 * * MON}")
    @Transactional
    public void evaluateWasteAboveStandard() {
        Instant from = Instant.now().minus(WASTE_SWEEP_DAYS, ChronoUnit.DAYS);
        Set<UUID> flagged = new HashSet<>();

        for (ProductWasteSummary waste : stockMovementRepository.sumWasteByProductSince(from)) {
            BigDecimal ratio = waste.excessRatio();
            if (ratio.compareTo(wasteThreshold) <= 0) continue;

            Product product = productRepository
                    .findByIdAndRestaurantId(waste.productId(), waste.restaurantId())
                    .orElse(null);
            if (product == null) continue;

            flagged.add(product.getId());

            // Por encima del 100% se tiró más de lo que el producto pierde por naturaleza:
            // ya no es un desvío, es otra cosa pasando.
            AlertSeverity severity = ratio.compareTo(BigDecimal.ONE) > 0
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            String msg = String.format(ES_AR, 
                    "'%s': se registraron %.2f de merma esta semana contra %.2f de merma estándar (%.0f%% más)",
                    product.getName(), waste.registered(), waste.standard(),
                    ratio.multiply(BigDecimal.valueOf(100)));

            alertRepository.findByProductIdAndTypeAndResolvedAtIsNull(product.getId(), AlertType.WASTE_ABOVE_STANDARD)
                    .ifPresentOrElse(existing -> {
                        existing.setMessage(msg);
                        existing.setSeverity(severity);
                        alertRepository.save(existing);
                    }, () -> createAlert(waste.restaurantId(), product.getId(),
                            AlertType.WASTE_ABOVE_STANDARD, severity, msg));
        }

        // La semana que el insumo vuelve a la normalidad la alerta se cierra sola, como las de
        // vencimiento: una alerta que solo se puede cerrar a mano deja de leerse.
        for (Alert alert : alertRepository.findAllByTypeAndResolvedAtIsNull(AlertType.WASTE_ABOVE_STANDARD)) {
            if (!flagged.contains(alert.getProductId())) {
                alertRepository.resolveByProductAndType(alert.getProductId(),
                        AlertType.WASTE_ABOVE_STANDARD, Instant.now());
            }
        }
    }

    private void resolveStaleExpirationAlerts(Set<UUID> stillExpiringProductIds) {
        for (Alert alert : alertRepository.findAllByTypeAndResolvedAtIsNull(AlertType.EXPIRATION)) {
            if (!stillExpiringProductIds.contains(alert.getProductId())) {
                alertRepository.resolveByProductAndType(alert.getProductId(), AlertType.EXPIRATION, Instant.now());
            }
        }
    }

    private void createAlert(UUID restaurantId, UUID productId, AlertType type,
                              AlertSeverity severity, String message) {
        Alert alert = new Alert();
        alert.setRestaurantId(restaurantId);
        alert.setProductId(productId);
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alertRepository.save(alert);
    }
}
