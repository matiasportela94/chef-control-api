package com.chefcontrol.api.stock;

import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.api.stock.dto.StockMovementResponse;
import com.chefcontrol.application.service.StockService;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/stock-movements")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockService stockService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STOCK_VIEW')")
    public ResponseEntity<PagedResponse<StockMovementResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID productId) {
        var movements = productId != null
                ? stockService.listMovementsByProduct(productId, PageRequest.of(page, size))
                : stockService.listMovements(PageRequest.of(page, size));
        return ResponseEntity.ok(PagedResponse.of(movements.map(StockMovementResponse::from)));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('PERM_STOCK_DELETE')")
    public ResponseEntity<StockMovementResponse> reverse(@PathVariable UUID id) {
        return ResponseEntity.ok(StockMovementResponse.from(stockService.reverseMovement(id)));
    }
}
