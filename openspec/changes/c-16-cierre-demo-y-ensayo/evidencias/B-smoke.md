# Frente B — Evidencia parcial de smoke

**Estado (actualizado 25/11 01:00): 2.3 completo; la reapertura del enlace nuevo quedó acreditada en el ensayo final. 2.4 acreditado con el solapamiento medido. 2.7: ensayo de 11 min 31 s con desvíos declarados. Ver [retoma en la notebook de demo](#retoma-en-la-notebook-de-demo--2411) y [ensayo final](#27--ensayo-final-cronometrado--2511). Pendiente: revisión humana (2.8).** Las secciones siguientes, hasta la retoma, son la evidencia histórica de Facu en su notebook. Referencias: [guion](../guion-demo.md), [diseño D4/D6/D7](../design.md), [tareas B](../tasks.md), [pendientes](B-pendientes.md).

- **Revisión candidata exacta (2.1):** `ff603a30bf822851e55d75d7c1b98c2359f0604d`, HEAD de `chore/c-16-guion-y-verificacion` al preparar esta evidencia; verificada por coordinación. El operador reportó el recorrido sobre el arranque local recuperado descrito abajo.
- **Fecha/hora y zona:** preflight y primer arranque documentados el 24/11/2026. Horas de turnos en ART (`America/Argentina/Buenos_Aires`); no se informó la hora efectiva del servidor durante el smoke manual ni el margen medido al confirmar las altas online o destino de reprogramación. La medición de navegador de B no sustituye ese dato.
- **Contexto:** notebook local, `http://localhost:8080`, perfil `demo`, datos V900/V901 y solo el buzón Gmail propio autorizado por el operador. El operador reportó el reset inicial y el smoke en respuestas paso a paso; no precisó el tipo de ventana o perfil usado para el guion. La [evidencia visual](B-visual.md) separa la observación directa preautenticación/pública del recorrido asistido.
- **Revisión humana:** pendiente; revisor, revisión exacta, fecha, bloqueos/diferencias y decisión. Vincular [comparación visual](B-visual.md) y evidencia sanitizada.
- Usá `Pendiente`, `Aprobado`, `Fallo` o `Bloqueado`. Indicá qué se ejecutó de verdad; si hubo una parte no ejecutada, separala en otra fila y dejala pendiente. No adjuntes secretos, cookies, tokens, enlaces completos, destinatarios, teléfonos ni trazas sin sanear.

## 2.1 · Base integrada y entrega de A

- Base de la propuesta integrada: `be7caff` (main local y merge-base). `ff603a3` es el merge del PR #28: primer padre `be7caff`, segundo padre `658101511ec64d6a01e7cc678798c132067b6df1` (`6581015`), de `chore/c-16-arranque`. A es ancestro de HEAD (salida 0) y el diff entre ambos commits está vacío.
- El [registro de A](A-arranque-datos-tests.md) no informa bloqueos funcionales abiertos. Preflight elevado a coordinación: A dejó reservas de ensayo en la base local. Antes del smoke, el ensayo final o la demo, el operador debe verificar el destino descartable con la app detenida y completar el reset y arranque según [README](../../../../README.md#correr-la-demo-en-la-notebook). B no ejecutó ese procedimiento en esta etapa. El detalle de codificación en la consola de A no afectó la app.
- **La tabla siguiente es evidencia heredada de Estefi (24/11/2026), no ejecuciones de B ni resultados nuevos sobre `ff603a3`.** B no reejecutó suites; se distinguen abajo las observaciones directas de B/coordinación y el smoke reportado por el operador.

| Evidencia heredada | Resultado registrado por A |
| --- | --- |
| Baseline sobre `be7caff` | `./gradlew build` en `backend/`: 321 unitarios y 459 de integración contra PostgreSQL real, sin fallos ni omitidos. En `frontend/`, lint sin errores y Vitest con 56 archivos, 678 tests en verde. |
| [Suite final con V901](A-arranque-datos-tests.md#suite-final) | `./gradlew build` en `backend/` con frontend incluido: 321 unitarios y 461 de integración contra PostgreSQL real, 0 fallos, errores u omitidos; `DatosDemoIT` 13/13, con dos casos nuevos. En `frontend/`: `npm run lint` sin errores, `npm test` con 56 archivos y 678 tests en verde, `npm run build` OK (`tsc --noEmit` y Vite). |
| [Concurrencia automatizada de A](A-arranque-datos-tests.md#suite-final) | `ReservasConcurrentesApiIT` 10/10 y `ReservasConcurrentesIT` 5/5: una sola reserva ganadora y la otra con conflicto `RN-TU-01`. Esto no acredita el smoke concurrente manual 2.4. |
| Arranque de A desde cero | Reset local de `public` con la app detenida; `bootRun` con perfil `demo` empaquetó y copió el frontend; Flyway aplicó V2, V3, V9, V900 y V901. Arranque en 10,7 s, SPA 200, perfil público con cuatro prestaciones, sesión anónima 401, disponibilidad consultada y login del odontólogo verificado en navegador, con los turnos del seed en agenda. |
| Gmail en A | Con datos sintéticos y buzón controlado, llegó la confirmación, el enlace abrió la gestión en la notebook, se reprogramó y llegó un nuevo correo con enlace nuevo; con ese último enlace se canceló y los cambios se reflejaron en agenda. A registra que llegaron todos los correos, sin indicar si llegaron a bandeja o spam. No se forzó un fallo de envío en vivo; la persistencia con `ERROR`/`SMTP_FALLO` quedó cubierta por `NotificacionesEmailIT` y `RegistroNotificacionIT` de A. No acredita el recorrido de correo de B. |

## Preflight local de B — no es smoke

- **Observado por B:** 2026-11-24 12:51 (UTC−03:00, `America/Argentina/Buenos_Aires`). `postgres` escucha en TCP 5432 y el servicio `postgresql-x64-19` está en ejecución. La ruta de PostgreSQL 18 del README no existe en esta notebook; `C:\Program Files\PostgreSQL\19\bin\psql.exe` está disponible (19.0).
- **Intervención manual inicial:** el usuario confirmó «Puerto libre y reset OK» luego de recibir el comando oficial del README con la ruta local de `psql` 19, y la indicación de comprobar el destino local descartable `localhost:5432/turnotarde`, usuario `turnotarde`, esquema `public`, antes de confirmar. El usuario reportó el esquema vacío; B no ejecutó ni observó la sesión SQL. La limpieza posterior al ensayo se registra por separado al final.
- **Comprobación local posterior:** TCP 8080, inicialmente ocupado por `PEMHTTPD-x64` (`httpd`), quedó libre antes del arranque. `pg_isready -h localhost -p 5432 -t 3` informó que acepta conexiones; no es una comprobación autenticada del contenido de la base. No se accedió a credenciales ni se detuvieron servicios desde B.

## Arranque local — fallo inicial y recuperación

- **Revisión y comando:** `ff603a3` (2026-11-24, hora local de Buenos Aires), desde `backend/`: `$env:SPRING_PROFILES_ACTIVE = 'demo'; .\gradlew.bat bootRun`.
- **Observado:** Gradle ejecutó `npmBuild`, `copyFrontend` y `processResources`. Flyway aplicó V2, V3, V9, V900 y V901. Después falló el arranque de Spring; no se confirmaron `Started TurnoTardeApplication` ni la aplicación de la contraseña de demo. La comprobación HTTP de `/reservar/demo` no obtuvo respuesta.
- **Diagnóstico y recuperación reportados por el operador:** `IllegalStateException` vinculada a `MAIL_USERNAME`. El operador completó la configuración local y reejecutó el `bootRun` del README sin repetir el reset; reportó «App lista». B no leyó `.env`, valores de entorno ni credenciales.
- **Readiness observado por coordinación tras la recuperación:** listener en TCP 8080 y HTTP `200` en `/login`, `/reservar/demo` y `/api/public/demo`. Esto acredita disponibilidad de rutas, no autenticación.
- **Estado de la base durante este arranque:** el operador informó el reset inicial; Flyway volvió a poblar `public` hasta V901 durante el primer intento. No hubo un nuevo reset para ese reintento.

## Pasos del smoke — resultados parciales

| Paso | Resultado esperado | Resultado observado | Resultado de la prueba |
| --- | --- | --- | --- |
| 1 · Login | Con credenciales de demo, entra a `/agenda`. | Reportado por operador: inició sesión y entró a la agenda. | Aprobado (reportado) |
| 2a · Horarios | Se guarda la franja de ejemplo sin cambiar las franjas del 25–27. | Reportado por operador: guardó el sábado de 10:00 a 11:00. No se detalló un cotejo de las demás franjas. | Aprobado para el alta (reportado); cotejo pendiente |
| 2b · Prestaciones | Se crea una prestación sintética de 15 min sin editar las del seed. | Reportado por operador: creó la prestación sintética de 15 min. No se detalló un cotejo del catálogo anterior. | Aprobado para el alta (reportado); cotejo pendiente |
| 2c · Bloqueo | Se guarda el rango; no se ofrece en disponibilidad pública ni se cancelan turnos. | Reportado por operador: bloqueo solo para el 28/11, sin turnos afectados; verificó su exclusión en la reserva pública. | Aprobado (reportado) |
| 3a · Disponibilidad y anticipación | El hueco de P-C está disponible y conserva el margen; no se exponen otros pacientes. | Reportado por operador: usó el 26/11 a las 18:30 para P-C. Observado por B, en otra comprobación sin alta: Consulta de 30 min disponible el 26/11 a las 18:00, a 52,68 h respecto del encabezado HTTP `Date`. No usar esa medición como hora/margen del smoke manual. | Pendiente: margen efectivo de altas/reprogramación y detalle de la vista manual |
| 3b · Alta online | Se registran las reservas de paciente del guion. | Reportado por operador: cuatro altas online (P-C, P-R, O-C y W). P-C se reservó primero el 26/11 a las 18:30; P-R reutilizó ese horario solo después de la cancelación de P-C. Nunca coexistieron y esta secuencia no acredita concurrencia. | Aprobado para las altas (reportado) |
| 4a · Confirmación Gmail | La confirmación se verifica en el buzón, antes que inferirla de SMTP. | Reportado por operador: revisó correos en el buzón local controlado. No precisó bandeja/spam ni el cotejo de todos los campos de P-C. | Aprobado para recepción (reportado); detalle pendiente |
| 4b · Gestión P-C y P-R | Los enlaces genuinos abren las respectivas pantallas de gestión. | Reportado por operador: comprobó los enlaces de P-C y P-R en el buzón controlado y gestionó los dos turnos por separado. No fue necesario invocar la contingencia por no entrega. | Aprobado (reportado) |
| 5a · Cancelación P-C | Original cancelado, solo lectura, intervalo liberado y aviso recibido. | Reportado por operador: canceló P-C (26/11 18:30), quedó en solo lectura, se liberó el intervalo y llegó el aviso. | Aprobado (reportado) |
| 5b · Reprogramación P-R → P-R2 | Original cancelado y sucesor reservado. | Reportado por operador: el turno P-R, independiente de P-C, pasó del 26/11 a las 18:30 al 27/11 a las 16:00; original cancelado y sucesor reservado. | Aprobado (reportado) |
| 5c · Correo nuevo P-R2 | Llega una confirmación con un enlace propio del sucesor. | Reportado por operador: recibió un enlace nuevo para el sucesor y ubicó P-R2 en Semana. No detalló por separado la reapertura de ese nuevo enlace ni los campos del correo. | Aprobado para recepción (reportado); verificación del nuevo enlace pendiente |
| 6a · Agenda Día | El 25/11 muestra los seis activos y el cancelado según filtro; estado y origen visibles donde corresponda. | Reportado por operador: vio seis activos de V900/V901, incluidos los manuales de 12:00 y 18:00; el de 11:00 figuró cancelado al usar el filtro. | Aprobado (reportado) |
| 6b · Agenda Semana | Permite cambiar de vista y ubicar turnos del 26/27. | Reportado por operador: pasó a Semana, ubicó P-R2 y volvió. No detalló navegación anterior/siguiente. | Aprobado para cambio de vista y ubicación (reportado); navegación por rangos pendiente |
| 7a · Cancelación O-C | El odontólogo cancela O-C y libera su intervalo; W queda reservado. | Reportado por operador: canceló O-C (26/11 19:00) como odontólogo y confirmó la liberación. W siguió reservado. | Aprobado (reportado) |
| 7b · Aviso de cancelación O-C | Se comprueba recepción en el buzón controlado. | Reportado por operador: confirmó el correo de cancelación de O-C. No precisó bandeja/spam. | Aprobado para recepción (reportado) |
| 8 · WhatsApp de W | Se abre WhatsApp con texto preparado, sin enviarlo. | Reportado por operador: W (26/11 19:30) sigue reservado; WhatsApp abrió con texto y no se envió ningún mensaje. No se documentaron detalles de popup o de la vista desde la que se abrió. | Aprobado para apertura sin envío (reportado) |
| 9a · Alta manual M | Se registra un turno futuro de origen `MANUAL` y se confirma el alta. | Reportado por operador: alta para 26/11 18:00 con origen manual y confirmación. El operador confirmó explícitamente que los campos del paciente eran sintéticos y que autorizó usar solo su propio Gmail para verificar el envío. | Aprobado para alta y origen (reportado) |
| 9b · Anticipación del alta manual | Para acreditar la excepción online de 24 h se requiere hora efectiva del servidor y un turno futuro a menos de 24 h. | El 26/11 18:00 no acredita por sí solo ni alta en el día ni un margen menor de 24 h. No se informó la hora de ejecución. | Pendiente |
| 9c · Correo de alta manual | El buzón controlado recibe la confirmación y el enlace abre gestión en localhost. | El operador eligió inicialmente «alta manual con correo» e indicó que usó los datos para verificar el envío. No detalló por separado la recepción ni confirmó la apertura del enlace de M. | Pendiente de acreditar entrega y apertura |
| 10 · Logout | Retorna a `/login` y reabrir `/agenda` redirige sin sesión. | Reportado por operador: confirmó el cierre de sesión y la redirección de la ruta privada. | Aprobado (reportado) |
| 2.4a · Concurrencia manual | Mismo intervalo elegible, distinto del guion, dos contextos sin sesión y DNIs sintéticos distintos, confirmaciones solapadas; una `201`, una `409` `RN-TU-01` y una reserva activa. | El operador reportó dos clics humanos casi simultáneos en dos pestañas y confirmó el resultado de la opción inicial: una `201`, una `409` `RN-TU-01` y un solo turno activo. No pudo mostrar solicitudes solapadas ni indicó el hueco real. Tampoco detalló el aislamiento de contextos, el aviso ni el refresco de disponibilidad. El reúso secuencial P-C/P-R no es evidencia concurrente. | Pendiente de acreditar el solapamiento y el contexto |
| 2.4b · Test automatizado de A (`ReservasConcurrentesApiIT`) | Una `201`, una `409` `RN-TU-01` y una fila reservada contra PostgreSQL real; no sustituye la ejecución manual. | [Suite final de A](A-arranque-datos-tests.md#suite-final): 10/10 en `./gradlew build` sobre la candidata de A, incorporada sin cambios de árbol a `ff603a3`. Sin reejecución en B. | Aprobado en A (heredado); 2.4 manual pendiente |
| 2.5 · Navegador local | Registrar cobertura real en escritorio y a 360 px, método y resultados. | B observó preautenticación y reserva pública sin envío a 1440 × 900 y 360 × 800. El operador confirmó el recorrido visual asistido a 1357 × 948 y 360 × 800; detalle en [B-visual](B-visual.md). | Aprobado para verificación de navegador; manual asistida en los estados restantes |
| 2.6 · Comparación visual | Comparar el recorrido con los mockups revisión 2 y registrar diferencias. | Solo se compararon los estados originales documentados de C-04/C-10. La recorrida manual no agregó comparaciones. | Pendiente post-demo y de revisión humana |
| 2.7 · Ensayo final y consolidación | Ensayo en la candidata integrada; suites de A documentadas en verde, evidencia A/B y alcance/pendientes para revisión final. | El operador reportó ensayo terminado, separado del smoke; no aportó duración exacta, detalle ni evidencia. Ver abajo. | Pendiente de acreditación |
| 2.8 · Entrega B | Entregar revisión candidata, guion, evidencia, resultados y diferencias para aprobación humana. | [Resumen local de pendientes](B-pendientes.md); no hay evidencia suficiente para solicitar la aceptación final de Estefi. | Pendiente |

## Ensayo final y limpieza — no son el smoke

- **Ensayo final (reportado):** tras recibir instrucciones para reset limpio, reconstrucción, preparación de P-R, O-C y W, y cronometraje, el operador reportó que completó el ensayo sobre `ff603a3`, separado del smoke. Luego explicitó que no tenía duración exacta, detalles ni evidencia. No se confirma a partir de esas instrucciones un reset intermedio ni resultados por paso. Los **13 min 30 s** del guion son teóricos, no una duración observada.
- **Detención:** el operador reportó que detuvo `bootRun` con `Ctrl+C`. Coordinación confirmó que no había listener en TCP 8080 y que `pg_isready` en `localhost:5432` aceptaba conexiones. Esto no verifica el contenido del esquema.
- **Reset final (reportado):** con el script del README y la ruta local de `psql` 19, el operador verificó la base `turnotarde`, el usuario `turnotarde`, loopback local, puerto `5432` y esquema `public`, comprobó el mensaje de éxito y confirmó «Esquema vacío confirmado». B no hizo una consulta SQL directa ni accedió a credenciales. No se volvió a arrancar la app.

## Retoma en la notebook de demo — 24/11

Estefi retomó B en **la notebook donde corre la demo**, con asistencia de Claude Code. Revisión `b248226` de `chore/c-16-guion-y-verificacion`: solo agrega documentación sobre `ff603a3`, que es ancestro, y el diff no toca código ni configuración. Horas en ART (`America/Argentina/Buenos_Aires`). El estado de la notebook de Facu no se trasladó: acá se hizo un reset propio.

**Fuentes.** «Directo» indica que Claude lo observó en HTTP, en Playwright o en el log de la app. «Operador» indica que Estefi lo informó. «Servidor» indica que sale de una consulta SQL de solo lectura que ejecutó Estefi sobre `turno` y `notificacion`, sin columnas de datos personales. No se guardaron nombres reales, emails, teléfonos, tokens ni enlaces de gestión.

### Preparación

| Paso | Observado | Fuente |
| --- | --- | --- |
| Destino y app | PostgreSQL 18 aceptaba conexiones en `localhost:5432`, el puerto 8080 estaba libre y no había ningún proceso `java`. | Directo |
| Reset | `backend/scripts/reiniciar-base-demo.sql`, con la base `turnotarde`, el usuario `turnotarde`, un servidor local y la confirmación `BORRAR`. Resultado: «Reset OK». | Operador |
| Arranque | 16:43, `SPRING_PROFILES_ACTIVE=demo`, `gradlew bootRun`. Flyway aplicó V2, V3, V9, V900 y V901 («now at version v901»). `Started TurnoTardeApplication` en 6,3 s y «Contraseña de demo aplicada». `/login`, `/reservar/demo` y `/api/public/demo` respondieron 200. | Directo |
| Línea base pública | Antes de cualquier alta, se guardó una foto de la disponibilidad de las 4 prestaciones del 25 al 27/11. | Directo |

### Márgenes medidos por el servidor (`inicio − creado_en`)

| Ref. | Turno | Inicio | Origen | Margen | Estado final | Resultado |
| --- | --- | --- | --- | --- | --- | --- |
| P-C | 12 | 26/11 18:00 Consulta | ONLINE | 49,23 h | CANCELADO por el paciente | Aprobado (> 24 h) |
| P-R | 13 | 26/11 18:30 Consulta | ONLINE | 49,71 h | CANCELADO por el paciente (reprogramado) | Aprobado |
| P-R2 | 14, desde el 13 | 27/11 16:00 Consulta | ONLINE | 71,21 h | Después lo canceló la odontóloga en la verificación visual | Aprobado |
| O-C | 15 | 26/11 19:00 Consulta | ONLINE | 50,19 h | CANCELADO por la odontóloga | Aprobado |
| W | 16 | 26/11 19:30 Consulta | ONLINE | 50,68 h | RESERVADO | Aprobado |
| Manual adicional | 17 | 27/11 09:15 Arreglo de caries | MANUAL | 64,33 h | RESERVADO | Alta manual; no acredita la excepción de 24 h |
| **M** | 18 | **25/11 11:00 Extracción** (11:00–12:00, pegado a la Limpieza manual de las 12:00) | **MANUAL** | **18,00 h** | RESERVADO | **Aprobado: 0 < margen < 24 h** |

Todos los inicios caen en la grilla de 15 min, dentro de la franja, sin solaparse con otros turnos ni con bloqueos. Si no fuera así, la constraint y las reglas habrían rechazado el alta.

### Cierre de los huecos que dejó Facu

| Hueco | Observado | Fuente | Resultado |
| --- | --- | --- | --- |
| Franjas originales intactas tras agregar la del sábado | Configuración muestra lunes a viernes 09:00–13:00 y 16:00–20:00, sábado 10:00–11:00 y domingo sin atención. La disponibilidad pública del 25 al 27 solo cambió en los huecos que ocuparon las altas. | Directo | Aprobado |
| Catálogo original intacto y alta de una prestación de 15 min | Las 4 prestaciones originales no cambiaron. En el recorrido del operador la prestación nueva no llegó a guardarse. Claude la creó desde la sesión local: `POST /api/prestaciones` devolvió 201 y «Control rápido · 15 min · Activa» apareció en la lista y en el perfil público. | Directo y servidor | Aprobado; el alta no la hizo el operador |
| Bloqueo del sábado 28 | Bloqueo del 28/11 10:00 al 29/11 00:00, sin turnos afectados. La reserva pública del 28 muestra «No hay horarios libres este día». | Directo | Aprobado |
| Vista pública sin datos de otros pacientes | La selección pública muestra solo prestación, día, horarios, odontóloga y dirección. | Directo | Aprobado |
| Navegación anterior/siguiente en Semana | La operadora la recorrió («funciona»). Claude también vio la vista Semana y su navegación. | Operador y directo | Aprobado |
| Cancelación de P-C por enlace | Turno 12 `CANCELADO/PACIENTE` y `CANCELACION ENVIADO` a las 17:02. El 26/11 18:00 volvió a ofrecerse en la reserva pública. La vista de solo lectura de P-C se capturó en C-11. | Servidor y directo | Aprobado |
| Correo nuevo de P-R2 | `REPROGRAMACION ENVIADO` para el turno 14 y la operadora informó que llegó. La **reapertura del enlace nuevo** no quedó detallada. | Servidor y operador | Recepción aprobada; reapertura pendiente para el ensayo final |
| Correo y enlace de M | `CONFIRMACION ENVIADO` para el turno 18 a las 17:00. Su enlace abrió la gestión en localhost con «Reservado», «Cambiar horario» y «Cancelar turno». | Servidor y directo | Aprobado |
| Bandeja o spam | La operadora confirmó, el 25/11, que todos los correos llegaron a la **bandeja de entrada**. | Operador | Aprobado |
| WhatsApp de W | Se abrió con el texto cargado. **Desvío del guion:** la operadora envió el mensaje a su propio teléfono de prueba. El guion pide no enviarlo en la demo. El botón está en el detalle de Día y de Semana. | Operador y directo | Apertura aprobada; desvío registrado |
| Fallo de SMTP | No ocurrió: las 15 notificaciones del recorrido quedaron `ENVIADO`. La persistencia ante un fallo de envío sigue cubierta por los IT de A. | Servidor | Sin fallos |

### 2.4 · Concurrencia manual con solapamiento medido

Se hizo aparte del guion, en un hueco que no usa ningún paso: Consulta del 27/11 a las 10:30, con unas 65 h de anticipación. Los dos formularios estaban en **dos contextos de navegador aislados** de Playwright (Chromium), sin sesión ni cookies compartidas, con DNIs ficticios distintos (40990033 y 40990044). El teléfono y el email los cargó la operadora: su propio buzón y un teléfono de prueba. Cada página programó el clic de «Confirmar reserva» para el mismo instante (20:13:47.910 UTC). Los tiempos salen de la Resource Timing API, referidos al primer envío.

| Contexto | Envío del POST | Fin | HTTP | Interfaz |
| --- | --- | --- | --- | --- |
| 1 | 0,0 ms | 77,8 ms | **201** | «¡Turno reservado!» |
| 2 | 0,2 ms | 100,8 ms | **409** | «Ese horario acaba de ser tomado, elegí otro» y «Ya actualizamos los horarios disponibles» |

- **Solapamiento acreditado:** el segundo pedido empezó a los 0,2 ms y el primero terminó a los 77,8 ms, así que estuvieron pendientes al mismo tiempo.
- **Una sola fila:** en el servidor existe una sola fila con inicio el 27/11 a las 10:30 (turno 20, que se canceló después en la verificación visual). El 10:30 desapareció de la disponibilidad pública. Entre el turno 20 y el turno 22 falta el id 21. Esto es compatible con un INSERT rechazado por la constraint, pero es una inferencia y no se verificó.
- **Intento descartado:** el primer intento, a las 10:00 (turno 19), devolvió 201 y 409, pero los pedidos no se superpusieron: el segundo salió a los 60 ms, cuando el primero ya había terminado a los 50 ms. No cuenta como prueba de concurrencia.
- **Prueba automatizada:** [`ReservasConcurrentesApiIT` 10/10 de A](A-arranque-datos-tests.md#suite-final) es la evidencia automatizada y no se volvió a ejecutar. Esta prueba manual no la reemplaza.

## 2.7 · Ensayo final cronometrado — 25/11

Se hizo en la notebook de demo, sobre la revisión `b248226`, después de un reset propio el 24/11 a las 18:02 (Flyway v901; el operador confirmó el reset). Los turnos de preparación (P-R, O-C y W, ids 12 a 14) se cargaron fuera del cronómetro, el 24/11 entre las 18:08 y las 18:10. La notebook se suspendió y la app se cayó; Claude la volvió a levantar el 25/11 a las 00:34 sin reset, y los datos de preparación se conservaron. El ensayo transcurrió entre las 00:46 y las 00:58 ART.

- **Duración informada por el operador: 11 min 31 s**, del login al logout con la redirección. Está **dentro del rango de 10 a 15 minutos**. Los 13 min 30 s del guion son teóricos.
- **Desvío declarado por el operador:** se confundió entre P-C y P-R. Reprogramó primero la reserva en vivo, la canceló y repitió el flujo con altas nuevas. El turno de preparación P-R (id 12, 26/11 18:30) quedó sin usar. La duración incluye esos reintentos, así que **no es un recorrido limpio del guion**. Sí recorre todos sus pasos.

| Paso | Evidencia (servidor salvo que se indique otra fuente) | Resultado |
| --- | --- | --- |
| 1 · Login | El operador inició el cronómetro al iniciar sesión. | Aprobado (reportado) |
| 2 · Configuración | Franja del sábado de 10:00 a 11:00 agregada, y las de lunes a viernes sin cambios. Prestación «Control Rápido» de 15 min creada y visible en el perfil público. Bloqueo del 28/11 de 10:00 a 12:00; el 28 no ofrece horarios. | Aprobado |
| 3 · Reserva online | Id 15: 26/11 18:00, `ONLINE`, margen de 41,18 h. Id 17: 26/11 18:00, Control Rápido, 41,12 h. Id 18: 26/11 18:00, 41,09 h. | Aprobado (> 24 h) |
| 4 · Correo | `CONFIRMACION ENVIADO` para los ids 15, 17 y 18. El operador confirmó que llegaron a la **bandeja de entrada**. | Aprobado |
| 5 · Gestión | Cancelación por el paciente: ids 17 y 15, este último cancelado al reprogramarse. Reprogramaciones: del 15 al 16 y del 18 al 19 (27/11 16:00, margen de 63 h), ambas con `REPROGRAMACION ENVIADO`. **El id 16 lo canceló el PACIENTE**, lo que solo es posible con el **enlace nuevo del sucesor**: queda acreditada la reapertura del enlace reprogramado, que era el hueco pendiente de 2.3. | Aprobado |
| 6 · Agenda | El operador la recorrió. Las vistas Día y Semana se cotejaron en 2.6. | Aprobado (reportado) |
| 7 · Cancelación por la odontóloga | Id 13 (O-C, 26/11 19:00) en `CANCELADO/ODONTOLOGO` y `CANCELACION ENVIADO` a las 00:55. | Aprobado |
| 8 · WhatsApp | W (id 14, 26/11 19:30) sigue `RESERVADO`. El operador informó que «funciona», sin más detalle. | Aprobado (reportado) |
| 9 · Turno manual | Id 20: **25/11 16:00, `MANUAL`, margen de 15,05 h**, con `CONFIRMACION ENVIADO`. | Aprobado (0 < margen < 24 h) |
| 10 · Logout | El operador paró el cronómetro con la redirección al login. | Aprobado (reportado) |

- **Correo:** las 12 notificaciones del ensayo quedaron `ENVIADO`, sin errores de SMTP.
- **Log de la app:** solo hay 2 `ClientAbortException`, que son conexiones cortadas por el navegador y no fallos de la app.
- **Limpieza:** antes de la demo hace falta un reset y volver a preparar P-R, O-C y W, porque el estado actual tiene turnos del ensayo.
