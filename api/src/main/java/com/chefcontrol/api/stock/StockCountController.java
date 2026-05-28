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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/stock-counts")
@RequiredArgsConstructor
public class StockCountController {

    private final StockCountService stockCountService;

    @GetMapping
    public ResponseEntity<PagedResponse<StockCountResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PagedResponse.of(
                stockCountService.listCounts(PageRequest.of(page, size)).map(StockCountResponse::summary)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockCountResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(StockCountResponse.from(stockCountService.getCount(id)));
    }

    @PostMapping
    public ResponseEntity<StockCountResponse> create(@Valid @RequestBody CreateStockCountRequest request) {
        var command = new CreateStockCountCommand(
                request.notes(),
                request.items().stream()
                        .map(i -> new StockCountItemCommand(i.productId(), i.unitId(), i.countedQuantity()))
                        .toList());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StockCountResponse.from(stockCountService.createStockCount(command)));
    }
}
