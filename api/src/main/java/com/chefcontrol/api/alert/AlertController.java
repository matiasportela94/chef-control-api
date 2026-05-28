package com.chefcontrol.api.alert;

import com.chefcontrol.api.alert.dto.AlertResponse;
import com.chefcontrol.api.shared.PagedResponse;
import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.shared.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;

    @GetMapping
    public ResponseEntity<PagedResponse<AlertResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = alertRepository.findByRestaurantIdAndResolvedAtIsNullOrderByCreatedAtDesc(
                TenantContext.require(), PageRequest.of(page, size));
        return ResponseEntity.ok(PagedResponse.of(result.map(AlertResponse::from)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<AlertResponse> markRead(@PathVariable UUID id) {
        Alert alert = alertRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.ALERT_NOT_FOUND, "Alert not found"));
        alert.setRead(true);
        return ResponseEntity.ok(AlertResponse.from(alertRepository.save(alert)));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<AlertResponse> resolve(@PathVariable UUID id) {
        Alert alert = alertRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.ALERT_NOT_FOUND, "Alert not found"));
        alert.setResolvedAt(Instant.now());
        alert.setRead(true);
        return ResponseEntity.ok(AlertResponse.from(alertRepository.save(alert)));
    }
}
