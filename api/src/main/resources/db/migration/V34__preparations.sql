-- Preparaciones: un insumo con receta propia (mayonesa base, medallón) que se produce en tandas.
--
-- La receta de una preparación vive en las mismas tablas que la de un plato: el dueño es un plato O un insumo.
-- recipe_items y recipe_versions son justo lo que hace falta (ítems, historia para ?at=, la versión vacía al borrar),
-- y duplicarlas en tablas propias duplicaría el versionado, que es lo más fácil de dejar con huecos.
-- yield_quantity = cuánto rinde una tanda, en la unidad por defecto de la preparación. Va en la receta y no en el
-- producto porque cambia con la receta, y el costo a una fecha necesita el rendimiento de esa fecha.

ALTER TABLE recipes ALTER COLUMN menu_item_id DROP NOT NULL;
ALTER TABLE recipes ADD COLUMN product_id UUID REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE recipes ADD COLUMN yield_quantity NUMERIC(12, 3);
ALTER TABLE recipes ADD CONSTRAINT recipes_owner_check CHECK ((menu_item_id IS NULL) <> (product_id IS NULL));
ALTER TABLE recipes ADD CONSTRAINT recipes_yield_check CHECK (product_id IS NULL OR yield_quantity > 0);
CREATE UNIQUE INDEX recipes_product_id_key ON recipes (product_id) WHERE product_id IS NOT NULL;

-- La versión vacía de un borrado no tiene rendimiento: por eso acá puede ser null.
ALTER TABLE recipe_versions ALTER COLUMN menu_item_id DROP NOT NULL;
ALTER TABLE recipe_versions ADD COLUMN product_id UUID REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE recipe_versions ADD COLUMN yield_quantity NUMERIC(12, 3);
ALTER TABLE recipe_versions ADD CONSTRAINT recipe_versions_owner_check
    CHECK ((menu_item_id IS NULL) <> (product_id IS NULL));
CREATE INDEX idx_recipe_versions_product_lookup ON recipe_versions (product_id, valid_from DESC)
    WHERE product_id IS NOT NULL;

-- Una tanda producida. sale_item_id / parent_production_id: producción automática (la que hizo falta para una venta,
-- o para otra producción); revertir la venta o la producción padre la revierte. Sin CHECK en status: enum de Java.
CREATE TABLE productions (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id        UUID           NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    product_id           UUID           NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity             NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),  -- en la unidad por defecto de la preparación
    unit_id              UUID           NOT NULL REFERENCES units(id),
    cost_per_unit        NUMERIC(12, 4),
    total_cost           NUMERIC(12, 2),
    expiration_date      DATE,
    sale_item_id         UUID           REFERENCES sale_items(id) ON DELETE CASCADE,
    parent_production_id UUID           REFERENCES productions(id) ON DELETE CASCADE,
    status               VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    notes                TEXT,
    user_id              UUID           REFERENCES users(id) ON DELETE SET NULL,
    produced_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_productions_restaurant ON productions (restaurant_id, produced_at DESC);
CREATE INDEX idx_productions_sale_item ON productions (sale_item_id) WHERE sale_item_id IS NOT NULL;
CREATE INDEX idx_productions_parent ON productions (parent_production_id) WHERE parent_production_id IS NOT NULL;

-- El lote que entra al producir. Como purchase_item_id: revertir la producción lo deja en cero.
ALTER TABLE stock_batches ADD COLUMN production_id UUID REFERENCES productions(id) ON DELETE CASCADE;
CREATE INDEX idx_stock_batches_production ON stock_batches (production_id) WHERE production_id IS NOT NULL;

-- Movimiento nuevo: PRODUCTION OUT = ingrediente consumido, PRODUCTION IN = entra el lote de la preparación.
-- El CHECK de V1 enumera los tipos: sin esto, el INSERT fallaría recién en la primera producción (la trampa de alerts.type).
ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_type_check
    CHECK (type IN ('PURCHASE', 'WASTE', 'SALE', 'ADJUSTMENT', 'REVERSAL', 'COUNT', 'PRODUCTION'));

-- Permisos nuevos para los roles que ya existen (los de cuentas nuevas los traen de RestaurantRegistrationService;
-- SUPERADMIN los tiene sin filas). Ver producción: quien ve stock. Registrar: quien registra merma. Revertir: quien
-- revierte movimientos.
INSERT INTO role_permissions (role_id, permission)
SELECT role_id, 'PRODUCTIONS_VIEW' FROM role_permissions WHERE permission = 'STOCK_VIEW'
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission)
SELECT role_id, 'PRODUCTIONS_CREATE' FROM role_permissions WHERE permission = 'WASTE_CREATE'
ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission)
SELECT role_id, 'PRODUCTIONS_DELETE' FROM role_permissions WHERE permission = 'STOCK_DELETE'
ON CONFLICT DO NOTHING;
