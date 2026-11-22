# Spec Delta

## Purpose

Implementa la experiencia de acceso del odontólogo: cómo se ingresan las credenciales, qué se muestra cuando la API las rechaza o bloquea, y cómo se vuelve al destino pedido después de autenticarse.

## ADDED Requirements

### Requirement: Formulario de acceso
La página `/login` DEBE (MUST) ofrecer dos campos identificados como "Email" y "Contraseña", con autocompletado adecuado, y un botón "Ingresar". El formulario DEBE (MUST) impedir envíos duplicados mientras un pedido está en curso, mostrando un estado de envío. Los textos DEBE (MUST) estar en español rioplatense con voseo.

#### Scenario: Envío correcto
- **WHEN** se completan email y contraseña válidos y se activa "Ingresar"
- **THEN** se envía un `POST /api/auth/login` con un cuerpo JSON con las claves `email` y `password`

#### Scenario: Doble activación del botón
- **WHEN** se activa "Ingresar" dos veces seguidas antes de que termine el primer pedido
- **THEN** solo se envía un pedido y el botón queda deshabilitado con un estado de envío visible

#### Scenario: Campos vacíos
- **WHEN** se intenta ingresar con alguno de los dos campos vacíos
- **THEN** la aplicación marca el campo faltante y no realiza el pedido a la API

### Requirement: Error genérico de credenciales inválidas
Ante un 401 con `RN-AU-01`, la página DEBE (MUST) mostrar siempre el mismo mensaje genérico: "El email o la contraseña no son correctos." DEBE (MUST) estar en un contenedor con rol `alert` y NO DEBE (MUST) revelar si el email existe, si la contraseña es incorrecta o si la cuenta está inactiva.

#### Scenario: Contraseña incorrecta
- **WHEN** la API responde 401 con `RN-AU-01` para una contraseña incorrecta
- **THEN** se muestra el mensaje genérico y no se distingue el motivo del rechazo

#### Scenario: Email inexistente o cuenta inactiva
- **WHEN** la API responde 401 con `RN-AU-01` para un email inexistente o una cuenta inactiva
- **THEN** se muestra exactamente el mismo mensaje y el mismo estado visual que para una contraseña incorrecta

### Requirement: Estado de bloqueo por intentos fallidos
Ante un 429 con `RN-AU-03`, la página DEBE (MUST) mostrar un aviso de bloqueo con el tiempo restante en minutos, calculado como el redondeo hacia arriba de `detalles.reintentarEnSegundos / 60`. El botón "Ingresar" DEBE (MUST) quedar deshabilitado mientras dure el bloqueo y volver a habilitarse automáticamente al vencer, sin recargar la página. La cuenta regresiva es solo presentación: NO DEBE (MUST) usarse para decidir reglas de negocio ni para reintentar pedidos por su cuenta.

#### Scenario: Bloqueo de quince minutos
- **WHEN** la API responde 429 con `detalles.reintentarEnSegundos` igual a 900
- **THEN** se muestra "Demasiados intentos. Probá de nuevo en 15 minutos." y el botón queda deshabilitado

#### Scenario: Bloqueo con segundos parciales
- **WHEN** la API responde 429 con `detalles.reintentarEnSegundos` igual a 841 o 899
- **THEN** se muestra "Demasiados intentos. Probá de nuevo en 15 minutos." porque el redondeo hacia arriba de esos valores da 15 (el "14 minutos" del mockup corresponde a 840 s y es ilustrativo)

#### Scenario: Vencimiento del bloqueo
- **WHEN** pasa el tiempo indicado en `reintentarEnSegundos`
- **THEN** el aviso de bloqueo desaparece y el botón "Ingresar" vuelve a estar habilitado sin recarga de página

#### Scenario: Detalles ausentes o inválidos
- **WHEN** la API responde 429 con `RN-AU-03` pero sin `reintentarEnSegundos` utilizable
- **THEN** se muestra un aviso de bloqueo comprensible sin un número inventado y el botón permanece deshabilitado hasta una nueva respuesta

### Requirement: Destino posterior al ingreso y cierre de sesión
Después de un inicio de sesión exitoso, la aplicación DEBE (MUST) navegar al destino indicado en el parámetro `redirect` solo si es una ruta interna válida; en cualquier otro caso DEBE (MUST) navegar a `/agenda`. El ingreso también DEBE (MUST) actualizar la caché de sesión del enrutador. El cierre de sesión DEBE (MUST) enviar `POST /api/auth/logout`, limpiar la caché local y llevar a `/login`.

#### Scenario: Redirección al destino pedido
- **WHEN** se abre `/login?redirect=%2Fconfig` y el inicio de sesión es exitoso
- **THEN** se navega a `/config` y la caché de sesión queda poblada con los datos del odontólogo

#### Scenario: Destino externo rechazado
- **WHEN** se abre `/login?redirect=https%3A%2F%2Fsitio-externo.com` y el inicio de sesión es exitoso
- **THEN** se navega a `/agenda` y no se sigue la URL externa

#### Scenario: Cierre de sesión
- **WHEN** se activa "Cerrar sesión" y la API responde 204
- **THEN** se descarta la información de sesión en memoria y se navega a `/login`

### Requirement: Inserción segura de textos
Todo texto que provenga de la API o de la persona DEBE (MUST) insertarse como texto plano. La UI NO DEBE (MUST) usar inserción de HTML dinámico bajo ninguna forma.

#### Scenario: Mensaje con marcas HTML
- **WHEN** la API devuelve un mensaje de error que contiene `<script>algo</script>`
- **THEN** el mensaje se muestra literalmente como texto y no se ejecuta ni se interpreta como marcado

#### Scenario: Regla estática de prevención
- **WHEN** se ejecuta el análisis estático del frontend
- **THEN** cualquier uso de inserción de HTML dinámico se reporta como error y falla la verificación
