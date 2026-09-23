-- ============================================================
-- V20: ícono por paso del menú.
--
-- Mismo patrón que product_categories.icon (V8): se guarda el nombre del ícono de Tabler
-- ("salad", "meat", "cake"), no una URL ni un SVG. El color sigue: tinta el ícono y pinta
-- la tarjeta del plato, igual que en /categories.
-- ============================================================

ALTER TABLE menu_sections ADD COLUMN icon VARCHAR(50);
