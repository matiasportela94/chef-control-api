package com.chefcontrol.api.ai;

import com.chefcontrol.api.catalog.dto.CatalogImportRequest.ProductImportRow;
import com.chefcontrol.api.catalog.dto.CatalogImportRequest.RecipeImportRow;
import com.chefcontrol.api.plan.RequiresFeature;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.service.CatalogSpreadsheetService;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.application.service.AIExecuteService;
import com.chefcontrol.application.service.TextInterpretService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiresFeature(Feature.AI_INTERPRETER)
@RequiredArgsConstructor
public class AIController {

    private final TextInterpretService textInterpretService;
    private final AIExecuteService     aiExecuteService;
    private final CatalogSpreadsheetService catalogSpreadsheetService;

    @PostMapping("/interpret")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<InterpretResponse> interpret(@Valid @RequestBody InterpretRequest request) {
        var r = textInterpretService.interpret(request.message());
        return ResponseEntity.ok(new InterpretResponse(
                r.intent(), r.confidence(), r.needsConfirmation(), r.extractedData(), r.responseToUser()));
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<ExecuteResponse> execute(@Valid @RequestBody ExecuteRequest request) {
        var result = aiExecuteService.execute(request.intent(), request.data());
        return ResponseEntity.ok(new ExecuteResponse(result.success(), result.message(), result.created(), result.skipped()));
    }

    /**
     * La IA traduce un Excel de catálogo armado como sea al cuerpo de /catalog-import, sin escribir nada: después
     * va la vista previa (dryRun) y la confirmación de siempre. Más de {@link CatalogSpreadsheetService#MAX_ROWS}
     * filas con datos → 400 AI_SPREADSHEET_TOO_LARGE. Puede tardar decenas de segundos.
     */
    @PostMapping("/interpret-catalog")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<InterpretCatalogResult> interpretCatalogSpreadsheet(
            @Valid @RequestBody InterpretCatalogRequest request) {
        var r = catalogSpreadsheetService.interpretCatalogSpreadsheet(request.sheets().stream()
                .map(s -> new SpreadsheetSheet(s.name(), s.rows().stream()
                        .map(row -> new SpreadsheetRow(row.ref(), row.cells() == null ? List.of() : row.cells()))
                        .toList()))
                .toList());
        return ResponseEntity.ok(new InterpretCatalogResult(
                r.products().stream().map(p -> new ProductImportRow(
                        p.row(), p.name(), p.unit(), p.category(), p.minStock(), p.yieldPercentage())).toList(),
                r.recipes().stream().map(x -> new RecipeImportRow(
                        x.row(), x.menuItem(), x.price(), x.section(), x.ingredient(), x.quantity(), x.unit())).toList(),
                r.warnings()));
    }

    record InterpretRequest(@NotBlank @Size(max = 500) String message) {}

    record InterpretResponse(String intent, int confidence, boolean needsConfirmation, Object data, String responseToUser) {}

    record ExecuteRequest(@NotBlank String intent, Map<String, Object> data) {}

    record ExecuteResponse(boolean success, String message, int created, int skipped) {}

    record InterpretCatalogRequest(@NotNull @Size(max = 20) List<@Valid @NotNull SpreadsheetSheetRequest> sheets) {}

    record SpreadsheetSheetRequest(String name, @NotNull @Size(max = 2000) List<@NotNull SpreadsheetRowRequest> rows) {}

    /**
     * @param ref   número global que pone el front a cada fila, para volver a la hoja y fila originales
     * @param cells number o texto tal cual, null si la celda está vacía
     */
    record SpreadsheetRowRequest(int ref, List<Object> cells) {}

    /** {@code row} de cada fila = el ref de la fila de origen. Las filas son las de /catalog-import, tal cual. */
    record InterpretCatalogResult(List<ProductImportRow> products, List<RecipeImportRow> recipes, List<String> warnings) {}
}
