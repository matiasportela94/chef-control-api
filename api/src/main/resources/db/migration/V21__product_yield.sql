-- Rendimiento del producto: qué porcentaje de lo comprado queda utilizable.
-- 90 = la papa pelada rinde 900 g por kg → para 1 kg neto hay que comprar 1/0,9 = 1,111 kg.
-- Puede superar 100: el arroz rinde ~250% (1 kg crudo da 2,5 kg cocido).
-- DEFAULT 100 = sin efecto hasta que alguien lo cargue; se activa producto por producto.
ALTER TABLE products
    ADD COLUMN yield_percentage NUMERIC(5, 2) NOT NULL DEFAULT 100
        CHECK (yield_percentage > 0 AND yield_percentage <= 1000);
