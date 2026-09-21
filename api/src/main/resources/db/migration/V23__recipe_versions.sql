-- Versiones de receta: la tercera entrada del food cost teórico que no tenía historia.
--
-- MenuItemService.saveRecipe() hace recipe.replaceItems(), que es destructivo: la composición
-- anterior desaparece. Con el costo de compra (ledger) y el rendimiento (V22) ya historizados,
-- la receta era lo único que quedaba obligando a calcular el pasado con datos de hoy — o sea,
-- a contestar "¿cuánto costaba en marzo?" con la receta de abril.
--
-- Normalizadas y no un snapshot JSONB: mantiene las FK a products y units, así el borrado duro
-- sigue funcionando por cascada y habilita preguntar por ingrediente a través del tiempo.
--
-- Sin valid_until, igual que V22: el final de cada versión es el valid_from de la siguiente.

CREATE TABLE recipe_versions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID        NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    menu_item_id  UUID        NOT NULL REFERENCES menu_items(id)  ON DELETE CASCADE,
    servings      INTEGER     NOT NULL DEFAULT 1,
    valid_from    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_by    UUID        REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE recipe_version_items (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_version_id UUID           NOT NULL REFERENCES recipe_versions(id) ON DELETE CASCADE,
    product_id        UUID           NOT NULL REFERENCES products(id)        ON DELETE CASCADE,
    quantity          NUMERIC(12, 3) NOT NULL,
    unit_id           UUID           NOT NULL REFERENCES units(id)
);

CREATE INDEX idx_recipe_versions_lookup ON recipe_versions (menu_item_id, valid_from DESC);
CREATE INDEX idx_recipe_version_items_version ON recipe_version_items (recipe_version_id);

-- Punto de partida: la receta actual de cada plato pasa a ser su primera versión.
-- valid_from = recipes.created_at, que es lo más cierto que sabemos. Si la receta se editó
-- entre medio, ese cambio no quedó registrado y no hay forma de recuperarlo.
INSERT INTO recipe_versions (id, restaurant_id, menu_item_id, servings, valid_from)
SELECT r.id, r.restaurant_id, r.menu_item_id, r.servings, r.created_at
FROM recipes r;

INSERT INTO recipe_version_items (recipe_version_id, product_id, quantity, unit_id)
SELECT ri.recipe_id, ri.product_id, ri.quantity, ri.unit_id
FROM recipe_items ri;
