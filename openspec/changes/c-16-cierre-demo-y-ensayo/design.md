# Design

## Context

Motivacion: `proposal.md`, Why. Fuentes leidas: CLAUDE.md, AGENTS.md, CHANGES.md (C-16, dependencias y convencion de migraciones), KB 06/07/08, README, openspec/config.yaml, `.env.example`, build Gradle, perfiles, V900, inicializador de password demo y test concurrente HTTP. No se encontro `.atl/skill-registry.md` ni `.agents/SKILLS.md`. Se aplicaron `openspec-propose` y `java-junit` a la planificacion, sin ejecutar tests ni consultar APIs externas.

Estado corroborado: raiz CLI `C:/dev/odonto-agenda`, rama `docs/c-16-propuesta` inicialmente limpia; solo C-21 activa antes de crear C-16. CHANGES y los archivos de archivo confirman C-06/C-11/C-13 completos, C-12 archivada, C-14 recortada a WhatsApp y C-15 completa. El flujo 5 de la KB describe una intencion que NO entro: prevalece la decision actual de CHANGES de diferir US-014 a C-21.

Observado por inspeccion, no comprobado por ejecucion:

| Fuente | Observacion | Verificacion pendiente en apply |
|---|---|---|
| `backend/build.gradle.kts` | `processResources` depende de `copyFrontend`, salvo `-PskipFrontend`; `copyFrontend` toma `npmBuild`, que depende de `npmInstall` (`npm ci`). No hay enlace explicito de `bootRun` a `copyFrontend`. | Verificar el grafo efectivo y arrancar desde recursos generados ausentes; no asumir que bootRun alcanza. |
| Mismo build | `test` excluye `*IT`; `integrationTest` incluye `*IT` y esta conectado a `check`. | Ejecutar `./gradlew build` sin omisiones ni `-PskipFrontend` y conservar resultados. |
| `frontend/package.json` | Existen `lint`, `test` (`vitest run`) y `build` (typecheck + Vite). | Ejecutarlos separadamente: el build Gradle no declara lint ni tests frontend. |
| `application.yml` / `application-demo.yml` | Importacion opcional del entorno local; perfil demo agrega `db/demo`; Flyway activo, out-of-order y JPA validate. | Confirmar migraciones, recursos y login en localhost:8080 con el perfil efectivo. |
| V900 | Turnos del 25/11 a 09:00 y 09:30, uno cancelado a 11:00; 26/11 a 17:00; 27 libre. Tokens seed sin original recuperable. | Comprobar huecos segun hora real, duraciones, bloqueos y limite por DNI. Generar links mediante reservas nuevas, no reconstruir tokens del seed. |
| `.env.example` / perfil test | Ya enumeran DB, correo, app, perfil demo y TEST_DB; password test puede heredar DB_PASSWORD. | Revisar completitud y precedencia sin leer el `.env` real; no definir password test vacio si se quiere herencia. |
| `ReservasConcurrentesApiIT` | Caso repetido que exige 201/409 RN-TU-01 y una sola fila reservada. | Preservarlo y ejecutar contra PostgreSQL real, junto con el resto de la suite. |

## Goals / Non-Goals

**Goals:** arranque reproducible en Windows con Java 25, Node 24 y PostgreSQL 18 local; datos aptos para el miercoles 2026-11-25; guia exacta y ensayo trazable entre Lauti y Jero.

**Non-Goals:** cambios de UI, nuevas funcionalidades, recordatorio automatico, produccion, auth nueva, reglas de reserva nuevas, infraestructura de browser o cambios del build raiz. Esta sesion solo crea propuesta. La implementacion espera la aprobacion de la propuesta y habilitacion de apply; el encargo ya incluye el reset local y ensayo Gmail para ese apply.

## Decisions

### D1. Coordinacion y permisos por frente

A lo realiza Lauti. En un apply futuro A solo puede editar `README.md`, `.env.example`, `backend/**` para seed/config y tests necesarios, y la seccion A de `tasks.md`. No toca B, CHANGES, main specs ni frontend. Si descubre que necesita frontend, build raiz u otra superficie fuera de esa lista, informa el bloqueo y solicita ampliacion ANTES de editar. No usa la posibilidad de escribir backend como autorizacion general para cambiar negocio o seguridad.

Jero ESPERA a que la propuesta este integrada en `main` antes de crear su rama desde esa base. La integracion de propuesta no ocurrio en esta entrega. B prepara guion y luego ejecuta smoke sobre una revision que incluya A; guarda `guion-demo.md` y `evidencias/` dentro del change y actualiza solo B. No corrige diferencias visuales implicitamente. Se elige esta division en lugar de edicion simultanea de README para evitar conflictos y mantener responsables claros.

Futura PR solo de propuesta: primero revision local, luego pedido explicito para commit/push/PR. Conventional Commits con scope `C-16` (por ejemplo `docs(C-16): proponer cierre de demo y ensayo`), sin atribucion IA. No se adelanta ninguna accion git de escritura en esta entrega.

### D2. Reset destructivo con destino y autorizacion verificables

Se documenta un procedimiento manual, no un reset automatico al iniciar la app. En apply habilitado tras aprobar la propuesta, antes de ejecutar detener la app, informar el destino concreto al operador y verificar que la base es local, exclusiva de demo y descartable; identificar host, puerto, base, usuario y esquema efectivos sin divulgar connection strings con credenciales. Contrastar desde la conexion `current_database()`, `current_user`, `inet_server_addr()` y `inet_server_port()` y el destino elegido; no alcanza que un nombre contenga "demo". Rechazar remoto, destino ambiguo, base de test, datos a preservar o desvio del alcance autorizado. Pedir aclaracion solo ante destino ambiguo o fuera de alcance.

Solo despues de esas guardas se ejecuta el reset ya encargado de `public` (incluido historial Flyway) en esa base y se deja que Flyway reconstruya. No borrar otras bases/esquemas, no ejecutar clean global, no deshabilitar constraints. Credenciales mediante mecanismo local seguro, nunca argumentos/logs/chat. La revision humana de datos/config precede al apply; no repetir permisos para el reset local ya encargado dentro del alcance aprobado. Alternativa rechazada: copiar el DROP sin guardas del README como receta automatica.

### D3. Secuencia real de frontend y arranque

README debe agregar `Correr la demo en la notebook`, con comandos exactos por directorio, sintaxis PowerShell, versiones, preparacion de PostgreSQL/app/test, entorno, reset guardado, build, perfil y navegador. El ejemplo actual de asignacion de perfil en linea es de shell POSIX: no presentarlo como comando PowerShell.

Punto de partida propuesto desde `backend/`: `./gradlew build` para compilacion completa y suites backend; si hace falta una preparacion separada de recursos, `./gradlew copyFrontend processResources`, seguida de `./gradlew bootRun --args='--spring.profiles.active=demo'`. Confirmar la secuencia real en apply y documentar solo la que efectivamente se ensaye desde cero. No usar `-PskipFrontend` como prueba de arranque completo ni confundir `npm run build` solo con copia a static. No modificar build para forzar la suposicion; si el grafo exige un cambio fuera del alcance, pedir ampliacion. El checklist del dia explicita Postgres arriba, entorno preparado por usuario, destino/reset confirmado cuando corresponda, navegador y acceso a login/reserva/gestion en `localhost:8080`.

La plantilla mantendra TODAS: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` (opcional comentada para herencia), `DEMO_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_NAME`, `APP_BASE_URL`, `APP_TIMEZONE`, `APP_RESERVA_MAX_DIAS`, `SPRING_PROFILES_ACTIVE`. Sin valores reales: sensibles vacias/comentadas; solo ejemplos ficticios o defaults publicos. El usuario configura secretos fuera del chat. No leer, imprimir, pedir ni evidenciar `.env`, contrasenas, cookies o tokens.

### D4. Datos de demo sin reescribir migraciones

V900 ya satisface la presencia del 25/11; primero verificar su suficiencia contra el guion, no agregar migracion por inercia. Si requiere ajuste, agregar una version 9xx libre (por ejemplo V916 si sigue libre al implementar) en `backend/src/main/resources/db/demo`, solo datos sinteticos y perfil demo. Nunca modificar V900 mergeada, ni su checksum, ni bajar garantias de la base.

El 25/11 se muestra la agenda de ese dia pero la reserva online/reprogramacion se hace en un hueco que respete ahora + 24 h (26 por la tarde o 27 segun el momento). Revisar elegibilidad real, no prometer todos los huecos del 26. El turno manual ya implementado permite mostrar altas del mismo dia solo si el inicio sigue siendo futuro. No cambiar Clock de produccion para el guion; tests deterministas con Clock inyectado. Una reserva nueva en buzon controlado provee el link: el seed no permite recuperar tokens.

### D5. Verificacion con Strict TDD y evidencia honesta

En apply, antes de tocar comportamiento existente, ejecutar tests relevantes y registrar baseline con cantidades; si falla, detenerse y reportar fallo preexistente, sin repararlo sin decision. Para cada cambio de comportamiento: test minimo RED, ejecucion fallida pertinente, minimo GREEN confirmado, triangulacion con al menos dos casos por comportamiento (exito y borde/error), refactor solo en verde. Si no hace falta cambiar comportamiento, registrar esa condicion; no inventar RED de documentacion.

Para seed/config, cubrir segun lo modificado carga limpia y reinicio sin duplicados, aislamiento de perfil demo y huecos elegibles con reloj fijo; no simular PostgreSQL con H2. Preservar el test de dos reservas concurrentes con exactamente una ganadora. No borrar, deshabilitar, omitir ni debilitar pruebas. Documentar evidencia por tarea: archivo/capa, baseline, RED, GREEN, triangulacion y refactor, con comandos/resultados sanitizados (A en README dentro de su alcance; B referencia esos resultados).

Aceptacion final: desde `backend/`, `./gradlew build` con unitarios, integracion PostgreSQL real y frontend; desde `frontend/`, `npm run lint`, `npm test`, `npm run build`. Registrar revision, comando, fecha, cantidades y resultado, sin atribuir al estado actual conteos historicos de CHANGES. No se ejecuta ninguno durante propose.

### D6. Gmail y evidencia sin credenciales

El encargo ya autoriza el ensayo Gmail para apply habilitado tras aprobar la propuesta, con credenciales cargadas por el usuario fuera del chat. Verificar configuracion y buzon controlado; pedir aclaracion solo ante destino ambiguo o fuera de alcance. Usar identidad sintetica y buzon controlado; no enviar a los pacientes `example.com` del seed. Verificar aceptacion SMTP y por separado llegada/spam, contenido y apertura de gestion en la notebook; cancelacion y reprogramacion usan reservas separadas o un orden que no invalide la siguiente prueba. Verificar el nuevo correo/link de reprogramacion. Si no llega, registrar fallo/bloqueo y verificar que la reserva persiste, no declarar entrega exitosa por SMTP aceptado.

`APP_BASE_URL=http://localhost:8080` apunta al anfitrion, no al servidor desde un telefono ajeno. El control 360 px se puede hacer en viewport local del navegador; no prometer acceso fisico desde celular con localhost ni introducir hosting/tuneles como requisito. Evidencias sin URL completa de gestion, tokens, cookies, destinatarios reales o capturas de credenciales; describir exito sin copiar el secreto.

### D7. Verificacion visual, no modificacion visual

Decision: no (no UI)

Se compara lo existente con mockups aprobados de los archivos C-04 (login/shell), C-06 (horarios/prestaciones/bloqueos), C-10 (reserva), C-11 (gestion), C-13 (agenda), C-14 (WhatsApp) y C-15 (manual), en `openspec/changes/archive/<fecha>-<change>/mockups/`. Se sigue revision 2 del design system; no se necesita mockup nuevo porque no se cambia la UI.

Jero genera matriz pantalla/estado, mockup, viewport escritorio o 360 px, revision, resultado, evidencia sanitizada y diferencias. Clasificacion operativa propuesta, no criterio nuevo del equipo: bloqueo funcional, diferencia visual para revision, detalle conocido no bloqueante sujeto a aprobacion. Incluir el detalle ya reportado en C-15 sobre ancho de botones escritorio sin asumir su correccion. No inventar tolerancias pixel-perfect ni aprobar desvios por cuenta propia. Sin browser automatizable, declarar ejecucion manual y su cobertura real; si no se pudo ejecutar, dejar pendiente, no "paso".

## Risks / Trade-offs

- [Reset borra datos] -> Tras aprobar la propuesta y habilitar apply, informar y verificar el destino efectivo del reset local ya encargado; detenerse ante ambiguedad o datos a preservar.
- [README afirma que bootRun compila todo] -> Hay evidencia estatica indirecta, no ejecucion; verificar desde cero y reemplazar la afirmacion por secuencia comprobada.
- [24 horas vuelve inutil un horario del guion] -> Mostrar agenda del 25 y elegir reservas futuras elegibles al ensayar; no cambiar reglas ni reloj real.
- [Gmail/internet/spam bloquean el ensayo] -> Prueba autorizada antes de la demo, datos sinteticos, evidencia sin secretos y resultado bloqueado si falta configuracion/entrega.
- [Defecto frontend/build fuera de alcance A] -> Detener esa correccion y pedir ampliacion; no expandir scope ni omitir tests para llegar a verde.
- [KB incluye recordatorio automatico no entregado] -> Guion declara US-014 fuera de alcance por CHANGES/C-21; incluye WhatsApp y manual realmente archivados.
- [No hay herramientas de browser disponibles en esta sesion] -> B queda planificado como manual salvo disponibilidad futura comprobada; no se declara validacion visual realizada.

## Migration Plan

1. Revisar localmente esta propuesta. Commit/push/PR solo de propuesta si el usuario lo pide; integrar esa propuesta en main solo mediante el flujo autorizado, antes de la rama de Jero.
2. Tras aprobar la propuesta y habilitar apply A, revisar cambios de config/datos y ejecutar reset local y ensayo Gmail ya encargados, informando y verificando el destino/configuracion; pedir aclaracion solo ante ambiguedad o desvio del alcance; ejecutar con guardas, TDD cuando corresponda y suite completa. A no marca B ni cierra C-16.
3. B ensaya sobre la revision integrada candidata que contenga A, aporta guion/evidencias y somete diferencias a revision final. Correcciones fuera de alcance necesitan nueva decision/autorizacion y nuevo ensayo pertinente.
4. Solo tras recibir la entrega de B de Jero y aprobar la revision final, Lauti integra el trabajo con permisos git explicitos, marca CHANGES C-16 `[x]` y ejecuta archive/sync en una accion futura autorizada. Ese cierre corresponde a Lauti, no a Jero ni al apply A, y no ocurre ahora.

Rollback: antes del reset, si hay datos a preservar no proceder. Despues de un reset autorizado no se promete recuperar datos descartados; ante error de seed no reescribir migraciones aplicadas, corregir con otra migracion aprobada o reconstruir la misma base local descartable verificando nuevamente el destino dentro del alcance encargado; pedir decision solo si el destino o alcance cambia. Revertir cambios de codigo mediante el flujo de revision, nunca mediante edicion de checksums.
