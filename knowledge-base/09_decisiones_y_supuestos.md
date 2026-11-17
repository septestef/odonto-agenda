# Decisiones y Supuestos

## Decisiones documentadas

### DD-01 — Modelo multi-odontólogo desde la v0.1
**Decisión**: cada odontólogo es una cuenta aislada con su propio `slug` público. Todas las tablas de negocio llevan `odontologo_id`.
**Contexto**: el producto es una suscripción por odontólogo, pero la demo usa uno solo.
**Alternativas consideradas**: una instancia por odontólogo; multi-tenant con un esquema de base por cliente.
**Justificación**: el costo extra hoy es mínimo (una columna y un filtro) y evita re-modelar al vender.
**Trade-offs aceptados**: todo servicio tiene que filtrar por odontólogo. Se mitiga con tests de aislamiento.

### DD-02 — El paciente no tiene cuenta: se identifica con DNI y un link único
**Decisión**: el paciente reserva con DNI y datos de contacto, y gestiona su turno con un token enviado por email.
**Contexto**: en Discovery se vio que el mercado permite reservar sin registro, y el usuario lo pidió explícitamente.
**Alternativas consideradas**: cuenta con email y contraseña; código por SMS (es pago).
**Justificación**: menos fricción para el paciente y nada de gestión de contraseñas de pacientes.
**Trade-offs aceptados**: el DNI no se verifica, así que alguien podría reservar a nombre de otro. Se mitiga con el link enviado solo al email cargado y el límite de 2 turnos futuros por DNI.

### DD-03 — Anti-solapamiento con una exclusion constraint de PostgreSQL
**Decisión**: `EXCLUDE USING gist (odontologo_id WITH =, tstzrange(inicio, fin) WITH &&) WHERE estado activo`.
**Contexto**: RN-TU-01 es la regla central y tiene que resistir reservas concurrentes.
**Alternativas consideradas**: verificar en Java antes de insertar (tiene condición de carrera); `SELECT ... FOR UPDATE` sobre el odontólogo (serializa todo y es fácil de olvidar); aislamiento serializable (reintentos complejos).
**Justificación**: la base garantiza la regla siempre, con cualquier código. El servicio igual valida antes, para dar mensajes claros.
**Trade-offs aceptados**: ata el sistema a PostgreSQL (ya es una restricción del proyecto) y descarta H2 para los tests.

### DD-04 — Sesión de servidor en lugar de JWT
**Decisión**: Spring Security con `HttpSession` y CSRF por cookie.
**Contexto**: el frontend se sirve desde el mismo origen que la API.
**Alternativas consideradas**: JWT en `localStorage` (vulnerable a XSS); JWT en cookie (con todo el mismo trabajo de CSRF).
**Justificación**: la sesión es la opción más simple y más segura en un mismo origen, y permite logout real.
**Trade-offs aceptados**: la sesión vive en memoria; si se escala horizontalmente hará falta Spring Session con JDBC.

### DD-05 — Vite para desarrollo y frontend servido por Spring en la demo
**Decisión**: TypeScript vanilla compilado con Vite; el build se copia a los recursos estáticos de Spring.
**Justificación**: buena experiencia de desarrollo (recarga en caliente, proxy a la API) y un solo proceso para la demo, sin CORS.

### DD-06 — Emails por Gmail SMTP con contraseña de aplicación
**Decisión**: Spring Mail contra `smtp.gmail.com:587`.
**Contexto**: presupuesto cero y el usuario quiere que en la demo lleguen emails reales.
**Trade-offs aceptados**: Gmail limita a unos 500 envíos por día y los emails pueden caer en spam. Para producción hay que evaluar un proveedor transaccional.

### DD-07 — Flyway para las migraciones
**Decisión**: el esquema se versiona con Flyway, con `ddl-auto=validate` en Hibernate.
**Justificación**: la exclusion constraint y la extensión `btree_gist` no se pueden expresar con anotaciones JPA. Además, la mantenibilidad es la prioridad.

### DD-08 — La mantenibilidad manda sobre el alcance
**Decisión**: ante la falta de tiempo se recorta alcance (las historias S y C), no calidad. No se negocian los tests de reglas y de concurrencia ni la estructura modular.
**Contexto**: el usuario eligió mantenibilidad sobre velocidad de entrega.

### DD-09 — Los emails se envían después del commit
**Decisión**: las notificaciones salen en `@TransactionalEventListener(phase = AFTER_COMMIT)`.
**Justificación**: no se manda un email por un turno que terminó en rollback, y un fallo del SMTP no revierte el turno (RN-NO-04).

## Supuestos inferidos

### SU-01 — El problema es real y el precio es aceptable
**Supuesto**: odontólogos independientes pagarían entre $25.000 y $35.000 ARS por mes.
**Origen**: Discovery (relevamiento de precios de la competencia más la estimación del usuario).
**Riesgo si es falso**: no hay negocio.
**Cómo validar**: después de la demo, hacer entrevistas o una prueba con 5 a 10 odontólogos.

### SU-02 — Pasos de 15 minutos y duraciones múltiplo de 15
**Origen**: práctica habitual en consultorios; no lo definió el usuario.
**Riesgo si es falso**: prestaciones de 20 minutos que no entran.
**Cómo validar**: confirmarlo con el usuario; es un cambio de configuración.

### SU-03 — Un DNI repetido actualiza los datos de contacto
**Origen**: decisión de simplicidad tomada en la KB.
**Riesgo si es falso**: alguien con el DNI de otro podría cambiar su email de contacto. Los turnos ya creados no se ven afectados, porque cada token se envió al email vigente cuando se creó.
**Cómo validar**: revisarlo antes de producción; la alternativa es no sobrescribir y avisar al odontólogo.

### SU-04 — Horizonte de reserva de 60 días
**Origen**: valor por defecto razonable; el usuario no lo definió (PA-01).
**Riesgo si es falso**: es bajo, porque es configurable.

### SU-05 — Las cuentas de odontólogo se crean por seed en la v0.1
**Origen**: el alta self-service y el cobro de la suscripción quedan fuera de alcance para la demo.
**Riesgo si es falso**: que la demo necesite mostrar el alta de un odontólogo. En ese caso sería una historia nueva.

### SU-06 — El odontólogo puede cargar turnos manuales sin la regla de 24 h
**Origen**: la regla de 24 h se definió para la reserva online del paciente.
**Cómo validar**: confirmarlo con el usuario.
