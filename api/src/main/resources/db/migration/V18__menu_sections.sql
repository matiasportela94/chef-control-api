-- ============================================================
-- V18: los pasos del menú (entradas, principales, postres, bebidas) dejan de ser texto libre.
--
-- menu_items.category era un VARCHAR que cada uno escribía a mano: sin lista, sin orden y con
-- "Principales" / "principales" / "Pricipales" conviviendo como si fueran secciones distintas.
-- No se podía filtrar en serio ni mostrar la carta en el orden en que se come.
--
-- Ahora hay una tabla por restaurante, con orden propio y color (el color pinta la tarjeta del
-- plato en /menu). El texto libre se migra a filas y la columna vieja se borra: si quedara,
-- habría dos fuentes de verdad para lo mismo.
--
-- FKs: restaurant_id CASCADE (borrado duro de restaurante, V15/V16 no alcanzan las tablas
-- nuevas). menu_items.section_id va SET NULL — borrar una sección no puede llevarse los platos.
-- Igual el servicio bloquea borrar una sección en uso; el SET NULL es la red de abajo.
-- ============================================================

CREATE TABLE menu_sections (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    color         VARCHAR(7),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_menu_sections_restaurant ON menu_sections(restaurant_id);
CREATE UNIQUE INDEX uq_menu_sections_restaurant_name ON menu_sections (restaurant_id, lower(name));

-- ── Backfill: una sección por cada categoría que ya exista ──
-- DISTINCT ON case-insensitive: si alguien cargó "Postres" y "postres", queda una sola fila
-- (la primera alfabéticamente) y los dos platos terminan apuntando ahí.
INSERT INTO menu_sections (restaurant_id, name, sort_order, color)
SELECT restaurant_id,
       name,
       (row_number() OVER (PARTITION BY restaurant_id ORDER BY lower(name)) - 1)::int,
       (ARRAY['#F36525','#16a34a','#2563eb','#d97706','#9333ea','#dc2626','#0891b2','#65a30d'])[
           1 + ((row_number() OVER (PARTITION BY restaurant_id ORDER BY lower(name)) - 1) % 8)]
FROM (
    SELECT DISTINCT ON (restaurant_id, lower(trim(category)))
           restaurant_id,
           trim(category) AS name
    FROM menu_items
    WHERE category IS NOT NULL AND trim(category) <> ''
    ORDER BY restaurant_id, lower(trim(category)), trim(category)
) existentes;

ALTER TABLE menu_items ADD COLUMN section_id UUID REFERENCES menu_sections(id) ON DELETE SET NULL;

UPDATE menu_items m
SET section_id = s.id
FROM menu_sections s
WHERE s.restaurant_id = m.restaurant_id
  AND lower(s.name) = lower(trim(m.category));

ALTER TABLE menu_items DROP COLUMN category;

CREATE INDEX idx_menu_items_section ON menu_items(section_id);
