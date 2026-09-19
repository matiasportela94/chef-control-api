package com.chefcontrol.application.service;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertSeverity;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.stock.StockBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
            String msg = String.format("Stock de '%s' (%.2f) está por debajo del mínimo (%.2f)",
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
            String msg = String.format("Stock de '%s' (%.2f) supera el máximo (%.2f)",
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
                    ? String.format("'%s' venció hace %d día(s) (%.2f unidades sin usar)",
                            product.getName(), -daysLeft, batch.getQuantityRemaining())
                    : daysLeft == 0
                    ? String.format("'%s' vence hoy (%.2f unidades sin usar)",
                            product.getName(), batch.getQuantityRemaining())
                    : String.format("'%s' vence en %d día(s) (%.2f unidades sin usar)",
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
