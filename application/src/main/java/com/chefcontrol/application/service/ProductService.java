package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.history.PriceHistoryEntry;
import com.chefcontrol.domain.repository.PriceHistoryRepository;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.ProductCategory;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.ProductCategoryRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final AuditService auditService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final CurrentUserProvider currentUserProvider;

    // ── Categories ──────────────────────────────────────────────────────────

    public List<ProductCategory> listCategories() {
        return categoryRepository.findAllAccessibleToRestaurant(TenantContext.require());
    }

    @Transactional
    public ProductCategory createCategory(String name, String description, String color, String icon, UUID parentId) {
        UUID restaurantId = TenantContext.require();
        if (parentId != null) {
            categoryRepository.findByIdAccessibleTo(parentId, restaurantId)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.CATEGORY_NOT_FOUND, "Parent category not found"));
        }
        ProductCategory category = new ProductCategory();
        category.setRestaurantId(restaurantId);
        category.setName(name);
        category.setDescription(description);
        category.setColor(color);
        category.setIcon(icon);
        category.setParentId(parentId);
        ProductCategory saved = categoryRepository.save(category);
        auditService.log(AuditAction.PRODUCT_CREATED, "ProductCategory", saved.getId());
        return saved;
    }

    @Transactional
    public ProductCategory updateCategory(UUID id, String name, String description, String color, String icon) {
        ProductCategory category = categoryRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.CATEGORY_NOT_FOUND, "Category not found"));
        if (category.isSystem()) {
            throw AppException.forbidden(ErrorCode.SYSTEM_CATEGORY_IMMUTABLE, "System categories cannot be modified");
        }
        if (name != null && !name.isBlank()) category.setName(name);
        category.setDescription(description);
        category.setColor(color);
        category.setIcon(icon);
        ProductCategory saved = categoryRepository.save(category);
        auditService.log(AuditAction.PRODUCT_UPDATED, "ProductCategory", saved.getId());
        return saved;
    }

    @Transactional
    public void deleteCategory(UUID id) {
        ProductCategory category = categoryRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.CATEGORY_NOT_FOUND, "Category not found"));
        if (category.isSystem()) {
            throw AppException.forbidden(ErrorCode.SYSTEM_CATEGORY_IMMUTABLE, "System categories cannot be deleted");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw AppException.conflict(ErrorCode.CATEGORY_HAS_PRODUCTS,
                    "Cannot delete a category that has products assigned to it");
        }
        categoryRepository.deleteById(id);
        auditService.log(AuditAction.PRODUCT_DEACTIVATED, "ProductCategory", id);
    }

    // ── Units ────────────────────────────────────────────────────────────────

    public List<Unit> listUnits() {
        return unitRepository.findAll();
    }

    // ── Products ─────────────────────────────────────────────────────────────

    public Page<Product> listProducts(PageRequest pageRequest) {
        return productRepository.findByRestaurantIdAndIsActiveTrue(TenantContext.require(), pageRequest);
    }

    public Product getProduct(UUID id) {
        return productRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .filter(Product::isActive)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));
    }

    @Transactional
    public Product createProduct(CreateProductCommand cmd) {
        UUID restaurantId = TenantContext.require();

        if (cmd.sku() != null && productRepository.existsByRestaurantIdAndSku(restaurantId, cmd.sku())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_SKU, "SKU already in use");
        }

        Product product = new Product();
        product.setRestaurantId(restaurantId);
        product.setName(cmd.name());
        product.setSku(cmd.sku());
        product.setMinStock(cmd.minStock());
        product.setMaxStock(cmd.maxStock());
        // Null = 100: el que no carga rendimiento no cambia de comportamiento.
        product.setYieldPercentage(cmd.yieldPercentage() != null ? cmd.yieldPercentage() : new BigDecimal("100"));
        resolveUnit(cmd.defaultUnitId()); // validates existence
        resolveCategory(cmd.categoryId(), restaurantId); // validates existence
        product.setDefaultUnitId(cmd.defaultUnitId());
        product.setCategoryId(cmd.categoryId());

        Product saved = productRepository.save(product);
        recordYield(saved, null);
        auditService.log(AuditAction.PRODUCT_CREATED, "Product", saved.getId(), payloadOf(saved));
        return saved;
    }

    @Transactional
    public Product updateProduct(UUID id, UpdateProductCommand cmd) {
        UUID restaurantId = TenantContext.require();
        Product product = productRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));
        BigDecimal previousYield = product.getYieldPercentage();

        if (cmd.sku() != null && !cmd.sku().equals(product.getSku())
                && productRepository.existsByRestaurantIdAndSku(restaurantId, cmd.sku())) {
            throw AppException.conflict(ErrorCode.DUPLICATE_SKU, "SKU already in use");
        }

        product.setName(cmd.name());
        product.setSku(cmd.sku());
        product.setMinStock(cmd.minStock());
        product.setMaxStock(cmd.maxStock());
        // Null = 100: el que no carga rendimiento no cambia de comportamiento.
        product.setYieldPercentage(cmd.yieldPercentage() != null ? cmd.yieldPercentage() : new BigDecimal("100"));
        resolveUnit(cmd.defaultUnitId()); // validates existence
        resolveCategory(cmd.categoryId(), restaurantId); // validates existence
        product.setDefaultUnitId(cmd.defaultUnitId());
        product.setCategoryId(cmd.categoryId());

        Product saved = productRepository.save(product);
        recordYield(saved, previousYield);
        auditService.log(AuditAction.PRODUCT_UPDATED, "Product", id, payloadOf(saved));
        return saved;
    }

    @Transactional
    public void deactivateProduct(UUID id) {
        Product product = productRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));
        product.deactivate();
        productRepository.save(product);
        auditService.log(AuditAction.PRODUCT_DEACTIVATED, "Product", id);
    }

    /**
     * Agrega un punto a la serie de rendimientos del insumo, en la misma transacción que el cambio.
     *
     * <p>Es la serie que explica un salto del food cost teórico: cambiar el rendimiento de la papa
     * reprecia todos los platos que la usan, sin que nadie haya tocado una receta.
     *
     * <p>Solo escribe si el valor se movió, comparando con {@code compareTo} y no con
     * {@code equals} ({@code 90} y {@code 90.00} no son iguales para {@code BigDecimal}).
     */
    private void recordYield(Product product, BigDecimal previousYield) {
        BigDecimal current = product.getYieldPercentage();
        if (current == null) return;
        if (previousYield != null && previousYield.compareTo(current) == 0) return;
        priceHistoryRepository.saveProductYield(PriceHistoryEntry.of(
                product.getRestaurantId(), product.getId(), current, currentUserProvider.currentUserId()));
    }

    /**
     * Payload de auditoría de un insumo. Hasta el 2026-09-21 no guardaba nada: el audit decía
     * que alguien tocó el producto, no qué le cambió.
     *
     * <p>Lleva el rendimiento porque cambiarlo <b>reprecia todos los platos que usan el insumo</b>
     * —el food cost teórico se mueve sin que nadie haya tocado una receta— y es justo el dato que
     * hay que poder rastrear cuando el número cambie sin explicación. Ninguno de los tres puede
     * ser null: nombre y unidad son obligatorios y el rendimiento cae en 100 por default.
     */
    private static Map<String, Object> payloadOf(Product product) {
        return Map.of(
                "name", product.getName(),
                "unitId", product.getDefaultUnitId(),
                "yieldPercentage", product.getYieldPercentage());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Unit resolveUnit(UUID unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND, "Unit not found"));
    }

    private ProductCategory resolveCategory(UUID categoryId, UUID restaurantId) {
        if (categoryId == null) return null;
        return categoryRepository.findByIdAccessibleTo(categoryId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.CATEGORY_NOT_FOUND, "Category not found"));
    }

    // ── Command records ──────────────────────────────────────────────────────

    public record CreateProductCommand(
            String name,
            String sku,
            UUID defaultUnitId,
            UUID categoryId,
            BigDecimal minStock,
            BigDecimal maxStock,
            BigDecimal yieldPercentage
    ) {}

    public record UpdateProductCommand(
            String name,
            String sku,
            UUID defaultUnitId,
            UUID categoryId,
            BigDecimal minStock,
            BigDecimal maxStock,
            BigDecimal yieldPercentage
    ) {}
}
