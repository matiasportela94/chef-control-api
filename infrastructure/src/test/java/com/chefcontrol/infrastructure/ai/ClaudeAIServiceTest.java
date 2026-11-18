package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AIService.CatalogSpreadsheetRequest;
import com.chefcontrol.application.port.AIService.ChatCatalogMenuItem;
import com.chefcontrol.application.port.AIService.ChatCatalogProduct;
import com.chefcontrol.application.port.AIService.ChatHistoryTurn;
import com.chefcontrol.application.port.AIService.ChatMessageRequest;
import com.chefcontrol.application.port.AIService.HistoryAction;
import com.chefcontrol.application.port.AIService.CurrentRecipe;
import com.chefcontrol.application.port.AIService.RecipeLine;
import com.chefcontrol.application.port.AIService.RecipeMessageRequest;
import com.chefcontrol.application.port.AIService.SpreadsheetRow;
import com.chefcontrol.application.port.AIService.SpreadsheetSheet;
import com.chefcontrol.application.port.AIService.UnitOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
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

    // ── Chat ──

    private static final UUID COMINO = UUID.fromString("0b6c1c3e-6f3a-4d43-9a39-4c7f6d1c2a10");
    private static final UUID MILANESA = UUID.fromString("5d1f2a3b-0c4e-4f6a-8b7c-9d0e1f2a3b4c");
    private static final ChatMessageRequest CHAT = new ChatMessageRequest("compré 500 g de comino a 3000 el kilo",
            LocalDate.of(2026, 11, 17), List.of(new ChatCatalogProduct(COMINO, "Comino", "kg", false)),
            List.of(new ChatCatalogMenuItem(MILANESA, "Milanesa", new BigDecimal("12000"))),
            List.of(new UnitOption("Kilogramo", "kg"), new UnitOption("Gramo", "g")));

    @Test
    void theChatCachesTheSystemPromptAndKeepsTheDateOutOfIt() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(jsonPath("$.system[0].cache_control.type").value("ephemeral"))
                .andExpect(jsonPath("$.system[0].text").value(org.hamcrest.Matchers.containsString(COMINO + " | Comino | kg")))
                .andExpect(jsonPath("$.system[0].text").value(org.hamcrest.Matchers.containsString(MILANESA + " | Milanesa | 12000")))
                .andExpect(jsonPath("$.system[0].text").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("2026-11-17"))))
                .andExpect(jsonPath("$.tool_choice.name").value("record_operations"))
                .andExpect(jsonPath("$.messages[0].content").value("HOY: 2026-11-17\ncompré 500 g de comino a 3000 el kilo"))
                .andRespond(withSuccess("""
                        {"stop_reason":"tool_use","usage":{"input_tokens":30,"output_tokens":90,"cache_read_input_tokens":4100},
                         "content":[{"type":"tool_use","name":"record_operations","input":{
                           "confidence":95,"needsConfirmation":false,"responseToUser":"Listo",
                           "operations":[{"type":"purchase","items":[{"productId":"%s","name":"comino","quantity":500,
                             "unit":"g","price":3000,"priceBasis":"unit","priceUnit":"kg"}]}]}}]}
                        """.formatted(COMINO), MediaType.APPLICATION_JSON));

        var result = f.service.interpretChatMessage(CHAT);

        var item = result.operations().getFirst().items().getFirst();
        assertEquals(COMINO, item.productId());
        assertEquals("g", item.unit());
        assertEquals("kg", item.priceUnit());
        assertEquals("unit", item.priceBasis());
        assertEquals(0, new BigDecimal("500").compareTo(item.quantity()));
        f.server.verify();
    }

    @Test
    void anInventedIdIsReadAsNotInTheCatalog() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess("""
                {"content":[{"type":"tool_use","name":"record_operations","input":{"confidence":90,
                  "needsConfirmation":false,"responseToUser":"","operations":[{"type":"waste",
                  "items":[{"productId":"tomate-1","name":"tomate","quantity":"2,5","reason":"EXPIRED"}]}]}}]}
                """, MediaType.APPLICATION_JSON));

        var item = f.service.interpretChatMessage(CHAT).operations().getFirst().items().getFirst();

        assertNull(item.productId());
        assertEquals(0, new BigDecimal("2.5").compareTo(item.quantity()));
        assertEquals("EXPIRED", item.wasteReason());
    }

    @Test
    void theConversationGoesInTheUserMessageWithItsNumberedActionsAndCorrectsIsRead() {
        var request = new ChatMessageRequest("no, eran 5 kg", LocalDate.of(2026, 11, 18), CHAT.products(),
                CHAT.menuItems(), CHAT.units(), List.of(new ChatHistoryTurn("compré 3 kg de comino a 3000",
                "Listo: Compra: 3 kg Comino a $ 3.000.", List.of(new HistoryAction(1, "Compra: 3 kg Comino a $ 3.000", false)))));
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(jsonPath("$.messages[0].content").value("CONVERSACIÓN DE HOY:\n"
                        + "Usuario: compré 3 kg de comino a 3000\nRespondí: Listo: Compra: 3 kg Comino a $ 3.000.\n"
                        + "  [acción 1] Compra: 3 kg Comino a $ 3.000\n\nHOY: 2026-11-18\nno, eran 5 kg"))
                .andRespond(withSuccess("""
                        {"content":[{"type":"tool_use","name":"record_operations","input":{"confidence":95,
                          "needsConfirmation":false,"responseToUser":"","operations":[{"type":"purchase","corrects":1,
                          "text":"no, eran 5 kg","items":[{"productId":"%s","name":"comino","quantity":5,"unit":"kg",
                          "price":3000,"priceBasis":"unit"}]}]}}]}
                        """.formatted(COMINO), MediaType.APPLICATION_JSON));

        var op = f.service.interpretChatMessage(request).operations().getFirst();

        assertEquals(1, op.corrects());
        assertEquals("no, eran 5 kg", op.text());
        f.server.verify();
    }

    @Test
    void aBusyProviderIsRetriedOnce() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withRawStatus(529));
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess("""
                {"content":[{"type":"tool_use","name":"record_operations","input":{"confidence":90,
                  "needsConfirmation":false,"responseToUser":"","operations":[]}}]}
                """, MediaType.APPLICATION_JSON));

        assertEquals(90, f.service.interpretChatMessage(CHAT).confidence());
        f.server.verify();
    }

    @Test
    void aBusyProviderTwiceIsAnInterpretationFailure() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withRawStatus(503));
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withRawStatus(503));

        var e = assertThrows(AppException.class, () -> f.service.interpretChatMessage(CHAT));
        assertEquals(ErrorCode.AI_INTERPRETATION_FAILED, e.getErrorCode());
        f.server.verify();
    }

    @Test
    void aChatProviderErrorIsAnInterpretationFailure() {
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withServerError());

        var e = assertThrows(AppException.class, () -> f.service.interpretChatMessage(CHAT));
        assertEquals(ErrorCode.AI_INTERPRETATION_FAILED, e.getErrorCode());
    }

    // ── Receta por chat ──

    @Test
    void theRecipeCallSendsTheCurrentRecipeAndNumbersTheIngredients() {
        var request = new RecipeMessageRequest("sacale el comino y ponele 2 huevos", "Mayonesa base", true,
                new CurrentRecipe(new BigDecimal("1.5"), "kg", List.of(
                        new RecipeLine("Comino", new BigDecimal("0.01"), "kg"),
                        new RecipeLine("Aceite", new BigDecimal("1"), "l"))),
                List.of(new ChatCatalogProduct(COMINO, "Comino", "kg", false)), List.of(new UnitOption("Kilogramo", "kg")));
        var f = fixture();
        f.server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(jsonPath("$.system[0].cache_control.type").value("ephemeral"))
                .andExpect(jsonPath("$.system[0].text").value(org.hamcrest.Matchers.containsString("Comino | kg")))
                .andExpect(jsonPath("$.tool_choice.name").value("write_recipe"))
                .andExpect(jsonPath("$.messages[0].content").value("RECETA DE: Mayonesa base (preparación)\n"
                        + "RECETA ACTUAL:\nComino | 0.01 | kg\nAceite | 1 | l\nRINDE: 1.5 kg\n"
                        + "MENSAJE: sacale el comino y ponele 2 huevos"))
                .andRespond(withSuccess("""
                        {"stop_reason":"tool_use","usage":{"input_tokens":30,"output_tokens":60},
                         "content":[{"type":"tool_use","name":"write_recipe","input":{"yieldQuantity":1.5,"yieldUnit":"kg",
                           "ingredients":[{"ingredient":"Aceite","quantity":1,"unit":"l"},
                                          {"ingredient":"huevos","quantity":2,"unit":"u"}],
                           "proposedProducts":[{"name":"huevos","unit":"u"}],"warnings":[]}}]}
                        """, MediaType.APPLICATION_JSON));

        var result = f.service.interpretRecipeMessage(request);

        assertEquals(2, result.preparations().size());
        var first = result.preparations().getFirst();
        assertEquals(1, first.row());
        assertEquals("Mayonesa base", first.preparation());   // el nombre lo pone el back, no el modelo
        assertEquals(0, new BigDecimal("1.5").compareTo(first.yieldQuantity()));
        assertNull(result.preparations().get(1).yieldQuantity());   // el rendimiento, solo en la primera fila
        assertTrue(result.recipes().isEmpty());
        assertEquals(2, result.proposedProducts().getFirst().row());   // la fila del primer ingrediente que lo usa
        f.server.verify();
    }
}
