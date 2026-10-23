package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.port.AIService.UnitOption;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.MenuSection;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * La IA traduce un Excel de catálogo armado como sea al cuerpo de /catalog-import. Solo traduce: no escribe nada,
 * y lo que devuelve pasa por la vista previa (dryRun) y la confirmación de siempre, así que el modelo nunca da de
 * alta un insumo por su cuenta.
 */
@Service
@RequiredArgsConstructor
public class CatalogSpreadsheetService {

    /**
     * Filas con datos por pedido. Medido con gemini-2.5-flash (2026-10-23): ~18 tokens de salida por fila, 308 filas
     * = 5.525 tokens en 22 s. Con el máximo de 8.192 que piden los adaptadores, 300 filas dejan casi la mitad de
     * margen para nombres largos y avisos.
     */
    public static final int MAX_ROWS = 300;

    private static final PageRequest ALL = PageRequest.of(0, Integer.MAX_VALUE);

    private final AIService aiService;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuSectionRepository menuSectionRepository;

    public CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(List<SpreadsheetSheet> sheets) {
        UUID restaurantId = TenantContext.require();
        // Las filas vacías no le sirven al modelo y gastan tokens.
        List<SpreadsheetSheet> withData = sheets.stream()
                .map(s -> new SpreadsheetSheet(s.name(), s.rows().stream().filter(CatalogSpreadsheetService::hasData).toList()))
                .filter(s -> !s.rows().isEmpty())
                .toList();
        int rows = withData.stream().mapToInt(s -> s.rows().size()).sum();
        if (rows == 0) {
            return new CatalogSpreadsheetInterpretation(List.of(), List.of(), List.of());
        }
        if (rows > MAX_ROWS) {
            throw AppException.badRequest(ErrorCode.AI_SPREADSHEET_TOO_LARGE,
                    "The spreadsheet has " + rows + " rows with data; the limit is " + MAX_ROWS);
        }

        return aiService.interpretCatalogSpreadsheet(new CatalogSpreadsheetRequest(
                withData,
                unitRepository.findAll().stream().map(u -> new UnitOption(u.getName(), u.getAbbreviation())).toList(),
                productRepository.findByRestaurantIdAndIsActiveTrue(restaurantId, ALL).content().stream()
                        .map(Product::getName).toList(),
                menuItemRepository.findByRestaurantIdAndActive(restaurantId, true, ALL).content().stream()
                        .map(MenuItem::getName).toList(),
                menuSectionRepository.findAllByRestaurantId(restaurantId).stream().map(MenuSection::getName).toList()));
    }

    private static boolean hasData(SpreadsheetRow row) {
        return row.cells() != null
                && row.cells().stream().anyMatch(c -> c != null && !c.toString().isBlank());
    }
}
