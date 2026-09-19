package com.chefcontrol.api.product;

import com.chefcontrol.api.product.dto.CategoryResponse;
import com.chefcontrol.api.product.dto.CreateCategoryRequest;
import com.chefcontrol.api.product.dto.UpdateCategoryRequest;
import com.chefcontrol.application.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_CATEGORIES_VIEW')")
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        List<CategoryResponse> categories = productService.listCategories()
                .stream()
                .map(CategoryResponse::from)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CATEGORIES_MANAGE')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = CategoryResponse.from(
                productService.createCategory(request.name(), request.description(), request.color(), request.icon(), request.parentId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_CATEGORIES_MANAGE')")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse response = CategoryResponse.from(
                productService.updateCategory(id, request.name(), request.description(), request.color(), request.icon()));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_CATEGORIES_MANAGE')")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        productService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
