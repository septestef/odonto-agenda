# preparacion-demo Specification

## Purpose
Define las condiciones observables para preparar y ensayar una demo local reproducible, con datos sintéticos, verificaciones completas y evidencia del resultado, sin alterar las reglas funcionales del producto.

## Requirements

### Requirement: Arranque local reproducible
La guía MUST permitir iniciar la aplicación en localhost con PostgreSQL local, perfil demo, migraciones aplicadas, frontend empaquetado y acceso del usuario de demo.

#### Scenario: Arranque desde una base vacía
- **WHEN** el operador configura el entorno y sigue la guía desde una base local vacía
- **THEN** se aplican las migraciones, carga la aplicación web y el usuario de demo puede iniciar sesión

#### Scenario: Configuración incompleta
- **WHEN** falta una credencial necesaria para iniciar
- **THEN** se informa qué configuración falta sin revelar su valor y no se declara exitoso el arranque

### Requirement: Reset limitado a la base local descartable
El procedimiento de reset MUST limitarse al esquema público de una base local de demo verificada, exclusiva y descartable, distinta de la base de test.

#### Scenario: Destino verificado
- **WHEN** se identifica e informa el destino local descartable y la aplicación está detenida
- **THEN** el reset afecta solo al esquema indicado y las migraciones pueden reconstruirlo

#### Scenario: Destino inseguro o ambiguo
- **WHEN** el destino es remoto, ambiguo, de test o contiene datos a preservar
- **THEN** el procedimiento se detiene antes de cualquier operación destructiva

### Requirement: Guía y plantilla de entorno completas
README MUST ofrecer pasos ejecutables de preparación y arranque con checklist; la plantilla de entorno MUST enumerar las variables necesarias con ejemplos ficticios o valores públicos no sensibles.

#### Scenario: Preparación de la notebook
- **WHEN** el operador consulta la guía y la plantilla
- **THEN** identifica requisitos, directorios, comandos, perfil, variables, secuencia de arranque y navegador

### Requirement: Datos de demo con huecos elegibles
El seed MUST proporcionar turnos sintéticos para mostrar la agenda y huecos para reservar o reprogramar conforme a las reglas vigentes.

#### Scenario: Agenda y reserva en vivo
- **WHEN** se consulta la agenda y la disponibilidad en la fecha del ensayo
- **THEN** se muestran los turnos de ejemplo y las reservas online usan huecos que cumplen la anticipación mínima de 24 horas

### Requirement: Migraciones aplicadas inmutables
La ampliación del seed MUST incorporarse mediante una nueva migración exclusiva de demo, sin modificar migraciones mergeadas.

#### Scenario: Ampliación de la agenda de ejemplo
- **WHEN** se aplican las migraciones de demo sobre una base limpia
- **THEN** una nueva migración agrega tres o cuatro turnos sintéticos de mañana y tarde, con prestaciones distintas y al menos uno manual, sin solaparse entre sí ni con los datos existentes, y conserva huecos elegibles en días siguientes

### Requirement: Verificación automatizada completa
La verificación MUST incluir unitarios, integración contra PostgreSQL real y frontend, junto con lint, tests y build propios del frontend, conservando comandos, revisión, cantidades y resultados como evidencia.
La suite MUST incluir el test concurrente existente `ReservasConcurrentesApiIT`.

#### Scenario: Verificaciones exitosas
- **WHEN** se ejecutan todas las verificaciones sobre la revisión candidata
- **THEN** la evidencia permite comprobar que todas terminaron correctamente sin omitir ni debilitar tests

#### Scenario: Verificación fallida
- **WHEN** una verificación falla
- **THEN** el fallo queda registrado y no se declara la suite en verde

### Requirement: Correo y gestión verificables
El ensayo de correo MUST usar datos sintéticos y un buzón controlado, comprobar entrega o spam y verificar el acceso al enlace de gestión.

#### Scenario: Correo recibido
- **WHEN** llega la confirmación de una reserva de ensayo
- **THEN** sus datos son correctos, el enlace abre la gestión y se verifican cancelación y reprogramación con su nuevo correo

#### Scenario: Correo no entregado
- **WHEN** SMTP falla o no llega el mensaje tras revisar también spam
- **THEN** se registra el fallo o bloqueo y se verifica que la reserva persiste; la aceptación SMTP no prueba entrega

### Requirement: Evidencia sin secretos
La evidencia MUST excluir credenciales, cookies, tokens y enlaces completos de gestión.

#### Scenario: Registro del ensayo
- **WHEN** se guardan resultados o capturas
- **THEN** se puede revisar el comportamiento sin exponer secretos ni permitir gestionar una reserva mediante la evidencia

### Requirement: Comparación visual trazable
El ensayo MUST comparar las pantallas de escritorio y 360 px con sus mockups aprobados, registrando revisión, contexto, resultado y diferencias.

#### Scenario: Comparación realizada
- **WHEN** se verifica una pantalla y estado del recorrido
- **THEN** la evidencia identifica su mockup y diferencias para revisión

#### Scenario: Sin automatización disponible
- **WHEN** no hay navegador automatizable
- **THEN** se declara la verificación manual y se deja pendiente cualquier paso no ejecutado
