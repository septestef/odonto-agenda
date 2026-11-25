# Frente B — Traspaso y criterios de cierre

> **Superado el 25/11:** Estefi retomó B en la notebook de demo y cerró estos pendientes. Ver la [retoma y el ensayo final](B-smoke.md#retoma-en-la-notebook-de-demo--2411) y la [matriz completa](B-visual.md#matriz-completa-2411--26). Este archivo queda como registro del traspaso.

**Estado al traspaso: OpenSpec 13/18 (`spec-driven`).** En [tareas B](../tasks.md#b--guion-smoke-y-verificación-visual-facu) solo están completas **2.1, 2.2 y 2.5**. Este traspaso de trabajo no es la entrega para aceptación final.

## Revisión y fuentes de evidencia

- **Base efectivamente ensayada:** `ff603a30bf822851e55d75d7c1b98c2359f0604d`, HEAD local de `chore/c-16-guion-y-verificacion` e igual a la referencia local `origin/main` al preparar este traspaso. Integra A (`6581015`, `chore/c-16-arranque`) sobre `be7caff`, con el mismo árbol que A. El trabajo documental de B estaba local y sin commit; el hash de la futura revisión publicada no es el hash del ensayo. Ver [trazabilidad](B-smoke.md#21--base-integrada-y-entrega-de-a).
- **Operador, manual:** [smoke por paso](B-smoke.md#pasos-del-smoke--resultados-parciales) y [usabilidad asistida](B-visual.md#recorrido-de-usabilidad-manual-asistido-25) a 1357 × 948 y 360 × 800. El operador también reportó un ensayo final separado, sin duración medida ni detalle comprobable.
- **B/coordinación, directo:** [preflight y arranque](B-smoke.md#preflight-local-de-b--no-es-smoke) y [Edge/CDP](B-visual.md#usabilidad-observada-directamente-por-b-25) a 1440 × 900 y 360 × 800, solo preautenticación y reserva pública sin envío. El HTTP 200 acredita disponibilidad de rutas, no login; la medición CDP no acredita el margen de las altas del operador.
- **Estefi, A:** [evidencia heredada](A-arranque-datos-tests.md) de suites en verde contra PostgreSQL real, incluido `ReservasConcurrentesApiIT` **10/10**, arranque limpio con frontend y ensayo Gmail. B no reejecutó las suites ni cuenta esa concurrencia, correo o arranque de A como ejecución de B.

**Estado en la notebook de B:** se resolvieron el 8080 ocupado y el fallo inicial atribuido por el operador a `MAIL_USERNAME`; después B confirmó el listener y las rutas HTTP. Al terminar, el operador informó que detuvo `bootRun` y confirmó que el esquema `public` de la base descartable quedó vacío; coordinación solo verificó que ya no había listener en 8080 y que PostgreSQL en 5432 aceptaba conexiones. No hubo consulta SQL directa de B ni nuevo arranque. Ver [limpieza final](B-smoke.md#ensayo-final-y-limpieza--no-son-el-smoke). Git no traslada el estado de la base, el proceso, el navegador, el buzón ni la configuración local.

## Recibir la rama en otra notebook (PowerShell)

Solo **después de publicada** y desde la raíz del clon correcto. Primero ejecutá `git status --short --branch`: si aparecen cambios, **no cambies de rama ni integres nada**; resguardá el trabajo con su responsable o usá un clon limpio. No hagas reset ni fuerces la rama. Con árbol limpio:

```powershell
git fetch origin
git show-ref --verify refs/remotes/origin/chore/c-16-guion-y-verificacion
```

Si el fetch falla o no existe la referencia, frená y confirmá la publicación. Si no tenés la rama local:

```powershell
git switch --track -c chore/c-16-guion-y-verificacion origin/chore/c-16-guion-y-verificacion
```

Si ya existe la rama local, usá **esta alternativa**, no vuelvas a crearla:

```powershell
git switch chore/c-16-guion-y-verificacion
git branch --set-upstream-to=origin/chore/c-16-guion-y-verificacion
git merge --ff-only origin/chore/c-16-guion-y-verificacion
```

Si algún comando falla, frená sin forzar. En ambos casos comprobá la candidata antes de ensayar:

```powershell
git status --short --branch
git rev-parse HEAD
git rev-parse origin/chore/c-16-guion-y-verificacion
git merge-base --is-ancestor ff603a3 HEAD
$LASTEXITCODE
git diff --name-status ff603a3 HEAD
```

Los dos hashes deben coincidir entre sí y con el informado al publicar; la comprobación de ancestro debe dar `0`. Revisá el diff completo contra `ff603a3`: se espera solo el [guion](../guion-demo.md), [tareas B](../tasks.md) y las tres evidencias B. Si hay cambios ajenos, en código o en configuración, no atribuyas automáticamente la evidencia anterior a ese commit: elevá el desvío. Anotá el hash publicado y conservá el hash que corresponde a cada ejecución.

### Preparación local

Usá [Correr la demo en la notebook](../../../../README.md#correr-la-demo-en-la-notebook) y [`.env.example`](../../../../.env.example) como únicas fuentes de preparación. Completá **a mano** un `.env` local e ignorado: revisá `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DEMO_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` (contraseña de aplicación), `MAIL_FROM_NAME`, `APP_BASE_URL`, `APP_TIMEZONE`, `APP_RESERVA_MAX_DIAS` y `SPRING_PROFILES_ACTIVE`; para suites, revisá los `TEST_DB_*` según la plantilla. No leas, reutilices ni copies el `.env` de la notebook de B, no expongas valores y verificá que el entorno efectivo no apunte a otra base.

Verificá PostgreSQL local en `localhost:5432`, base descartable `turnotarde`, usuario `turnotarde` y esquema `public`, separado de `turnotarde_test`. El README muestra PostgreSQL 18: usá la ruta del `psql.exe` que **realmente esté instalado**, sin cambiar el destino ni el script (en B se usó PostgreSQL 19). Antes de arrancar, verificá que 8080 esté libre; después, comprobá Flyway V901, el arranque de demo, el login real y la reserva pública en `http://localhost:8080`. Para Gmail seguí [el procedimiento del README](../../../../README.md#correo-de-confirmación-y-avisos): con autorización, datos sintéticos y buzón controlado, comprobá llegada en bandeja o spam y apertura en la misma notebook. Ni el arranque ni SMTP aceptado prueban entrega.

## Qué falta acreditar

### 2.3 · Smoke por paso

Seguí el [guion](../guion-demo.md) sin modificarlo y completá los huecos de [B-smoke](B-smoke.md#pasos-del-smoke--resultados-parciales). Por paso y por preparación de turno, anotá hash, fecha/hora y zona, contexto y fuente (manual del operador o directo), esperado, observado y resultado: `Aprobado`, `Fallo`, `Bloqueado` o `Pendiente`. No completes de memoria lo que no quedó registrado.

- Para cada alta **P-C, P-R, O-C y W**, y para el destino **P-R2**, anotá la fecha y hora reales del inicio, la duración y la hora efectiva del servidor al elegir y justo antes de confirmar. Registrá el margen medido (`inicio − hora del servidor`, en ART, `America/Argentina/Buenos_Aires`): **más de 24 h** para el ensayo, con holgura para presentar. El 26 y 27 de noviembre de 2026 son candidatos históricos del guion, no disponibilidad prometida. Para **M**, acreditá del mismo modo un hueco futuro con **0 < margen < 24 h**, origen `MANUAL` y aviso de alta. Comprobá grilla de 15 min, duración completa, franja y ausencia de solapamientos o bloqueos.
- Cerrá los cotejos parciales: franjas y catálogo original intactos, vista pública sin datos de otros pacientes, navegación anterior/siguiente de Semana, y contexto de apertura de WhatsApp con texto preparado y sin envío. Documentá bandeja/spam y cotejo de los campos del correo donde falte, reabrí el enlace nuevo de **P-R2** y verificá recepción y apertura del enlace de **M**. Si falla el correo, comprobá si el turno persiste sin duplicarlo y registrá el bloqueo.

**Vigencia para 2.3 y 2.4:** si al retomar ya no hay huecos elegibles para el guion con los datos de fechas fijas, marcá la comprobación afectada **Bloqueada por fecha**, con hora del servidor y motivo. Coordiná con Estefi una prueba aislada con `Clock` fijo o un fixture soportado, **solo con aprobación previa** y con evidencia separada. Eso no es el smoke real de la demo del 25/11 ni cierra la tarea por sí solo. No cambies el reloj de la máquina, el código, las reglas ni el seed para fabricar elegibilidad.

### 2.4 · Concurrencia manual, aparte del guion

En un navegador con interfaz visible, prepará **dos formularios separados**, en dos ventanas o perfiles independientes sin sesión, con DNIs sintéticos distintos y el **mismo** hueco libre y prestación, ajenos a P-C/P-R/O-C/W/M. Registrá fecha, hora, duración y contexto; medí el margen con la hora efectiva del servidor al preparar y justo antes del envío, igual que en 2.3. Si no hay un hueco elegible con más de 24 h, aplicá el bloqueo anterior.

Confirmá ambos formularios con dos operadores o de manera coordinada, sin que el segundo espere al resultado del primero. Usá **DevTools → Network solo para tiempos y estados HTTP**, con las dos mediciones en una referencia temporal comparable: anotá envío/fin de ambos POST y comprobá que empezaron antes de que terminara el otro (ventanas pendientes solapadas). Dos clics cercanos no lo demuestran; si no podés acreditar el solapamiento, el intento no cuenta. Esperá **una `201`, una `409` con el aviso de `RN-TU-01` («Ese horario acaba de ser tomado, elegí otro»)** y el refresco de esa disponibilidad, y verificá **un único turno activo** en el intervalo en agenda. Anotá el aviso desde la UI, sin consultar ni copiar cuerpos. Guardá solo un resumen sanitizado: sin HAR, cuerpos, encabezados, cookies, datos de pacientes ni enlaces privados. Vinculá el [10/10 automatizado de A](A-arranque-datos-tests.md#suite-final) sin presentarlo como prueba manual de B.

### 2.6 · Cotejo con mockups revisión 2

Completá la [matriz existente](B-visual.md#matriz-de-comparación-26) en escritorio y a 360 px, renderizando los HTML originales con los recursos de diseño como describe B. Quedan: **C-04** shell autenticado y retorno tras logout; **C-06** franjas/edición, prestaciones/listado/alta y bloqueos/listado; **C-10** efecto del bloqueo en selección y confirmación; **C-11** turno activo, diálogo de cancelación, selector de reprogramación y originales en solo lectura; **C-13** Día, Semana, detalle, confirmación y aviso de cancelación; **C-14** WhatsApp en detalle de Día y Semana con los estados que aparezcan naturalmente; **C-15** acceso a «Nuevo turno» (Semana sin mockup propio), formulario, alta exitosa en Día y botones de escritorio. Respetá las exclusiones y limitaciones de la matriz; la usabilidad de 2.5 no prueba paridad.

Para C-15, cotejá ubicación, orden, ancho, alineación y separación de «Nuevo turno», «Volver al horario» y «Confirmar turno». Confirmá el antecedente de botones con Estefi: la única diferencia documentada en C-15 es la insignia «Manual» solo en Día; no demuestra una diferencia de botones. Por fila agregá revisión, método, viewport, referencia, resultado y diferencia sanitizada; elevá también las diferencias ya observadas en C-04/C-10. El cotejo puede hacerse post-demo, pero no se da por completado por la fecha ni por este traspaso. No hagas correcciones de código ni apruebes diferencias sin revisión humana.

### 2.7 · Ensayo final separado y cronometrado

Ejecutá un recorrido completo separado del smoke y de la concurrencia con la candidata integrada. Aislá los datos de los otros recorridos: usá una base local descartable, prepará P-R/O-C/W según el guion fuera del cronómetro y no reutilices altas previas ni datos del seed para gestión. Si los huecos requeridos ya no son elegibles, registrá el bloqueo: no presentes el recorrido como ensayo final completo. Si hace falta limpiar antes para garantizar el aislamiento, solo el operador ejecuta los pasos 1–3 del [README](../../../../README.md#correr-la-demo-en-la-notebook): app detenida, destino local descartable verificado y reset oficial, seguido de arranque y Flyway. Si no se puede garantizar el aislamiento sin reset, no arranques el ensayo sobre datos ambiguos.

Registrá candidata, fecha, hora y zona (`America/Argentina/Buenos_Aires`), inicio al comenzar el **login**, fin al completar **logout** con redirección al intentar reabrir la agenda, y tiempo transcurrido real (**10–15 min**, sin preparación ni limpieza). Registrá resultados y cobertura efectiva por paso, con los bloqueos y las contingencias, no los 13 min 30 s teóricos del guion. Al terminar, el operador detiene la app y, para la limpieza final, sigue exactamente los pasos 1–2 del README con el `psql.exe` instalado: confirma el destino local y el reset de `public`, verifica que el esquema quedó vacío y no vuelve a arrancar. No uses la base de test ni ejecutes el reset si el destino es ambiguo o hay datos que preservar. Separá la confirmación del operador de cualquier verificación del puerto por coordinación.

Consolidá los enlaces a A y B, el alcance del [guion](../guion-demo.md#cobertura-y-lo-que-no-está), los pendientes y las suites en verde con el hash que las respalda; declaralas heredadas cuando corresponda. Solicitá la revisión final solo con evidencia de la cobertura real, el tiempo medido y los faltantes explícitos.

### 2.8 · Entrega y revisión humana

Cuando B esté acreditado, entregale a Estefi el hash de la candidata y la relación con el hash ensayado, [guion](../guion-demo.md), [evidencia de A](A-arranque-datos-tests.md), [smoke y ensayo](B-smoke.md), [matriz y diferencias](B-visual.md) y este resumen. Pedile que revise resultados, cobertura, bloqueos y diferencias; registrá revisor, fecha, revisión y decisión. Si falta evidencia, identificá el faltante sin pedir aceptación final en su lugar. **Solo Estefi**, después de completar B y aprobar la revisión, integra, marca C-16 en `CHANGES.md` y archiva según el [diseño](../design.md#migration-plan).
