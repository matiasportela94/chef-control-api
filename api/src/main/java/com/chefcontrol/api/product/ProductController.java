package com.chefcontrol.api.product;

import com.chefcontrol.api.product.dto.CreateProductRequest;
import com.chefcontrol.api.product.dto.ProductResponse;
import com.chefcontrol.api.product.dto.UpdateProductRequest;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.application.service.ProductService;
import com.chefcontrol.application.service.ProductService.CreateProductCommand;
import com.chefcontrol.application.service.ProductService.UpdateProductCommand;
import com.chefcontrol.application.service.StockService;
import jakarta.validation.Valid;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final StockService stockService;

    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                productService.listProducts(PageRequest.of(page, size)), ProductResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ProductResponse.from(productService.getProduct(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        var command = new CreateProductCommand(
                request.name(),
                request.sku(),
                request.defaultUnitId(),
                request.categoryId(),
                request.minStock(),
                request.maxStock());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductResponse.from(productService.createProduct(command)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        var command = new UpdateProductCommand(
                request.name(),
                request.sku(),
                request.defaultUnitId(),
                request.categoryId(),
                request.minStock(),
                request.maxStock());
        return ResponseEntity.ok(ProductResponse.from(productService.updateProduct(id, command)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable UUID id) {
        productService.getProduct(id); // validates product belongs to tenant
        BigDecimal stock = stockService.getCurrentStock(id);
        return ResponseEntity.ok(Map.of("productId", id, "currentStock", stock));
    }
}
