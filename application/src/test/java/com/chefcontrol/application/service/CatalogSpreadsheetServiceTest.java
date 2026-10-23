package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.MenuItemRepository;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.shared.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogSpreadsheetServiceTest {

    @Mock UnitRepository unitRepository;
    @Mock ProductRepository productRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock MenuSectionRepository menuSectionRepository;

    private static final UUID R = UUID.randomUUID();
    private final FakeAI ai = new FakeAI();
    private CatalogSpreadsheetService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        Unit kg = new Unit();
        kg.setName("Kilogramo");
        kg.setAbbreviation("kg");
        when(unitRepository.findAll()).thenReturn(List.of(kg));
        Product papa = new Product();
        papa.setName("Papa");
        when(productRepository.findByRestaurantIdAndIsActiveTrue(eq(R), any())).thenReturn(new Page<>(List.of(papa), 0, 1, 1));
        when(menuItemRepository.findByRestaurantIdAndActive(eq(R), eq(true), any()))
                .thenReturn(new Page<>(List.of(MenuItem.builder().name("Ñoquis").build()), 0, 1, 1));
        when(menuSectionRepository.findAllByRestaurantId(R)).thenReturn(List.of());
        service = new CatalogSpreadsheetService(ai, unitRepository, productRepository, menuItemRepository, menuSectionRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void emptyRowsAndSheetsDoNotReachTheModelButTheCatalogDoes() {
        service.interpretCatalogSpreadsheet(List.of(
                new SpreadsheetSheet("Insumos", List.of(row(1, "Papa", "kilos"), row(2, null, " "), row(3))),
                new SpreadsheetSheet("Vacía", List.of(row(4, (Object) null)))));

        CatalogSpreadsheetRequest sent = ai.requests.getFirst();
        assertEquals(1, sent.sheets().size());
        assertEquals(List.of(1), sent.sheets().getFirst().rows().stream().map(SpreadsheetRow::ref).toList());
        assertEquals("kg", sent.units().getFirst().abbreviation());
        assertEquals(List.of("Papa"), sent.productNames());
        assertEquals(List.of("Ñoquis"), sent.menuItemNames());
    }

    @Test
    void anEmptySpreadsheetDoesNotCallTheModel() {
        var result = service.interpretCatalogSpreadsheet(List.of(new SpreadsheetSheet("Hoja1", List.of(row(1, "", null)))));

        assertTrue(result.products().isEmpty() && result.recipes().isEmpty());
        assertTrue(ai.requests.isEmpty());
    }

    @Test
    void moreRowsThanTheLimitIsRejectedBeforeCallingTheModel() {
        List<SpreadsheetRow> rows = IntStream.rangeClosed(1, CatalogSpreadsheetService.MAX_ROWS + 1)
                .mapToObj(i -> row(i, "Insumo " + i, "kg")).toList();

        var e = assertThrows(AppException.class,
                () -> service.interpretCatalogSpreadsheet(List.of(new SpreadsheetSheet("Insumos", rows))));
        assertEquals(ErrorCode.AI_SPREADSHEET_TOO_LARGE, e.getErrorCode());
        assertTrue(ai.requests.isEmpty());
    }

    @Test
    void exactlyTheLimitIsAccepted() {
        List<SpreadsheetRow> rows = IntStream.rangeClosed(1, CatalogSpreadsheetService.MAX_ROWS)
                .mapToObj(i -> row(i, "Insumo " + i, "kg")).toList();

        service.interpretCatalogSpreadsheet(List.of(new SpreadsheetSheet("Insumos", rows)));

        assertEquals(1, ai.requests.size());
    }

    private static SpreadsheetRow row(int ref, Object... cells) {
        return new SpreadsheetRow(ref, Arrays.asList(cells));
    }

    /** El puerto falso: guarda lo que le llega y devuelve vacío. */
    private static class FakeAI implements AIService {
        final List<CatalogSpreadsheetRequest> requests = new ArrayList<>();

        @Override
        public AIInterpretation interpret(InterpretRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(CatalogSpreadsheetRequest request) {
            requests.add(request);
            return new CatalogSpreadsheetInterpretation(List.of(), List.of(), List.of());
        }
    }
}
