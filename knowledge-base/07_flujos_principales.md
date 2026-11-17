# Flujos Principales

## Flujo 1: Login del odontólogo

**Disparador**: el odontólogo abre `/login`. **Actor**: Odontólogo.

1. El frontend envía `POST /api/auth/login` con `{email, password}`.
2. Spring Security valida contra `odontologo.password_hash` (BCrypt) y revisa el contador de intentos (RN-AU-03).
3. Si las credenciales son válidas, se crea una sesión HTTP y se responde con una cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`) más el token CSRF.
4. El frontend pide `GET /api/auth/me` y navega a `/agenda`.

**Casos de error**:
- Credenciales inválidas → 401 con mensaje genérico.
- Login bloqueado → 429 con el tiempo restante.

## Flujo 2: Cálculo de disponibilidad

**Disparador**: el paciente elige prestación y fecha en `/reservar/{slug}`.

1. `GET /api/public/{slug}/disponibilidad?prestacionId=X&fecha=YYYY-MM-DD`.
2. El servicio carga, para ese odontólogo y ese día (en hora de Argentina):
   - las franjas de `horario_atencion` del día de la semana;
   - los `bloqueo` que se cruzan con el día;
   - los `turno` que ocupan lugar (`RESERVADO`, `ATENDIDO`, `AUSENTE`) ese día.
3. Para cada franja genera inicios cada 15 minutos (`t`) y acepta `t` si se cumple todo esto:
   - `t + duración <= fin de la franja`;
   - `t >= ahora + 24h` (RN-TU-03) y `t <= hoy + 60 días` (RN-TU-11);
   - `[t, t + duración)` no se cruza con ningún bloqueo ni turno.
4. Responde la lista de inicios disponibles en hora local. Es una función pura, testeable con un `Clock` fijo.

```
Paciente → SPA → GET disponibilidad → DisponibilidadService
                                       ├─ HorarioRepository
                                       ├─ BloqueoRepository
                                       └─ TurnoRepository
                                     ← [09:00, 09:15, ...]
```

## Flujo 3: Reserva online

**Disparador**: el paciente confirma un horario. **Actor**: Paciente.

1. `POST /api/public/{slug}/turnos` con `{prestacionId, inicio, dni, nombre, apellido, telefono, email}`.
2. Se validan formato y obligatorios (RN-PA-04), que la prestación esté activa y que el inicio cumpla RN-TU-03, RN-TU-11 y RN-AG-05 (dentro de una franja y fuera de bloqueos).
3. En la transacción:
   1. Upsert del paciente por (`odontologo_id`, `dni`) (RN-PA-02).
   2. Verificación del límite de 2 turnos futuros (RN-PA-03).
   3. Generación del token; se guarda `token_hash`.
   4. `INSERT turno` con estado `RESERVADO` y origen `ONLINE`.
4. Commit. Si la base lanza la violación de `turno_sin_solapamiento`, la API responde 409 con el código `RN-TU-01`.
5. Después del commit (`@TransactionalEventListener(AFTER_COMMIT)`), se envía el email de confirmación con el link `/turno/{token}` y se registra en `notificacion`.
6. Responde 201. El token en claro solo viaja en el email, nunca en la respuesta: así se asegura que solo el dueño del email pueda gestionar el turno.

**Casos de error**:
- Dos pacientes reservan el mismo horario a la vez → el primero en hacer commit gana; el segundo recibe 409.
- Falla el SMTP → el turno queda reservado y la notificación queda en estado `ERROR` (RN-NO-04).

## Flujo 4: Cancelar o reprogramar desde el link

**Disparador**: el paciente abre `/turno/{token}`.

1. `GET /api/public/turnos/{token}` → se busca por `sha256(token)` y se muestra el turno. Si ya pasó o está cancelado, se muestra en solo lectura.
2. **Cancelar**: `POST .../cancelar` → estado `CANCELADO`, `cancelado_por = PACIENTE`; después del commit sale el email.
3. **Reprogramar**: el paciente ve la disponibilidad de la misma prestación y envía `POST .../reprogramar {inicio}`. En una transacción:
   1. Se cancela el turno original.
   2. Se inserta el turno nuevo con `reprogramado_desde_id` y un token nuevo.
   3. Si el insert viola la constraint, se hace rollback completo y el original sigue `RESERVADO`.
   4. Después del commit sale el email con el link nuevo.

**Casos de error**: token inexistente → 404; turno pasado → 409 `RN-TU-07`; horario ocupado → 409 `RN-TU-01`.

## Flujo 5: Recordatorios

**Disparador**: `@Scheduled` cada 15 minutos.

1. Busca los turnos `RESERVADO` con `inicio` entre `ahora + 23h45m` y `ahora + 24h` y con `recordatorio_enviado_en IS NULL`. Como nadie puede reservar con menos de 24 h, todo turno online entra en alguna ventana. Los turnos manuales cargados con menos de 24 h no reciben recordatorio automático: el odontólogo puede usar el botón de WhatsApp.
2. Para cada uno envía el email, marca `recordatorio_enviado_en` y registra la notificación.
3. Es idempotente: si el job se reinicia, no duplica envíos.

## Flujo 6: Recordatorio por WhatsApp

1. En la agenda, el odontólogo toca "WhatsApp" sobre un turno.
2. `GET /api/turnos/{id}/whatsapp` devuelve la URL `https://wa.me/{telefono}?text={mensaje codificado}`.
3. El frontend la abre en una pestaña nueva y el odontólogo envía el mensaje a mano.
