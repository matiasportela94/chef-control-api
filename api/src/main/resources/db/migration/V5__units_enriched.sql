-- ============================================================
-- V5 — Units enriched: base_unit_id, to_base_factor, is_system
--      + seed culinario AR (lb, oz, cc, packaging)
-- ============================================================

ALTER TABLE units ADD COLUMN base_unit_id   UUID           REFERENCES units(id);
ALTER TABLE units ADD COLUMN to_base_factor NUMERIC(18, 6);
ALTER TABLE units ADD COLUMN is_system      BOOLEAN        NOT NULL DEFAULT true;

-- ---- WEIGHT: base = Gramo ----
UPDATE units SET to_base_factor = 1
    WHERE name = 'Gramo';

UPDATE units SET to_base_factor = 1000,
                 base_unit_id   = (SELECT id FROM units WHERE name = 'Gramo')
    WHERE name = 'Kilogramo';

-- ---- VOLUME: base = Mililitro ----
UPDATE units SET to_base_factor = 1
    WHERE name = 'Mililitro';

UPDATE units SET to_base_factor = 1000,
                 base_unit_id   = (SELECT id FROM units WHERE name = 'Mililitro')
    WHERE name = 'Litro';

-- ---- UNIT: base = Unidad ----
UPDATE units SET to_base_factor = 1
    WHERE name = 'Unidad';

UPDATE units SET to_base_factor = 12,
                 base_unit_id   = (SELECT id FROM units WHERE name = 'Unidad')
    WHERE name = 'Docena';

UPDATE units SET to_base_factor = 1,
                 base_unit_id   = (SELECT id FROM units WHERE name = 'Unidad')
    WHERE name = 'Porción';

-- Cajón: sin factor universal (depende del producto)

-- ---- Nuevas unidades WEIGHT ----
INSERT INTO units (name, abbreviation, type, to_base_factor, base_unit_id) VALUES
    ('Libra', 'lb', 'WEIGHT', 453.592000,
        (SELECT id FROM units WHERE name = 'Gramo')),
    ('Onza',  'oz', 'WEIGHT',  28.349500,
        (SELECT id FROM units WHERE name = 'Gramo'));

-- ---- Nuevas unidades VOLUME ----
INSERT INTO units (name, abbreviation, type, to_base_factor, base_unit_id) VALUES
    ('Centímetro cúbico', 'cc', 'VOLUME', 1,
        (SELECT id FROM units WHERE name = 'Mililitro'));

-- ---- Nuevas unidades UNIT (packaging contextual, sin factor universal) ----
INSERT INTO units (name, abbreviation, type) VALUES
    ('Botella',  'bot', 'UNIT'),
    ('Lata',     'lat', 'UNIT'),
    ('Bolsa',    'bol', 'UNIT'),
    ('Caja',     'cja', 'UNIT'),
    ('Paquete',  'paq', 'UNIT');

-- ---- unit_conversions: UNIQUE constraint + inverso faltante ----
ALTER TABLE unit_conversions
    ADD CONSTRAINT uq_unit_conversions_pair UNIQUE (from_unit_id, to_unit_id);

-- Inverso faltante: Unidad → Docena
INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor)
SELECT f.id, t.id, 0.083333
FROM units f, units t
WHERE f.name = 'Unidad' AND t.name = 'Docena';
