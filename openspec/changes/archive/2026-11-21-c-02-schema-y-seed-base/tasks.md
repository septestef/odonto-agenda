# Tasks — C-02 `schema-y-seed-base`

> Cada tarea es de 2 h como máximo. TDD: el test se escribe primero y se lo ve en rojo. Los `*IT` corren con `./gradlew integrationTest -PskipFrontend` contra `turnotarde_test` (nunca H2). Decisiones en `design.md` (D1–D10).
>
> Mientras `V2` no esté mergeada, si hay que corregirla después de aplicarla en una base local, reiniciar ese esquema (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;` como `turnotarde`) en vez de agregar otra migración; Flyway, si no, rechaza el cambio de checksum.

## 1. Soporte de tests y convención de migraciones

- [x] 1.1 Crear en `backend/src/test/java/ar/turnotarde/soporte/` el helper `LimpiezaBase` (TRUNCATE ... RESTART IDENTITY CASCADE de las 7 tablas), los fixtures JDBC con un instante fijo para `creado_en`/`actualizado_en` y `RelojDePrueba` (Clock mutable, fijable y avanzable) con su `@TestConfiguration` `@Primary` (D9). Verificar: `./gradlew compileTestJava -PskipFrontend` compila y `./gradlew test -PskipFrontend` sigue en verde.
- [x] 1.2 Escribir `MigracionesFueraDeOrdenIT` (configuración efectiva de Flyway con `outOfOrder = true`; migraciones de prueba `V2`, `V7` y luego `V5` en `src/test/resources/db/prueba-orden/` aplicadas sobre un esquema descartable) y verlo en rojo; agregar `spring.flyway.out-of-order: true` en `application.yml` (D1). Verificar: el IT pasa.
- [x] 1.3 Agregar al `README.md` la sección "Migraciones": un número por change (`V<NN>__`, `V<NN>_<k>__`), 900+ solo en `db/demo`, `out-of-order` y por qué, nunca editar una migración mergeada, `DEFAULT` obligatorio para columnas `NOT NULL` nuevas en tablas con datos de demo, y cómo reiniciar una base local (D1, Migration Plan). Verificar: la sección existe y los comandos citados coinciden con los del design.

## 2. Esquema `V2` y constraint anti-solapamiento

- [x] 2.1 Escribir `RestriccionesEsquemaIT` con los escenarios de `specs/modelo-datos` (tablas y columnas, FKs, CHECKs de duración, franjas, bloqueo, `dni`, `slug`, estados y `inicio < fin` del turno; UNIQUEs de email, slug, prestación por odontólogo, DNI por odontólogo y `token_hash`; sin columna de token en claro). Verificar: compila y falla porque las tablas no existen.
- [x] 2.2 Escribir `TurnoSinSolapamientoIT` con los escenarios de `specs/turnos-sin-solapamiento` (superposición, turno contenido, contiguos `[)`, otro odontólogo, cancelado superpuesto, cancelar libera, reactivar sobre ocupado), verificando SQLState `23P01` y el nombre `turno_sin_solapamiento`. Verificar: compila y falla en rojo.
- [x] 2.3 Escribir `db/migration/V2__esquema_base.sql` según D2 (`btree_gist WITH SCHEMA public`, 7 tablas, identity, `timestamptz`, auditoría sin `DEFAULT`, CHECKs, UNIQUEs, índices y `turno_sin_solapamiento` literal de la KB) y actualizar `ArranqueAplicacionIT` (reemplazar "no hay tablas de negocio" por "existen las 7 tablas" y "sin perfil demo no se aplicó `V900`"). Verificar: `RestriccionesEsquemaIT`, `TurnoSinSolapamientoIT` y `ArranqueAplicacionIT` en verde.

## 3. Entidades, repositorios y auditoría

- [x] 3.1 Crear `shared/persistencia/EntidadAuditada` y `ConfiguracionAuditoria` (`@EnableJpaAuditing` con `DateTimeProvider` sobre el `Clock`, D5), la entidad `Prestacion` con `desactivar()` y su repositorio; escribir primero en `MapeoPersistenciaIT` los escenarios de auditoría con `RelojDePrueba` (alta en `2026-11-25T12:00:00Z`, modificación una hora después). Verificar: el IT pasa, `SinHoraDelSistemaTest` sigue en verde y `ManejadorErroresTest` (slice web) no se rompe.
- [x] 3.2 Crear las entidades `Odontologo` (con `cambiarPasswordHash`), `HorarioAtencion`, `Bloqueo`, `Paciente`, `Turno`, `Notificacion`, los enums `EstadoTurno`, `OrigenTurno`, `CanceladoPor`, `TipoNotificacion`, `EstadoNotificacion` y un repositorio por entidad (`OdontologoRepository.findBySlug`) según D3–D4; ampliar `MapeoPersistenciaIT` con ida y vuelta de cada entidad (enums, `LocalTime`, `char(64)`, instante UTC estable con otra zona por defecto). Verificar: el contexto arranca con `ddl-auto=validate` y el IT pasa (si `char(64)` falla, aplicar la mitigación R2 y registrarla).
- [x] 3.3 Escribir `ReservasConcurrentesIT` (dos hilos con `TransactionTemplate` + `saveAndFlush`, largada con `CountDownLatch`, 5 repeticiones con base limpia). Verificar: en cada repetición exactamente una transacción confirma, la otra falla con causa `23P01` y queda un solo turno activo.
- [x] 3.4 Escribir `MapeoNoCoincideIT` con la app de prueba en `ar.pruebas.mapeoroto` (fuera del paquete base) y una entidad sobre `tabla_que_no_existe` (D9). Verificar: el arranque falla con `SchemaManagementException` que nombra la tabla y ningún otro IT escanea esa entidad (`./gradlew integrationTest -PskipFrontend` completo en verde).

## 4. Datos de demo y contraseña

- [x] 4.1 Agregar `spring-security-crypto` a `backend/build.gradle.kts` (D6); crear `odontologo/demo/PropiedadesDemo` y `ConfiguracionDemo` (perfil `demo`) y en `application-demo.yml` `app.demo.password: ${DEMO_PASSWORD:}` y `spring.flyway.locations: classpath:db/migration,classpath:db/demo`; escribir primero en `DatosDemoIT` el caso "perfil demo sin contraseña" (esquema `demo_seed`, D9). Verificar: el arranque falla con el mensaje que nombra `DEMO_PASSWORD` y el mensaje no contiene ningún valor sensible.
- [x] 4.2 Escribir `db/demo/V900__seed_demo.sql` según D8 (odontólogo `demo` con hash `!PENDIENTE-DEMO_PASSWORD`, 10 franjas, 4 prestaciones, 4 pacientes `@example.com`, los 7 turnos de la tabla con fechas fijas en hora AR, `token_hash` aleatorio, auditoría fija) y los casos de datos de `DatosDemoIT` (cantidades, semana 23–27/11, un cancelado, todo dentro de franjas, segundo `migrate` sin duplicados). Verificar: `DatosDemoIT` pasa y `ArranqueAplicacionIT` confirma que sin perfil demo no hay `V900` en `public`.
- [x] 4.3 Implementar `odontologo/demo/ContrasenaDemoInicializador` (`ApplicationRunner` `@Profile("demo")`, D7) y sus casos en `DatosDemoIT`: el hash verifica la contraseña de prueba, reejecutar con otra contraseña cambia el hash, el hash inicial del seed no verifica nada. Verificar: `DatosDemoIT` completo en verde y ningún log contiene la contraseña.
- [x] 4.4 Prueba manual sobre la base local `turnotarde`: `DEMO_PASSWORD` en el `.env`, `SPRING_PROFILES_ACTIVE=demo`, `./gradlew bootRun -PskipFrontend`. Verificar: arranca, el log muestra la aplicación de `V2` y `V900` y "Contraseña de demo aplicada" sin el valor; un segundo arranque no reaplica migraciones.

## 5. Verificación final

- [x] 5.1 Correr `./gradlew clean build` desde `backend/` (unitarios, ArchUnit, integración y frontend embebido). Verificar: termina en verde sin tests salteados.
- [x] 5.2 Revisar que no se versionó ningún secreto (`git diff --stat` y búsqueda de `DEMO_PASSWORD=`/hashes `$2a$` en los archivos nuevos) y que no hay controladores ni DTOs nuevos que expongan entidades. Verificar: búsqueda sin resultados.
- [x] 5.3 Registrar en `design.md` la sección "Desvíos del apply" (si los hubo) y correr `openspec validate c-02-schema-y-seed-base --strict`. Verificar: la validación pasa.

## Workflow follow-up

- Revisión ALTO del usuario antes del apply (este documento es solo la propuesta).
- Avisar al equipo la convención de migraciones (D1) y a C-03 el contrato de BCrypt sin prefijo (D6).
- Corregir CHANGES.md (`V1` → `V2`, `out-of-order`) fuera de este change.
- Archivar con `/opsx:archive c-02-schema-y-seed-base` y marcar `[x]` C-02 en CHANGES.md.
