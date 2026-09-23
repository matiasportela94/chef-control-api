package com.chefcontrol.application.service;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertSeverity;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.stock.StockBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ponytail: cubre solo la lógica de evaluateExpirations() (días→severidad, dedupe,
 * resolución de alertas obsoletas) — no hay suite de integración, sigue el patrón
 * mínimo del resto del backend (sin tests previos en este módulo).
 */
@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock ProductRepository productRepository;
    @Mock StockBatchRepository stockBatchRepository;
    @Mock StockMovementRepository stockMovementRepository;

    private AlertEvaluationService service() {
        return new AlertEvaluationService(alertRepository, productRepository, stockBatchRepository,
                stockMovementRepository);
    }

    private Product product(UUID id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Ojo de bife");
        return p;
    }

    private StockBatch batch(UUID restaurantId, UUID productId, LocalDate expiration, BigDecimal qty) {
        return StockBatch.builder()
                .id(UUID.randomUUID())
                .restaurantId(restaurantId)
                .productId(productId)
                .quantityRemaining(qty)
                .expirationDate(expiration)
                .build();
    }

    @Test
    void alreadyExpiredBatch_createsCriticalAlert() {
        UUID restaurantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StockBatch expired = batch(restaurantId, productId, LocalDate.now().minusDays(1), new BigDecimal("2.00"));

        when(stockBatchRepository.findExpiringSoon(any())).thenReturn(List.of(expired));
        when(productRepository.findByIdAndRestaurantId(productId, restaurantId)).thenReturn(Optional.of(product(productId)));
        when(alertRepository.findByProductIdAndTypeAndResolvedAtIsNull(productId, AlertType.EXPIRATION))
                .thenReturn(Optional.empty());
        when(alertRepository.findAllByTypeAndResolvedAtIsNull(AlertType.EXPIRATION)).thenReturn(List.of());

        service().evaluateExpirations();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert saved = captor.getValue();
        assertThat(saved.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(saved.getMessage()).contains("venció hace 1 día");
    }

    @Test
    void batchExpiringInTwoDays_createsWarningAlert() {
        UUID restaurantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StockBatch soon = batch(restaurantId, productId, LocalDate.now().plusDays(2), new BigDecimal("5.00"));

        when(stockBatchRepository.findExpiringSoon(any())).thenReturn(List.of(soon));
        when(productRepository.findByIdAndRestaurantId(productId, restaurantId)).thenReturn(Optional.of(product(productId)));
        when(alertRepository.findByProductIdAndTypeAndResolvedAtIsNull(productId, AlertType.EXPIRATION))
                .thenReturn(Optional.empty());
        when(alertRepository.findAllByTypeAndResolvedAtIsNull(AlertType.EXPIRATION)).thenReturn(List.of());

        service().evaluateExpirations();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void productNoLongerExpiring_resolvesStaleAlert() {
        UUID staleProductId = UUID.randomUUID();
        Alert stale = new Alert();
        stale.setProductId(staleProductId);
        stale.setType(AlertType.EXPIRATION);

        when(stockBatchRepository.findExpiringSoon(any())).thenReturn(List.of());
        when(alertRepository.findAllByTypeAndResolvedAtIsNull(AlertType.EXPIRATION)).thenReturn(List.of(stale));

        service().evaluateExpirations();

        verify(alertRepository).resolveByProductAndType(eq(staleProductId), eq(AlertType.EXPIRATION), any());
        verify(productRepository, never()).findByIdAndRestaurantId(any(), any());
    }
}
