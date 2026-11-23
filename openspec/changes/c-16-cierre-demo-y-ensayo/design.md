# Diseño

## Reglas comunes

- No leer ni mostrar `.env`, secretos, cookies, tokens ni enlaces completos de gestión. El usuario configura las credenciales localmente fuera del chat. Guardar solo evidencia sanitizada.
- Antes del reset, detener la aplicación, informar host/puerto/base/usuario/esquema efectivos y confirmar que el destino es local, exclusivo de demo, descartable y distinto de test. Contrastar la conexión efectiva; detenerse ante destino remoto, ambiguo o datos a preservar.
- La propuesta se aprueba e integra antes de implementar. El encargo incluye reset local y ensayo Gmail para el apply habilitado; pedir aclaración solo ante ambigüedad o desvío del alcance. Commit y push requieren pedido explícito, Conventional Commits con scope `C-16` y sin atribución a IA.
- No omitir, borrar ni debilitar tests. Reportar bloqueos y cambios necesarios fuera del alcance antes de ampliar el trabajo.

## Context

Ver `proposal.md` para la motivación. Demo en localhost el miércoles 2026-11-25; entorno Windows, Java 25, Node 24 y PostgreSQL 18 local.

CHANGES confirma C-06/C-11/C-13 completos, C-12 archivada, C-14 con WhatsApp y C-15 con turno manual. US-014 se difiere a C-21; el flujo de recordatorios automáticos de la KB no forma parte de la demo.

## Goals / Non-Goals

**Objetivo:** secuencia reproducible y ensayo verificable entre Lauti y Jero, con evidencia vinculada a la revisión candidata.

**Fuera de alcance:** nuevas funcionalidades, producción, cambios visuales, reglas de reserva nuevas o infraestructura de navegador. Las diferencias visuales se reportan para revisión.

## Decisions

### D1. Responsables y superficies de trabajo

Lauti realiza A: `README.md`, `.env.example`, `backend/**` para seed/configuración y tests necesarios, sección A de `tasks.md` y `openspec/changes/c-16-cierre-demo-y-ensayo/evidencias/` para sus resultados. README contiene únicamente la sección `Correr la demo en la notebook` con pasos y checklist de arranque; los registros de ejecución van en evidencias, no en README.

Jero crea su rama desde main actualizada después de integrar la propuesta. Realiza B: `guion-demo.md`, evidencia dentro del mismo directorio y sección B de `tasks.md`. Ensaya una revisión que incluya A. Separar las entregas por responsable evita conflictos; las correcciones fuera de las superficies autorizadas requieren decisión.

### D2. Reset local sin automatismo de arranque

El reset es una operación explícita del ensayo, no un efecto de iniciar la aplicación. Tras verificar las reglas comunes, ejecutar `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` solo en el destino confirmado, incluido el historial Flyway de ese esquema. Flyway reconstruye las migraciones; no usar clean global ni deshabilitar constraints.

### D3. Frontend, perfil y entorno

El build encadena `bootRun` → `processResources` → `copyFrontend` → `npmBuild` → `npmInstall`; `processResources` declara `dependsOn(copyFrontend)` en `backend/build.gradle.kts`. Confirmar el comportamiento al arrancar desde cero sin `-PskipFrontend`, con perfil demo, Flyway y login en `http://localhost:8080`.

Documentar comandos PowerShell por directorio y la secuencia efectivamente ensayada. Desde `backend/`, usar el wrapper y el perfil `demo` mediante entorno o `--args='--spring.profiles.active=demo'`; no presentar una asignación POSIX como sintaxis PowerShell. El checklist incluye PostgreSQL levantado, entorno completo, base reseteada y navegador elegido. `localhost` se verifica en la notebook, también para los enlaces recibidos por correo.

La plantilla enumera `DB_URL`, `DB_USER`, `DB_PASSWORD`, `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`, `DEMO_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_NAME`, `APP_BASE_URL`, `APP_TIMEZONE`, `APP_RESERVA_MAX_DIAS` y `SPRING_PROFILES_ACTIVE`. Mantener `TEST_DB_PASSWORD` comentada si hereda `DB_PASSWORD`: un valor vacío explícito pisa la herencia.

### D4. Seed y elegibilidad temporal

V900 ya incluye turnos el 25/11 a 09:00 y 09:30 y uno cancelado a 11:00; el 26 tiene uno a 17:00 y el 27 está libre. Crear obligatoriamente una nueva versión 9xx libre en `backend/src/main/resources/db/demo` con tres o cuatro turnos sintéticos adicionales el 25/11, repartidos entre mañana y tarde, con prestaciones distintas y al menos uno de origen `MANUAL`. No deben solaparse entre sí ni con V900; conservar huecos libres el 26/27 para reservar en vivo. V900 y las migraciones mergeadas permanecen inmutables.

Mostrar la agenda del 25/11 y elegir reservas online/reprogramaciones que respeten ahora + 24 h (26 o 27 según el momento). El turno manual puede mostrar altas del mismo día si siguen siendo futuras. No alterar reloj ni reglas de producción. Validar la nueva migración 9xx con un IT de perfil demo contra PostgreSQL real que compruebe carga desde una base limpia, turnos adicionales y huecos esperados con `Clock` fijo; no exigir RED/GREEN ni triangulación para el seed.

### D5. Suites y evidencia

Antes de modificar comportamiento existente, ejecutar baseline pertinente; si falla, reportar el fallo preexistente y detener esa modificación para decidir cómo resolverlo. Los ajustes dentro del alcance se resuelven de raíz, sin recortar pruebas.

Ejecutar desde `backend/` `./gradlew build`, con unitarios, `integrationTest` sobre PostgreSQL real y frontend. El build separa `*Test`/`*IT` y conecta integración a `check`. Preservar `ReservasConcurrentesApiIT`: exactamente una respuesta 201, otra 409 RN-TU-01 y una fila reservada. Desde `frontend/`, ejecutar por separado `npm run lint`, `npm test` y `npm run build`.

Guardar comandos, revisión, fecha, cantidades y resultados en `openspec/changes/c-16-cierre-demo-y-ensayo/evidencias/`. No trasladar conteos históricos a la revisión candidata ni crear resultados antes de ejecutarlos. Esa carpeta también recibe el baseline y el ensayo de correo; README conserva solo pasos y checklist.

### D6. Gmail y enlace de gestión

Usar datos sintéticos y buzón controlado, verificando configuración efectiva. Distinguir aceptación SMTP de llegada: revisar bandeja y spam y abrir el enlace en la notebook. Ensayar cancelación y reprogramación con reservas separadas o en un orden que mantenga válido cada paso; comprobar el nuevo correo de reprogramación. Si el envío falla, comprobar que el turno persiste y registrar el bloqueo.

Los tokens del seed no son recuperables: obtener el enlace mediante una reserva nueva. No enviar a las direcciones ficticias del seed. La evidencia identifica el resultado sin incluir los datos restringidos por las reglas comunes.

### D7. Comparación visual y alcance del guion

Decision: no (no UI)

Jero compara escritorio y 360 px con mockups aprobados revisión 2 de C-04/C-06/C-10/C-11/C-13/C-14/C-15 archivados. Registrar pantalla/estado, mockup, viewport, revisión, resultado y diferencias; incluir el detalle conocido de botones de escritorio de C-15. Clasificar bloqueos funcionales, diferencias visuales y detalles conocidos para revisión humana, sin inventar tolerancias ni aprobar diferencias por cuenta propia.

Sin navegador automatizable, declarar prueba manual y dejar pendiente lo no ejecutado. El guion recorre historias M implementadas, US-011 y US-015; excluye US-013, US-014/C-21 y funcionalidades post-demo.

## Risks / Trade-offs

- [Reset borra datos] → Verificar el destino según las reglas comunes y no proceder si hay datos a preservar.
- [La regla de 24 h invalida un horario del guion] → Mostrar la agenda del día y elegir huecos online futuros elegibles.
- [Gmail, internet o spam impiden completar el ensayo] → Probar antes de la demo y registrar el bloqueo; SMTP aceptado no acredita entrega.
- [Defecto fuera del alcance A] → Reportarlo y pedir ampliación antes de corregir; no omitir verificaciones.
- [No hay navegador automatizable] → Verificación manual declarada con cobertura real y pendientes explícitos.

## Migration Plan

1. Aprobar e integrar la propuesta antes de iniciar las ramas de implementación.
2. Lauti completa A, guarda evidencia y entrega la revisión candidata a Jero.
3. Jero completa B sobre esa revisión y entrega guion, evidencia y diferencias para revisión final.
4. Tras B y revisión aprobada, Lauti integra, marca C-16 `[x]` en CHANGES y ejecuta archive/sync mediante el flujo autorizado.

Rollback: si hay datos a preservar, no resetear. Una base descartada no tiene recuperación prometida; los errores de seed se corrigen con otra migración o reconstruyendo la misma base local verificada, nunca editando checksums.
