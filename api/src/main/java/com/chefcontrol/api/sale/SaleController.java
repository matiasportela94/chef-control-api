package com.chefcontrol.api.sale;

import com.chefcontrol.api.sale.dto.CreateSaleRequest;
import com.chefcontrol.api.sale.dto.SaleResponse;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.SaleService;
import com.chefcontrol.application.service.SaleService.CreateSaleCommand;
import com.chefcontrol.application.service.SaleService.SaleItemCommand;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.shared.PageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public ResponseEntity<PagedResponse<SaleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(PagedResponse.of(
                saleService.listSales(PageRequest.of(page, size)).map(SaleResponse::summary)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleResponse> get(@PathVariable UUID id) {
        Sale sale = saleService.getSale(id);
        return ResponseEntity.ok(SaleResponse.from(sale, saleService.getSaleItems(id)));
    }

    @PostMapping
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody CreateSaleRequest request) {
        var cmd = new CreateSaleCommand(
                request.notes(),
                request.soldAt(),
                request.items().stream()
                        .map(i -> new SaleItemCommand(i.menuItemId(), i.quantity()))
                        .toList());
        Sale sale = saleService.recordSale(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SaleResponse.from(sale, saleService.getSaleItems(sale.getId())));
    }
}
