-- Lo que hay que avisar al deshacer una acción del chat y que el deshacer no arregla solo: deshacer un cierre de caja
-- que reemplazó las ventas del día no las restaura (el ledger es append-only).
ALTER TABLE chat_actions ADD COLUMN undo_note VARCHAR(300);
