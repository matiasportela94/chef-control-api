package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.ProductService.CreateProductCommand;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductIvaTest {

    @Mock ProductRepository productRepository;
    @Mock ProductCategoryRepository categoryRepository;
    @Mock UnitRepository unitRepository;
    @Mock AuditService auditService;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock CurrentUserProvider currentUserProvider;
    @InjectMocks ProductService service;

    private final UUID unitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(UUID.randomUUID());
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(new Unit()));
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void aRateOutsideTheArgentineOnesIsRejected() {
        assertThatThrownBy(() -> service.createProduct(papa("19")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_IVA_PERCENTAGE);
        verify(productRepository, never()).save(any());
    }

    @Test
    void theRateIsSavedAndAuditedAndNullMeansNotSet() {
        assertThat(service.createProduct(papa("10.5")).getIvaPercentage()).isEqualByComparingTo("10.5");
        verify(auditService).log(eq(AuditAction.PRODUCT_CREATED), eq("Product"), any(),
                argThat((Map<String, Object> m) -> new BigDecimal("10.5").equals(m.get("ivaPercentage"))));

        assertThat(service.createProduct(papa(null)).getIvaPercentage()).isNull(); // y el payload no explota con null
    }

    private CreateProductCommand papa(String ivaPercentage) {
        return new CreateProductCommand("Papa", null, unitId, null, null, null, null,
                ivaPercentage == null ? null : new BigDecimal(ivaPercentage));
    }
}
