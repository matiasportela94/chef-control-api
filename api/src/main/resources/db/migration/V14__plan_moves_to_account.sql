-- ============================================================
-- V14: el plan pasa de restaurants a accounts.
--
-- Un restaurante no paga, la cuenta paga. Hasta ahora cada restaurante tenía
-- su propio plan porque no existía el concepto de cuenta; con accounts ya
-- creada (V13), el plan se muda ahí — es lo que permite vender "un plan"
-- (con límite de restaurantes, features, etc.) en vez de "un plan por local".
-- ============================================================

ALTER TABLE accounts ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'TRIAL'
    CHECK (plan IN ('TRIAL', 'STARTER', 'PRO', 'ENTERPRISE'));

-- Cada cuenta hereda el plan de su (único, hasta ahora) restaurante.
UPDATE accounts a SET plan = r.plan
FROM restaurants r
WHERE r.account_id = a.id;

ALTER TABLE restaurants DROP COLUMN plan;
