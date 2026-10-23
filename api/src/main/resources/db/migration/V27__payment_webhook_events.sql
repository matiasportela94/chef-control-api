-- ============================================================
-- V27: registro de avisos (webhooks) de proveedores de pago.
--
-- Cada aviso que pasa la validación de firma se guarda tal cual llegó. Sirve para dos cosas:
--   · no procesar dos veces el mismo aviso (MP reintenta cada 15 min si no recibe 200)
--   · ver el contenido real de eventos que todavía no manejamos (ej. Card Updater) antes de
--     decidir qué hacer con ellos, en vez de adivinar su formato.
--
-- (provider, event_key) es la identidad del aviso: event_key es el id de la notificación que
-- manda el proveedor. Crece sin límite: aún no hay limpieza (ver CLAUDE.md, pendientes).
-- ============================================================
CREATE TABLE payment_webhook_events (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider    VARCHAR(30)  NOT NULL,
    event_key   VARCHAR(200) NOT NULL,
    topic       VARCHAR(80),
    data_id     VARCHAR(120),
    payload     TEXT         NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_webhook_events UNIQUE (provider, event_key)
);
