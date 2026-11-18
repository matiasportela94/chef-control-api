-- Lo que registró el chat de la IA (F19), para deshacerlo: quien lo cargó, durante 15 minutos, sin el permiso de
-- borrar; después, o lo de otro, con el permiso de borrar de siempre. Una fila por entidad (una compra, cada
-- waste_event). entity_id sin FK: según type es una compra, una merma, una venta, un conteo o una producción.
-- Sin CHECK en type: enum de Java.
CREATE TABLE chat_actions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID         NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    user_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    type          VARCHAR(30)  NOT NULL,
    entity_id     UUID         NOT NULL,
    summary       VARCHAR(500) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    undone_at     TIMESTAMPTZ
);
