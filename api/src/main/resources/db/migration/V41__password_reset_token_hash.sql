-- ============================================================
-- Chef Control — V41: los tokens de los links de contraseña se guardan hasheados
-- ============================================================
-- La base guarda el SHA-256 (hex) del token del link, nunca el token: un volcado de la base o un log de SQL ya no
-- alcanzan para elegir la contraseña de otro. Los links vigentes dejan de servir (aceptado por Mati, 2026-11-23): quien
-- tenga uno pide otro, y una invitación pendiente se vuelve a mandar.

DELETE FROM password_reset_tokens;

ALTER TABLE password_reset_tokens DROP COLUMN token;
ALTER TABLE password_reset_tokens ADD COLUMN token_hash VARCHAR(64) NOT NULL UNIQUE;
