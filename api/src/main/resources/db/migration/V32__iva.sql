-- IVA en compras. Todo nullable: null = "no cargado" en insumos y restaurantes, y "compra anterior al IVA" en
-- compras y líneas. Lo viejo no se recalcula: no hay forma de saber si esos precios traían IVA.

-- Alícuota con la que se compra el insumo. Los valores válidos (0, 2.5, 5, 10.5, 21, 27) los valida el dominio
-- (IvaRates); acá solo el rango, así una alícuota nueva no pide migración.
ALTER TABLE products
    ADD COLUMN iva_percentage NUMERIC(5, 2) CHECK (iva_percentage >= 0 AND iva_percentage < 100);

-- RESPONSABLE_INSCRIPTO | MONOTRIBUTO (enum de Java). Sin CHECK, como subscriptions.status.
ALTER TABLE restaurants
    ADD COLUMN tax_condition VARCHAR(30);

-- Si los precios de la compra se cargaron con el IVA adentro (Factura B) o sin (Factura A).
-- total = net_total + iva_total en las compras nuevas.
ALTER TABLE purchases
    ADD COLUMN prices_include_iva BOOLEAN,
    ADD COLUMN net_total          NUMERIC(12, 2),
    ADD COLUMN iva_total          NUMERIC(12, 2);

-- Congelados al guardar, como el costo: la alícuota usada y el neto e IVA de la línea.
ALTER TABLE purchase_items
    ADD COLUMN iva_percentage NUMERIC(5, 2) CHECK (iva_percentage >= 0 AND iva_percentage < 100),
    ADD COLUMN net_amount     NUMERIC(12, 2),
    ADD COLUMN iva_amount     NUMERIC(12, 2);
