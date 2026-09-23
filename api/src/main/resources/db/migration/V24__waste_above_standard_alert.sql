-- Alerta de merma por encima de la estándar.
--
-- El CHECK de alerts.type enumera los valores permitidos, así que agregar uno al enum de Java
-- sin tocar la base hace que el INSERT falle recién cuando la alerta se dispara por primera vez
-- —en un cron, de madrugada, donde nadie lo ve.
--
-- La columna pasa de VARCHAR(20) a VARCHAR(30): 'waste_above_standard' mide exactamente 20 y
-- entraba justo, lo que dejaba la próxima adición condenada a truncarse en silencio.

ALTER TABLE alerts DROP CONSTRAINT IF EXISTS alerts_type_check;
ALTER TABLE alerts ALTER COLUMN type TYPE VARCHAR(30);
ALTER TABLE alerts ADD CONSTRAINT alerts_type_check
    CHECK (type IN ('low_stock', 'overstock', 'expiration', 'price_increase', 'waste_above_standard'));
