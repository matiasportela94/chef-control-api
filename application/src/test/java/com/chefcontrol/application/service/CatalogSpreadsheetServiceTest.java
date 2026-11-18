package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetInterpretation;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.IngredientMatch;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
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

    @Test
    void aProposedProductThatAlreadyExistsOrRepeatsIsDropped() {
        ai.answer = interpretation(
                List.of(new ProductRow(2, "Cebolla", "kg", null, null, null)),
                List.of(),
                List.of(new ProductRow(10, "PAPÁ", "kg", null, null, null),     // = "Papa" del catálogo
                        new ProductRow(11, "cebolla ", "kg", null, null, null), // = la de las filas de insumos
                        new ProductRow(12, "Ajo", "kg", null, null, null),
                        new ProductRow(13, "ajo", "kg", null, null, null)),     // repetida
                List.of(), List.of());

        var result = service.interpretCatalogSpreadsheet(List.of(new SpreadsheetSheet("Recetas", List.of(row(1, "x")))));

        assertEquals(List.of(new ProductRow(12, "Ajo", "kg", null, null, null)), result.proposedProducts());
    }

    @Test
    void aRecognizedIngredientIsRenamedOnlyIfTheNameExistsAndTheWarningSaysSo() {
        ai.answer = interpretation(
                List.of(new ProductRow(1, "Cebolla", "kg", null, null, null)),
                List.of(recipe(5, "Papas"), recipe(6, "cebollas"), recipe(7, "PAPA"), recipe(8, "Tomates")),
                List.of(new ProductRow(5, "Papas", "kg", null, null, null),       // el modelo igual la propuso
                        new ProductRow(8, "Tomates", "kg", null, null, null)),
                List.of(new IngredientMatch(5, "Papas", "papa"),        // existe en el catálogo como "Papa"
                        new IngredientMatch(6, "cebollas", "Cebolla"),  // existe en la hoja de insumos
                        new IngredientMatch(7, "PAPA", "Papa"),         // solo mayúsculas: no hace falta tocarlo
                        new IngredientMatch(8, "Tomates", "Tomate")),   // no existe: queda como vino
                List.of());

        var result = service.interpretCatalogSpreadsheet(List.of(new SpreadsheetSheet("Recetas", List.of(row(1, "x")))));

        assertEquals(List.of("Papa", "Cebolla", "PAPA", "Tomates"),
                result.recipes().stream().map(RecipeRow::ingredient).toList());
        assertEquals(List.of(
                "Usé 'Papa' del catálogo para 'Papas'.",
                "Usé 'Cebolla' de la hoja de insumos para 'cebollas'."), result.warnings()); // sin "fila": el ref no es la del Excel
        assertEquals(List.of("Tomates"), result.proposedProducts().stream().map(ProductRow::name).toList());
        assertTrue(result.ingredientMatches().isEmpty());
    }

    @Test
    void aProductTakenFromARecipeRowIsAProposalNotAProduct() {
        ai.answer = interpretation(
                List.of(new ProductRow(13, "limon", "kg", null, null, null),   // pasó así en la prueba real
                        new ProductRow(14, "Azúcar", "kg", null, null, null),
                        new ProductRow(2, "Harina", "kg", null, null, null)),  // fila de insumos de verdad
                List.of(recipe(13, "papa"), recipe(14, "Azúcar")),
                List.of(), List.of(), List.of());

        var result = service.interpretCatalogSpreadsheet(List.of(new SpreadsheetSheet("Recetas", List.of(row(1, "x")))));

        assertEquals(List.of("Harina"), result.products().stream().map(ProductRow::name).toList());
        assertEquals(List.of("limon", "Azúcar"), result.proposedProducts().stream().map(ProductRow::name).toList());
    }

    @Test
    void aPluralTheModelDidNotMarkIsStillTheExistingProduct() {
        // Pasó en la prueba del 2026-11-18: "huevos" propuesto con "Huevo" en el catálogo; se habría creado duplicado.
        ai.answer = interpretation(List.of(), List.of(recipe(1, "papas"), recipe(2, "Cebollas")),
                List.of(new ProductRow(1, "papas", "kg", null, null, null),
                        new ProductRow(2, "Cebollas", "kg", null, null, null)),
                List.of(), List.of());

        var result = service.interpretCatalogSpreadsheet(List.of(new SpreadsheetSheet("Recetas", List.of(row(1, "x")))));

        assertEquals(List.of("Papa", "Cebollas"), result.recipes().stream().map(RecipeRow::ingredient).toList());
        assertEquals(List.of("Cebollas"), result.proposedProducts().stream().map(ProductRow::name).toList());
        assertEquals(List.of("Usé 'Papa' del catálogo para 'papas'."), result.warnings());
    }

    @Test
    void pluralsAreComparedWordByWord() {
        assertTrue(CatalogSpreadsheetService.samePlural("limones", "Limón"));
        assertTrue(CatalogSpreadsheetService.samePlural("Papas fritas", "papa frita"));
        assertFalse(CatalogSpreadsheetService.samePlural("Tomate", "Tomate perita"));
        assertNull(CatalogSpreadsheetService.singularMatch("tomates", List.of("Tomate", "Tomates")));   // ambiguo: no elige
    }

    private static RecipeRow recipe(int row, String ingredient) {
        return new RecipeRow(row, "Plato", null, null, ingredient, java.math.BigDecimal.ONE, "kg");
    }

    private static SpreadsheetRow row(int ref, Object... cells) {
        return new SpreadsheetRow(ref, Arrays.asList(cells));
    }

    /** El puerto falso: guarda lo que le llega y devuelve vacío. */
    /** Sin preparaciones: las de estos casos son de insumos y platos. */
    private static CatalogSpreadsheetInterpretation interpretation(List<ProductRow> products, List<RecipeRow> recipes,
            List<ProductRow> proposed, List<AIService.IngredientMatch> matches, List<String> warnings) {
        return new CatalogSpreadsheetInterpretation(products, List.of(), recipes, proposed, matches, warnings);
    }

    private static class FakeAI implements AIService {
        final List<CatalogSpreadsheetRequest> requests = new ArrayList<>();
        CatalogSpreadsheetInterpretation answer = interpretation(List.of(), List.of(), List.of(), List.of(), List.of());

        @Override
        public AIInterpretation interpret(InterpretRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(CatalogSpreadsheetRequest request) {
            requests.add(request);
            return answer;
        }

        @Override
        public ChatInterpretation interpretChatMessage(ChatMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CatalogSpreadsheetInterpretation interpretRecipeMessage(RecipeMessageRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
