package com.chefcontrol.api.ai;

import com.chefcontrol.application.service.TextInterpretService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final TextInterpretService textInterpretService;

    @PostMapping("/interpret")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'KITCHEN')")
    public ResponseEntity<InterpretResponse> interpret(@Valid @RequestBody InterpretRequest request) {
        var r = textInterpretService.interpret(request.message());
        return ResponseEntity.ok(new InterpretResponse(
                r.intent(),
                r.confidence(),
                r.needsConfirmation(),
                r.extractedData(),
                r.responseToUser()
        ));
    }

    record InterpretRequest(
            @NotBlank @Size(max = 500) String message
    ) {}

    record InterpretResponse(
            String intent,
            int confidence,
            boolean needsConfirmation,
            Object data,
            String responseToUser
    ) {}
}
