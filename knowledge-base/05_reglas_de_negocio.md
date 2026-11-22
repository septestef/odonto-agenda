# Reglas de Negocio

Cada regla tiene un código único `RN-{DOMINIO}-{NN}`. El backend devuelve ese código en el campo `codigo` del error cuando una regla rechaza una operación.

## Dominio: Autenticación (RN-AU)

- **RN-AU-01**: Solo el odontólogo inicia sesión, con email y contraseña. El paciente nunca tiene cuenta.
- **RN-AU-02**: Las contraseñas se guardan con BCrypt; nunca en claro ni en logs.
- **RN-AU-03**: Después de 5 intentos fallidos seguidos para un mismo email, el login se bloquea 15 minutos. Es la mitigación mínima contra fuerza bruta.
- **RN-AU-04**: La sesión expira tras 8 horas de inactividad.

## Dominio: Agenda (RN-AG)

- **RN-AG-01**: El horario de atención es semanal y recurrente, con una o más franjas por día que no pueden solaparse entre sí.
- **RN-AG-02**: Un bloqueo anula la disponibilidad en su rango. Crear un bloqueo **no** cancela los turnos que ya existen en ese rango: el sistema los lista y el odontólogo decide si cancelarlos.
- **RN-AG-03**: Las prestaciones duran un múltiplo de 15 minutos.
- **RN-AG-04**: Dar de baja una prestación la saca de la reserva online, pero los turnos existentes la conservan.
- **RN-AG-05**: Los horarios disponibles se ofrecen en pasos de 15 minutos dentro de cada franja. Un turno debe entrar completo en una franja: no puede cruzar el cierre ni un bloqueo.
- **RN-AG-06**: Todas las fechas se interpretan en la zona horaria `America/Argentina/Buenos_Aires`.

## Dominio: Turnos (RN-TU)

- **RN-TU-01**: Nunca puede haber dos turnos superpuestos del mismo odontólogo, ni siquiera forzados por él (no hay sobreturnos). Los turnos cancelados no ocupan lugar. Se garantiza en la base con una exclusion constraint, no solo en el código.
- **RN-TU-02**: La duración del turno es la de la prestación elegida, fijada al momento de reservar. Si después cambia la duración de la prestación, el turno no se modifica.
- **RN-TU-03**: El paciente puede reservar solo con al menos 24 h de anticipación (`inicio >= ahora + 24h`). Una reserva para dentro de menos de 24 h se rechaza.
- **RN-TU-04**: La reserva online queda en estado `RESERVADO` al instante. No requiere confirmación.
- **RN-TU-05**: El paciente puede cancelar su turno en cualquier momento antes del inicio. El horario se libera de inmediato.
- **RN-TU-06**: Reprogramar es cancelar el turno actual y crear uno nuevo en **una sola transacción**: si el turno nuevo falla, el original sigue intacto. El turno nuevo cumple RN-TU-03 y queda enlazado al anterior (`reprogramado_desde_id`).
- **RN-TU-07**: No se puede reservar, cancelar ni reprogramar un turno cuyo inicio ya pasó.
- **RN-TU-08**: El odontólogo puede cargar un turno manual para cualquier horario futuro dentro de su agenda; para él no aplica la regla de 24 h. Sí aplica RN-TU-01.
- **RN-TU-09**: El odontólogo puede cancelar cualquier turno futuro. El paciente recibe un email de aviso.
- **RN-TU-10**: Solo un turno `RESERVADO` cuyo inicio ya pasó puede marcarse `ATENDIDO` o `AUSENTE`.
- **RN-TU-11**: Se puede reservar hasta 60 días hacia adelante (valor por defecto, configurable por propiedad). Ver la pregunta abierta PA-01.

## Dominio: Pacientes (RN-PA)

- **RN-PA-01**: El paciente se identifica con el DNI (7 u 8 dígitos) dentro de cada odontólogo. El mismo DNI con dos odontólogos distintos son dos pacientes distintos.
- **RN-PA-02**: Si alguien reserva con un DNI ya existente, se actualizan nombre, teléfono y email con lo último ingresado. Es la opción más simple; queda registrada como supuesto SU-03.
- **RN-PA-03**: Un mismo DNI no puede tener más de 2 turnos futuros en estado `RESERVADO` con el mismo odontólogo. Limita el abuso, porque el DNI no se verifica.
- **RN-PA-04**: Nombre, apellido, DNI, teléfono y email son obligatorios. El email y el teléfono se validan por formato.

## Dominio: Notificaciones (RN-NO)

- **RN-NO-01**: Al reservar, el paciente recibe un email de confirmación con el link único `/turno/{token}`.
- **RN-NO-02**: Cancelar o reprogramar dispara un email al paciente.
- **RN-NO-03**: Cada turno `RESERVADO` recibe un recordatorio por email una sola vez, unas 24 h antes del inicio. Un job corre cada 15 minutos y marca `recordatorio_enviado_en`.
- **RN-NO-04**: Si el envío de un email falla, el turno **no** se revierte. El error queda en `notificacion`. Un turno vale aunque el email no llegue.
- **RN-NO-05**: El botón de WhatsApp arma `https://wa.me/{telefono}?text={mensaje}` con fecha, hora, prestación y dirección del consultorio. El envío es manual: lo hace el odontólogo.

## Dominio: Seguridad (RN-SE)

- **RN-SE-01**: El odontólogo solo ve y modifica sus propios datos. El `odontologo_id` sale siempre de la sesión. Pedir un recurso ajeno devuelve 404, no 403, para no confirmar que existe.
- **RN-SE-02**: El token de gestión del turno tiene 32 bytes aleatorios (codificados en base64url). En la base se guarda solo su hash SHA-256. Deja de servir cuando el turno pasa o se cancela; en ese caso el link muestra el turno en solo lectura.
- **RN-SE-03**: Las respuestas públicas nunca exponen datos de otros pacientes. La disponibilidad devuelve horarios libres, no turnos.

### Propuesta para C-09 (pendiente de aprobación de Estefi; no vigente)

- **RN-SE-04 (propuesta)**: En gestión por token, si el turno no tiene un snapshot de email de gestión o ese snapshot no es un único buzón admisible según el criterio vigente de C-08, se rechaza reprogramar antes de escribir o generar un token nuevo. Respuesta: HTTP 409, `{ "codigo": "RN-SE-04", "mensaje": "No podés reprogramar este turno desde el link." }`. No se usa otro destinatario ni se cambia la validación de reserva o admisión de correo de C-07/C-08. Esto no impide consultar ni cancelar un turno elegible; en ese caso la cancelación no envía correo ni genera auditoría del aviso.

## Excepciones globales

- **RN-GL-01**: Toda operación que escribe turnos es transaccional. Si la constraint RN-TU-01 se viola, la API responde 409 con el código `RN-TU-01` y el mensaje "Ese horario acaba de ser tomado, elegí otro".
- **RN-GL-02**: Ninguna regla de negocio depende de la hora del cliente: "ahora" lo define el reloj del servidor, inyectado como `Clock` para poder testearlo.
