package com.chefcontrol.domain.purchase;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que un proveedor imprime → el insumo: lo confirma una persona en la primera factura y la próxima se reconoce sola.
 *
 * @param printedKey          el texto impreso normalizado (sin mayúsculas, acentos ni espacios de más)
 * @param unitsPerPrintedUnit cuántas unidades del insumo trae una unidad impresa ("BOLSA HARINA 25 KG" → 25); null si la
 *                            unidad impresa se convierte sola
 */
public record SupplierProductAlias(UUID id, UUID restaurantId, UUID supplierId, String printedKey, UUID productId,
                                   BigDecimal unitsPerPrintedUnit) {}
