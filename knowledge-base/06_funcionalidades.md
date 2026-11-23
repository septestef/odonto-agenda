# Funcionalidades

Organizadas por épica y luego por historia de usuario (US-NNN). Prioridad MoSCoW pensada para la demo del 2026-11-25: **M** = imprescindible, **S** = debería entrar, **C** = solo si sobra tiempo.

## Épica 1: Acceso del odontólogo

### US-001 — Iniciar sesión (M)
**Como** odontólogo **quiero** ingresar con mi email y contraseña **para** gestionar mi agenda.
- [ ] Con credenciales válidas se crea la sesión y se redirige a `/agenda`.
- [ ] Con credenciales inválidas aparece un mensaje genérico, que no revela si el email existe.
- [ ] Tras 5 intentos fallidos, el login se bloquea 15 minutos.
- [ ] Cerrar sesión invalida la sesión en el servidor.

**Reglas**: RN-AU-01..04

## Épica 2: Configuración de la agenda

### US-002 — Definir el horario de atención (M)
**Como** odontólogo **quiero** cargar mis franjas por día de la semana **para** que solo se ofrezcan esos horarios.
- [ ] Puedo cargar varias franjas por día y dejar días sin atención.
- [ ] Se rechazan las franjas solapadas y las que tienen el inicio después del fin.

**Reglas**: RN-AG-01, RN-AG-06

### US-003 — Gestionar prestaciones (M)
**Como** odontólogo **quiero** crear prestaciones con su duración **para** que cada turno ocupe el tiempo real.
- [ ] Crear, editar y dar de baja; la duración es múltiplo de 15.
- [ ] Una prestación dada de baja no aparece en la reserva online.

**Reglas**: RN-AG-03, RN-AG-04, RN-TU-02

### US-004 — Bloquear la agenda (M)
**Como** odontólogo **quiero** bloquear un rango de fechas y horas **para** no recibir turnos cuando no atiendo.
- [ ] El rango bloqueado desaparece de la disponibilidad pública.
- [ ] Si hay turnos en el rango, el sistema los lista y me deja cancelarlos.

**Reglas**: RN-AG-02, RN-TU-09

## Épica 3: Reserva online del paciente

### US-005 — Ver los horarios libres (M)
**Como** paciente **quiero** elegir una prestación y una fecha y ver los horarios libres **para** sacar turno sin llamar.
- [ ] Solo se muestran horarios dentro del horario de atención, fuera de bloqueos y turnos, con al menos 24 h de anticipación y hasta 60 días hacia adelante.
- [ ] El turno completo entra en la franja (no cruza el cierre).
- [ ] Funciona bien en un celular de 360 px de ancho.

**Reglas**: RN-AG-05, RN-TU-03, RN-TU-11, RN-SE-03

### US-006 — Reservar con DNI (M)
**Como** paciente **quiero** reservar con mi DNI y mis datos de contacto **para** no tener que crear una cuenta.
- [ ] Pide DNI, nombre, apellido, teléfono y email, todos validados.
- [ ] Al confirmar, el turno queda `RESERVADO` y veo una pantalla de éxito.
- [ ] Si el horario se ocupó mientras completaba el formulario, veo "Ese horario acaba de ser tomado, elegí otro" y la disponibilidad se refresca.
- [ ] No puedo tener más de 2 turnos futuros con el mismo odontólogo.

**Reglas**: RN-TU-01, RN-TU-03, RN-TU-04, RN-PA-01..04, RN-GL-01

### US-007 — Recibir la confirmación por email (M)
**Como** paciente **quiero** recibir un email con los datos del turno y un link **para** poder gestionarlo.
- [ ] El email incluye fecha, hora, prestación, dirección del consultorio y el link `/turno/{token}`.
- [ ] Si el envío falla, el turno igual queda reservado y el error queda registrado.

**Reglas**: RN-NO-01, RN-NO-04, RN-SE-02

## Épica 4: Gestión del turno por el paciente

### US-008 — Cancelar mi turno (M)
**Como** paciente **quiero** cancelar desde el link **para** liberar el horario.
- [ ] Se puede cancelar en cualquier momento antes del inicio, con una confirmación previa.
- [ ] El horario vuelve a estar disponible de inmediato y recibo un email.
- [ ] Un turno cancelado o pasado se muestra en solo lectura.

**Reglas**: RN-TU-05, RN-TU-07, RN-NO-02, RN-SE-02

### US-009 — Reprogramar mi turno (M)
**Como** paciente **quiero** elegir otro horario desde el link **para** no tener que cancelar y reservar de nuevo.
- [ ] Veo la disponibilidad para la misma prestación.
- [ ] El cambio es atómico: si falla, conservo mi turno original.
- [ ] Recibo un email con el nuevo horario y un link nuevo.

**Reglas**: RN-TU-06, RN-NO-02

## Épica 5: Agenda del odontólogo

### US-010 — Ver la agenda diaria y semanal (M)
**Como** odontólogo **quiero** ver mis turnos por día y por semana **para** organizarme.
- [ ] Vista diaria y semanal, con navegación anterior/siguiente y botón "hoy".
- [ ] Cada turno muestra paciente, prestación, horario, estado y origen. Se ven los bloqueos y el horario de atención.

### US-011 — Cargar un turno manual (S)
**Como** odontólogo **quiero** cargar un turno para un paciente que me llamó **para** tener toda la agenda en un solo lugar.
- [ ] Mismo formulario de paciente; sin la regla de 24 h; con anti-solapamiento.
- [ ] El paciente recibe el email de confirmación con su link.

**Reglas**: RN-TU-01, RN-TU-08

### US-012 — Cancelar un turno como odontólogo (M)
- [ ] Al cancelar, el horario se libera y el paciente recibe un aviso por email.

**Reglas**: RN-TU-09, RN-NO-02

### US-013 — Marcar atendido o ausente (C)
- [ ] Solo para turnos `RESERVADO` cuyo inicio ya pasó.

**Reglas**: RN-TU-10

## Épica 6: Recordatorios

### US-014 — Recordatorio automático por email (S)
- [ ] Un job cada 15 minutos envía un recordatorio a los turnos que empiezan en unas 24 h, una sola vez por turno.
- [ ] El email incluye el link para cancelar o reprogramar.

**Reglas**: RN-NO-03, RN-NO-04

### US-015 — Botón de WhatsApp (S)
**Como** odontólogo **quiero** un botón que abra WhatsApp con el recordatorio ya escrito **para** avisarle al paciente sin tipear.
- [ ] Abre `wa.me/{telefono}?text=...` en una pestaña nueva, con los datos del turno.

**Reglas**: RN-NO-05, RN-NO-06, RN-NO-07
