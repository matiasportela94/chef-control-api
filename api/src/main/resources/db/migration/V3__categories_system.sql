-- V3: System categories — make restaurant_id nullable, add is_system and parent_id

ALTER TABLE product_categories
    ALTER COLUMN restaurant_id DROP NOT NULL;

ALTER TABLE product_categories
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN parent_id UUID    REFERENCES product_categories(id);

INSERT INTO product_categories (restaurant_id, name, is_system, parent_id) VALUES
    (NULL, 'Carnes y proteínas',       true, NULL),
    (NULL, 'Frutas y verduras',        true, NULL),
    (NULL, 'Lácteos y huevos',         true, NULL),
    (NULL, 'Secos y abarrotes',        true, NULL),
    (NULL, 'Bebidas',                  true, NULL),
    (NULL, 'Panadería y pastelería',   true, NULL),
    (NULL, 'Packaging y descartables', true, NULL),
    (NULL, 'Limpieza e higiene',       true, NULL);

CREATE INDEX ON product_categories (is_system);
