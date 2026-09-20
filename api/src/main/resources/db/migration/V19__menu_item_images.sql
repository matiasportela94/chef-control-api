-- ============================================================
-- V19: foto opcional por plato.
--
-- Los bytes van en la base, no en un servicio externo. A 100 restaurantes son unos 900MB con
-- fotos de ~150KB: engorda el backup, pero evita credenciales nuevas y, sobre todo, mantiene
-- verdadero el borrado duro — DELETE /account y el borrado de restaurante ya se llevan todo lo
-- que cuelga de menu_items. Con un bucket afuera habría que borrar allá también, y una foto
-- huérfana de un cliente que se dio de baja es un problema que no quiero tener.
--
-- Tabla aparte y no una columna en menu_items: así el SELECT de la grilla no arrastra los bytes.
-- PK = menu_item_id: una foto por plato.
-- ============================================================

CREATE TABLE menu_item_images (
    menu_item_id UUID        PRIMARY KEY REFERENCES menu_items(id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL,
    bytes        BYTEA       NOT NULL,
    size_bytes   INTEGER     NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
