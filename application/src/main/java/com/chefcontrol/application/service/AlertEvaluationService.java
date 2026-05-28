package com.chefcontrol.application.service;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertSeverity;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final AlertRepository alertRepository;
    private final ProductRepository productRepository;

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
            boolean alreadyOpen = alertRepository
                    .findByProductIdAndTypeAndResolvedAtIsNull(product.getId(), AlertType.LOW_STOCK)
                    .isPresent();
            if (!alreadyOpen) {
                String msg = String.format("Stock de '%s' (%.3f) está por debajo del mínimo (%.3f)",
                        product.getName(), stockAfter, product.getMinStock());
                createAlert(restaurantId, product.getId(), AlertType.LOW_STOCK,
                        product.lowStockSeverity(stockAfter), msg);
            }
        } else {
            alertRepository.resolveByProductAndType(product.getId(), AlertType.LOW_STOCK, Instant.now());
        }
    }

    private void evaluateOverstock(Product product, UUID restaurantId, BigDecimal stockAfter) {
        if (product.getMaxStock() == null) return;

        if (product.isOverstock(stockAfter)) {
            boolean alreadyOpen = alertRepository
                    .findByProductIdAndTypeAndResolvedAtIsNull(product.getId(), AlertType.OVERSTOCK)
                    .isPresent();
            if (!alreadyOpen) {
                String msg = String.format("Stock de '%s' (%.3f) supera el máximo (%.3f)",
                        product.getName(), stockAfter, product.getMaxStock());
                createAlert(restaurantId, product.getId(), AlertType.OVERSTOCK,
                        AlertSeverity.WARNING, msg);
            }
        } else {
            alertRepository.resolveByProductAndType(product.getId(), AlertType.OVERSTOCK, Instant.now());
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
