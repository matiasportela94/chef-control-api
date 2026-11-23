-- El email se guarda sin mayúsculas ni espacios (User.normalizeEmail): con "Juan@Gmail.com" y "juan@gmail.com" había dos
-- usuarios para la misma persona. Con el CHECK, el UNIQUE de V1 queda sin distinguir mayúsculas. Falla si ya hay dos
-- emails que solo difieren en eso: hay que unificarlos a mano antes (en staging y en la base local no había, 2026-11-23).
UPDATE users SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));
ALTER TABLE users ADD CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email)));
