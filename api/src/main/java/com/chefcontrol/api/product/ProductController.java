package com.chefcontrol.api.product;

import com.chefcontrol.api.product.dto.CreateProductRequest;
import com.chefcontrol.api.product.dto.ProductResponse;
import com.chefcontrol.api.foodcost.dto.ProductCostEvolutionResponse;
import com.chefcontrol.api.product.dto.UpdateProductRequest;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.ProductService;
import com.chefcontrol.application.service.FoodCostService;
import com.chefcontrol.application.service.ProductService.CreateProductCommand;
import com.chefcontrol.application.service.ProductService.UpdateProductCommand;
import com.chefcontrol.application.service.StockBatchService;
import com.chefcontrol.application.service.StockService;
import jakarta.validation.Valid;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final StockService stockService;
    private final FoodCostService foodCostService;
    private final StockBatchService stockBatchService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTS_VIEW')")
    public ResponseEntity<PagedResponse<ProductResponse>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var products  = productService.listProducts(PageRequest.of(page, size));
        var stockMap  = stockService.getAllCurrentStocks();
        var expiryMap = stockBatchService.getNextExpirationDates();
        return ResponseEntity.ok(PagedResponse.from(
                products, p -> ProductResponse.from(p,
                        stockMap.getOrDefault(p.getId(), BigDecimal.ZERO),
                        expiryMap.get(p.getId()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS_VIEW')")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ProductResponse.from(productService.getProduct(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTS_CREATE')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        var command = new CreateProductCommand(
                request.name(),
                request.sku(),
                request.defaultUnitId(),
                request.categoryId(),
                request.minStock(),
                request.maxStock(),
                request.yieldPercentage());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductResponse.from(productService.createProduct(command)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS_UPDATE')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        var command = new UpdateProductCommand(
                request.name(),
                request.sku(),
                request.defaultUnitId(),
                request.categoryId(),
                request.minStock(),
                request.maxStock(),
                request.yieldPercentage());
        return ResponseEntity.ok(ProductResponse.from(productService.updateProduct(id, command)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS_DELETE')")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stock")
    @PreAuthorize("hasAuthority('PERM_PRODUCTS_VIEW')")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable UUID id) {
        productService.getProduct(id); // validates product belongs to tenant
        BigDecimal stock = stockService.getCurrentStock(id);
        return ResponseEntity.ok(Map.of("productId", id, "currentStock", stock));
    }

    /** Costo por unidad comprada y por unidad utilizable, en cada momento en que alguno cambió. */
    @GetMapping("/{id}/cost-evolution")
    @PreAuthorize("hasAuthority('PERM_FOOD_COST_VIEW')")
    public ResponseEntity<ProductCostEvolutionResponse> getProductCostEvolution(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        var product = productService.getProduct(id);
        return ResponseEntity.ok(ProductCostEvolutionResponse.from(id, product.getName(),
                product.getDefaultUnitAbbreviation(), from, to,
                foodCostService.seriesReliableFrom(),
                foodCostService.calculateProductCostEvolution(id, from, to)));
    }
}
