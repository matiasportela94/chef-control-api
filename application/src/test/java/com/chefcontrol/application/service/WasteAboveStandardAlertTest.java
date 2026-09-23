package com.chefcontrol.application.service;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertSeverity;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductWasteSummary;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ponytail: el umbral y la severidad, que son las dos decisiones del barrido, más el cierre
 * automático. Lo demás (qué insumos entran) lo decide el HAVING de la query, no este código.
 */
@ExtendWith(MockitoExtension.class)
class WasteAboveStandardAlertTest {

    @Mock AlertRepository alertRepository;
    @Mock ProductRepository productRepository;
    @Mock StockBatchRepository stockBatchRepository;
    @Mock StockMovementRepository stockMovementRepository;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private AlertEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new AlertEvaluationService(alertRepository, productRepository,
                stockBatchRepository, stockMovementRepository);
        ReflectionTestUtils.setField(service, "wasteThreshold", new BigDecimal("0.20"));
    }

    /** Merma estándar de 10 y registrada la que diga el caso. */
    private void sweepWith(String registered) {
        when(stockMovementRepository.sumWasteByProductSince(any()))
                .thenReturn(List.of(new ProductWasteSummary(restaurantId, productId,
                        new BigDecimal("10.00"), new BigDecimal(registered))));
        service.evaluateWasteAboveStandard();
    }

    private void productExists() {
        Product p = new Product();
        p.setId(productId);
        p.setName("Papa");
        when(productRepository.findByIdAndRestaurantId(productId, restaurantId))
                .thenReturn(Optional.of(p));
        when(alertRepository.findByProductIdAndTypeAndResolvedAtIsNull(productId, AlertType.WASTE_ABOVE_STANDARD))
                .thenReturn(Optional.empty());
    }

    @Test
    void justoEnElUmbral_noAvisa() {
        // 2 sobre 10 es exactamente 20%: el umbral es "más que", no "desde".
        sweepWith("2.00");

        verify(alertRepository, never()).save(any());
    }

    @Test
    void porEncimaDelUmbral_avisaConWarning() {
        productExists();
        sweepWith("3.50");

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(AlertType.WASTE_ABOVE_STANDARD);
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(captor.getValue().getMessage()).contains("Papa").contains("35%");
    }

    /** Más del 100% ya no es un desvío: se tiró más de lo que el producto pierde por naturaleza. */
    @Test
    void masQueLaMermaEstandar_esCritico() {
        productExists();
        sweepWith("15.00");

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void elInsumoQueVuelveALaNormalidad_cierraSuAlerta() {
        UUID otherProductId = UUID.randomUUID();
        Alert stale = new Alert();
        stale.setProductId(otherProductId);
        when(alertRepository.findAllByTypeAndResolvedAtIsNull(AlertType.WASTE_ABOVE_STANDARD))
                .thenReturn(List.of(stale));

        sweepWith("1.00"); // por debajo del umbral: nadie queda marcado

        verify(alertRepository).resolveByProductAndType(eq(otherProductId),
                eq(AlertType.WASTE_ABOVE_STANDARD), any(Instant.class));
    }
}
