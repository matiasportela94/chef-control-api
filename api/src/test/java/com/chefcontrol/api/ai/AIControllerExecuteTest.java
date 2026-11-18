package com.chefcontrol.api.ai;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.service.AIExecuteService;
import com.chefcontrol.application.service.CatalogSpreadsheetService;
import com.chefcontrol.application.service.TextInterpretService;
import com.chefcontrol.application.service.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El hueco del 2026-11-17: /ai/execute pedía solo AI_USE y cualquiera cargaba compras por la IA. */
class AIControllerExecuteTest {

    private final AIExecuteService executeService = mock(AIExecuteService.class);
    private final AIController controller = new AIController(mock(TextInterpretService.class), executeService,
            mock(CatalogSpreadsheetService.class), mock(TranscriptionService.class));

    @Test
    void aPurchaseNeedsThePurchasesPermission() {
        var e = assertThrows(AppException.class, () -> controller.execute(
                new AIController.ExecuteRequest("purchase", Map.of()), auth("PERM_AI_USE", "PERM_WASTE_CREATE")));

        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(executeService);
    }

    @Test
    void aWasteWithItsPermissionRuns() {
        when(executeService.execute(eq("waste"), any())).thenReturn(new AIExecuteService.ExecuteResult(true, "ok", 1, 0));

        controller.execute(new AIController.ExecuteRequest("waste", Map.of()), auth("PERM_AI_USE", "PERM_WASTE_CREATE"));

        verify(executeService).execute(eq("waste"), any());
    }

    private static Authentication auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken("u", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
