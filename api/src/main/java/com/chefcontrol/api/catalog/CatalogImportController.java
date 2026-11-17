package com.chefcontrol.api.catalog;

import com.chefcontrol.api.catalog.dto.CatalogImportRequest;
import com.chefcontrol.api.catalog.dto.CatalogImportResult;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.service.AccountService;
import com.chefcontrol.application.service.CatalogImportService;
import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.user.Permission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.Set;

@RestController
@RequestMapping("/catalog-import")
@RequiredArgsConstructor
public class CatalogImportController {

    private final CatalogImportService catalogImportService;
    private final AccountService accountService;

    /**
     * Alta en lote de insumos y recetas (el front lee el Excel). Siempre 200: los problemas de las filas vuelven en
     * {@code errors} y, si hay alguno, no se guarda nada. Con {@code dryRun=true} valida y cuenta sin escribir.
     *
     * <p>Las recetas piden más que los insumos: crear plato, editar receta y que el plan tenga recetas. Por eso
     * esa parte se chequea acá según el contenido, y no con anotaciones fijas.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_PRODUCTS_CREATE')")
    public ResponseEntity<CatalogImportResult> importCatalog(@Valid @RequestBody CatalogImportRequest request,
                                                             @RequestParam(defaultValue = "false") boolean dryRun,
                                                             Authentication authentication) {
        if (!request.recipes().isEmpty()) {
            requireAuthority(authentication, "PERM_MENU_CREATE");
            requireAuthority(authentication, "PERM_MENU_UPDATE");
            accountService.requireFeature(Feature.RECIPES_AND_FOOD_COST);
        }
        // La receta de una preparación es un dato del insumo: pide editar insumos, y es parte de las recetas del plan.
        if (!request.preparations().isEmpty()) {
            requireAuthority(authentication, "PERM_PRODUCTS_UPDATE");
            accountService.requireFeature(Feature.RECIPES_AND_FOOD_COST);
        }
        var report = catalogImportService.importCatalog(
                request.products().stream().map(p -> new ProductRow(
                        p.row(), p.name(), p.unit(), p.category(), p.minStock(), p.yieldPercentage(),
                        p.ivaPercentage())).toList(),
                request.preparations().stream().map(r -> new PreparationRow(r.row(), r.preparation(),
                        r.yieldQuantity(), r.yieldUnit(), r.ingredient(), r.quantity(), r.unit())).toList(),
                request.recipes().stream().map(r -> new RecipeRow(
                        r.row(), r.menuItem(), r.price(), r.section(), r.ingredient(), r.quantity(), r.unit())).toList(),
                dryRun, permissionsOf(authentication), Set.copyOf(request.replaceRecipeMenuItemIds()),
                Set.copyOf(request.replacePreparationProductIds()));
        return ResponseEntity.ok(CatalogImportResult.from(report));
    }

    /** Las authorities PERM_X del JWT, de vuelta a {@link Permission}. */
    private static Set<Permission> permissionsOf(Authentication authentication) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (Permission p : Permission.values()) {
            if (hasAuthority(authentication, "PERM_" + p.name())) permissions.add(p);
        }
        return permissions;
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }

    private static void requireAuthority(Authentication authentication, String authority) {
        if (!hasAuthority(authentication, authority)) {
            throw AppException.forbidden(ErrorCode.FORBIDDEN, "Missing " + authority);
        }
    }
}
