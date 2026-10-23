-- ============================================================
-- V31: cambios de plan y pagos únicos.
--
-- Cómo cambia de plan una cuenta que paga (decidido por Mati, 2026-10-23):
-- · Suba de plan en el mismo período: inmediata, con un pago único por la diferencia prorrateada
--   (one_time_payments). Después se cambia el monto de la suscripción para los cobros siguientes.
-- · Baja de plan, o cambio de período (mensual ↔ anual): una solicitud (subscription_change_requests) que
--   rige al terminar el período pagado y se aplica recién cuando entra ese cobro.
--   - Baja: se cambia el monto de la suscripción vigente en el proveedor.
--   - Cambio de período: la frecuencia no se puede cambiar en MP, así que es una suscripción nueva
--     (subscriptions.starts_at = cuándo se cobra por primera vez) y a la vigente se le pone fecha de fin.
--
-- · Sin CHECK en los estados, el propósito ni los planes, como el resto (ver V28).
-- · Todo cuelga de accounts con ON DELETE CASCADE: el cierre de cuenta se lleva estas filas. Lo que haya en el
--   proveedor (una suscripción nueva todavía sin cobrar) lo cancela el servicio antes de borrar.
-- ============================================================

ALTER TABLE subscriptions ADD COLUMN starts_at TIMESTAMPTZ;

-- La vigente ya no incluye la suscripción de un cambio de período mientras no arranca: conviven la vigente y la
-- que la va a reemplazar. Cuando la nueva arranca, la vieja se cierra antes de guardar la nueva.
DROP INDEX uq_subscriptions_one_live_per_account;
CREATE UNIQUE INDEX uq_subscriptions_one_live_per_account
    ON subscriptions (account_id)
    WHERE status IN ('ACTIVE', 'PAST_DUE') OR (status = 'PENDING' AND starts_at IS NULL);

CREATE TABLE subscription_change_requests (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    -- la vigente cuando se pidió: solo sus avisos aplican o tiran la solicitud
    subscription_id     UUID          NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    plan_tier           VARCHAR(20)   NOT NULL,
    billing_period      VARCHAR(10)   NOT NULL,
    amount_ars          NUMERIC(12,2) NOT NULL,
    effective_at        TIMESTAMPTZ   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    new_subscription_id UUID          REFERENCES subscriptions(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_change_requests_one_pending_per_account
    ON subscription_change_requests (account_id)
    WHERE status = 'PENDING';

CREATE TABLE one_time_payments (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose             VARCHAR(30)   NOT NULL,
    amount_ars          NUMERIC(12,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    preference_id       VARCHAR(100),
    provider_payment_id VARCHAR(100),
    target              VARCHAR(50),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_one_time_payments_account_created ON one_time_payments (account_id, created_at DESC);
