-- ============================================================
-- V12: Overrides puntuales de permisos por usuario/restaurante.
-- Los roles (OWNER/MANAGER/KITCHEN/READONLY) siguen fijos y su set de
-- permisos por default vive en código (RoleName.defaultPermissions()).
-- Esta tabla es solo la excepción: "este usuario en este restaurante
-- tiene/no tiene tal permiso puntual, más allá de lo que le da su rol".
-- ============================================================

CREATE TABLE permission_overrides (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    permission      VARCHAR(30) NOT NULL,
    granted         BOOLEAN     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, restaurant_id, permission)
);

CREATE INDEX ON permission_overrides (user_id, restaurant_id);
