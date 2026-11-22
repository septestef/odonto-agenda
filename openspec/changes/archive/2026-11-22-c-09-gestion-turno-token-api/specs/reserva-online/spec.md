# Delta: Reserva online

## MODIFIED Requirements

### Requirement: Reserva exitosa
Una reserva válida MUST crear un turno `RESERVADO` de origen `ONLINE`, con fin igual al inicio más la duración vigente de la prestación (RN-TU-02, RN-TU-04), y MUST responder 201 con inicio, fin, prestación, datos públicos del consultorio y el email al que se enviará el link. MUST guardar en la misma transacción y en el turno el email de esa solicitud como `email_gestion`, sin alterar ni la validación pública, ni el upsert por DNI, ni el cuerpo del 201. Actualizar `paciente.email` más adelante MUST NOT cambiar ese snapshot.

#### Scenario: Reserva de un horario libre
- **WHEN** se reserva "Consulta" el viernes 2026-11-27 a las 09:00 con datos válidos
- **THEN** la API responde 201 con inicio `2026-11-27T09:00:00-03:00` y fin `2026-11-27T09:30:00-03:00`, y en la base queda un turno `RESERVADO`, origen `ONLINE`, de 12:00Z a 12:30Z

#### Scenario: Dos reservas del mismo DNI con emails distintos
- **WHEN** se confirman dos reservas del mismo DNI y consultorio con emails distintos
- **THEN** ambos 201 conservan su estructura y el email que se ingresó en cada solicitud, `paciente.email` se actualiza como antes y cada turno conserva el `email_gestion` de su propia solicitud
