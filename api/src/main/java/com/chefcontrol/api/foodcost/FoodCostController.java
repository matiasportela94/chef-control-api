package com.chefcontrol.api.foodcost;

import com.chefcontrol.api.plan.RequiresFeature;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.api.foodcost.dto.FoodCostResponse;
import com.chefcontrol.application.service.FoodCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/food-cost")
@RequiresFeature(Feature.RECIPES_AND_FOOD_COST)
@RequiredArgsConstructor
public class FoodCostController {

    private final FoodCostService foodCostService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_FOOD_COST_VIEW')")
    public ResponseEntity<FoodCostResponse> calculate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(FoodCostResponse.from(foodCostService.calculate(from, to)));
    }
}
