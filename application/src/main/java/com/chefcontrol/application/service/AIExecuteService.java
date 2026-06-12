package com.chefcontrol.application.service;

import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.waste.WasteReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIExecuteService {

    private final PurchaseService purchaseService;
    private final WasteService    wasteService;
    private final ProductRepository productRepository;

    public ExecuteResult execute(String intent, Map<String, Object> data) {
        return switch (intent) {
            case "purchase" -> executePurchase(data);
            case "waste"    -> executeWaste(data);
            default         -> ExecuteResult.unsupported(intent);
        };
    }

    // ── purchase ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ExecuteResult executePurchase(Map<String, Object> data) {
        List<Map<String, Object>> items = extractItems(data);
        if (items.isEmpty()) return ExecuteResult.error("No se encontraron items en la compra.");

        UUID restaurantId = TenantContext.require();
        UUID supplierId   = parseUuid(data.get("supplier_id"));
        var commands = new ArrayList<PurchaseService.PurchaseItemCommand>();
        int skipped = 0;

        for (Map<String, Object> item : items) {
            UUID productId = parseUuid(item.get("product_id"));
            if (productId == null) { skipped++; continue; }

            var product = productRepository.findByIdAndRestaurantId(productId, restaurantId).orElse(null);
            if (product == null || product.getDefaultUnitId() == null) { skipped++; continue; }

            BigDecimal qty = toBigDecimal(item.get("quantity"));
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) { skipped++; continue; }

            BigDecimal price = toBigDecimal(item.get("price_per_unit"));
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) { skipped++; continue; }

            LocalDate expirationDate = parseLocalDate(item.get("expiration_date"));
            commands.add(new PurchaseService.PurchaseItemCommand(
                    productId, product.getDefaultUnitId(), qty, price, expirationDate));
        }

        if (commands.isEmpty()) return ExecuteResult.error("Ningún producto coincide con el catálogo.");

        purchaseService.createPurchase(new PurchaseService.CreatePurchaseCommand(
                supplierId, null, Instant.now(), commands));

        return ExecuteResult.ok(commands.size(), skipped, "Compra registrada");
    }

    // ── waste ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ExecuteResult executeWaste(Map<String, Object> data) {
        List<Map<String, Object>> items = extractItems(data);
        if (items.isEmpty()) return ExecuteResult.error("No se encontraron items en la merma.");

        UUID restaurantId = TenantContext.require();
        int created = 0, skipped = 0;

        for (Map<String, Object> item : items) {
            UUID productId = parseUuid(item.get("product_id"));
            if (productId == null) { skipped++; continue; }

            var product = productRepository.findByIdAndRestaurantId(productId, restaurantId).orElse(null);
            if (product == null || product.getDefaultUnitId() == null) { skipped++; continue; }

            BigDecimal qty = toBigDecimal(item.get("quantity"));
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) { skipped++; continue; }

            WasteReason reason = parseReason(item.get("reason"));
            wasteService.createWasteEvent(new WasteService.CreateWasteEventCommand(
                    productId, product.getDefaultUnitId(), qty, reason));
            created++;
        }

        if (created == 0) return ExecuteResult.error("Ningún producto coincide con el catálogo.");
        return ExecuteResult.ok(created, skipped, "Merma registrada");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> data) {
        if (data == null) return List.of();
        Object raw = data.get("items");
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
            return (List<Map<String, Object>>) list;
        }
        // AI returned a flat object without items[] wrapper
        if (data.containsKey("product_id")) return List.of(data);
        return List.of();
    }

    private UUID parseUuid(Object v) {
        if (v == null) return null;
        try { return UUID.fromString(v.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    private WasteReason parseReason(Object v) {
        if (v == null) return WasteReason.OTHER;
        try { return WasteReason.valueOf(v.toString().toUpperCase()); } catch (Exception e) { return WasteReason.OTHER; }
    }

    private LocalDate parseLocalDate(Object v) {
        if (v == null) return null;
        try { return LocalDate.parse(v.toString()); } catch (Exception e) { return null; }
    }

    // ── result ────────────────────────────────────────────────────────────────

    public record ExecuteResult(boolean success, String message, int created, int skipped) {

        static ExecuteResult ok(int created, int skipped, String label) {
            String msg = label + ": " + created + " producto(s)";
            if (skipped > 0) msg += " (" + skipped + " no encontrado(s) en catálogo)";
            return new ExecuteResult(true, msg, created, skipped);
        }

        static ExecuteResult error(String msg) {
            return new ExecuteResult(false, msg, 0, 0);
        }

        static ExecuteResult unsupported(String intent) {
            return new ExecuteResult(false,
                    "El intent '" + intent + "' no tiene ejecución automática aún. Usá el formulario.",
                    0, 0);
        }
    }
}
