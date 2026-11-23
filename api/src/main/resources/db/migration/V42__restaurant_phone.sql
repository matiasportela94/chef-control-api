-- ============================================================
-- Chef Control — V42: el teléfono de cada local
-- ============================================================
-- La persona es su email. Tiene un teléfono personal (users.phone, lo carga ella) y uno por local
-- (user_restaurants.phone, lo carga el admin en /users; null = ese local usa el personal). El que vale en un local es
-- coalesce(ur.phone, u.phone), y la única regla es que dos personas con membresía activa en el mismo local no tengan
-- el mismo; la valida UserManagementService. Entre locales y cuentas se repite libremente, por eso se va el UNIQUE.

ALTER TABLE user_restaurants ADD COLUMN phone VARCHAR(20);

-- Sin backfill, a propósito (Mati, 2026-11-23): las membresías que ya existían quedan en null y heredan el personal.
-- Copiarlo dejaría números viejos pegados en silencio cuando alguien cambia su personal en /profile.

-- El índice users_phone_idx queda.
ALTER TABLE users DROP CONSTRAINT users_phone_key;
