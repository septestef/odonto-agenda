# Design

## Context

Este diseño viene de C-14 (`recordatorios-y-whatsapp`), donde US-014 quedó diferida el 2026-11-23. C-14 se archivó con US-015 implementada.

C-08 finaliza avisos de reserva, cancelación y reprogramación tras el commit, usando el token efímero del evento cuando corresponde. `ProcesarAviso`, `PlantillasCorreo`, `TransporteCorreo` y `RegistroNotificacion` distinguen preparación, aceptación SMTP y auditoría independiente; el asunto y la plantilla de `RECORDATORIO` aún no están implementados. V2 ya admite el tipo `RECORDATORIO`, guarda `recordatorio_enviado_en` y tiene índice por estado/inicio, pero no registra la toma durable de un intento. C-09 persiste el snapshot `turno.email_gestion` y solo el hash del token.

El flujo vigente de C-11 usa el token del link para consultar, cancelar y reprogramar; relee el original tras un 204 y nunca obtiene el link del sucesor de la API. El cliente publicado (`features/turno/api-gestion.ts`) pre-valida un único segmento ASCII base64url de 43 caracteres antes de consultar.

## Goals / Non-Goals

**Goals:** mantener el recordatorio desacoplado de las escrituras de reserva; conservar el comportamiento de los links ya enviados, la privacidad y la garantía de ocupación.

**Non-Goals:** cambiar los avisos originales, la cadena de seguridad o la UI pública; garantizar entrega exactamente una vez; almacenar links para reenvío.

## Decisions

### D1 — Ventana y destinatario

Usar `@Scheduled(cron = "0 0/15 * * * *")`, alineado a los minutos 00/15/30/45, y leer el `Clock` inyectado para trabajar con `Instant` UTC. Cada corrida trunca ese instante a 15 minutos para obtener su tick nominal `t` y barre `[t + 23h45m, t + 24h]`, inclusivo. Se descartan `fixedDelay` y `fixedRate`, y calcular la ventana desde el ahora real: con esos esquemas, la demora de una corrida (por ejemplo el SMTP serial) corre los límites y deja huecos `(T + 24h, T + 24h + δ]` que ningún tick cubre. Con el tick nominal, las ventanas consecutivas quedan contiguas aunque haya demora. Incluir ambos bordes tolera una reserva al límite y deja un borde común que el control de duplicados resuelve. Revalidar con el reloj del servidor al despachar cada turno, sin ampliar la ventana del tick. No confundir `inicio - creado_en` con tiempo restante: un manual cargado con menos de 24 h queda excluido aun cuando caiga en el barrido.

**Corrida perdida:** no recuperar fuera de ventana ni ampliar el umbral; después del reinicio evaluar la ventana vigente, sin simular ticks perdidos. Una caída, el retraso de una corrida o una reserva en el límite cuyo commit no fue visible a tiempo pueden dejar un turno sin aviso. Usar exclusivamente el snapshot admisible según C-08: no completar desde `paciente.email`, auditoría, otros turnos ni request. Seeds y legados sin snapshot válido no reciben aviso ni se backfillean; diagnóstico fijo y sanitizado.

### D2 — Gate de Estefi para el link de gestión (pendiente)

El original se genera con 32 bytes aleatorios, se envía en el evento de C-08 y solo se conserva el SHA-256. Esa información no alcanza para reconstruir el mismo secreto. El recordatorio necesita un link real que respete las autorizaciones y las transiciones de C-09, no solo una URL que parezca válida. Ninguna alternativa queda elegida:

| Alternativa | Ambos links, revocación y exposición | Rollout, claves y mantenimiento |
|---|---|---|
| Segunda credencial opaca por turno, almacenada solo como hash | Emitida para el recordatorio, no reemplaza el hash original. Ambas resuelven solo ese turno; las acciones se revalidan y quedan inhabilitadas al cancelar, reprogramar o comenzar. Definir vencimiento/retención que conserve la vista de solo lectura esperada y revocación independiente ante incidente. Dos bearer links aumentan la superficie y requieren controles equivalentes. | Migración aditiva de relación de credenciales, unicidad y resolución expresamente aprobada; sin backfill del secreto original. Mantener los 43 caracteres permite conservar la forma de la ruta C-11, no autoriza por sí solo el nuevo mecanismo. No agrega clave de firma/cifrado; requiere manejo y limpieza de hashes adicionales y pruebas de colisiones/revocación. |
| Credencial firmada de alcance controlado | El original sigue siendo hash-only; la nueva credencial se limita al turno y a las operaciones aprobadas, no concede acceso al sucesor y vuelve a verificar estado/tiempo/permisos. Acordar expiración y revocación independiente (estado o registro revocable); rotar una clave global no sustituye revocar un solo link. No incluir PII ni identificadores internos legibles en el link. | La firma introduce una autoridad nueva y riesgo por compromiso de clave; versionado, almacenamiento seguro, rotación con verificación de claves anteriores y plan de revocación/rollback. **Un formato distinto no pasa la validación de C-11 ni el contrato de C-09 por defecto**: requiere acordar y adaptar consumidor, ruta/API y specs expresamente, preservando links antiguos y pruebas públicas. |
| Retención cifrada recuperable del secreto original | Permitiría enviar el mismo link: no crea una segunda autorización, pero aumenta exposición en base, backups y operación de descifrado; ambos correos comparten la revocación de una sola credencial y mantienen las reglas de solo lectura. | **Incompatible con hash-only sin reformular la política** (`AGENTS.md`, RN-SE-02, contrato de gestión). Solo evaluable con aprobación y cambios de política/contrato explícitos, retención acotada, separación y permisos de claves, rotación, plan de purga incluidas copias y rollback. Solo desde nuevas emisiones compatibles; no recupera secretos de legados. Coste operativo mayor. |

Se descarta enviar el hash, volver a emitir un token invalidando el original, guardar token/link en texto plano o caché, u omitir el link. Mantener `no-store`, `no-referrer`, rutas autorizadas y diagnóstico sin credenciales. No exponer token, hash ni link en logs, auditoría o respuestas. No cambiar RN-SE-02, C-09 ni C-11 sin aprobación explícita.

### D3 — Toma durable, marca y auditoría (semántica propuesta, pendiente de Estefi)

Preferir una toma durable separada, única por turno, en transacción corta, y reservar `recordatorio_enviado_en` para la aceptación SMTP observada (no entrega a bandeja). Revalidar `RESERVADO`, horario, marca, alta manual y snapshot antes de confirmar la toma. Una toma ya confirmada inhibe nuevos intentos aunque la marca siga nula y no exista fila final. Alternativa descartada como recomendación: marcar `recordatorio_enviado_en` antes del transporte evitaría duplicados, pero haría mentir al nombre del campo; marcar solo después de enviar y sin toma permite duplicados entre procesos o tras una caída. El mecanismo de toma final depende de la alternativa de credencial.

Hacer las escrituras del recordatorio acotadas, sin `UPDATE` de `estado`, `inicio`, `fin`, `odontologo_id` ni `save/flush` de la entidad completa; esas columnas disparan `turno_serializar_por_odontologo` y pueden invertir el orden de locks con C-09. No tomar locks de paciente/advisory después de bloquear la fila. Si C-09/C-12 gana antes de tomar el original, no enviar; si la toma gana, revalidar estado justo antes del transporte cuando sea posible, sin locks durante SMTP. Sigue existiendo una carrera entre última lectura y SMTP: un aviso en vuelo puede llegar después de cancelar o mover; el link nunca puede reactivar un turno ni dirigir al sucesor. El sucesor tiene elegibilidad propia.

Reusar transporte, remitente/perfiles, escape HTML y auditoría de C-08 sin fabricar `TurnoReservado` ni depender del listener original `AFTER_COMMIT`. Proponer asunto fijo «Recordá tu turno» y plantilla específica. Tras la toma confirmada, ejecutar SMTP fuera de transacción; registrar en transacción corta el resultado observable, acompañado de la marca si hubo aceptación, sin sobrescribir otras columnas del turno. Sin aceptación observada, la marca queda nula: SMTP fallido o timeout se registran como `ERROR`/`SMTP_FALLO` si la base está disponible. Fallas de configuración, URL o render usan categorías de C-08 cuando correspondan y no llaman SMTP. Un link que no puede prepararse no habilita un envío; acordar si ese fracaso consume la toma y con qué categoría sanitizada. Caída entre toma y SMTP implica pérdida posible; caída posterior a SMTP o error de auditoría implica resultado desconocido o no registrado. Garantía propuesta: **como máximo un intento de despacho durable**, no exactly-once SMTP, ni reparación automática.

## Risks / Trade-offs

- **El recordatorio no puede usar el secreto original sin modificar el diseño** → gate D2, pruebas de ambos links y del flujo C-09/C-11.
- **Pérdida de aviso o auditoría durante caída/timeout** → aceptar explícitamente el contrato de un intento máximo, no afirmar entrega; verificar reinicio y fallas con SMTP falso.
- **Carreras con la ocupación** → transacciones cortas, actualización acotada, revalidación y PostgreSQL real; preservar exactamente una ganadora en reservas concurrentes.

## Migration Plan

Acordar D2–D3 con Estefi (D1 ya queda fijado en el delta), actualizar este plan con el mecanismo elegido y agregar al delta de `notificaciones-email` la exclusión entre corridas y la política ante fallas antes de implementar. Hacer rollout aditivo y compatible antes de activar el scheduler; no backfillear secretos ni destinos. El rollback empieza desactivando el job, no limpiando tomas para reenviar. En cualquier alternativa que emita una credencial nueva, retirar el emisor y conservar el verificador compatible por el período acordado o aplicar su revocación explícita, sin romper los links originales.
