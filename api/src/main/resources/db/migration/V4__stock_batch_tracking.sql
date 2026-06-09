-- ============================================================
-- Control de vencimientos por lote de compra (FIFO)
-- ============================================================

ALTER TABLE stock_batches
    ADD COLUMN purchase_item_id UUID REFERENCES purchase_items(id);

CREATE INDEX ON stock_batches (purchase_item_id);
CREATE INDEX ON stock_batches (restaurant_id, product_id, quantity_remaining);

-- Registra qué lotes tocó cada movimiento de stock y por cuánto.
-- quantity con signo: positivo = el lote gana cantidad (compra/reversión de salida),
-- negativo = el lote pierde cantidad (venta/merma/ajuste/reversión de entrada).
CREATE TABLE stock_movement_batch_allocations (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_movement_id   UUID           NOT NULL REFERENCES stock_movements(id),
    stock_batch_id      UUID           NOT NULL REFERENCES stock_batches(id),
    quantity            NUMERIC(12, 3) NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX ON stock_movement_batch_allocations (stock_movement_id);
CREATE INDEX ON stock_movement_batch_allocations (stock_batch_id);
