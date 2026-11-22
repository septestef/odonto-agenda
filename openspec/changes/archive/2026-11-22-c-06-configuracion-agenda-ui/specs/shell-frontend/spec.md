# Spec Delta — shell-frontend

## MODIFIED Requirements

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
