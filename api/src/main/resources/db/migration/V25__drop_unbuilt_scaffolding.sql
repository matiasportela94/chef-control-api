-- ============================================================
-- V25: se van las tablas que V1 creó para features que todavía no existen.
--
-- Siete tablas, cero filas, cero entidades JPA que las mapeen: se escribieron en V1 —"schema
-- completo"— con año y medio de anticipación sobre WhatsApp/AI (Fase 3) y POS (Fase 5, Q1 2027).
--
-- Por qué molestan si están vacías: el esquema es documentación. Alguien que lo lee cree que la
-- sincronización con POS existe, y `sales` tenía una columna `pos_sync_log_id` —en una tabla
-- viva, sin mapear— apuntando a ese invento. Además viajan en cada backup y aparecen en cada
-- listado de dependencias.
--
-- Por qué no se pierde nada: el DDL queda en V1 y en el historial de git para siempre. Cuando
-- toque el POS con Fudo/Maxirest, el esquema se va a diseñar contra su API real y estas columnas
-- —que son una adivinanza de 2026— se reescribirían igual. Lo mismo WhatsApp: hay arquitectura
-- escrita en docs/whatsapp-architecture.md, y esa es la fuente de verdad, no esta tabla.
--
-- El orden importa: primero lo que apunta, después lo apuntado. Se evita DROP ... CASCADE a
-- propósito, porque se llevaría en silencio cualquier cosa que no esté en esta lista.
-- ============================================================

-- La única referencia desde una tabla viva.
ALTER TABLE sales DROP COLUMN IF EXISTS pos_sync_log_id;

-- AI (Fase 3): ai_interpretations referencia a messages.
DROP TABLE IF EXISTS ai_interpretations;
DROP TABLE IF EXISTS messages;

-- POS (Fase 5): mappings y logs referencian a integrations.
DROP TABLE IF EXISTS pos_item_mappings;
DROP TABLE IF EXISTS pos_sync_logs;
DROP TABLE IF EXISTS pos_integrations;

DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS whatsapp_sessions;
