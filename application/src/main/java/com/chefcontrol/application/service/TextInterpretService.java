package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AIService;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TextInterpretService {

    private final AIService aiService;
    private final ProductRepository productRepository;

    public AIService.AIInterpretation interpret(String message) {
        UUID restaurantId = TenantContext.require();
        String catalog    = buildCatalog(restaurantId);
        return aiService.interpret(new AIService.InterpretRequest(message, restaurantId, null, catalog));
    }

    private String buildCatalog(UUID restaurantId) {
        List<Product> products = productRepository
                .findByRestaurantIdAndIsActiveTrue(restaurantId, PageRequest.of(0, 500))
                .content();
        if (products.isEmpty()) return "";
        var sb = new StringBuilder();
        for (Product p : products) {
            sb.append(p.getId()).append(" | ")
              .append(p.getName()).append(" | ")
              .append(p.getSku() != null ? p.getSku() : "-").append(" | ")
              .append(p.getDefaultUnitName() != null ? p.getDefaultUnitName() : "-")
              .append("\n");
        }
        return sb.toString().trim();
    }
}
