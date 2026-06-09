package com.chefcontrol.api.purchase;

import com.chefcontrol.api.purchase.dto.CreatePurchaseRequest;
import com.chefcontrol.api.purchase.dto.PurchaseDetailResponse;
import com.chefcontrol.api.purchase.dto.PurchaseResponse;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.PurchaseService;
import com.chefcontrol.application.service.PurchaseService.CreatePurchaseCommand;
import com.chefcontrol.application.service.PurchaseService.PurchaseItemCommand;
import com.chefcontrol.domain.purchase.Purchase;
import jakarta.validation.Valid;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    public ResponseEntity<PagedResponse<PurchaseResponse>> listPurchases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                PagedResponse.from(purchaseService.listPurchases(PageRequest.of(page, size)),
                        PurchaseResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseDetailResponse> getPurchase(@PathVariable UUID id) {
        Purchase purchase = purchaseService.getPurchase(id);
        return ResponseEntity.ok(
                PurchaseDetailResponse.from(purchase, purchaseService.getPurchaseItems(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<PurchaseDetailResponse> createPurchase(
            @Valid @RequestBody CreatePurchaseRequest request) {

        CreatePurchaseCommand command = new CreatePurchaseCommand(
                request.supplierId(),
                request.notes(),
                request.purchasedAt(),
                request.items().stream()
                        .map(i -> new PurchaseItemCommand(
                                i.productId(), i.unitId(), i.quantity(), i.pricePerUnit(), i.expirationDate()))
                        .toList());

        Purchase purchase = purchaseService.createPurchase(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                PurchaseDetailResponse.from(purchase, purchaseService.getPurchaseItems(purchase.getId())));
    }
}
