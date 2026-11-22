-- C-09: destinatario estable del link, sin reconstruir el de turnos legados.
ALTER TABLE turno ADD COLUMN email_gestion varchar(255);
