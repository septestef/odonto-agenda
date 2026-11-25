# Evidencia del frente A — arranque, datos y tests

Responsable: Estefi. Fecha: 2026-11-24. Máquina: notebook de Estefi (Windows 11, JDK 25.0.4.1, Node 24.20.0, PostgreSQL 18.6 local).
Rama: `chore/c-16-arranque`, base `be7caff` (main con la propuesta integrada).
Sin secretos: no se registran contraseñas, `.env`, cookies, tokens ni links de gestión.

| Tarea | Paso | Resultado |
|---|---|---|
| 1.1 | Baseline antes de tocar nada, sobre `be7caff`: `./gradlew build` desde `backend/` | OK — 321 unitarios y 459 de integración contra PostgreSQL real (`turnotarde_test`), 0 fallos, 0 omitidos. Incluye `ReservasConcurrentesApiIT` y `ReservasConcurrentesIT`. 4 min 34 s. |
| 1.1 | Baseline frontend: `npm run lint` y `npm test` desde `frontend/` | OK — lint sin errores; Vitest 56 archivos, 678 tests en verde. |
| 1.2 | Revisión de `.env.example` contra design D3 | OK sin cambios — ya enumera las 16 variables; `TEST_DB_PASSWORD` está comentada para heredar `DB_PASSWORD`. |
| 1.3 | Reset con `backend/scripts/reiniciar-base-demo.sql` (nuevo), app detenida | OK — destino verificado por el script: base `turnotarde`, usuario `turnotarde`, servidor `::1`, puerto 5432. Confirmado con `BORRAR`; se borró solo el esquema `public` (8 tablas, `btree_gist`, la función de serialización y el historial de Flyway). |
| 1.4 | `V901__seed_demo_agenda_25.sql` en `db/demo` (V900 sin tocar) | OK — 2 pacientes ficticios y 4 turnos el 25/11: 10:30 Consulta, 12:00 Limpieza (MANUAL), 16:30 Extracción, 18:00 Arreglo de caries (MANUAL). El 26 y el 27 sin cambios. |
| 1.4 | `DatosDemoIT` actualizado: conteos (11 turnos, 6 pacientes, versión 901) y dos casos nuevos (agenda del 25/11; huecos con `Clock` fijo el 25/11 a las 09:00: online el 26 y el 27, ninguno online el 25, manual el 25 a las 11:00 y 16:00) | OK — 13/13 en verde. |
| 1.5 | Arranque desde cero (`static` generado borrado antes): `$env:SPRING_PROFILES_ACTIVE = 'demo'; .\gradlew.bat bootRun` | OK — `copyFrontend` y `processResources` corren dentro de `bootRun`; Flyway aplicó V2, V3, V9, V900 y V901 sobre el esquema vacío; `Started TurnoTardeApplication` en 10,7 s; contraseña de demo aplicada. |
| 1.5 | Comprobaciones en `http://localhost:8080` | OK — SPA (`/`) 200 con título OdontoAgenda; `GET /api/public/demo` con 4 prestaciones; `/api/auth/me` sin sesión 401; disponibilidad del 25/11 solo desde las 16:00 (24 h desde las 12:24 del 24/11) y huecos el 26 y el 27. Login del odontólogo verificado por Estefi en el navegador: la agenda muestra los turnos del seed. |
| 1.6 / 1.7 | Suite final con V901 | Ver tabla "Suite final". |
| 1.8 | Ensayo de Gmail con datos sintéticos y buzón de prueba propio (hecho por Estefi en la notebook) | OK — llegó la confirmación; el link de gestión abrió en la notebook; se reprogramó, llegó el nuevo correo con link nuevo y con ese link se canceló. Los turnos se reflejaron en la agenda del odontólogo. Llegaron todos los correos. El caso "falla el envío y el turno persiste" no se forzó en vivo; lo cubren `NotificacionesEmailIT` y `RegistroNotificacionIT` (estado `ERROR`/`SMTP_FALLO` con el turno confirmado), en verde en la suite. |
| 1.9 | README: sección `Correr la demo en la notebook` con pasos PowerShell y checklist | OK — secuencia igual a la ensayada en 1.3 y 1.5. |

## Suite final

Sobre la revisión candidata (`be7caff` + cambios de A), con la app detenida:

| Comando | Resultado |
|---|---|
| `./gradlew build` desde `backend/` (incluye `npmBuild` y `copyFrontend`) | BUILD SUCCESSFUL en 4 min 27 s — 321 unitarios y 461 de integración contra PostgreSQL real, 0 fallos, 0 errores, 0 omitidos. Los 2 de integración nuevos son los casos de V901 en `DatosDemoIT` (13/13). |
| `ReservasConcurrentesApiIT` y `ReservasConcurrentesIT` | 10/10 y 5/5 en verde: siempre una sola reserva ganadora y la otra con conflicto RN-TU-01. |
| `npm run lint` desde `frontend/` | OK, sin errores. |
| `npm test` desde `frontend/` | 56 archivos, 678 tests en verde. |
| `npm run build` desde `frontend/` | OK (`tsc --noEmit` + Vite). |

## Notas para B

- Para reservar en vivo durante la demo: 26/11 o 27/11 (24 h). El 25/11 a la mañana la reserva online no ofrece horarios del mismo día; el alta manual sí (por ejemplo 11:00 o 16:00).
- Las reservas de ensayo de este frente quedaron en la base local; antes del ensayo final o de la demo, repetir el reset (README, paso 2).
- El log de arranque muestra "Contrase├▒a" con caracteres rotos en la consola de Windows: es solo la codificación de la terminal, no afecta a la app.
