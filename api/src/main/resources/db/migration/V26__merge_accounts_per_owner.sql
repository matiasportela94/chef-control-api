-- ============================================================
-- V26: una cuenta por dueño, no una por restaurante.
--
-- V13 creó las cuentas a partir de los restaurantes existentes —una por cada uno— porque en ese
-- momento no había de dónde sacar el agrupamiento. Quien tuviera dos restaurantes quedó con dos
-- cuentas de un restaurante cada una, en vez de una cuenta con dos locales. El síntoma visible es
-- que /restaurants muestra un solo local: lista por cuenta, y la cuenta tiene uno.
--
-- El criterio es "mismas cuentas si mismo dueño". Es seguro hoy porque no existe forma de crear
-- una cuenta a mano: toda cuenta salió del backfill de V13, así que dos cuentas del mismo
-- owner_user_id son siempre el mismo cliente partido en dos. **Si algún día se puede registrar
-- una segunda cuenta a propósito, esta migración no debe repetirse.**
--
-- Sobrevive la más vieja. Se lleva:
--   · los restaurantes de las otras
--   · el mejor plan del grupo, para que fusionar nunca degrade a nadie
--   · los roles: se remapea por nombre, y el que no tenga equivalente se muda en vez de borrarse
--
-- role_permissions se va solo por ON DELETE CASCADE al borrar los roles huérfanos.
-- permission_overrides es por usuario + restaurante, así que no lo toca nada de esto.
-- ============================================================

-- La cuenta que sobrevive por cada dueño: la más vieja, con el id como desempate para que el
-- resultado sea el mismo en cualquier entorno donde corra.
CREATE TEMP TABLE account_merge AS
SELECT a.id                AS from_account,
       keep.id             AS to_account
FROM accounts a
JOIN LATERAL (
    SELECT k.id FROM accounts k
    WHERE k.owner_user_id = a.owner_user_id
    ORDER BY k.created_at, k.id
    LIMIT 1
) keep ON TRUE
WHERE a.id <> keep.id;

-- ── 1. el mejor plan del grupo se queda en la cuenta que sobrevive ──
-- Fusionar no puede bajarle el plan a nadie.
WITH rank_plan AS (
    SELECT m.to_account,
           MAX(CASE a.plan WHEN 'ENTERPRISE' THEN 4 WHEN 'PRO' THEN 3
                           WHEN 'STARTER' THEN 2 ELSE 1 END) AS best
    FROM account_merge m
    JOIN accounts a ON a.id IN (m.from_account, m.to_account)
    GROUP BY m.to_account
)
UPDATE accounts a
   SET plan = CASE rp.best WHEN 4 THEN 'ENTERPRISE' WHEN 3 THEN 'PRO'
                           WHEN 2 THEN 'STARTER' ELSE 'TRIAL' END
  FROM rank_plan rp
 WHERE a.id = rp.to_account;

-- ── 2. los restaurantes cambian de cuenta ──
UPDATE restaurants r
   SET account_id = m.to_account
  FROM account_merge m
 WHERE r.account_id = m.from_account;

-- ── 3. los permisos apuntan a roles de la cuenta que se va ──
-- Se remapean al rol del mismo nombre en la cuenta que sobrevive.
UPDATE user_restaurants ur
   SET role_id = nuevo.id
  FROM roles viejo
  JOIN account_merge m ON m.from_account = viejo.account_id
  JOIN roles nuevo ON nuevo.account_id = m.to_account AND nuevo.name = viejo.name
 WHERE ur.role_id = viejo.id;

-- ── 4. roles sin equivalente: se mudan en vez de borrarse ──
-- Un rol propio de la cuenta que se va (nombre que no existe en la otra) se pierde si se borra,
-- y con él los permisos de quien lo tuviera asignado.
UPDATE roles viejo
   SET account_id = m.to_account
  FROM account_merge m
 WHERE viejo.account_id = m.from_account
   AND NOT EXISTS (
       SELECT 1 FROM roles nuevo
        WHERE nuevo.account_id = m.to_account AND nuevo.name = viejo.name);

-- ── 5. los roles duplicados que quedaron sin usar ──
DELETE FROM roles ro USING account_merge m WHERE ro.account_id = m.from_account;

-- ── 6. las cuentas vacías ──
DELETE FROM accounts a USING account_merge m WHERE a.id = m.from_account;

-- ── 7. el nombre ──
-- La cuenta se llamaba como uno de sus restaurantes, porque V13 la nombró con r.name. Después de
-- fusionar eso confunde: la cuenta es la unidad de facturación, no un local. Se la renombra con
-- el dueño, que es lo único cierto y genérico. No hay endpoint para renombrarla, así que si el
-- cliente tiene un nombre comercial hay que cambiarlo por SQL.
UPDATE accounts a
   SET name = 'Grupo ' || u.name
  FROM users u
 WHERE u.id = a.owner_user_id
   AND a.id IN (SELECT DISTINCT to_account FROM account_merge);

DROP TABLE account_merge;
