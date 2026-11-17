# Actores y Roles

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|---|---|---|
| Odontólogo (`ODONTOLOGO`) | Profesional independiente, dueño de su agenda. En el modelo de datos cada odontólogo es una cuenta aislada del resto | Ingresa con email y contraseña; configura la agenda y gestiona sus turnos |
| Paciente (anónimo) | Persona que reserva un turno. No tiene cuenta | Usa la página pública `/reservar/{slug}` y el link único `/turno/{token}` que recibe por email |
| Sistema (scheduler) | Proceso interno de Spring | Envía los recordatorios por email |

En la v0.1 **no hay rol administrador**: las cuentas de odontólogo se crean por seed (ver `04_modelo_de_datos.md`).

## RBAC — Matriz de permisos

El paciente no es un rol autenticado: lo "autoriza" el `slug` público del odontólogo (para reservar) o el token del turno (para gestionarlo).

| Rol / credencial | Recurso | Permisos |
|---|---|---|
| ODONTOLOGO | Su perfil | Leer |
| ODONTOLOGO | Sus horarios de atención | Leer, reemplazar |
| ODONTOLOGO | Sus bloqueos | Crear, leer, borrar |
| ODONTOLOGO | Sus prestaciones | Crear, leer, actualizar, dar de baja |
| ODONTOLOGO | Sus turnos | Crear (carga manual), leer, cancelar, marcar atendido o ausente |
| ODONTOLOGO | Sus pacientes | Leer (los crea el flujo de reserva) |
| ODONTOLOGO | Recursos de **otro** odontólogo | Ninguno: devuelve 404 |
| Público + `slug` | Perfil público, prestaciones activas, disponibilidad | Leer |
| Público + `slug` | Turno | Crear |
| Token de turno | Ese turno, y nada más | Leer, cancelar, reprogramar |

Regla transversal: toda consulta del odontólogo filtra por `odontologo_id` de la sesión. Nunca se confía en un id de odontólogo que llegue en el request (ver RN-SE-01).

## Rutas públicas

Frontend (SPA):
- `/login`
- `/reservar/{slug}`
- `/turno/{token}`

API:
- `POST /api/auth/login`
- `GET /api/public/{slug}`
- `GET /api/public/{slug}/disponibilidad`
- `POST /api/public/{slug}/turnos`
- `GET|POST /api/public/turnos/{token}/**`

Todo lo demás bajo `/api/**` requiere sesión de ODONTOLOGO.
