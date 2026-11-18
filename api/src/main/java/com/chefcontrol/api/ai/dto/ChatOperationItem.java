package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatService;
import com.chefcontrol.domain.waste.WasteReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param productId    el insumo (compra, merma, ajuste, producción)
 * @param menuItemId   el plato (venta)
 * @param name         el nombre del catálogo
 * @param quantity     en la unidad por defecto del insumo, ya convertida; en ventas, porciones; en un ajuste, lo contado
 * @param unitName     la abreviatura de esa unidad ("kg"); null en ventas
 * @param pricePerUnit solo compras: pesos por unitName
 * @param expirationDate solo compras
 * @param wasteReason  solo mermas
 */
public record ChatOperationItem(UUID productId, UUID menuItemId, String name, BigDecimal quantity, String unitName,
                                BigDecimal pricePerUnit, LocalDate expirationDate, WasteReason wasteReason) {

    static ChatOperationItem from(ChatService.OperationItem i) {
        return new ChatOperationItem(i.productId(), i.menuItemId(), i.name(), i.quantity(), i.unitName(),
                i.pricePerUnit(), i.expirationDate(), i.wasteReason());
    }

    ChatService.OperationItem toCommand() {
        return new ChatService.OperationItem(productId, menuItemId, name, quantity, unitName, pricePerUnit,
                expirationDate, wasteReason);
    }
}
