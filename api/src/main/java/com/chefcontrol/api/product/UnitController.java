package com.chefcontrol.api.product;

import com.chefcontrol.api.product.dto.UnitResponse;
import com.chefcontrol.application.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/units")
@RequiredArgsConstructor
public class UnitController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<UnitResponse>> listUnits() {
        List<UnitResponse> units = productService.listUnits()
                .stream()
                .map(UnitResponse::from)
                .toList();
        return ResponseEntity.ok(units);
    }
}
