-- C-03 (D10): el login ignora mayúsculas, así que la identidad también debe hacerlo en la base.
-- El constraint case-sensitive de V2 (odontologo_email_uk) se mantiene; este lo complementa.
CREATE UNIQUE INDEX odontologo_email_sin_mayusculas_uq ON odontologo (lower(email));
