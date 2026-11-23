# Spec Delta — US-014: Recordatorio automático por email

## ADDED Requirements

### Requirement: US-014 — Selección de recordatorios en una ventana de 15 minutos

El job MUST correr cada 15 minutos y calcular la ventana inclusiva `[ahora + 23h45m, ahora + 24h]` con el reloj UTC del servidor. Solo MUST considerar turnos `RESERVADO` con `recordatorio_enviado_en` nulo y sin intento durable previamente tomado. Para un turno `MANUAL`, si `inicio - creado_en < 24h`, MUST excluirlo aun dentro de la ventana (RN-NO-03). Una corrida perdida MUST NOT ensanchar la ventana ni recuperar turnos que ya salieron de ella.

#### Scenario: Bordes con reloj fijo
- **WHEN** una corrida lee cinco turnos por lo demás elegibles con inicio inmediatamente antes del mínimo, exactamente en el mínimo, en el interior, exactamente en el máximo e inmediatamente después del máximo
- **THEN** solo admite los tres del intervalo cerrado; un borde compartido con la siguiente corrida no habilita un segundo intento

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
- **THEN** solo el snapshot recibe el HTML con horario local, prestación y link; sin sesión, ambos links resuelven el mismo turno y la gestión aplica los controles vigentes, incluidas las reglas del destino al reprogramar

#### Scenario: Legado sin destinatario admisible
- **WHEN** un turno de legado o seed tiene snapshot nulo o no admisible, aunque `paciente.email` sea válido
- **THEN** no hay SMTP, marca de envío ni fila de notificación para un destinatario sustituto; solo puede quedar un diagnóstico sanitizado

#### Scenario: Gestión del original después de una reprogramación
- **WHEN** se reprograma con éxito usando uno de los links del original
- **THEN** ninguno de esos links permite mutar o identificar al sucesor; solo el link del sucesor lo autoriza según C-09, y el original conserva el contrato de solo lectura

### Requirement: US-014 — Intento acotado y resultado independiente del turno

Para un turno elegible, el sistema MUST admitir como máximo un intento de despacho durable por turno, aun con corridas concurrentes o reinicios, y MUST NOT prometer entrega ni reintentar automáticamente tras incertidumbre. La toma MUST comprobar nuevamente elegibilidad con datos confirmados. El SMTP MUST ocurrir después de liberar los locks de base. Con un resultado observable, MUST intentar registrar `RECORDATORIO` con `ENVIADO` si SMTP acepta, o `ERROR` y categoría sanitizada si falla (incluido timeout). Ningún resultado de transporte o auditoría MUST revertir el turno, cambiar su ocupación ni generar un aviso de C-08 ajeno a esta historia.

#### Scenario: Doble corrida y reinicio
- **WHEN** dos corridas con conexiones distintas compiten por el mismo turno y luego se reinicia la aplicación dentro de la ventana
- **THEN** hay una sola toma durable y como máximo una llamada SMTP; si ya fue aceptado y se registró el resultado normalmente hay una sola fila `RECORDATORIO/ENVIADO`

#### Scenario: Falla SMTP y timeout incierto
- **WHEN** el transporte devuelve una falla o un timeout de aceptación incierta y la base está disponible
- **THEN** queda `RECORDATORIO/ERROR` con `SMTP_FALLO` sin datos sensibles en el error; no se reenvía y el estado, horario y ocupación siguen intactos

#### Scenario: Caída después de tomar o falla al registrar
- **WHEN** el proceso cae después de la toma confirmada pero antes de SMTP, o SMTP acepta y luego falla el registro
- **THEN** el sistema no vuelve a intentar SMTP al reiniciar, no afirma entrega ni inventa una fila de resultado y conserva intacto el turno; el primer caso puede perder el envío

#### Scenario: Cancelación o reprogramación gana la toma
- **WHEN** C-09 o C-12 confirma la cancelación del original antes de que el recordatorio revalide su elegibilidad
- **THEN** no se toma ni se despacha el recordatorio del original; el eventual sucesor se evalúa de forma independiente y se preserva el contrato de concurrencia de reservas
