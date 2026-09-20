-- ============================================================
-- V17: cartas como entidad propia.
--
-- Hasta acá "la carta" era menu_items.is_active: los platos activos eran la carta actual y los
-- inactivos se mostraban como "cartas pasadas". Eso mezclaba dos cosas distintas — que un plato
-- exista en el catálogo, y que esté ofrecido en una carta — y no permitía tener más de una carta
-- ni saber qué tenía cada una.
--
-- Ahora: menu_items.is_active = el plato existe en el catálogo (baja lógica).
--        cartas + carta_items = qué platos se ofrecen en cada carta.
--
-- Sin precio propio (el precio sigue en menu_items, así que las ventas no cambian) y sin
-- versionado: quién agregó o sacó cada plato y cuándo queda en audit_log.
--
-- OJO con el ON DELETE CASCADE explícito: el DO block de V15 reescribió las FKs que existían
-- en ese momento, no las futuras. Toda tabla nueva que cuelgue de restaurants tiene que
-- declarar su cascada o rompe el borrado duro de restaurante y de cuenta (V15/V16).
-- ============================================================

CREATE TABLE cartas (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE carta_items (
    carta_id     UUID NOT NULL REFERENCES cartas(id)     ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    PRIMARY KEY (carta_id, menu_item_id)
);

CREATE INDEX idx_cartas_restaurant        ON cartas(restaurant_id);
CREATE INDEX idx_carta_items_menu_item    ON carta_items(menu_item_id);

-- Case-insensitive igual que CartaService.requireAvailableName()
CREATE UNIQUE INDEX uq_cartas_restaurant_name ON cartas (restaurant_id, lower(name));

-- ── Backfill: lo que hoy se ve como "carta actual" pasa a ser una carta de verdad ──
INSERT INTO cartas (restaurant_id, name)
SELECT id, 'Carta actual' FROM restaurants;

INSERT INTO carta_items (carta_id, menu_item_id)
SELECT c.id, m.id
FROM cartas c
JOIN menu_items m ON m.restaurant_id = c.restaurant_id AND m.is_active
WHERE c.name = 'Carta actual';

-- Los platos inactivos (las "cartas pasadas" de antes) quedan en el catálogo como dados de
-- baja, fuera de toda carta. No se pierde nada y siguen siendo reactivables desde /menu.
