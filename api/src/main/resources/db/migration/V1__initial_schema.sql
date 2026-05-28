-- ============================================================
-- Chef Control — V1: Initial Schema
-- Flyway migration: backend/src/main/resources/db/migration/
-- 25 tablas + índices + seeds de roles y unidades
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TENANCY
-- ============================================================

CREATE TABLE restaurants (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) UNIQUE NOT NULL,
    timezone    VARCHAR(50)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    plan        VARCHAR(20)  NOT NULL DEFAULT 'TRIAL'
                    CHECK (plan IN ('TRIAL', 'STARTER', 'PRO', 'ENTERPRISE')),
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20)  UNIQUE,   -- número WhatsApp, clave de identificación en webhook
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(20)  UNIQUE NOT NULL
                CHECK (name IN ('OWNER', 'MANAGER', 'KITCHEN', 'READONLY'))
);

INSERT INTO roles (name) VALUES ('OWNER'), ('MANAGER'), ('KITCHEN'), ('READONLY');

CREATE TABLE user_restaurants (
    user_id         UUID         NOT NULL REFERENCES users(id),
    restaurant_id   UUID         NOT NULL REFERENCES restaurants(id),
    role_id         UUID         NOT NULL REFERENCES roles(id),
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, restaurant_id)
);

CREATE TABLE whatsapp_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    expires_at      TIMESTAMPTZ NOT NULL,   -- medianoche del día en curso
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)                        -- un solo contexto activo por usuario
);

-- ============================================================
-- CATÁLOGO
-- ============================================================

CREATE TABLE units (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50)  UNIQUE NOT NULL,
    abbreviation    VARCHAR(10)  NOT NULL,
    type            VARCHAR(10)  NOT NULL
                        CHECK (type IN ('WEIGHT', 'VOLUME', 'UNIT'))
);

INSERT INTO units (name, abbreviation, type) VALUES
    ('Kilogramo',  'kg',  'WEIGHT'),
    ('Gramo',      'g',   'WEIGHT'),
    ('Litro',      'l',   'VOLUME'),
    ('Mililitro',  'ml',  'VOLUME'),
    ('Unidad',     'u',   'UNIT'),
    ('Docena',     'doc', 'UNIT'),
    ('Cajón',      'caj', 'UNIT'),
    ('Porción',    'por', 'UNIT');

CREATE TABLE unit_conversions (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    from_unit_id    UUID           NOT NULL REFERENCES units(id),
    to_unit_id      UUID           NOT NULL REFERENCES units(id),
    factor          NUMERIC(18, 6) NOT NULL,
    CHECK (from_unit_id <> to_unit_id)
);

INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor)
SELECT f.id, t.id, 1000    FROM units f, units t WHERE f.name = 'Kilogramo' AND t.name = 'Gramo';
INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor)
SELECT f.id, t.id, 0.001   FROM units f, units t WHERE f.name = 'Gramo'     AND t.name = 'Kilogramo';
INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor)
SELECT f.id, t.id, 1000    FROM units f, units t WHERE f.name = 'Litro'     AND t.name = 'Mililitro';
INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor)
SELECT f.id, t.id, 0.001   FROM units f, units t WHERE f.name = 'Mililitro' AND t.name = 'Litro';
INSERT INTO unit_conversions (from_unit_id, to_unit_id, factor)
SELECT f.id, t.id, 12      FROM units f, units t WHERE f.name = 'Docena'    AND t.name = 'Unidad';

CREATE TABLE product_categories (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    name            VARCHAR(100) NOT NULL,
    color           VARCHAR(7),              -- hex, ej: #FF5733
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id       UUID           NOT NULL REFERENCES restaurants(id),
    category_id         UUID           REFERENCES product_categories(id),
    name                VARCHAR(255)   NOT NULL,
    sku                 VARCHAR(100),
    default_unit_id     UUID           NOT NULL REFERENCES units(id),
    min_stock           NUMERIC(12, 3),
    max_stock           NUMERIC(12, 3),
    is_active           BOOLEAN        NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, sku)
);

CREATE TABLE suppliers (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    name            VARCHAR(255) NOT NULL,
    contact_info    JSONB,       -- { phone, email, address, notes }
    is_active       BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- COMPRAS
-- ============================================================

CREATE TABLE purchases (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID           NOT NULL REFERENCES restaurants(id),
    supplier_id     UUID           REFERENCES suppliers(id),
    user_id         UUID           NOT NULL REFERENCES users(id),
    total           NUMERIC(12, 2),
    notes           TEXT,
    purchased_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE purchase_items (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id     UUID           NOT NULL REFERENCES purchases(id),
    product_id      UUID           NOT NULL REFERENCES products(id),
    quantity        NUMERIC(12, 3) NOT NULL,
    unit_id         UUID           NOT NULL REFERENCES units(id),
    price_per_unit  NUMERIC(12, 4) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- STOCK (corazón del sistema)
-- ============================================================

CREATE TABLE stock_movements (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID           NOT NULL REFERENCES restaurants(id),
    product_id      UUID           NOT NULL REFERENCES products(id),
    type            VARCHAR(20)    NOT NULL
                        CHECK (type IN ('PURCHASE', 'WASTE', 'SALE', 'ADJUSTMENT', 'REVERSAL', 'COUNT')),
    direction       VARCHAR(3)     NOT NULL
                        CHECK (direction IN ('IN', 'OUT')),
    quantity        NUMERIC(12, 3) NOT NULL,
    unit_id         UUID           NOT NULL REFERENCES units(id),
    cost_per_unit   NUMERIC(12, 4),
    stock_before    NUMERIC(12, 3) NOT NULL,
    stock_after     NUMERIC(12, 3) NOT NULL,
    reference_id    UUID,                      -- ID del registro origen (purchase_item, waste_event, etc.)
    reference_type  VARCHAR(30),               -- 'purchase_item' | 'waste_event' | 'sale_item' | 'stock_count' | 'manual'
    reversed_by     UUID           REFERENCES stock_movements(id),
    user_id         UUID           NOT NULL REFERENCES users(id),
    source          VARCHAR(15)    NOT NULL DEFAULT 'dashboard'
                        CHECK (source IN ('DASHBOARD', 'WHATSAPP', 'SYSTEM', 'POS')),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
    -- Sin updated_at: registro inmutable. NUNCA UPDATE NI DELETE.
);

CREATE TABLE stock_batches (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id       UUID           NOT NULL REFERENCES restaurants(id),
    product_id          UUID           NOT NULL REFERENCES products(id),
    quantity_remaining  NUMERIC(12, 3) NOT NULL,
    expiration_date     DATE,
    cost_per_unit       NUMERIC(12, 4),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE stock_counts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    user_id         UUID        NOT NULL REFERENCES users(id),
    notes           TEXT,
    counted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE waste_events (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID           NOT NULL REFERENCES restaurants(id),
    product_id      UUID           NOT NULL REFERENCES products(id),
    quantity        NUMERIC(12, 3) NOT NULL,
    unit_id         UUID           NOT NULL REFERENCES units(id),
    reason          VARCHAR(255),
    cost            NUMERIC(12, 2),
    user_id         UUID           NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ALERTAS
-- ============================================================

CREATE TABLE alerts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    product_id      UUID        REFERENCES products(id),
    type            VARCHAR(20)  NOT NULL
                        CHECK (type IN ('low_stock', 'overstock', 'expiration', 'price_increase')),
    severity        VARCHAR(10)  NOT NULL
                        CHECK (severity IN ('info', 'warning', 'critical')),
    message         TEXT         NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT false,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- IA Y MENSAJES (vacías en Fase 1, listas para Fase 2)
-- ============================================================

CREATE TABLE messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    user_id         UUID        REFERENCES users(id),
    channel         VARCHAR(15)  NOT NULL
                        CHECK (channel IN ('whatsapp', 'web', 'system')),
    direction       VARCHAR(10)  NOT NULL
                        CHECK (direction IN ('inbound', 'outbound')),
    content_type    VARCHAR(10)  NOT NULL
                        CHECK (content_type IN ('text', 'audio', 'image', 'document')),
    content         TEXT,
    media_url       VARCHAR(500),
    processed       BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_interpretations (
    id                      UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id              UUID     REFERENCES messages(id),
    restaurant_id           UUID     NOT NULL REFERENCES restaurants(id),
    intent                  VARCHAR(20),
    confidence              INTEGER  CHECK (confidence BETWEEN 0 AND 100),
    needs_confirmation      BOOLEAN  NOT NULL DEFAULT false,
    confirmed_by_user_id    UUID     REFERENCES users(id),
    raw_json                JSONB,
    executed                BOOLEAN  NOT NULL DEFAULT false,
    executed_movement_id    UUID     REFERENCES stock_movements(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- RECETAS Y VENTAS (vacías en Fase 1, activas en Fase 2)
-- ============================================================

CREATE TABLE menu_items (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID           NOT NULL REFERENCES restaurants(id),
    name            VARCHAR(255)   NOT NULL,
    description     TEXT,
    price           NUMERIC(12, 2),
    category        VARCHAR(100),
    is_active       BOOLEAN        NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE recipes (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_item_id    UUID        NOT NULL REFERENCES menu_items(id),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    servings        INTEGER     NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recipe_items (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id   UUID           NOT NULL REFERENCES recipes(id),
    product_id  UUID           NOT NULL REFERENCES products(id),
    quantity    NUMERIC(12, 3) NOT NULL,
    unit_id     UUID           NOT NULL REFERENCES units(id)
);

CREATE TABLE sales (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID           NOT NULL REFERENCES restaurants(id),
    user_id         UUID           REFERENCES users(id),  -- NULL si viene del POS automáticamente
    total_amount    NUMERIC(12, 2) NOT NULL,
    source          VARCHAR(10)    NOT NULL DEFAULT 'manual'
                        CHECK (source IN ('manual', 'pos')),
    pos_sync_log_id UUID,          -- FK a pos_sync_logs agregada abajo con ALTER TABLE
    notes           TEXT,
    sold_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE sale_items (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id         UUID           NOT NULL REFERENCES sales(id),
    menu_item_id    UUID           NOT NULL REFERENCES menu_items(id),
    quantity        INTEGER        NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL
);

-- ============================================================
-- INTEGRACIÓN POS (vacías hasta Fase 5 — Q1 2027)
-- ============================================================

CREATE TABLE pos_integrations (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id       UUID        NOT NULL REFERENCES restaurants(id),
    provider            VARCHAR(20)  NOT NULL
                            CHECK (provider IN ('fudo', 'maxirest', 'other')),
    api_key_encrypted   TEXT,                  -- cifrado en capa de aplicación, nunca en claro
    webhook_secret      VARCHAR(255),
    is_active           BOOLEAN      NOT NULL DEFAULT false,
    config_json         JSONB,                 -- { base_url, account_id, branch_id, ... }
    last_sync_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (restaurant_id, provider)           -- un solo proveedor por tipo por restaurante
);

CREATE TABLE pos_item_mappings (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id       UUID        NOT NULL REFERENCES restaurants(id),
    pos_integration_id  UUID        NOT NULL REFERENCES pos_integrations(id),
    pos_item_id         VARCHAR(100) NOT NULL,  -- ID del plato tal cual viene del POS
    pos_item_name       VARCHAR(255) NOT NULL,  -- nombre tal cual viene del POS
    menu_item_id        UUID        REFERENCES menu_items(id),  -- NULL hasta que el manager lo mapee
    is_mapped           BOOLEAN      NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (pos_integration_id, pos_item_id)
);

CREATE TABLE pos_sync_logs (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id       UUID        NOT NULL REFERENCES restaurants(id),
    pos_integration_id  UUID        NOT NULL REFERENCES pos_integrations(id),
    sync_type           VARCHAR(10)  NOT NULL CHECK (sync_type IN ('webhook', 'poll')),
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('success', 'error', 'partial')),
    records_imported    INTEGER      NOT NULL DEFAULT 0,
    error_message       TEXT,
    synced_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- FK diferida: sales → pos_sync_logs (pos_sync_logs se crea después de sales)
ALTER TABLE sales
    ADD CONSTRAINT fk_sales_pos_sync_log
    FOREIGN KEY (pos_sync_log_id) REFERENCES pos_sync_logs(id);

-- ============================================================
-- REPORTES
-- ============================================================

CREATE TABLE reports (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
    type            VARCHAR(30)  NOT NULL,
    period_start    TIMESTAMPTZ  NOT NULL,
    period_end      TIMESTAMPTZ  NOT NULL,
    data_json       JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- AUDITORÍA
-- ============================================================

CREATE TABLE audit_log (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID,                                   -- NULL si acción del sistema
    actor_email     VARCHAR(255),                           -- desnormalizado: sobrevive al borrado del usuario
    restaurant_id   UUID,                                   -- tenant al momento de la acción
    action          VARCHAR(100)  NOT NULL,                 -- AuditAction enum
    entity_type     VARCHAR(100),                           -- 'StockMovement', 'User', etc.
    entity_id       UUID,
    payload         JSONB,                                  -- snapshot relevante de la acción
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ÍNDICES
-- ============================================================

CREATE INDEX ON audit_log (actor_id, created_at DESC);
CREATE INDEX ON audit_log (restaurant_id, created_at DESC);
CREATE INDEX ON audit_log (entity_type, entity_id);

CREATE INDEX ON stock_movements (restaurant_id, product_id);
CREATE INDEX ON stock_movements (created_at DESC);
CREATE INDEX ON stock_batches (restaurant_id, expiration_date);
CREATE INDEX ON alerts (restaurant_id, is_read);
CREATE INDEX ON ai_interpretations (restaurant_id, created_at DESC);
CREATE INDEX ON user_restaurants (user_id);
CREATE INDEX ON user_restaurants (restaurant_id);
CREATE INDEX ON users (phone);
CREATE INDEX ON products (restaurant_id);
CREATE INDEX ON product_categories (restaurant_id);
CREATE INDEX ON suppliers (restaurant_id);
CREATE INDEX ON purchases (restaurant_id);
CREATE INDEX ON messages (restaurant_id, created_at DESC);
CREATE INDEX ON messages (user_id);
CREATE INDEX ON waste_events (restaurant_id);
-- whatsapp_sessions(user_id): cubierto por el UNIQUE del DDL de la tabla
