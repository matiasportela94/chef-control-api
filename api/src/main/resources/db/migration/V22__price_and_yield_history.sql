-- Historial de los dos datos maestros que mueven el food cost.
--
-- Por qué tabla propia y no audit_log: JpaAuditService es @Async, corre en otra transacción y
-- se traga toda excepción. Una fila que falta ahí puede significar "no cambió" o "la escritura
-- se perdió", y no hay forma de distinguirlas. Acá se escribe en la MISMA transacción que el
-- update y solo cuando el valor cambió, así la ausencia de fila significa una sola cosa.
--
-- Sin valid_until a propósito: es derivable del valid_from de la fila siguiente, y guardarlo
-- obliga a tocar dos filas por cambio — que es donde estas tablas se desincronizan y terminan
-- con huecos o solapamientos. El valor a una fecha sale de:
--   WHERE ... AND valid_from <= :t ORDER BY valid_from DESC LIMIT 1

CREATE TABLE menu_item_price_history (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID          NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    menu_item_id  UUID          NOT NULL REFERENCES menu_items(id)  ON DELETE CASCADE,
    price         NUMERIC(12,2) NOT NULL,
    valid_from    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    changed_by    UUID          REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE product_yield_history (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id    UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    product_id       UUID         NOT NULL REFERENCES products(id)    ON DELETE CASCADE,
    yield_percentage NUMERIC(5,2) NOT NULL,
    valid_from       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    changed_by       UUID         REFERENCES users(id) ON DELETE SET NULL
);

-- El orden del índice es el de la consulta: la entidad primero, la fecha descendente después.
CREATE INDEX idx_menu_item_price_history_lookup
    ON menu_item_price_history (menu_item_id, valid_from DESC);
CREATE INDEX idx_product_yield_history_lookup
    ON product_yield_history (product_id, valid_from DESC);

-- Punto de partida de la serie para lo que ya existe. Sin esto, un plato creado antes de hoy
-- no tiene contra qué comparar su primer cambio y la serie arranca colgada.
-- valid_from = created_at: es lo más cierto que sabemos, aunque el precio pueda haber cambiado
-- entre medio sin que quedara registro.
INSERT INTO menu_item_price_history (restaurant_id, menu_item_id, price, valid_from)
SELECT restaurant_id, id, price, created_at FROM menu_items WHERE price IS NOT NULL;

INSERT INTO product_yield_history (restaurant_id, product_id, yield_percentage, valid_from)
SELECT restaurant_id, id, yield_percentage, created_at FROM products;
