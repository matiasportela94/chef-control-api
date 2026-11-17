package com.chefcontrol.api.catalog;

import com.chefcontrol.api.catalog.dto.CatalogImportRequest;
import com.chefcontrol.api.catalog.dto.CatalogImportRequest.ProductImportRow;
import com.chefcontrol.api.catalog.dto.CatalogImportRequest.RecipeImportRow;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.service.AccountService;
import com.chefcontrol.application.service.CatalogImportService;
import com.chefcontrol.application.service.CatalogImportService.Report;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.user.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Lo que depende del contenido: las recetas piden crear plato, editar receta y un plan con recetas. */
@ExtendWith(MockitoExtension.class)
class CatalogImportControllerTest {

    @Mock CatalogImportService catalogImportService;
    @Mock AccountService accountService;
    @InjectMocks CatalogImportController controller;

    private static final CatalogImportRequest PRODUCTS_ONLY = new CatalogImportRequest(
            List.of(new ProductImportRow(2, "Papa", "kg", null, null, null, null)), null, null, null, null);
    private static final CatalogImportRequest WITH_RECIPES = new CatalogImportRequest(null,
            List.of(new RecipeImportRow(2, "Ñoquis", null, null, "papa", BigDecimal.ONE, "kg")), null, null, null);

    @Test
    void productsOnlyNeedNeitherMenuPermissionsNorThePlan() {
        when(catalogImportService.importCatalog(anyList(), anyList(), anyList(), eq(true), anySet(), anySet(), anySet())).thenReturn(emptyReport());

        controller.importCatalog(PRODUCTS_ONLY, true, auth("PERM_PRODUCTS_CREATE"));

        verifyNoInteractions(accountService);
    }

    @Test
    void recipesWithoutMenuPermissionsAreForbidden() {
        var e = assertThrows(AppException.class,
                () -> controller.importCatalog(WITH_RECIPES, false, auth("PERM_PRODUCTS_CREATE", "PERM_MENU_UPDATE")));

        assertEquals(ErrorCode.FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(catalogImportService);
    }

    @Test
    void recipesNeedThePlanFeature() {
        doThrow(AppException.forbidden(ErrorCode.PLAN_FEATURE_REQUIRED, "no"))
                .when(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);

        var e = assertThrows(AppException.class, () -> controller.importCatalog(WITH_RECIPES, false,
                auth("PERM_PRODUCTS_CREATE", "PERM_MENU_CREATE", "PERM_MENU_UPDATE")));

        assertEquals(ErrorCode.PLAN_FEATURE_REQUIRED, e.getErrorCode());
        verifyNoInteractions(catalogImportService);
    }

    @Test
    void recipesWithPermissionsAndPlanAreImported() {
        when(catalogImportService.importCatalog(anyList(), anyList(), anyList(), eq(false), anySet(), anySet(), anySet())).thenReturn(emptyReport());

        var response = controller.importCatalog(WITH_RECIPES, false,
                auth("PERM_PRODUCTS_CREATE", "PERM_MENU_CREATE", "PERM_MENU_UPDATE"));

        assertEquals(200, response.getStatusCode().value());
        verify(accountService).requireFeature(Feature.RECIPES_AND_FOOD_COST);
    }

    @Test
    void thePermissionsOfTheSessionReachTheService() {
        when(catalogImportService.importCatalog(anyList(), anyList(), anyList(), eq(true), anySet(), anySet(), anySet())).thenReturn(emptyReport());

        controller.importCatalog(PRODUCTS_ONLY, true, auth("PERM_PRODUCTS_CREATE", "PERM_CATEGORIES_CREATE", "OTHER"));

        verify(catalogImportService).importCatalog(anyList(), anyList(), anyList(), eq(true),
                eq(EnumSet.of(Permission.PRODUCTS_CREATE, Permission.CATEGORIES_CREATE)), eq(Set.of()), eq(Set.of()));
    }

    @Test
    void theRecipesToReplaceReachTheService() {
        UUID dish = UUID.randomUUID();
        when(catalogImportService.importCatalog(anyList(), anyList(), anyList(), eq(false), anySet(), anySet(), anySet())).thenReturn(emptyReport());

        controller.importCatalog(new CatalogImportRequest(null, WITH_RECIPES.recipes(), List.of(dish, dish), null, null), false,
                auth("PERM_PRODUCTS_CREATE", "PERM_MENU_CREATE", "PERM_MENU_UPDATE"));

        verify(catalogImportService).importCatalog(anyList(), anyList(), anyList(), eq(false), anySet(), eq(Set.of(dish)), anySet());
    }

    private static Authentication auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken("user", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static Report emptyReport() {
        return new Report(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, 0, 0, List.of(), List.of());
    }
}
