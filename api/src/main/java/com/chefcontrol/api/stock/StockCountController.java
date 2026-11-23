package com.chefcontrol.api.stock;

import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.api.stock.dto.CreateStockCountRequest;
import com.chefcontrol.api.stock.dto.StockCountResponse;
import com.chefcontrol.application.service.StockCountService;
import com.chefcontrol.application.service.StockCountService.CreateStockCountCommand;
import com.chefcontrol.application.service.StockCountService.StockCountItemCommand;
import com.chefcontrol.domain.shared.PageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/stock-counts")
@RequiredArgsConstructor
public class StockCountController {

    private final StockCountService stockCountService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STOCK_COUNTS_VIEW')")
    public ResponseEntity<PagedResponse<StockCountResponse>> listStockCounts(
                                                                             @RequestParam(defaultValue = "0") int page,
                                                                             @RequestParam(defaultValue = "20") int size) {
        var counts = stockCountService.listCounts(PageRequest.of(page, size));
        var reversed = stockCountService.findReversedStockCountIds(counts.content().stream().map(c -> c.getId()).toList());
        return ResponseEntity.ok(PagedResponse.of(counts.map(c -> StockCountResponse.summary(c, reversed.contains(c.getId())))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_STOCK_COUNTS_VIEW')")
    public ResponseEntity<StockCountResponse> getStockCount(@PathVariable UUID id) {
        return ResponseEntity.ok(StockCountResponse.from(stockCountService.getCount(id)));
    }

    /** Devuelve el stock a como estaba antes de contar. Mismo permiso que revertir un movimiento. */
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('PERM_STOCK_DELETE')")
    public ResponseEntity<StockCountResponse> reverseStockCount(@PathVariable UUID id) {
        return ResponseEntity.ok(StockCountResponse.from(stockCountService.reverseStockCount(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_STOCK_COUNTS_CREATE')")
    public ResponseEntity<StockCountResponse> createStockCount(@Valid @RequestBody CreateStockCountRequest request) {
        var command = new CreateStockCountCommand(
                request.notes(),
                request.items().stream()
                        .map(i -> new StockCountItemCommand(i.productId(), i.unitId(), i.countedQuantity()))
                        .toList());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StockCountResponse.from(stockCountService.createStockCount(command)));
    }
}
