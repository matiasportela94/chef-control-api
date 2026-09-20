-- ============================================================
-- V16: borrado duro de una cuenta entera.
--
-- Al borrar la cuenta se borran tambien los usuarios que quedan sin ninguna
-- membresia (ver AccountRepository.deleteWithAllData). password_reset_tokens
-- es lo unico que cuelga de users y NO cuelga de ningun restaurante, asi que
-- es la unica FK que falta poner en cascada para que ese borrado no falle.
-- ============================================================

ALTER TABLE password_reset_tokens
    DROP CONSTRAINT password_reset_tokens_user_id_fkey,
    ADD CONSTRAINT password_reset_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
