-- IVA de las ventas. Para un Responsable Inscripto el IVA de lo que vende es del fisco (débito fiscal), no ingreso:
-- el food cost % se calcula sobre el precio sin IVA. Para el monotributista el precio entero es ingreso.

-- La alícuota con la que vende el restaurante. Solo se usa si tax_condition = RESPONSABLE_INSCRIPTO.
-- Las alícuotas válidas las valida el dominio (IvaRates); acá solo el rango, como en V32.
ALTER TABLE restaurants
    ADD COLUMN sales_iva_percentage NUMERIC(5,2) NOT NULL DEFAULT 21
        CHECK (sales_iva_percentage >= 0 AND sales_iva_percentage < 100);

-- Congelados al vender, como el precio: la alícuota que regía (RI → la del restaurante; si no, 0) y el ingreso
-- neto, redondeado al centavo por línea. Null = venta anterior a esta migración: el precio entero es ingreso y
-- no se recalcula nunca.
ALTER TABLE sales
    ADD COLUMN iva_percentage NUMERIC(5,2) CHECK (iva_percentage >= 0 AND iva_percentage < 100),
    ADD COLUMN net_total      NUMERIC(12,2);

ALTER TABLE sale_items
    ADD COLUMN net_amount NUMERIC(12,2);
