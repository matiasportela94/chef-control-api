package com.chefcontrol.api.waste;

import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.api.waste.dto.CreateWasteEventRequest;
import com.chefcontrol.api.waste.dto.WasteEventResponse;
import com.chefcontrol.application.service.WasteService;
import com.chefcontrol.application.service.WasteService.CreateWasteEventCommand;
import jakarta.validation.Valid;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/waste-events")
@RequiredArgsConstructor
public class WasteEventController {

    private final WasteService wasteService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_WASTE_VIEW')")
    public ResponseEntity<PagedResponse<WasteEventResponse>> listWasteEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                PagedResponse.from(wasteService.listWasteEvents(PageRequest.of(page, size)),
                        WasteEventResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_WASTE_VIEW')")
    public ResponseEntity<WasteEventResponse> getWasteEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(WasteEventResponse.from(wasteService.getWasteEvent(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_WASTE_CREATE')")
    public ResponseEntity<WasteEventResponse> createWasteEvent(
            @Valid @RequestBody CreateWasteEventRequest request) {
        CreateWasteEventCommand command = new CreateWasteEventCommand(
                request.productId(),
                request.unitId(),
                request.quantity(),
                request.reason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WasteEventResponse.from(wasteService.createWasteEvent(command)));
    }
}
