-- F19, tanda 8: el ticket Z o reporte de caja (foto o PDF) → las ventas del día, con el ticket guardado.

-- El número de Z: para avisar que ese cierre ya está cargado.
ALTER TABLE sales ADD COLUMN report_number VARCHAR(30);

-- Lo que imprime el POS del local → el plato. menu_item_id null = "no es un plato" (bebidas, cubiertos, extras que no
-- están cargados): el próximo Z lo deja ignorado solo, sin que haya que sacarlo a mano cada noche.
CREATE TABLE menu_item_aliases (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    printed_key   VARCHAR(300) NOT NULL,
    menu_item_id  UUID REFERENCES menu_items(id) ON DELETE CASCADE,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, printed_key)
);

-- El ticket, adjunto a la venta como la factura a la compra.
CREATE TABLE sale_attachments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id       UUID NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    content_type  VARCHAR(50) NOT NULL,
    filename      VARCHAR(255),
    size_bytes    INTEGER NOT NULL,
    bytes         BYTEA NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sale_attachments_sale ON sale_attachments (sale_id);
