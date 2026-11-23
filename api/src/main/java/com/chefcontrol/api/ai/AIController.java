package com.chefcontrol.api.ai;

import com.chefcontrol.api.ai.dto.InterpretCatalogResult;
import com.chefcontrol.api.plan.RequiresFeature;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.service.CatalogSpreadsheetService;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.application.service.TranscriptionService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiresFeature(Feature.AI_INTERPRETER)
@RequiredArgsConstructor
public class AIController {

    private final CatalogSpreadsheetService catalogSpreadsheetService;
    private final TranscriptionService transcriptionService;

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
        return ResponseEntity.ok(InterpretCatalogResult.from(r));
    }

    /**
     * Audio (webm, mp4/m4a u ogg, detectado por magic bytes) a texto en castellano. Solo transcribe: el texto vuelve al
     * front, que lo manda a /ai/chat como si se hubiera escrito. Más de 4 MB → AI_AUDIO_TOO_LARGE.
     */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    public ResponseEntity<TranscriptionResponse> transcribeAudio(@RequestParam("audio") MultipartFile audio)
            throws IOException {
        return ResponseEntity.ok(new TranscriptionResponse(transcriptionService.transcribeAudio(audio.getBytes())));
    }

    record TranscriptionResponse(String text) {}

    record InterpretCatalogRequest(@NotNull @Size(max = 20) List<@Valid @NotNull SpreadsheetSheetRequest> sheets) {}

    record SpreadsheetSheetRequest(String name, @NotNull @Size(max = 2000) List<@NotNull SpreadsheetRowRequest> rows) {}

    /**
     * @param ref   número global que pone el front a cada fila, para volver a la hoja y fila originales
     * @param cells number o texto tal cual, null si la celda está vacía
     */
    record SpreadsheetRowRequest(int ref,
                                 // nullable: el back acepta null (celda vacía) y sin esto el cliente generado no.
                                 @ArraySchema(schema = @Schema(nullable = true))
                                 List<Object> cells) {}
}
