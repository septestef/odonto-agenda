# Spec Delta

P7/P9 aprobadas. Precisa precedencia de filtros y confidencialidad C-12; no modifica specs principales ni filtros actuales. P8 aprobada conserva intacto el requisito compartido «Conflicto de turnos en la base como RN-TU-01», sin excepción para cancelar. Apply autorizado explícitamente el 2026-11-22; primera etapa limitada a preparación/baseline según design.md. P4 aprobada conserva su resolución: CANCELADO idempotente precede al tiempo; los demás estados con inicio <= ahora usan RN-TU-07; ATENDIDO/AUSENTE futuros son inconsistencia interna persistida cubierta por el fallback 500 genérico y log existentes, sin nuevo código ni cambio de advice.

## MODIFIED Requirements

### Requirement: Errores de autenticación y autorización
Los rechazos por falta de sesión (401) y por autorización o CSRF (403) MUST usar el mismo formato uniforme `{codigo, mensaje, detalles?}` con `Content-Type: application/json`, MUST NOT ser redirects ni HTML, y MUST NOT exponer detalles internos. El 401 por falta de sesión MUST usar `NO_AUTENTICADO` y el 403 MUST usar `ACCESO_DENEGADO`; los rechazos de credenciales y de bloqueo del login usan `RN-AU-01` y `RN-AU-03`. La cadena existente MUST conservar su precedencia: un pedido sin sesión que supera CSRF o no lo requiere alcanza autorización y recibe 401; si falla CSRF antes, MUST responder 403 sin ejecutar la operación, aun cuando tampoco tenga sesión. MUST NOT garantizarse 401 universal para pedidos que incumplen ambas condiciones.

#### Scenario: Falta de sesión
- **WHEN** un pedido sin sesión que no requiere CSRF o ya lo supera llega a un endpoint protegido
- **THEN** la respuesta es 401 en JSON con `codigo` `NO_AUTENTICADO` y sin header `Location`

#### Scenario: CSRF inválido
- **WHEN** un pedido que modifica estado falla la verificación CSRF
- **THEN** la respuesta es 403 en JSON con `codigo` `ACCESO_DENEGADO`, aunque además no tenga sesión, sin mutar datos

#### Scenario: Credenciales inválidas
- **WHEN** el login recibe credenciales inválidas
- **THEN** la respuesta es 401 en JSON con `codigo` `RN-AU-01` y un mensaje genérico

## ADDED Requirements

### Requirement: Respuestas C-12 sin secretos de gestión
Las respuestas de agenda y su detalle anidado, listados de pacientes, cancelación privada y errores de estos endpoints MUST NOT exponer token/hash ni email de gestión, bajo nombres `token_hash`/`tokenHash` o `email_gestion`/`emailGestion`, ni sus valores por eco en mensajes/detalles. Los DTOs privados MUST limitarse a los campos de P2/P3; el email mutable autorizado de paciente no representa ni reemplaza email_gestion. La cancelación exitosa MUST conservar 204 vacío. La traducción de conflictos MUST reutilizar el RN-TU-01 y mensaje vigentes, sin SQL/causas internas ni nuevo código técnico.

#### Scenario: Contacto mutable distinto del destinatario de gestión
- **WHEN** la agenda y los pacientes muestran paciente.email `actual@example.test` y el turno guarda un email_gestion centinela diferente y un token_hash centinela
- **THEN** las respuestas exponen solo el contacto mutable contratado y ninguno de los campos ni valores reservados, tampoco en detalles anidados o errores de cancelación

#### Scenario: Fallo de lock en cancelación sin datos internos
- **WHEN** la cancelación privada recibe 40P01, 55P03 o falla de lock pesimista equivalente, incluso al cerrar la transacción
- **THEN** responde 409 con solo `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }`, sin secretos ni transición confirmada ni aviso; las fallas no clasificadas siguen siendo ERROR_INTERNO

#### Scenario: Inconsistencia futura con respuesta y diagnóstico sanitizados
- **WHEN** tras pertenencia, lock y reloj se observa ATENDIDO o AUSENTE con inicio > ahora al cancelar privadamente
- **THEN** el fallback existente responde 500 application/json con solo `{ "codigo": "ERROR_INTERNO", "mensaje": "Ocurrió un error inesperado. Probá de nuevo en unos minutos." }`; el diagnóstico local y la excepción completa registrada por el advice no contienen datos personales, emails, tokens/hashes, entidades ni causas con campos, y no hay mutación, flush de actualización, evento ni notificación; no se modifica el contrato compartido ni se usa una RN como rechazo de esta inconsistencia
