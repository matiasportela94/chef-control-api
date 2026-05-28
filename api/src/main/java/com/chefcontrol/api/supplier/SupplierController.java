package com.chefcontrol.api.supplier;

import com.chefcontrol.api.supplier.dto.CreateSupplierRequest;
import com.chefcontrol.api.supplier.dto.SupplierResponse;
import com.chefcontrol.api.supplier.dto.UpdateSupplierRequest;
import com.chefcontrol.application.service.SupplierService;
import com.chefcontrol.application.service.SupplierService.SupplierCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> listSuppliers() {
        return ResponseEntity.ok(
                supplierService.listSuppliers().stream().map(SupplierResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierResponse response = SupplierResponse.from(
                supplierService.createSupplier(toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(SupplierResponse.from(
                supplierService.updateSupplier(id, toCommand(request))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Void> deactivateSupplier(@PathVariable UUID id) {
        supplierService.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }

    private SupplierCommand toCommand(CreateSupplierRequest r) {
        return new SupplierCommand(r.name(), r.phone(), r.email(), r.address(), r.notes());
    }

    private SupplierCommand toCommand(UpdateSupplierRequest r) {
        return new SupplierCommand(r.name(), r.phone(), r.email(), r.address(), r.notes());
    }
}
