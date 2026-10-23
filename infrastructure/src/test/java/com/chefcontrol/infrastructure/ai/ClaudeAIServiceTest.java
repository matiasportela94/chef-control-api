package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.port.AIService.UnitOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Claude no se puede llamar en local (no hay API key): esto cubre lo que se le manda y cómo se lee la respuesta. */
class ClaudeAIServiceTest {

    private static final CatalogSpreadsheetRequest REQUEST = new CatalogSpreadsheetRequest(
            List.of(new SpreadsheetSheet("Recetas", List.of(new SpreadsheetRow(7, List.of("Papa", 1, "kilos"))))),
            List.of(new UnitOption("Kilogramo", "kg")), List.of("Papa"), List.of(), List.of());

    private record Fixture(ClaudeAIService service, MockRestServiceServer server) {}

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new ClaudeAIService(builder, "KEY", "claude-haiku-4-5", new ObjectMapper()), server);
    }

    @Test
    void forcesTheCatalogToolAndReadsItsInput() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "KEY"))
                .andExpect(jsonPath("$.max_tokens").value(CatalogSpreadsheetPrompt.MAX_OUTPUT_TOKENS))
                .andExpect(jsonPath("$.tool_choice.name").value("import_catalog"))
                .andExpect(jsonPath("$.tools[0].input_schema.required[1]").value("dishes"))
                .andExpect(jsonPath("$.messages[0].content").value("## Hoja: Recetas\n7 | Papa | 1 | kilos\n"))
                .andRespond(withSuccess("""
                        {"stop_reason":"tool_use","usage":{"input_tokens":1200,"output_tokens":80},
                         "content":[{"type":"tool_use","name":"import_catalog","input":{
                           "products":[{"row":7,"name":"Papa","unit":"kg"}],"dishes":[],"warnings":["ok"]}}]}
                        """, MediaType.APPLICATION_JSON));

        var result = f.service.interpretCatalogSpreadsheet(REQUEST);

        assertEquals("kg", result.products().getFirst().unit());
        assertEquals(List.of("ok"), result.warnings());
        f.server.verify();
    }

    @Test
    void aTruncatedAnswerMeansTheSpreadsheetIsTooLarge() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess("{\"stop_reason\":\"max_tokens\",\"usage\":{},\"content\":[]}", MediaType.APPLICATION_JSON));

        var e = assertThrows(AppException.class, () -> f.service.interpretCatalogSpreadsheet(REQUEST));
        assertEquals(ErrorCode.AI_SPREADSHEET_TOO_LARGE, e.getErrorCode());
    }

    @Test
    void aProviderErrorIsAnInterpretationFailure() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withServerError());

        var e = assertThrows(AppException.class, () -> f.service.interpretCatalogSpreadsheet(REQUEST));
        assertEquals(ErrorCode.AI_INTERPRETATION_FAILED, e.getErrorCode());
    }
}
