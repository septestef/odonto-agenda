# Evidencia de apply C-12 — agenda, pacientes y cancelación

## Autorización y estado fuente

P1–P9 y apply autorizados explícitamente el 2026-11-22 (ya registrados en design/proposal/tasks). Alcance vigente: implementación de C-12 con checkpoints de agenda, pacientes y cancelación/aviso. Las secciones siguientes conservan la secuencia histórica de cada checkpoint y sus verificaciones. Sin decisiones adicionales al diseño. La desviación TDD del checkpoint 3 queda registrada y mantiene tareas abiertas para revisión; no se afirma cumplimiento estricto completo.

Antes de escribir código: rama `feat/c-12-agenda-odontologo-api`, HEAD `2130a3a`; `git status --short`: únicamente `?? openspec/changes/c-12-agenda-odontologo-api/`. CLI: spec-driven, ready, 3/24. XML y log verificados antes de tests/producción.

## Baseline recuperado, sin ejecución duplicada

Handle original inaccesible para pollear con estas herramientas. Consulta SQLite read-only a `C:/Users/lauta/.local/share/opencode/opencode.db`, tabla part, time_created=1795380627355: estado error, `Tool execution aborted`, output ausente. Input real recuperado (no reejecutado):

```powershell
if (-not (Test-Path -LiteralPath "C:\dev\odonto-agenda\backend")) { throw 'Falta backend' }; $cronometro = [System.Diagnostics.Stopwatch]::StartNew(); & .\gradlew.bat test integrationTest -PskipFrontend --rerun-tasks --console=plain; $resultado = $LASTEXITCODE; $cronometro.Stop(); "BASELINE_EXIT_CODE=$resultado"; "BASELINE_WALL_SECONDS=$($cronometro.Elapsed.TotalSeconds.ToString('F3', [System.Globalization.CultureInfo]::InvariantCulture))"; exit $resultado
```

No se recuperaron BASELINE_EXIT_CODE/WALL_SECONDS; no se infieren. Salida persistida recuperada de `C:/Users/lauta/.gradle/daemon/9.9.1/daemon-13536.out.log`:

- Líneas 87–98: build `8c9d62e9-8ba2-4476-a5e4-c0bebad40df2`, currentDir backend, inicio `2026-11-22T17:50:34.225-0300`.
- Líneas 170–171: `BUILD SUCCESSFUL in 2m 43s`, `6 actionable tasks: 6 executed`.
- Línea 173: `2026-11-22T17:53:14.844-0300` finished executing; líneas 184–185: dispatching Success.
- Línea 178: daemon idle. Java 13536 permanece como daemon; no se esperó ni relanzó el build terminado.

Agregación real PowerShell: Get-ChildItem de TEST-*.xml por carpeta, parseo [xml](Get-Content), suma atributos tests/failures/errors/skipped con Measure-Object; LastWriteTimeUtc contrastado con timestamp XML:

| Suite | XML | Tests | Failures | Errors | Skipped | Timestamp XML UTC mín–máx | Escritura XML UTC mín–máx |
|---|---:|---:|---:|---:|---:|---|---|
| test | 39 | 277 | 0 | 0 | 0 | 20:51:19.123–20:51:31.046 | 20:51:31.3718453–20:51:31.3873694 |
| integrationTest | 41 | 316 | 0 | 0 | 0 | 20:51:33.573–20:53:12.842 | 20:53:13.5934977–20:53:13.6216051 |

Fechas 2026-11-22: coinciden con ejecución 17:50–17:53 -03:00 y fuente sin cambios. Incluyen auth C-03, configuración C-05, reserva/concurrencia C-07, correo/eventos C-08 y gestión token C-09. IntegracionTest usa perfil test y PostgreSQL real turnotarde_test. Sin fallos previos ni omitidos. Se preservan conteos antes de sobrescribir reportes con TDD.

## Ciclos TDD

Reportes: `backend/build/test-results/{test,integrationTest}/` y `backend/build/reports/tests/{test,integrationTest}/`. Todas las ejecuciones siguientes desde `backend`, el 2026-11-22; resultados reales de las herramientas, no resultados esperados. XML se sobrescribe con la siguiente ejecución; se conservan acá los conteos.

### Comandos y secuencia

Alias de lectura (comandos exactos ejecutados):

```powershell
# U
.\gradlew.bat test --tests ar.turnotarde.turno.RangoAgendaTest -PskipFrontend --console=plain
# I
.\gradlew.bat integrationTest --tests ar.turnotarde.turno.AgendaOdontologoIT -PskipFrontend --console=plain
# UI
.\gradlew.bat test --tests ar.turnotarde.turno.RangoAgendaTest integrationTest --tests ar.turnotarde.turno.AgendaOdontologoIT -PskipFrontend --console=plain
# C
.\gradlew.bat check -PskipFrontend --console=plain
```

| Paso | Comando | Resultado real | Interpretación |
|---|---|---|---|
| R1 | U | BUILD FAILED in 4s, compileTestJava: 2 cannot find symbol RangoAgenda | RED antes de crear producción; no XML nuevo en fallo de compilación |
| G1 | U | BUILD SUCCESSFUL in 5s | GREEN: 4 casos (1 día y orden 0/-1/-32) |
| R2 | U | 7 tests, 2 failed; BUILD FAILED in 4s | Triangulación RED: 32/365 no lanzaban error; 31 admitido |
| G2 | U | BUILD SUCCESSFUL in 3s | GREEN 7 casos tras límite calendario de 31 días |
| R3 | I | 19 tests, 19 failed; BUILD FAILED in 19s | RED HTTP real 404 antes del endpoint/servicio/proyección: binding, semántica, vacíos y cruce medianoche con JSON exacto |
| G3 | UI | BUILD SUCCESSFUL in 19s | GREEN: 7 unitarios y 19 IT, mínimo endpoint/proyección/DTO/readOnly |
| T1 | I | compileTestJava falla: JsonNode no tiene findValuesAsText; BUILD FAILED in 3s | Error del test, no falla de producción; se reemplazó por aserciones explícitas de tamaño/ids sin debilitar expectativas |
| T2 | I | BUILD SUCCESSFUL in 17s | 26 IT: semana, histórico/futuro con datos, estados/orígenes, contactos/duración, N+1, seguridad, inyección A/B |
| R4 | UI | 30 IT, 2 failed; BUILD FAILED in 20s | RED adicional: ISO.DATE aceptaba fecha con Z y con -03:00 (200 en lugar de 400) |
| G4 intento | UI | 30 IT, 10 failed; BUILD FAILED in 20s | Binder local estricto rechaza formato, pero DateTimeParseException escapaba como 500; mantuvimos tests |
| G4 | UI | BUILD SUCCESSFUL in 28s | GREEN 7 unitarios/30 IT: editor local traduce fallo de parseo a IllegalArgumentException fija para conversión MVC/advice existente |
| C final | C | BUILD SUCCESSFUL in 2m 7s; 6 actionable tasks: 2 executed, 4 up-to-date | Regresión completa después del último cambio de código, tareas test/integrationTest ejecutadas |

No se omitieron ni debilitaron pruebas. No hizo falta refactor adicional tras GREEN; revisión de separación controller/service/proyección/records y check completo en verde. El binder local es corrección de comportamiento descubierta por triangulación, no un cambio de contrato/configuración compartida.

### Reportes finales verificados por XML

Agregación con Get-ChildItem/parseo XML/Measure-Object, después de C:

| Suite | XML | Tests | Failures | Errors | Skipped | Timestamp XML UTC mín–máx |
|---|---:|---:|---:|---:|---:|---|
| test | 40 | 284 | 0 | 0 | 0 | 22:06:50.624–22:07:00.737 |
| integrationTest | 42 | 346 | 0 | 0 | 0 | 22:07:03.187–22:08:52.228 |

Nuevos: `test/TEST-ar.turnotarde.turno.RangoAgendaTest.xml`: 7/0/0/0; `integrationTest/TEST-ar.turnotarde.turno.AgendaOdontologoIT.xml`: 30/0/0/0. Regresión concurrente: ReservasConcurrentesApiIT 10/0/0/0 y ReservasConcurrentesIT 5/0/0/0, además de todas las demás suites preexistentes. Totales finales = baseline + 7 unitarios + 30 IT.

### Matriz por tarea

Paths de test relativos a `backend/src/test/java/ar/turnotarde/turno/`.

| Tarea | Test file | Capa | Safety net | RED | GREEN | Triangulación | Refactor/revisión |
|---|---|---|---|---|---|---|---|
| 2.1 | RangoAgendaTest.java + AgendaOdontologoIT.java | Unit + HTTP/PostgreSQL | 277 + 316 verdes antes de código | R1/R2/R3/R4 ejecutados | G2/G3/G4 y C | 7 unitarios; 12 bindings inválidos + 4 rangos semánticos + 4 vacíos/límites en IT | Binder local estricto; C verde, sin extracción extra |
| 2.2 | AgendaOdontologoIT.java | HTTP/PostgreSQL | Mismo baseline; único existente modificado: TurnoRepository | R3: cruce medianoche y vacíos devuelven 404 | G3/G4/C | diaria/semanal, dos bordes UTC, cruce, A/B mismo horario, vacío, histórico/2028 con datos | Query plana con joins, tx readOnly; C verde |
| 2.3 | AgendaOdontologoIT.java | HTTP/PostgreSQL + observación Hibernate | Mismo baseline | R3: JSON exacto no disponible (404) | G3/G4/C | todos estados, ambos orígenes, baja lógica, desempate inicio/id, null y autores PACIENTE/ODONTOLOGO, contactos actualizados, 30 min aunque catálogo=45, centinelas en éxito/error | Mapeo único a records; C verde |
| 6.1 pertinente | AgendaOdontologoIT.java | cadena real + login real | C-03 incluido en baseline | Sin RED independiente de filtros heredados: no se modificó seguridad | T2/G4/C | GET autenticado sin cookie/header CSRF; anónimo 401 y sin Location; authorities del principal real cambiadas a ROLE_OTRO para 403 exacto | Verificación de reuso; tarea global permanece pendiente |
| 6.2 pertinente | AgendaOdontologoIT.java | HTTP/PostgreSQL A/B | Mismo baseline | R3 incluye exclusión de agenda ajena | G3/T2/G4/C | query/header/body inyectados en ambas direcciones, mismo DNI/horario; JSON exacto, centinelas distintos de email mutable y errores sin eco | Sin contrato compartido modificado; tarea global pendiente |

N+1: estadísticas de la SessionFactory habilitadas únicamente durante el test y restauradas en finally. GET de 1 turno y GET de 13 turnos (pacientes/prestaciones distintos) preparan exactamente 1 statement cada uno, entityLoadCount=0. No nueva infraestructura/config de producción. Fixture y login quedan fuera del intervalo medido.

### Descubrimientos y correspondencia de fuente

- Spring ISO.DATE admite offsets en fecha: para cumplir P1 se usa @InitBinder local de ControladorAgenda con LocalDate.parse, sin tocar conversiones globales/advice. Parseo inválido se traduce a IllegalArgumentException de mensaje fijo, sin causa ni entrada sensible, que MVC convierte al error de binding vigente. El resultado HTTP exacto se verificó.
- Durante esta sesión apareció externamente `b4b46b7 docs(C-12): proponer agenda del odontologo para revision`, ahora HEAD de la misma rama y tracking origin. El agente NO ejecutó commit/push. `git show --stat b4b46b7` muestra solo los nueve artifacts originales; `git diff 2130a3a b4b46b7 -- backend` vacío. Por tanto no cambia la correspondencia del baseline con backend 2130a3a. Se preserva ese commit.
- Skills cargadas antes de producción: openspec-apply-change, java-springboot, springboot-patterns, java-junit, springboot-security. Leídos AGENTS suministrado por harness, KB relevante, CHANGES (solo lectura), config, registry y todos los artifacts/deltas. Context7 consultado para proyecciones JPQL, estadísticas Hibernate y binder local Spring.
- No decisiones fuera del diseño. El record nuevo PacienteRespuesta existe únicamente como detalle anidado del contrato P2; no se implementó listado de pacientes ni cancelación.
- Sin herramientas Engram disponibles en este harness: evidencia, hallazgos y continuidad persistidos en este archivo, sin afirmar mem_save ni exportar memoria.

## Cierre del checkpoint 1 — agenda

Completas 1.4 y 2.1–2.3. 6.1/6.2 conservan checkbox pendiente porque abarcan también pacientes/cancelación aún no implementados; su porción agenda está cubierta arriba. 7.x no se marca por adelantado (verificación final del change completo pendiente).

Siguiente checkpoint: pacientes 3.x y sus verificaciones de seguridad/aislamiento; después cancelación 4.x/5.x y concurrencia 6.x. No se iniciaron. Mantener change activo, datos/contratos compartidos intactos y trabajo local. OpenSpec validate --strict y git diff --check pasaron; no archive/sync/commit/push/PR ni edición de CHANGES.md.

## Checkpoint 2 — pacientes (misma sesión)

### Alcance autorizado y safety net

Pedido humano posterior: continuar únicamente 3.1/3.2/3.3 y porciones pertinentes de 6.1/6.2; no implementar cancelación. Se mantienen skills ya cargadas, P1–P9 aprobadas y commit documental externo b4b46b7. Reconsulta status/apply: spec-driven ready, 7/24 al inicio. Se releyeron P3 y specs exactas pacientes/errores, PacienteRepository/Paciente y DTO PacienteRespuesta. Este último se reutilizó sin cambios de forma.

Baseline válido: último check de agenda, 284 unitarios + 346 IT, todos 0/0/0, registrado arriba. No se repitió check ni baseline completo. Único existente de producción modificado en este checkpoint: PacienteRepository. Antes de modificarlo se ejecutó:

```powershell
.\gradlew.bat integrationTest --tests ar.turnotarde.shared.persistencia.MapeoPersistenciaIT --tests ar.turnotarde.turno.ReservasConcurrentesIT --tests ar.turnotarde.paciente.RegistroPacientesIT -PskipFrontend --console=plain
```

Resultado real: BUILD SUCCESSFUL in 21s, sin fallos previos. Selección derivada del uso existente de PacienteRepository en mapeo/concurrencia y de la persistencia de contactos por RegistroPacientes. No se inventa conteo del output inicial (no lo imprime); sus XML ya fueron sobrescritos. Esas mismas tres suites se verificaron también al cierre con 12+5+4 casos, respectivamente.

### Secuencia RED → GREEN → triangulación → refactor

Comandos exactos (todos desde backend, 2026-11-22):

```powershell
# PU
.\gradlew.bat test --tests ar.turnotarde.paciente.PaginacionPacientesTest -PskipFrontend --console=plain
# PI
.\gradlew.bat integrationTest --tests ar.turnotarde.paciente.PacientesOdontologoIT -PskipFrontend --console=plain
# PUI
.\gradlew.bat test --tests ar.turnotarde.paciente.PaginacionPacientesTest integrationTest --tests ar.turnotarde.paciente.PacientesOdontologoIT -PskipFrontend --console=plain
# PF — cierre focal y regresiones pertinentes, sin check completo
.\gradlew.bat test --tests ar.turnotarde.paciente.PaginacionPacientesTest --tests ar.turnotarde.turno.RangoAgendaTest integrationTest --tests ar.turnotarde.paciente.PacientesOdontologoIT --tests ar.turnotarde.paciente.RegistroPacientesIT --tests ar.turnotarde.shared.persistencia.MapeoPersistenciaIT --tests ar.turnotarde.turno.ReservasConcurrentesIT --tests ar.turnotarde.turno.AgendaOdontologoIT -PskipFrontend --console=plain
```

| Paso | Comando | Resultado real | Motivo / cambio posterior |
|---|---|---|---|
| PR1 | PU | BUILD FAILED in 2s; compileTestJava: 2 cannot find symbol PaginacionPacientes | RED previo a producción; sin XML nuevo por fallo de compilación |
| PG1 | PU | BUILD SUCCESSFUL in 5s | GREEN: 2 casos iniciales (page0/size20 y ambos campos inválidos) |
| PR2 | PU | 8 tests, 1 failed; BUILD FAILED in 4s | Triangulación RED: size101 no rechazado; offsets 1/100/intmax y otros bordes cubiertos |
| PG2 | PU | BUILD SUCCESSFUL in 4s | GREEN 8 casos tras agregar límite superior 100 |
| PR3 | PI | 19 tests, 19 failed; BUILD FAILED in 16s | RED HTTP: endpoint aún inexistente (404); defaults/vacío, conversión int32, semántica, DTO/contactos, A/B y recorrido con sesión real |
| PG3 | PUI | BUILD SUCCESSFUL in 20s | GREEN 8 unitarios + 19 IT: controlador, servicio readOnly, count y proyección paginada propia |
| PR4 | PI | 24 tests, 2 failed; BUILD FAILED in 16s | Triangulación RED: page2147483647/size100 devuelve 500 con colección vacía o de 1 elemento; demás páginas/orden/101 pacientes pasan |
| PG4 | PUI | BUILD SUCCESSFUL in 27s | GREEN 8 unitarios + 24 IT; offset long se compara con total antes de invocar paginación JPA |
| PF | PF | BUILD SUCCESSFUL in 25s; 6 actionable tasks: 3 executed, 3 up-to-date | Refactor menor de legibilidad (import y List<PacienteRespuesta> en lugar de tipo plenamente calificado); focal y regresiones verdes |

Se mantuvieron todos los tests y expectativas. Sin nuevas dependencias, sin cambios de auth/advice/contratos compartidos. Sin decisiones fuera del diseño.

### Matriz TDD por tarea

Tests bajo `backend/src/test/java/ar/turnotarde/paciente/`:

| Tarea | Test file | Capa | Safety net | RED | GREEN | Triangulación | Refactor |
|---|---|---|---|---|---|---|---|
| 3.1 | PaginacionPacientesTest.java + PacientesOdontologoIT.java | Unit + HTTP/PG | Check previo + safety net focal exitoso | PR1/PR2 y PR3 ejecutados | PG2/PG3/PG4/PF | 8 unitarios, 12 conversiones HTTP (vacíos/no entero/overflow/underflow y precedencia), errores semánticos exactos de uno/ambos campos; sizes1/100 y defaults solo ausencia | PF verde |
| 3.2 | PacientesOdontologoIT.java | HTTP/PG real | Mismo safety net antes de PacienteRepository | PR3: DTO A/B no disponible; PR4: offset grande 500 | PG3/PG4/PF | A/B mismo DNI, paciente solo histórico y otro sin turnos; 101 propios/2 ajenos; orden apellido/nombre/id con empates; default20 y segunda página, size1/100; páginas1/99/intmax excedidas, totales propios y sin duplicados | List tipada/import; PF verde |
| 3.3 | PacientesOdontologoIT.java | HTTP/PG real | Mismo safety net | PR3: wrapper/contacto exacto 404 | PG3/PG4/PF | JSON exacto vacío y con datos, página excedida con totales reales, email mutable vigente distinto de email_gestion, hash/email centinela ausentes en respuestas y entradas inválidas sin eco | Reutiliza PacienteRespuesta sin modificar; PF verde |
| 6.1 pacientes | PacientesOdontologoIT.java | Login/sesión y cadena reales | C-03 verde en check previo | PR3: recorrido autenticado devuelve404, no nuevo RED independiente de filtros heredados | PG3/PG4/PF | GET sin CSRF con login real200; anónimo401 exacto sin redirect; mismo principal real con ROLE_OTRO para403 exacto | Sin cambios seguridad; pendiente global hasta POST |
| 6.2 pacientes | PacientesOdontologoIT.java | HTTP/PG A/B | Mismo safety net | PR3: listado A/B devuelve404 | PG3/PG4/PF | Inyección de dueño en query/headers/body en ambas direcciones; conteos/listados propios; mismo DNI; privacidad y errores sin valores reservados | PF verde; pendiente global hasta cancelación |

### Conteos finales focales, verificados por XML

Agregación real PowerShell de `build/test-results/test/TEST-*.xml` y `build/test-results/integrationTest/TEST-*.xml`, atributos tests/failures/errors/skipped. La ejecución PF reemplazó reportes completos previos; estos NO son totales globales del proyecto:

| Suite focal | XML | Tests | Failures | Errors | Skipped | Timestamp XML UTC mín–máx |
|---|---:|---:|---:|---:|---:|---|
| test | 2 | 15 | 0 | 0 | 0 | 22:18:55.591–22:18:55.778 |
| integrationTest | 5 | 75 | 0 | 0 | 0 | 22:18:57.104–22:19:16.103 |

| XML (prefijo TEST-ar.turnotarde.) | Tests | Failures/errors/skipped |
|---|---:|---|
| paciente.PaginacionPacientesTest.xml | 8 | 0/0/0 |
| turno.RangoAgendaTest.xml | 7 | 0/0/0 |
| paciente.PacientesOdontologoIT.xml | 24 | 0/0/0 |
| paciente.RegistroPacientesIT.xml | 4 | 0/0/0 |
| shared.persistencia.MapeoPersistenciaIT.xml | 12 | 0/0/0 |
| turno.AgendaOdontologoIT.xml | 30 | 0/0/0 |
| turno.ReservasConcurrentesIT.xml | 5 | 0/0/0 |

Reportes HTML: `backend/build/reports/tests/test/index.html` e `integrationTest/index.html`. Evidencia nueva del bloque: 8 unitarios + 24 PostgreSQL IT. Sin repetir check completo; la verificación global final queda para después del checkpoint de cancelación.

### Implementación y continuidad del checkpoint 2

- Nuevos: `paciente/ControladorPacientes.java`, `ConsultaPacientesService.java`, `PaginacionPacientes.java`, `dto/PaginaPacientesRespuesta.java`; tests indicados arriba. Modificado `paciente/PacienteRepository.java` con conteo por dueño y consulta de proyección DTO ordenada/paginada. Paths de producción relativos a `backend/src/main/java/ar/turnotarde/`.
- Parseo int32 explícito antes de semántica, defaults solo si parámetro ausente; errores fijos a través de ReglaNegocioException/advice existente, sin eco ni causa con valores del request. DTO PacienteRespuesta anterior intacto.
- Offset long validado: contar primero y omitir consulta paginada si offset >= total; cumple P3 sin exponer Page ni agregar infraestructura. Orden ejecutado por PostgreSQL, no normalización/collation nueva. Servicio readOnly y dueño exclusivo de OdontologoActual.
- Checkpoints 2.x y 3.x completos. 6.1/6.2 cubiertos para ambos GET pero checkbox global sigue pendiente por POST. Continuidad: cancelación/aviso 4.x/5.x y concurrencia 6.x; no iniciados.
- Skills de la misma sesión vigentes; Context7 consultado para APIs de paginación/proyección Spring Data JPA. Memoria/continuidad en este archivo porque Engram no está disponible.
- Se preserva b4b46b7 y todos los cambios locales de agenda; no commit/push/PR/archive/sync ni modificación de CHANGES.md, auth/advice o contratos compartidos.

## Checkpoint 3 — cancelación y aviso (misma sesión)

### Alcance, baseline y safety net

Pedido humano explícito de implementar 4.x/5.x/6.x con P1–P9 vigentes y cierre 7.x solo con evidencia. No hubo decisiones fuera del diseño. Se reutilizaron las cinco skills cargadas. Reconsulta apply: spec-driven ready, 10/24 al comenzar. Se contrastaron GestionTurnoService/GuardiaSolapamiento/ResumenTurno/AislamientoOdontologo, los tests reales C-09/C-08 y Clock/fake SMTP existentes. Context7 consultado sobre proxy transaccional y TransactionalEventListener AFTER_COMMIT/rollback.

Antes de modificar existentes (Turno.java y TurnoRepository.java), safety net focal desde backend:

```powershell
.\gradlew.bat test --tests ar.turnotarde.turno.*Test integrationTest --tests ar.turnotarde.publico.CancelacionTurnoTokenIT --tests ar.turnotarde.publico.ReprogramacionTurnoTokenIT --tests ar.turnotarde.turno.AgendaOdontologoIT --tests ar.turnotarde.turno.ReservasConcurrentesIT -PskipFrontend --console=plain
```

BUILD SUCCESSFUL in 31s. XML agregados ANTES de sobrescribirlos: **29 unitarios** (EventosTurno3/Guardia4/OriginalEditable8/Rango7/Reglas2/Token5) y **74 IT** (CancelacionToken10/ReprogramacionToken29/Agenda30/ReservasConcurrentes5), todos failures/errors/skipped=0. Baseline previo checkpoint2 15 unitarios/75 IT también verde; no baseline completo duplicado.

### Comandos y resultados reales

```powershell
# CU
.\gradlew.bat test --tests ar.turnotarde.turno.ReglasCancelacionOdontologoTest -PskipFrontend --console=plain
# CI
.\gradlew.bat integrationTest --tests ar.turnotarde.notificacion.CancelacionOdontologoIT -PskipFrontend --console=plain
# CF — única ejecución completa del checkpoint 3, posterior al último cambio de código/tests
.\gradlew.bat check -PskipFrontend --console=plain
```

| Paso | Comando | Resultado ejecutado | Evidencia |
|---|---|---|---|
| CR1 | CU | BUILD FAILED in 2s; compileTestJava: 2 cannot find symbol ReglasCancelacionOdontologo | RED previo a producción de reglas; no XML nuevo por fallo de compilación |
| CG1 | CU | BUILD SUCCESSFUL in 4s | GREEN 2 casos: reservado futuro cercano y cancelado pasado idempotente |
| CR2 | CU | 7 tests, 5 failed; BUILD FAILED in 4s | Triangulación RED de tiempos/estados futuros inconsistentes |
| CG2 | CU | BUILD SUCCESSFUL in 3s | GREEN7; 3 estados con igualdad/pasado y 2 estados futuros con excepción exacta sin causa |
| CR3 | CI | 20 tests, 16 failed; BUILD FAILED in 15s | RED HTTP antes de endpoint/servicio/lock/transición/emisor. Fallan 204, errores de id, tiempos y estados inconsistentes con404. Cuatro404 ajeno/inexistente ya pasan por ausencia de ruta: no se presentan como prueba suficiente de aislamiento |
| CG3 | CI | BUILD SUCCESSFUL in 16s | GREEN20: POST real, datos persistidos, snapshot exacto de evento, flush visible antes de publish, metadatos y errores |
| CT1 | CI | 34 tests, 2 failed; BUILD FAILED in 18s | Test nuevo SMTP esperaba instante al terminar intento; expectativa incorrecta respecto de C-08, no bug de producción |
| CT2 | CI | BUILD SUCCESSFUL in 19s | GREEN34 tras corregir expectativa exacta a instante capturado por ProcesarAviso antes de SMTP (AHORA, no AHORA+5). No se modificó C-08 |
| CT3 | CI | BUILD SUCCESSFUL in 20s | GREEN45, incluyendo locks observados, reloj posterior, carreras C-09, antes/después commit y contacto editado tras publish |
| CF | CF | BUILD SUCCESSFUL in 1m 54s; 6 actionable tasks: 4 executed, 2 up-to-date | Check completo tras refactor de imports explícitos y agregado de caso de sucesor C-09: GREEN7 unitarios/46 IT nuevos y todas las regresiones |

**Desviación de proceso, no ocultada:** las pruebas específicas de lock/reloj/guardia al commit, no-bloqueo SMTP, rollback real, fallos SMTP y carreras/liberación se escribieron DESPUÉS del primer GREEN del POST. Pasaron como triangulación del código ya implementado; no hubo RED independiente previo de esos escenarios (el fallo CT1 es del test, NO un RED de producción válido). No se alteró producción para fabricar retrospectivamente un RED ni se presenta CR3 como prueba de todos esos escenarios. Sus comportamientos están verificados, pero la secuencia estricta requerida por 4.2/4.4/5.2/5.3/5.4/6.3/6.4 no está documentada como cumplida: esas tareas y 7.2 permanecen abiertas para revisión del coordinador. No se solicita ni se asume una excepción automática al modo TDD.

### Matriz por tarea — estado funcional y trazabilidad separados

U = `backend/src/test/java/ar/turnotarde/turno/ReglasCancelacionOdontologoTest.java`.
I = `backend/src/test/java/ar/turnotarde/notificacion/CancelacionOdontologoIT.java`.
Safety net para todas las filas: 29 unitarios/74 IT verdes antes de modificar Turno/repositorio. Refactor de imports explícitos confirmado por CF verde; sin cambios de producción después de CF.

| Tarea | Test/capa | RED | GREEN y triangulación reales | Refactor | Checkbox |
|---|---|---|---|---|---|
| 4.1 | U unit + I HTTP/PG | CR1/CR2/CR3 | futuro1seg; igualdad/pasado RESERVADO/ATENDIDO/AUSENTE; CANCELADO pasado ambos autores; dos futuros inconsistentes500 exacto. Fila completa intacta, flush nunca invocado, cero eventos/SMTP/auditoría. Logs completos sin emails/token/hash/teléfono/nombre/apellido/usuario centinela; marcador+enum y excepción fija sin causa | CF verde | completo |
| 4.2 | I HTTP/PG real + spy ZonaNegocio | Sin RED independiente del lock/reloj | SQL del waiter observado en pg_stat_activity: fila Turno con dueño y FOR UPDATE, sin joins. Otra conexión bloquea paciente/prestación/odontólogo NOWAIT y advisory mientras C-12 retiene turno. Segundo POST relee y no repite evento. ZonaNegocio.ahora nunca llamado antes de adquirir fila y una vez después; inicio/canceladoEn usan reloj posterior | CF verde | pendiente trazabilidad |
| 4.3 | I HTTP/PG | CR3 | Sin body ni Content-Type,204 vacío; id0/-1 VALIDACION detalle exacto; texto/overflow/underflow SOLICITUD_INVALIDA; no mutación en rechazos; HTTP termina mientras SMTP bloqueado | CF verde | completo |
| 4.4 | I HTTP/PG + fallo pesimista sintético | Sin RED independiente de guardia | Constraint trigger de prueba DEFERRABLE INITIALLY DEFERRED provoca errores reales al COMMIT:40P01/55P03/23P01 nombrado→409 RN-TU-01 exacto;23505→500 genérico. Se publicó dentro de tx pero rollback restaura fila y cero AFTER_COMMIT/SMTP/auditoría. CannotAcquireLockException en repo→409 sin publish. Regresiones C-07/C-09 verdes | CF verde | pendiente trazabilidad |
| 5.1 | I HTTP/PG, evento real | CR3 snapshot exacto | ResumenTurno igual a record esperado, pacienteEmail solo email_gestion, nombre/contactos/catálogo vigentes y duración real30 aunque catálogo45; dirección null. Flush comprobado por SELECT en listener síncrono. Null/vacío/múltiples buzones/dominio inadmisible→204, evento email null, cero SMTP/fila, sin fallback | CF verde | completo |
| 5.2 | I consumidor C-08 real + fake SMTP | Sin RED independiente de entrega | SMTP bloqueado con latches: observa CANCELADO desde conexión del worker, HTTP204 ya completo. Asunto/local09:00/sin link-token; contacto cambiado antes de cancelar y tras publicar antes del procesamiento no cambia destinatario. Sucesor C-09 hereda snapshot y C-12 lo cancela tras cambio de contacto; enviado al heredado | CF verde | pendiente trazabilidad |
| 5.3 | I servicio proxificado + tx PG real | Sin RED independiente de rollback | TransactionTemplate exterior participa con servicio real, muta/flush/publica, listener síncrono ve CANCELADO y evento; setRollbackOnly restaura fila completa, cero AFTER_COMMIT/SMTP/fila. INSERT real al mismo horario sigue rechazado por exclusión | CF verde | pendiente trazabilidad |
| 5.4 | I consumidor/fake SMTP | CT1 fue error de expectativa, NO RED de producción | Rechazo SMTP y timeout tras DATA conservan CANCELADO204, un intento, CANCELACION ERROR/SMTP_FALLO con Clock exacto antes del intento; sin secretos en log ni reenvío. Rama aceptada ENVIADO también verificada | CF verde | pendiente trazabilidad |
| 6.1 | I + GET IT previos | CR3 habilita POST; sin RED independiente de filtros heredados | Login/sesión real, CSRF válido204; sin/inválido403 sin mutación; sin sesión con CSRF válido401; sin ambos403; rolincorrecto403; JSON exacto sin redirects. Ambos GET previos conservados verdes | CF verde | completo: verificación de cadena heredada sin producción nueva |
| 6.2 | I + GET IT previos | CR3 con happy/errores;404 inicial por sí solo no era suficiente | POST ajeno/inexistente igual con 4 estados pasados y dueño inyectado en query/header/body; propio con dueño ajeno inyectado204. Fila intacta, cero evento en rechazo, JSON exacto/vacío sin secretos. Privacidad/contacto mutable en ambos GET verdes | CF verde | completo: triangulación de aislamiento/privacidad |
| 6.3 | I HTTP paralelo + PG | Sin RED independiente de carrera | Dos POST en hilos/transacciones distintas: primero pausado tras publish antes de commit, segundo con pg_blocking_pids comprobado;204/204, un evento/autor/instante/aviso/fila, repetición204. Espera hasta inicio→RN07. Seis carreras observadas con C-09cancelar/reprogramar: gana C-09→C-12 idempotente preserva original/sucesor; gana C-12→C-09 RNSE02 o RN07 según reloj. Reprogramación ya retiene paciente/advisory al esperar turno y C-12 confirma sin inversión | CF verde | pendiente trazabilidad |
| 6.4 | I + ReservasConcurrentesIT/ApiIT | Sin RED independiente de liberación | Antes de commit, conexión separada ve RESERVADO y C-07rechaza409; tras confirmar, reserva HTTP201 en mismo intervalo y originalCANCELADO. Tras rollback original ocupa con exclusión real. Suites existentes de reservas concurrentes5+10 verdes sin modificaciones | CF verde | pendiente trazabilidad |
| 7.1 | check global/XML | No aplica producción | 299 unitarios +416 PostgreSQL IT, todos0/0/0; regresiones C03/C05/C07/C08/C09 incluidas | CF verde | completo |
| 7.2 | revisión de artefactos/diff/CLI | No aplica | OpenSpec strict y diffcheck pasan; matriz y alcance documentados; permanece pendiente por salvedad TDD para revisión del coordinador. No PR remoto por restricción explícita | Sin nueva suite requerida | pendiente |

### Resultados finales globales confirmados por XML

Último comando ejecutado: CF, desde backend, 2026-11-22. No se volvió a ejecutar check ni suites después de su resultado verde. Parseo XML con Get-ChildItem/Get-Content [xml], suma de atributos tests/failures/errors/skipped y mínimos/máximos de timestamp:

| Suite | XML | Tests | Failures | Errors | Skipped | Timestamp XML UTC mín–máx |
|---|---:|---:|---:|---:|---:|---|
| test | 42 | 299 | 0 | 0 | 0 | 22:34:35.939–22:34:44.118 |
| integrationTest | 44 | 416 | 0 | 0 | 0 | 22:34:46.386–22:36:23.367 |

Reportes vigentes: `backend/build/test-results/test/TEST-*.xml`, `backend/build/test-results/integrationTest/TEST-*.xml` y `backend/build/reports/tests/{test,integrationTest}/index.html`.

Nuevos de checkpoint3: `TEST-ar.turnotarde.turno.ReglasCancelacionOdontologoTest.xml` **7/0/0/0** y `TEST-ar.turnotarde.notificacion.CancelacionOdontologoIT.xml` **46/0/0/0**. Regresiones observadas individualmente: NotificacionesEmailIT18, CancelacionTurnoTokenIT10, ReprogramacionTurnoTokenIT29, ReservasConcurrentesApiIT10, ReservasConcurrentesIT5, todas0/0/0. No tests omitidos/deshabilitados ni debilitados. No H2 ni SMTP externo.

### Implementación, alcance y handoff

- Nuevos de producción: `turno/ReglasCancelacionOdontologo.java`, `CancelacionOdontologoService.java`, `ControladorCancelacionOdontologo.java` bajo `backend/src/main/java/ar/turnotarde/`.
- Existentes modificados: `turno/Turno.java` agrega transición ODONTOLOGO; `TurnoRepository.java` agrega `bloquearPropio(id,dueno)` SQL nativo solo de fila. Se preservan métodos C-09. Servicio toma dueño de sesión, bloquea primero, lee único ahora, aplica precedencia P4, transiciona/flush y recién entonces lee asociaciones ordinarias/snapshot/evento. Controller no transaccional usa guardia sobre bean proxificado, incluyendo commit.
- Sin cambios a advice, GuardiaSolapamiento, C-09, configuración auth/SMTP, firmas DTO/event existentes, migraciones ni main specs. P4 diagnóstico local y excepción fija sin causa sensible. C-08 se reutiliza sin fallback del contacto mutable ni promesa durable.
- Tests nuevos: U e I indicados arriba; no se modificaron tests existentes. Barreras/latches con timeout y pg_stat_activity/pg_blocking_pids verifican contención; no Thread.sleep ni carreras lanzadas sin observar locks.
- Git conserva `feat/c-12-agenda-odontologo-api`, HEAD b4b46b7 documental externo; implementación y artifacts permanecen sin stage/commit. CHANGES.md intacto. No archive/sync/commit/push/PR.
- Funcionalidad de los tres checkpoints verificada; tareas estrictas sin RED independiente previo quedan abiertas para revisión. El coordinador debe revisar esta desviación y la entrega local antes de finalizar; no afirmar24/24 ni TDD perfecto. No se inventa una resolución ni se ejecutan mutaciones artificiales para fabricar historia RED.
- No Engram disponible: evidencia y resumen de continuidad se conservan aquí. Próxima acción: revisión del coordinador sobre código, matriz y estado formal; no hay implementación adicional autorizada fuera del diseño pendiente de decisión.
