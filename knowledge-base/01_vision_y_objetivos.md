# Visión y Objetivos

## Propósito del sistema

**turnotardeutn permite que un odontólogo independiente publique su agenda y que sus pacientes reserven, cancelen o reprogramen turnos online sin crear una cuenta y sin que existan superposiciones.**

Hoy el odontólogo independiente en Argentina coordina sus turnos por WhatsApp, teléfono o papel. Eso le genera superposiciones, ausencias y tiempo perdido respondiendo mensajes. Las soluciones del mercado (ver `discovery/research-report.md`) suelen ser caras, ocultan el precio o están pensadas para clínicas con varios profesionales. turnotardeutn apunta al profesional que trabaja solo, con una suscripción mensual fija y precio público.

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|---|---|---|
| Odontólogo | Tener una agenda sin superposiciones que se llene sola | Configurar horario, bloqueos y prestaciones; ver la agenda del día y de la semana; recordar turnos por email o WhatsApp |
| Paciente | Sacar turno en minutos, a cualquier hora, sin llamar ni registrarse | Cancelar o reprogramar desde el link que recibe por email |

## Alcance v0.1 (demo del 2026-11-25)

- Login del odontólogo. Las cuentas se crean por seed y el modelo de datos ya admite varios odontólogos.
- Configuración del horario de atención semanal, con varias franjas por día.
- Bloqueos de agenda (vacaciones, trámites) por rango de fecha y hora.
- Prestaciones con duración propia (consulta, limpieza, extracción, etc.).
- Página pública de reserva por odontólogo (`/reservar/{slug}`) que muestra solo horarios realmente libres.
- Reserva del paciente sin cuenta, con DNI, nombre, apellido, teléfono y email.
- Email de confirmación de la reserva, con un link único para gestionar el turno.
- Cancelación y reprogramación del turno por el paciente, desde ese link.
- Anti-solapamiento estricto, también frente a reservas concurrentes.
- Agenda del odontólogo en vista diaria y semanal.
- Recordatorio automático por email y botón manual de WhatsApp (`wa.me`) por turno.
- Cancelación de un turno por el odontólogo, con aviso por email al paciente.

## Fuera de alcance

- Registro de pacientes con cuenta y contraseña.
- Alta self-service de odontólogos y cobro de la suscripción.
- Historia clínica y odontograma (forman parte del producto comercial, no de la demo).
- Lista de espera, señas con Mercado Pago y controles periódicos (recall).
- Caja, reportes, obras sociales y prepagas, facturación ARCA.
- Varios profesionales por consultorio, sillones o sucursales.
- WhatsApp Business API (es paga).
- Deploy en la nube: la demo corre en localhost.

## Métricas de éxito

- **Demo:** un paciente reserva, recibe el email, cancela o reprograma, y el odontólogo lo ve en su agenda, todo en vivo y sin errores.
- **Integridad:** cero turnos superpuestos, incluso con dos reservas simultáneas al mismo horario (cubierto por un test).
- **Producto, después de la demo:** porcentaje de turnos reservados online frente a los cargados a mano, y tasa de ausentismo antes y después.
