# shell-frontend Specification

## Purpose
Define el esqueleto de navegación de la aplicación: qué rutas existen, cuáles exigen sesión y qué se muestra mientras las pantallas de cada funcionalidad todavía no están construidas.

## Requirements

### Requirement: Enrutamiento por rutas declaradas
La aplicación DEBE (MUST) resolver la URL del navegador contra un conjunto declarado de rutas: `/login`, `/agenda`, `/config`, `/reservar/{slug}` y `/turno/{token}`. Las rutas con parámetros DEBE (MUST) capturar el segmento correspondiente sin confundirlo con una ruta fija. Cualquier otra ruta DEBE (MUST) mostrar una vista de "no encontrado" en español rioplatense, sin lanzar un error sin manejar.

#### Scenario: Navegación directa a una ruta protegida
- **WHEN** una persona abre directamente `/agenda` en el navegador
- **THEN** la aplicación resuelve la ruta y muestra el contenido correspondiente (o el guard de sesión decide antes)

#### Scenario: Rutas con parámetros
- **WHEN** se abre `/reservar/ana-gomez` o `/turno/abc123`
- **THEN** el enrutador captura `ana-gomez` como `slug` y `abc123` como `token`, y muestra el marcador de esa ruta

#### Scenario: Ruta inexistente
- **WHEN** se abre `/no-existe`
- **THEN** se muestra una vista de "no encontrado" con un enlace usable para volver a una ruta válida

#### Scenario: Navegación sin recarga de página
- **WHEN** se activa un enlace interno o se usa el botón "atrás" del navegador
- **THEN** la aplicación actualiza la vista y la barra de direcciones sin pedir un documento nuevo al servidor

### Requirement: Guard de sesión en rutas del odontólogo
Antes de mostrar `/agenda` o `/config`, la aplicación DEBE (MUST) verificar la sesión consultando `GET /api/auth/me`. El resultado DEBE (MUST) quedar en caché en memoria mientras la sesión siga siendo válida, para no repetir la consulta en cada cambio de ruta. Sin sesión válida, la aplicación DEBE (MUST) redirigir a `/login` conservando el destino pedido en el parámetro `redirect`. Las rutas públicas `/login`, `/reservar/{slug}` y `/turno/{token}` NUNCA DEBE (MUST) consultar `/api/auth/me`.

#### Scenario: Sesión válida con caché
- **WHEN** `GET /api/auth/me` responde 200 con los datos del odontólogo y se navega de `/agenda` a `/config`
- **THEN** no se emite un segundo pedido a `/api/auth/me` y ambas vistas se muestran sin redirección

#### Scenario: Sesión ausente o vencida
- **WHEN** `GET /api/auth/me` responde 401 con `NO_AUTENTICADO`
- **THEN** la aplicación descarta la caché de sesión y redirige a `/login?redirect=%2Fagenda` cuando el destino pedido era `/agenda`

#### Scenario: Rutas públicas sin verificación
- **WHEN** se abre `/reservar/ana-gomez` o `/turno/abc123` sin sesión iniciada
- **THEN** no se realiza ningún pedido a `/api/auth/me` y se muestra el marcador público correspondiente

#### Scenario: Falla de red al verificar la sesión
- **WHEN** el pedido a `/api/auth/me` falla por error de red (sin respuesta HTTP)
- **THEN** se muestra una vista de error de carga con una acción de reintento y NO se interpreta la falla como sesión vencida

### Requirement: Layout base y marcadores de funcionalidad

Las rutas del odontólogo DEBE (MUST) mostrarse dentro de un layout con: salto al contenido, logo de la marca, navegación a Agenda y Configuración con el elemento activo marcado para lectores de pantalla, nombre del odontólogo sesionado y acción de cerrar sesión. Las rutas `/agenda`, `/reservar/{slug}` y `/turno/{token}` DEBE (MUST) seguir mostrando únicamente un marcador genérico que indique qué pantalla vendrá, sin lógica de negocio, formularios ni pedidos propios. La ruta `/config` DEBE (MUST) mostrar la interfaz real de configuración de horarios, prestaciones y bloqueos, con sus propios formularios y pedidos a la API, según lo especificado en `configuracion-agenda-ui`. El guard de sesión y el comportamiento de las rutas públicas NO DEBE (MUST) cambiar.

#### Scenario: Identificación de la sección activa
- **WHEN** se muestra `/agenda` y cuando se muestra `/config`
- **THEN** el enlace correspondiente queda marcado como página actual (`aria-current="page"`) y el otro no

#### Scenario: Cierre de sesión desde el layout
- **WHEN** se activa "Cerrar sesión" en el app bar
- **THEN** se envía el pedido de cierre de sesión, se limpia la caché de sesión y se navega a `/login`

#### Scenario: Acceso por teclado al contenido
- **WHEN** una persona usa Tab desde el inicio de la página
- **THEN** el primer control visible es "Saltar al contenido" y al activarlo el foco queda en el contenido principal

#### Scenario: Marcadores sin lógica de funcionalidad
- **WHEN** se abre `/agenda`, `/reservar/{slug}` o `/turno/{token}`
- **THEN** se muestra solo el texto del marcador y no se consultan endpoints de agenda, turnos ni configuración

#### Scenario: Configuración con lógica propia
- **WHEN** se abre `/config` con sesión válida
- **THEN** se muestran las secciones Horarios, Prestaciones y Bloqueos y se consultan los endpoints de configuración, sin mostrar el marcador genérico
