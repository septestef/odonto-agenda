# Spec Delta

## Purpose

Establece las condiciones observables para preparar y ensayar la demo local del 25/11/2026 de forma reproducible, segura y verificable, sin alterar las reglas funcionales existentes.

## ADDED Requirements

### Requirement: Arranque reproducible y reset local autorizado
La guia de demo MUST permitir arrancar desde cero en la notebook con PostgreSQL local, perfil `demo`, migraciones aplicadas, frontend empaquetado y login en `http://localhost:8080`. El reset MUST limitarse al esquema `public` de la base local de demo confirmada para apply habilitado tras aprobar la propuesta, conforme al reset ya encargado. Antes de ejecutarlo se MUST informar y verificar el destino concreto, exclusivo de demo y descartable; pedir aclaracion solo ante destino ambiguo o fuera del alcance autorizado; MUST NOT ejecutarse ante destino remoto, ambiguo, de test o con datos a preservar.

#### Scenario: Arranque desde cero autorizado
- **WHEN** la propuesta esta aprobada y apply habilitado, se informa y verifica el destino local descartable del reset encargado y el operador configura el entorno y sigue la guia
- **THEN** Flyway reconstruye el esquema y los datos de demo, la SPA carga en localhost:8080 y el usuario demo puede iniciar sesion

#### Scenario: Destino no confirmado
- **WHEN** no se puede confirmar destino local descartable o el destino queda fuera del alcance autorizado
- **THEN** el procedimiento se detiene antes de cualquier operacion destructiva y pide decision humana sin mostrar secretos

### Requirement: Guia completa sin credenciales reales
README MUST contener la seccion exacta `Correr la demo en la notebook` con pasos ejecutables verificados para la plataforma local y checklist del dia (Postgres, entorno `.env`, reset autorizado y navegador). La plantilla MUST enumerar todas las variables de app, DB y test documentadas, con ejemplos ficticios o valores publicos no sensibles y sin credenciales reales. Los secretos MUST ser configurados por el usuario fuera del chat y MUST NOT aparecer en evidencia.

#### Scenario: Operador prepara la notebook
- **WHEN** el operador consulta la guia y `.env.example`
- **THEN** puede identificar requisitos, directorios, comandos, perfil, variables y orden real de build/arranque sin asumir que bootRun prepara el frontend

#### Scenario: Falta configuracion sensible
- **WHEN** falta una credencial necesaria para el ensayo
- **THEN** el ensayo queda bloqueado hasta que el usuario la configure fuera del chat, sin pedir ni registrar su valor

### Requirement: Datos aptos para la fecha de demo
La demo MUST mostrar turnos sinteticos el 25/11/2026 y disponer de huecos para reserva y reprogramacion que cumplan las reglas vigentes al momento del ensayo. MUST conservarse la inmutabilidad de migraciones mergeadas y MUST NOT relajarse la anticipacion de 24 horas para reservar online.

#### Scenario: Agenda del dia y reserva en vivo
- **WHEN** se ensaya el 25/11 con la fecha y zona reales del negocio
- **THEN** la agenda muestra los turnos de ese dia y las reservas online usan huecos futuros elegibles, no horarios del mismo dia incompatibles con las 24 horas

#### Scenario: Seed suficiente o insuficiente
- **WHEN** se revisan los datos existentes contra el guion
- **THEN** si alcanzan se conservan, y si requieren correccion se agrega una migracion 9xx exclusiva de demo sin editar V900 ni cambiar reglas del producto

### Requirement: Verificacion automatizada completa y concurrencia preservada
La aceptacion de C-16 MUST incluir build con unitarios, integracion PostgreSQL real y frontend, y lint, tests y build propios del frontend. MUST mantenerse el caso concurrente de dos reservas al mismo horario con exactamente una ganadora; MUST NOT omitirse, borrarse ni debilitarse tests para declarar exito.

#### Scenario: Suite completa exitosa
- **WHEN** se ejecutan todas las verificaciones sobre la revision candidata con la base de test dedicada disponible
- **THEN** se conserva evidencia sanitizada de comandos, revision y resultados y el caso concurrente acredita una reserva confirmada y un conflicto

#### Scenario: Baseline con fallos
- **WHEN** falla el baseline previo a modificar comportamiento
- **THEN** se detiene la modificacion, se reporta el fallo preexistente y se espera decision sin reparar ni omitir el test por iniciativa propia

### Requirement: Ensayo de correo y gestion desde el enlace
El ensayo Gmail MUST usar datos sinteticos y un buzon controlado autorizado, comprobar entrega o spam y probar el enlace de gestion en la notebook. MUST distinguir aceptacion SMTP de entrega efectiva y MUST NOT registrar credenciales, cookies, tokens ni enlaces completos de gestion.

#### Scenario: Correo recibido
- **WHEN** se crea una reserva de ensayo autorizada y llega la confirmacion
- **THEN** se verifica fecha, hora, prestacion, direccion y apertura del link, y se ensayan cancelacion y reprogramacion con su nuevo email sin divulgar el enlace

#### Scenario: Correo no entregado
- **WHEN** SMTP falla o el mensaje no llega tras revisar tambien spam
- **THEN** se registra el resultado sanitizado como fallo o bloqueo, se comprueba que el turno sigue reservado y no se declara aprobada la entrega por la sola aceptacion SMTP

### Requirement: Ensayo integrado y comparacion visual trazables
El cierre MUST incluir guion y smoke de historias M implementadas, WhatsApp y turno manual, concurrencia y uso a 360 px, y comparacion de cada pantalla con su mockup aprobado revision 2. La evidencia MUST indicar revision, contexto, resultado y diferencias clasificadas como bloqueo funcional, diferencia visual o detalle conocido no bloqueante sujeto a revision. Sin browser automatizable, MUST declararse verificacion manual. US-014 y funcionalidades post-demo MUST figurar fuera de alcance.

#### Scenario: Recorrido y comparacion realizados
- **WHEN** Jero ensaya la revision candidata y compara las pantallas de escritorio y movil
- **THEN** quedan resultados por paso y pantalla, referencia al mockup y diferencias clasificadas para revision humana, sin inventar criterios de aceptacion del equipo

#### Scenario: Paso no ejecutable o diferencia pendiente
- **WHEN** una herramienta no esta disponible o aparece un desvio que necesita cambios fuera del alcance
- **THEN** se declara manual, pendiente o bloqueado segun corresponda y se pide decision, sin fingir automatizacion ni corregir UI implicitamente
