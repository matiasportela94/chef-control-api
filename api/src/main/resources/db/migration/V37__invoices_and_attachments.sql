-- F19, tanda 7: factura o remito (foto o PDF) → compra, con la factura guardada.

-- El proveedor se reconoce por CUIT. Único por local entre los activos: uno dado de baja no bloquea el alta.
ALTER TABLE suppliers ADD COLUMN cuit VARCHAR(11);
CREATE UNIQUE INDEX ux_suppliers_restaurant_cuit ON suppliers (restaurant_id, cuit) WHERE cuit IS NOT NULL AND is_active;

-- El comprobante de la compra: avisar si esa factura (proveedor + número) ya está cargada.
ALTER TABLE purchases ADD COLUMN invoice_type VARCHAR(10);    -- A | B | C | REMITO
ALTER TABLE purchases ADD COLUMN invoice_number VARCHAR(30);

-- Lo que un proveedor imprime → el insumo: lo confirma una persona la primera vez y la próxima factura se reconoce sola.
-- units_per_printed_unit: cuántas unidades del insumo trae una unidad impresa ("BOLSA HARINA 25 KG" → 25), aprendido
-- cuando la unidad impresa no se convierte.
CREATE TABLE supplier_product_aliases (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id          UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    supplier_id            UUID NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    printed_key            VARCHAR(300) NOT NULL,
    product_id             UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    units_per_printed_unit NUMERIC(14, 6),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (supplier_id, printed_key)
);

-- La factura o el remito, en la base como las fotos de los platos: el borrado duro sigue llevándose todo.
CREATE TABLE purchase_attachments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id   UUID NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    content_type  VARCHAR(50) NOT NULL,
    filename      VARCHAR(255),
    size_bytes    INTEGER NOT NULL,
    bytes         BYTEA NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_purchase_attachments_purchase ON purchase_attachments (purchase_id);
