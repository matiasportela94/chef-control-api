package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.service.SupplierService.SupplierCommand;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.SupplierRepository;
import com.chefcontrol.domain.supplier.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierServiceCuitTest {

    @Mock SupplierRepository supplierRepository;
    @Mock AuditService auditService;

    private static final UUID R = UUID.randomUUID();
    private SupplierService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        when(supplierRepository.save(any())).thenAnswer(i -> {
            Supplier s = i.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        service = new SupplierService(supplierRepository, auditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void theCuitIsStoredWithoutDashesAndValidated() {
        assertEquals("20123456786", service.createSupplier(command("20-12345678-6")).getCuit());
        assertNull(service.createSupplier(command(" ")).getCuit());
        assertEquals(ErrorCode.INVALID_CUIT, assertThrows(AppException.class, () ->
                service.createSupplier(command("20-12345678-5"))).getErrorCode());
    }

    @Test
    void twoActiveSuppliersCannotShareACuit() {
        Supplier other = new Supplier();
        other.setId(UUID.randomUUID());
        when(supplierRepository.findActiveByRestaurantIdAndCuit(R, "20123456786")).thenReturn(Optional.of(other));

        assertEquals(ErrorCode.SUPPLIER_CUIT_TAKEN, assertThrows(AppException.class, () ->
                service.createSupplier(command("20123456786"))).getErrorCode());
        // El mismo proveedor, al editarse, no choca consigo mismo.
        when(supplierRepository.findByIdAndRestaurantId(other.getId(), R)).thenReturn(Optional.of(other));
        assertEquals("20123456786", service.updateSupplier(other.getId(), command("20123456786")).getCuit());
    }

    private static SupplierCommand command(String cuit) {
        return new SupplierCommand("Verdulería", null, null, null, null, cuit);
    }
}
