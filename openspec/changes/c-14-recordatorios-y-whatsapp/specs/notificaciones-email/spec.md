# Spec Delta — US-014: Recordatorio automático por email

## ADDED Requirements

### Requirement: US-014 — Selección de recordatorios en una ventana de 15 minutos

El job MUST dispararse con el cron `0 0/15 * * * *`, no con `fixedDelay` ni `fixedRate`. Cada corrida MUST truncar a 15 minutos el instante del `Clock` UTC inyectado (tick nominal `t`) y usar la ventana inclusiva `[t + 23h45m, t + 24h]`. Solo MUST considerar turnos `RESERVADO` con `recordatorio_enviado_en` nulo y sin otro recordatorio en curso o ya realizado. Para un turno `MANUAL`, si `inicio - creado_en < 24h`, MUST excluirlo aun dentro de la ventana (RN-NO-03). Una corrida perdida MUST NOT ensanchar la ventana ni recuperar turnos que ya salieron de ella.

#### Scenario: Bordes con reloj fijo
- **WHEN** una corrida lee cinco turnos por lo demás elegibles con inicio inmediatamente antes del mínimo, exactamente en el mínimo, en el interior, exactamente en el máximo e inmediatamente después del máximo
- **THEN** solo admite los tres del intervalo cerrado; un borde compartido con la siguiente corrida no habilita un segundo recordatorio

#### Scenario: Corrida demorada
- **WHEN** el tick de las 10:00:00 UTC empieza a ejecutarse a las 10:00:40 y el siguiente a las 10:15:05
- **THEN** sus ventanas son `[09:45, 10:00]` y `[10:00, 10:15]` del día siguiente, calculadas desde el tick nominal; un turno que inicia a las 10:00:20 del día siguiente queda cubierto por la segunda, sin huecos entre corridas

#### Scenario: Estado, marca y alta manual
- **WHEN** la ventana incluye cancelados, atendidos, ausentes, un reservado ya marcado y dos reservados manuales creados respectivamente con 23 h 59 min y exactamente 24 h de anticipación, sin otro impedimento
- **THEN** solo el manual creado con exactamente 24 h puede ser admitido

#### Scenario: Corrida perdida
- **WHEN** la aplicación se reinicia después de una corrida perdida y un turno no procesado ya salió de la ventana
- **THEN** no lo envía tarde ni lo marca como procesado; sigue evaluando normalmente los turnos de la ventana vigente

### Requirement: US-014 — Recordatorio al destinatario congelado con link válido

El recordatorio MUST usar exclusivamente `turno.email_gestion` si es un único buzón admisible según C-08. MUST incluir fecha y hora de Buenos Aires, prestación, dirección si está informada y un link válido de gestión bajo `APP_BASE_URL`. MUST permitir las acciones que correspondan al turno según las reglas vigentes, sin invalidar el link original ni saltarse ninguna validación de consulta, cancelación o reprogramación. El sistema MUST NOT enviar un recordatorio sin link válido ni exponer credenciales en logs, auditoría, respuestas o persistencia en claro.

#### Scenario: Contacto actualizado y link operativo
- **WHEN** el email actual del paciente difiere del snapshot de un turno elegible para gestión y el transporte acepta el recordatorio
- **THEN** solo el snapshot recibe el HTML con horario local, prestación y link; sin sesión, el link del recordatorio resuelve ese turno, el link del email de confirmación sigue funcionando y la gestión aplica los controles vigentes, incluidas las reglas del destino al reprogramar

#### Scenario: Legado sin destinatario admisible
- **WHEN** un turno de legado o seed tiene snapshot nulo o no admisible, aunque `paciente.email` sea válido
- **THEN** no hay SMTP, marca de envío ni fila de notificación para un destinatario sustituto; solo puede quedar un diagnóstico sanitizado

#### Scenario: Gestión del original después de una reprogramación
- **WHEN** se reprograma con éxito usando uno de los links del original
- **THEN** ninguno de esos links permite mutar o identificar al sucesor; solo el link del sucesor lo autoriza según C-09, y el original conserva el contrato de solo lectura

### Requirement: US-014 — Un solo recordatorio y resultado independiente del turno

Para un turno elegible, el sistema MUST NOT despachar más de un recordatorio aceptado por SMTP, aun con corridas concurrentes o reinicios (RN-NO-03), y MUST NOT prometer entrega. Antes de despachar MUST comprobar nuevamente la elegibilidad con datos confirmados. El SMTP MUST ocurrir después de liberar los locks de base. Con un resultado observable, MUST registrar `RECORDATORIO` con `ENVIADO` y marcar `recordatorio_enviado_en` si SMTP acepta, o `ERROR` con categoría sanitizada si falla (RN-NO-04). Ningún resultado de transporte o auditoría MUST revertir el turno ni cambiar su ocupación. La exclusión entre corridas y la política ante fallas o timeouts quedan para el gate 4.1.

#### Scenario: Doble corrida y reinicio
- **WHEN** dos corridas con conexiones distintas compiten por el mismo turno y luego se reinicia la aplicación dentro de la ventana
- **THEN** como máximo un recordatorio es aceptado por SMTP y hay como máximo una fila `RECORDATORIO/ENVIADO` para ese turno

#### Scenario: Falla SMTP
- **WHEN** el transporte devuelve una falla y la base está disponible
- **THEN** queda `RECORDATORIO/ERROR` con `SMTP_FALLO` sin datos sensibles en el error, `recordatorio_enviado_en` sigue nulo y el estado, horario y ocupación del turno siguen intactos

#### Scenario: Cancelación o reprogramación gana
- **WHEN** C-09 o C-12 confirma la cancelación del original antes de que el recordatorio revalide su elegibilidad
- **THEN** no se despacha el recordatorio del original; el eventual sucesor se evalúa de forma independiente y se preserva el contrato de concurrencia de reservas
