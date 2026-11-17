package com.chefcontrol.api.production;

import com.chefcontrol.api.plan.RequiresFeature;
import com.chefcontrol.api.production.dto.ProductionResponse;
import com.chefcontrol.api.production.dto.RecordProductionRequest;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.ProductionService;
import com.chefcontrol.application.service.ProductionService.RecordProductionCommand;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.shared.PageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** Producciones de preparaciones: "hice 5 kg de mayonesa base". Las automáticas (por una venta) también se listan. */
@RestController
@RequestMapping("/productions")
@RequiresFeature(Feature.RECIPES_AND_FOOD_COST)
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionService productionService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTIONS_CREATE')")
    public ResponseEntity<ProductionResponse> recordProduction(@Valid @RequestBody RecordProductionRequest request) {
        var production = productionService.recordProduction(new RecordProductionCommand(request.productId(),
                request.quantity(), request.unitId(), request.expirationDate(), request.producedAt(), request.notes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductionResponse.from(production,
                productionService.getProductionIngredients(production.getId())));
    }

    /** La más reciente primero, sin los ingredientes. {@code automatic}: solo las automáticas o solo las manuales. */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTIONS_VIEW')")
    public ResponseEntity<PagedResponse<ProductionResponse>> listProductions(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Boolean automatic,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                productionService.listProductions(productId, automatic, from, to, PageRequest.of(page, size)),
                p -> ProductionResponse.from(p, null)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCTIONS_VIEW')")
    public ResponseEntity<ProductionResponse> getProduction(@PathVariable UUID id) {
        return ResponseEntity.ok(ProductionResponse.from(productionService.getProduction(id),
                productionService.getProductionIngredients(id)));
    }

    /** Lo producido sale entero, el lote queda en cero y los ingredientes vuelven; con ella, sus automáticas. */
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('PERM_PRODUCTIONS_DELETE')")
    public ResponseEntity<ProductionResponse> reverseProduction(@PathVariable UUID id) {
        return ResponseEntity.ok(ProductionResponse.from(productionService.reverseProduction(id),
                productionService.getProductionIngredients(id)));
    }
}
