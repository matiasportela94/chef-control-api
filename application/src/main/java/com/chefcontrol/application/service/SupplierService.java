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

    public record SupplierCommand(
            String name,
            String phone,
            String email,
            String address,
            String notes
    ) {}
}
