package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.SupplierRepository;
import com.chefcontrol.domain.supplier.ContactInfo;
import com.chefcontrol.domain.supplier.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditService auditService;

    public List<Supplier> listSuppliers() {
        return supplierRepository.findAllByRestaurantIdAndIsActiveTrueOrderByName(TenantContext.require());
    }

    @Transactional
    public Supplier createSupplier(SupplierCommand cmd) {
        Supplier supplier = new Supplier();
        supplier.setRestaurantId(TenantContext.require());
        supplier.setName(cmd.name());
        supplier.setContactInfo(new ContactInfo(cmd.phone(), cmd.email(), cmd.address(), cmd.notes()));
        supplier.setCuit(validCuit(cmd.cuit(), null));
        Supplier saved = supplierRepository.save(supplier);
        auditService.log(AuditAction.SUPPLIER_CREATED, "Supplier", saved.getId());
        return saved;
    }

    @Transactional
    public Supplier updateSupplier(UUID id, SupplierCommand cmd) {
        Supplier supplier = supplierRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.SUPPLIER_NOT_FOUND, "Supplier not found"));
        supplier.setName(cmd.name());
        supplier.setContactInfo(new ContactInfo(cmd.phone(), cmd.email(), cmd.address(), cmd.notes()));
        supplier.setCuit(validCuit(cmd.cuit(), id));
        Supplier saved = supplierRepository.save(supplier);
        auditService.log(AuditAction.SUPPLIER_UPDATED, "Supplier", id);
        return saved;
    }

    @Transactional
    public void deactivateSupplier(UUID id) {
        Supplier supplier = supplierRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.SUPPLIER_NOT_FOUND, "Supplier not found"));
        supplier.setActive(false);
        supplierRepository.save(supplier);
        auditService.log(AuditAction.SUPPLIER_DEACTIVATED, "Supplier", id);
    }

    /** Null o vacío = sin CUIT. Si viene, válido (dígito verificador) y sin otro proveedor activo del local con el mismo. */
    private String validCuit(String raw, UUID self) {
        if (raw == null || raw.isBlank()) return null;
        String cuit = com.chefcontrol.domain.supplier.Cuit.normalize(raw)
                .orElseThrow(() -> AppException.badRequest(ErrorCode.INVALID_CUIT, "Invalid CUIT"));
        supplierRepository.findActiveByRestaurantIdAndCuit(TenantContext.require(), cuit)
                .filter(other -> !other.getId().equals(self))
                .ifPresent(other -> {
                    throw AppException.conflict(ErrorCode.SUPPLIER_CUIT_TAKEN, "Another supplier has that CUIT");
                });
        return cuit;
    }

    public record SupplierCommand(
            String name,
            String phone,
            String email,
            String address,
            String notes,
            String cuit
    ) {}
}
