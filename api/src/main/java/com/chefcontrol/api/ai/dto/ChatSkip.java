package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.domain.chat.ChatOperationType;

/**
 * Lo que el chat no registró ni propone.
 *
 * @param type      null si no es una operación con deshacer: una consulta o una receta
 * @param name      el insumo (o el plato o la preparación de una receta), como lo dijo el usuario o como está en el
 *                  catálogo
 * @param errorCode un ErrorCode: CHAT_PERMISSION_MISSING, CHAT_PRODUCT_NOT_IN_CATALOG, CHAT_MENU_ITEM_NOT_IN_CATALOG,
 *                  CHAT_MISSING_PRICE,
 *                  CHAT_INVALID_QUANTITY, CHAT_OPERATION_NOT_SUPPORTED, UNIT_NOT_FOUND, o el que tiró el servicio
 *                  (UNIT_CONVERSION_NOT_FOUND, MISSING_PURCHASE_COST…). Una receta: CHAT_PERMISSION_MISSING,
 *                  PLAN_FEATURE_REQUIRED, AI_INTERPRETATION_FAILED o CHAT_OPERATION_NOT_SUPPORTED (una por mensaje)
 * @param message   el texto en castellano que arma el back
 */
public record ChatSkip(ChatOperationType type, String name, String errorCode, String message) {

    static ChatSkip from(ChatService.Skipped s) {
        return new ChatSkip(s.type(), s.name(), s.errorCode().name(), s.message());
    }
}
