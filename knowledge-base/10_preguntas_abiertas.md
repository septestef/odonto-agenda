# Preguntas Abiertas

## Inconsistencias detectadas

### IN-01 — Cancelación con 24 h o en cualquier momento
**Primera respuesta en Discovery**: "24 horas" de anticipación mínima para cancelar o reprogramar.
**Respuesta posterior**: "si el paciente cancela con menos de 24 h, se libera el turno".
**Impacto**: define si el paciente puede cancelar a último momento.
**Resolución aplicada**: se puede cancelar en cualquier momento antes del inicio (RN-TU-05). La regla de 24 h aplica a **reservar** y al turno nuevo de una reprogramación (RN-TU-03, RN-TU-06). Confirmar.

### IN-02 — La historia clínica entra en el precio pero no en la demo
**Modelo comercial**: la suscripción incluye "historia clínica básica".
**Alcance v0.1**: la historia clínica queda fuera de la demo del 25/11.
**Impacto**: no se puede vender lo que muestra la demo con la propuesta comercial completa.
**Resolución propuesta**: planificarla como el primer change después de la demo.

## Preguntas abiertas (priorizadas)

| ID | Prioridad | Pregunta | Bloquea | Decisor |
|---|---|---|---|---|
| PA-01 | Media | ¿Cuántos días hacia adelante se puede reservar? (60 por defecto, configurable) | Ajuste de configuración | Usuario / producto |
| PA-03 | Media | ¿Pasos de 15 minutos y duraciones múltiplo de 15 están bien? (SU-02) | US-003, US-005 | Usuario |
| PA-04 | Media | ¿Hay Docker en la notebook? Si hay, los tests de integración pueden usar Testcontainers en lugar de una base local de test | Setup de tests | Equipo técnico |
| PA-05 | Baja | ¿Un DNI repetido sobrescribe los datos de contacto o se alerta al odontólogo? (SU-03) | Producción | Producto |
| PA-06 | Baja | ¿Qué falta para cumplir las leyes 25.326, 26.529 y 27.706 antes de manejar pacientes reales? (consentimiento, registro de la base ante la AAIP, política de privacidad) | Producción | Legal / producto |
| PA-07 | Baja | ¿Cómo se validan el problema y el precio con odontólogos reales? (SU-01) | Comercialización | Producto |
| PA-08 | Baja | ¿Dónde se aloja el producto en producción y con qué proveedor de email? | Producción | Equipo técnico |
| PA-09 | Baja | ¿Alta self-service de odontólogos y cobro de la suscripción? ¿Con qué medio de pago? | Comercialización | Producto |

## Preguntas resueltas

| ID | Respuesta | Resolución |
|---|---|---|
| PA-02 | Sí: los turnos manuales están exentos de la regla de 24 h (RN-TU-08). Pueden cargarse en cualquier horario futuro dentro de la agenda; sí aplican RN-TU-01, RN-TU-11 y RN-TU-12. | Confirmada por el usuario el 2026-11-23 durante C-15. Ver SU-06 en `09_decisiones_y_supuestos.md`. |
