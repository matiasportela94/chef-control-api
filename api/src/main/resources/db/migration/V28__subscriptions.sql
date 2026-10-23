-- ============================================================
-- V28: suscripciones de pago de una cuenta.
--
-- Una cuenta paga su plan con una suscripción recurrente en el proveedor (MercadoPago). Esta tabla
-- es la copia local de su estado; la verdad la tiene el proveedor y se sincroniza por webhook.
--
-- · account_id con ON DELETE CASCADE: cerrar la cuenta (DELETE /account) se apoya en las cascadas
--   de V15/V16 y esta tabla tiene que irse con ella. OJO: borrar la fila NO cancela la suscripción
--   en el proveedor — eso lo hace el servicio antes de borrar, o se le sigue cobrando a la cuenta.
-- · billing_period: MONTHLY o ANNUAL. Es la misma suscripción en el proveedor con otra frecuencia.
-- · current_period_end: instante en que termina lo ya pagado (el próximo cobro). Si la suscripción
--   se cancela, la cuenta conserva el plan hasta ese instante (ver accounts.plan_expires_at, V29).
-- · Sin CHECK sobre status ni plan_tier: son enums de Java y un CHECK obligaría a una migración por
--   cada valor nuevo (ya pasó con alerts.type, ver CLAUDE.md).
-- · Una sola suscripción "viva" por cuenta, garantizado por el índice parcial. PAST_DUE todavía no
--   existe como estado: se agrega con el job de mora, y ya está en el índice para no rehacerlo.
-- ============================================================
CREATE TABLE subscriptions (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id               UUID          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    plan_tier                VARCHAR(20)   NOT NULL,
    billing_period           VARCHAR(10)   NOT NULL,
    status                   VARCHAR(20)   NOT NULL,
    provider                 VARCHAR(30)   NOT NULL,
    provider_subscription_id VARCHAR(100),
    amount_ars               NUMERIC(12,2) NOT NULL,
    payer_email              VARCHAR(255)  NOT NULL,
    current_period_end       TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_subscriptions_provider_id
    ON subscriptions (provider, provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;

CREATE UNIQUE INDEX uq_subscriptions_one_live_per_account
    ON subscriptions (account_id)
    WHERE status IN ('PENDING', 'ACTIVE', 'PAST_DUE');

CREATE INDEX idx_subscriptions_account_created ON subscriptions (account_id, created_at DESC);
