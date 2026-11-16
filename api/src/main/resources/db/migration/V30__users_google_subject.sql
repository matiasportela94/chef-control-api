-- "Acceder con Google": el claim "sub" del ID token identifica a la cuenta de Google para siempre; el email
-- puede cambiar del lado de Google, el sub no. Nulo para quien entra solo con contraseña.
ALTER TABLE users ADD COLUMN google_subject VARCHAR(255) UNIQUE;
