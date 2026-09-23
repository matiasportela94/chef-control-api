-- ============================================================
-- V13: cuentas + roles dinámicos por cuenta.
--
-- Antes: "roles" era una tabla GLOBAL de 4 filas fijas para toda la plataforma
-- (CHECK IN ('OWNER','MANAGER','KITCHEN','READONLY')). Ahora cada cuenta tiene
-- sus propios roles, editables, y los comparten todos sus restaurantes.
--
-- El rol de sistema (SUPERADMIN, is_system=true) reemplaza a OWNER: nunca se
-- edita ni se borra, y siempre tiene todos los permisos por código (no guarda
-- filas en role_permissions) — es la red de seguridad contra quedar bloqueado
-- de la propia cuenta.
--
-- permission_overrides se vacía: el catálogo de permisos cambió de forma
-- (VIEW/MANAGE -> VIEW/CREATE/UPDATE/DELETE) y esta tabla es nueva de esta
-- misma sesión de desarrollo, sin datos reales que preservar todavía.
-- ============================================================

-- ── 1. accounts ──────────────────────────────────────────────
CREATE TABLE accounts (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id      UUID        NOT NULL REFERENCES users(id),
    name               VARCHAR(255) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    seed_restaurant_id UUID -- temporal, se dropea al final de esta misma migración
);

-- Una cuenta por restaurante existente, con el dueño = quien hoy tiene el rol OWNER ahí.
INSERT INTO accounts (owner_user_id, name, created_at, seed_restaurant_id)
SELECT ur.user_id, r.name, r.created_at, r.id
FROM restaurants r
JOIN user_restaurants ur ON ur.restaurant_id = r.id
JOIN roles old_role ON old_role.id = ur.role_id AND old_role.name = 'OWNER';

-- ── 2. restaurants.account_id ────────────────────────────────
ALTER TABLE restaurants ADD COLUMN account_id UUID REFERENCES accounts(id);

UPDATE restaurants r SET account_id = a.id
FROM accounts a WHERE a.seed_restaurant_id = r.id;

ALTER TABLE restaurants ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE accounts DROP COLUMN seed_restaurant_id;

-- ── 3. roles: de global-fijo a por-cuenta-editable ───────────
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_check;
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_key;
ALTER TABLE roles ADD COLUMN account_id UUID REFERENCES accounts(id);
ALTER TABLE roles ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE roles ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE roles ALTER COLUMN name TYPE VARCHAR(50);

-- 4 roles nuevos por cuenta: SUPERADMIN (de sistema) + MANAGER/KITCHEN/READONLY (editables,
-- arrancan con el mismo alcance que tenían como roles fijos).
INSERT INTO roles (account_id, name, is_system)
SELECT a.id, v.name, v.is_system
FROM accounts a
CROSS JOIN (VALUES ('SUPERADMIN', true), ('MANAGER', false), ('KITCHEN', false), ('READONLY', false))
    AS v(name, is_system);

CREATE TABLE role_permissions (
    role_id    UUID        NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(30) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- MANAGER arranca con todo salvo administrar roles/permisos.
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('PRODUCTS_VIEW'),('PRODUCTS_CREATE'),('PRODUCTS_UPDATE'),('PRODUCTS_DELETE'),
    ('CATEGORIES_VIEW'),('CATEGORIES_CREATE'),('CATEGORIES_UPDATE'),('CATEGORIES_DELETE'),
    ('SUPPLIERS_VIEW'),('SUPPLIERS_CREATE'),('SUPPLIERS_UPDATE'),('SUPPLIERS_DELETE'),
    ('PURCHASES_VIEW'),('PURCHASES_CREATE'),('PURCHASES_UPDATE'),('PURCHASES_DELETE'),
    ('SALES_VIEW'),('SALES_CREATE'),('SALES_DELETE'),
    ('WASTE_VIEW'),('WASTE_CREATE'),
    ('STOCK_VIEW'),('STOCK_DELETE'),
    ('STOCK_COUNTS_VIEW'),('STOCK_COUNTS_CREATE'),
    ('MENU_VIEW'),('MENU_CREATE'),('MENU_UPDATE'),('MENU_DELETE'),
    ('FOOD_COST_VIEW'),
    ('ALERTS_VIEW'),('ALERTS_UPDATE'),
    ('USERS_VIEW'),('USERS_CREATE'),('USERS_UPDATE'),('USERS_DELETE'),
    ('AUDIT_VIEW'),
    ('AI_USE')
) AS p(permission)
WHERE r.name = 'MANAGER' AND r.is_system = false;

-- KITCHEN
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission FROM roles r
CROSS JOIN (VALUES
    ('AI_USE'),('WASTE_VIEW'),('WASTE_CREATE'),('STOCK_VIEW'),
    ('STOCK_COUNTS_VIEW'),('STOCK_COUNTS_CREATE'),('MENU_VIEW'),
    ('PRODUCTS_VIEW'),('PURCHASES_VIEW'),('SALES_VIEW')
) AS p(permission)
WHERE r.name = 'KITCHEN';

-- READONLY
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission FROM roles r
CROSS JOIN (VALUES
    ('PRODUCTS_VIEW'),('CATEGORIES_VIEW'),('SUPPLIERS_VIEW'),('PURCHASES_VIEW'),
    ('SALES_VIEW'),('WASTE_VIEW'),('STOCK_VIEW'),('STOCK_COUNTS_VIEW'),
    ('MENU_VIEW'),('FOOD_COST_VIEW'),('ALERTS_VIEW')
) AS p(permission)
WHERE r.name = 'READONLY';

-- ── 4. repuntar cada membresía al rol nuevo de su cuenta ─────
-- (OWNER -> SUPERADMIN; el resto conserva el mismo nombre)
UPDATE user_restaurants ur
SET role_id = new_role.id
FROM roles old_role, restaurants r, roles new_role
WHERE ur.role_id = old_role.id
  AND old_role.account_id IS NULL
  AND r.id = ur.restaurant_id
  AND new_role.account_id = r.account_id
  AND new_role.name = CASE WHEN old_role.name = 'OWNER' THEN 'SUPERADMIN' ELSE old_role.name END;

-- Los 4 roles globales viejos ya no los referencia nadie.
DELETE FROM roles WHERE account_id IS NULL;

ALTER TABLE roles ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE roles ADD CONSTRAINT roles_account_name_unique UNIQUE (account_id, name);

-- ── 5. permission_overrides: catálogo cambió de forma, sin datos reales que perder ──
TRUNCATE permission_overrides;
