package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.history.PriceHistoryEntry;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.PriceHistoryRepository;
import com.chefcontrol.domain.repository.ProductCategoryRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ponytail: lo único que puede salir mal acá es escribir de más o de menos. De menos rompe la
 * serie; de más la llena de filas que no cambiaron nada y "cuánto rendía en marzo" pasa a ser
 * recorrer repetidos. El caso 90 vs 90.00 está porque BigDecimal.equals() dice que son distintos.
 */
@ExtendWith(MockitoExtension.class)
class ProductYieldHistoryTest {

    @Mock ProductRepository productRepository;
    @Mock ProductCategoryRepository categoryRepository;
    @Mock UnitRepository unitRepository;
    @Mock AuditService auditService;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock CurrentUserProvider currentUserProvider;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @BeforeEach void setTenant() { TenantContext.set(restaurantId); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private ProductService service() {
        return new ProductService(productRepository, categoryRepository, unitRepository,
                auditService, priceHistoryRepository, currentUserProvider);
    }

    private void updateWith(String currentYield, BigDecimal newYield) {
        Product existing = new Product();
        existing.setId(productId);
        existing.setRestaurantId(restaurantId);
        existing.setName("Papa");
        existing.setDefaultUnitId(unitId);
        existing.setYieldPercentage(new BigDecimal(currentYield));

        when(productRepository.findByIdAndRestaurantId(productId, restaurantId))
                .thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Unit unit = new Unit();
        unit.setId(unitId);
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        service().updateProduct(productId, new ProductService.UpdateProductCommand(
                "Papa", null, unitId, null, null, null, newYield));
    }

    @Test
    void cambiarElRendimiento_agregaUnPuntoALaSerie() {
        updateWith("100", new BigDecimal("85"));

        ArgumentCaptor<PriceHistoryEntry> captor = ArgumentCaptor.forClass(PriceHistoryEntry.class);
        verify(priceHistoryRepository).saveProductYield(captor.capture());
        assertThat(captor.getValue().value()).isEqualByComparingTo("85");
        assertThat(captor.getValue().entityId()).isEqualTo(productId);
        assertThat(captor.getValue().restaurantId()).isEqualTo(restaurantId);
    }

    @Test
    void guardarSinTocarElRendimiento_noEscribeNada() {
        updateWith("90", new BigDecimal("90"));

        verify(priceHistoryRepository, never()).saveProductYield(any());
    }

    /** 90 y 90.00 no son equals() para BigDecimal, pero valen lo mismo. */
    @Test
    void mismoValorConOtraEscala_noEscribeNada() {
        updateWith("90", new BigDecimal("90.00"));

        verify(priceHistoryRepository, never()).saveProductYield(any());
    }
}
