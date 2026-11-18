package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.application.service.ChatService.ChatStatus;

import java.util.List;
import java.util.UUID;

/**
 * @param message       el texto para el usuario, armado por el back con lo que pasó de verdad
 * @param actions       lo registrado, cada uno con su deshacer (REGISTERED)
 * @param proposal      lo que se confirmaría con /ai/chat/confirm (NEEDS_CONFIRMATION)
 * @param skipped       lo que no se registró ni se propone, con su motivo
 * @param catalogImport la receta que pidió el mensaje, para la vista previa de /catalog-import (dryRun y después la
 *                      confirmación); null si el mensaje no trae receta. Con algo acá se muestra la vista previa, sea
 *                      cual sea el status: en un mensaje mixto, el status es el de lo operativo
 * @param replaceRecipeMenuItemIds     el plato a pisar, si la receta es de uno que ya existe: va tal cual a /catalog-import
 * @param replacePreparationProductIds ídem, la preparación
 */
public record ChatResponse(ChatStatus status, String message, List<ChatActionResponse> actions,
                           List<ChatOperation> proposal, List<ChatSkip> skipped, InterpretCatalogResult catalogImport,
                           List<UUID> replaceRecipeMenuItemIds, List<UUID> replacePreparationProductIds) {

    public static ChatResponse from(ChatService.ChatResult r) {
        var recipe = r.recipe();
        return new ChatResponse(r.status(), r.message(),
                r.actions().stream().map(ChatActionResponse::from).toList(),
                r.proposal().stream().map(ChatOperation::from).toList(),
                r.skipped().stream().map(ChatSkip::from).toList(),
                recipe == null ? null : InterpretCatalogResult.from(recipe.catalogImport()),
                recipe == null ? List.of() : recipe.replaceRecipeMenuItemIds(),
                recipe == null ? List.of() : recipe.replacePreparationProductIds());
    }
}
