-- La conversación del chat (F19, tanda 5): una fila por intercambio (un mensaje y su respuesta), por usuario y local.
-- La memoria es la de hoy (desde la medianoche argentina o la última "Nueva conversación"); las filas se guardan 30
-- días para revisar cómo interpreta el modelo mensajes reales (status = NOT_UNDERSTOOD, sobre todo).
CREATE TABLE chat_messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind         VARCHAR(20) NOT NULL,   -- MESSAGE | CONFIRMATION | NEW_CONVERSATION
    user_message TEXT,                   -- null en CONFIRMATION y NEW_CONVERSATION
    status       VARCHAR(30),            -- el ChatStatus de la respuesta: columna propia para buscarlo a mano
    reply        JSONB,                  -- la respuesta: texto, ids de acciones, propuestas, lo salteado, la receta
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_messages_conversation ON chat_messages (user_id, restaurant_id, created_at);
