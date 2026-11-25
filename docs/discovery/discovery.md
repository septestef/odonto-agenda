# Discovery — turnotardeutn

**Fecha**: 2026-11-17
**Fuentes investigadas**: 20 sistemas (ver `docs/discovery/sources/`); informe de mercado completo en `docs/discovery/informe-discovery.md` (tabla comparativa, matriz de puntuación ponderada, análisis competitivo y recomendación).

## 1. Problema que resuelve

El odontólogo independiente en Argentina coordina sus turnos por WhatsApp, teléfono o papel, lo que genera superposiciones, ausencias y tiempo perdido. Las soluciones existentes suelen ser caras, ocultan el precio o están sobredimensionadas para un profesional que trabaja solo.

> Supuesto: este enunciado se derivó del relevamiento de mercado, no de una validación con odontólogos reales.

## 2. Usuarios / roles

- **Odontólogo**: configura su agenda (horario de atención, bloqueos, prestaciones con su duración) y gestiona los turnos. Es el único usuario con login en la v1.
- **Paciente**: reserva, cancela o reprograma su turno **sin cuenta**, identificándose con DNI, nombre, teléfono y email. Gestiona su turno mediante un link único enviado a su email.

## 3. Casos de uso

1. Como odontólogo, quiero definir mi horario de atención, mis bloqueos y mis prestaciones con su duración, para que solo se ofrezcan horarios reales.
2. Como paciente, quiero ver los horarios libres y reservar con mi DNI, sin crear una cuenta.
3. Como paciente, quiero cancelar o reprogramar mi turno desde el link que me llegó por email.
4. Como odontólogo, quiero ver mi agenda del día y de la semana.
5. Como odontólogo, quiero recordarle el turno al paciente por email o con un botón de WhatsApp.

## 4. Competidores / soluciones existentes

| Competidor | País | Puntaje ponderado (0–5) | Nota |
|---|---|---|---|
| DentalSoft | AR | 3.95 | Argentino, precios en ARS, Mercado Pago sin ARCA |
| Dentalink | CL (módulo AR) | 3.95 | Muy completo, orientado a clínicas |
| OdontoClinIA | AR | 3.90 | Sillón como recurso de agenda |
| AgendaPro | AR (página dental) | 3.65 | Único que declara Ley 25.326 y 26.529 |
| DentalCore | AR/LatAm | 3.40 | Recalculado el 2026-11-25 (antes 2.00): el sitio figuraba caído y la verificación del equipo lo encontró operativo. Declara periodontograma y OS + ARCA + MP |
| DentalTec | AR | 3.25 | ARCA sin Mercado Pago |

**Competidores prioritarios para ver en demo**: DentalSoft, AgendaPro, Dentalink, OdontoClinIA y DentalTec (Bilog como reserva). Desde el 2026-11-25 se suma DentalCore como sexto candidato (ver `docs/discovery/informe-discovery.md`, D.1).
**Referentes de UX**: Dentally (lista de espera), Turnito (reserva por link sin registro), Doctoralia (búsqueda y reserva del paciente).

**Notas**: el puntaje mide evidencia pública, no calidad real; casi toda la evidencia es afirmación comercial del proveedor. La reserva sin cuenta es el estándar del mercado y por eso se adoptó. Vacíos detectados en el mercado argentino: ningún sistema muestra lista de espera ni relleno automático de horarios liberados, casi no hay controles periódicos ligados a tratamientos, nadie declara la Ley 27.706 y la mitad oculta sus precios.

## 5. Funcionalidades necesarias (MVP demo 25/11/2026)

- Agenda del odontólogo con horario de atención y bloqueos.
- Prestaciones con duración propia.
- Anti-solapamiento estricto de turnos.
- Login del odontólogo.
- Reserva online del paciente sin cuenta (DNI + contacto).
- Cancelación y reprogramación por el paciente mediante link único.
- Recordatorio por email y botón de WhatsApp (`wa.me`).
- Vista de agenda diaria y semanal.

## 6. Funcionalidades opcionales

- Historia clínica básica y odontograma (forman parte del producto comercial, pero no de la demo del 25/11).
- Lista de espera con relleno automático de horarios liberados.
- Seña con Mercado Pago.
- Controles periódicos (recall) ligados a tratamientos.
- Caja y reportes (ocupación, ausentismo).
- Obras sociales/prepagas y facturación ARCA.
- Varios profesionales, sillones o sucursales.
- WhatsApp Business API.

## 7. Reglas de negocio

- Nunca puede haber dos turnos superpuestos, tampoco forzados por el odontólogo.
- La duración del turno depende del tipo de prestación.
- Solo se reserva dentro del horario de atención y fuera de los bloqueos.
- Se puede reservar hasta 24 h antes del turno; una reserva para dentro de menos de 24 h se rechaza.
- El turno no requiere confirmación: queda reservado al crearlo.
- El paciente puede cancelar en cualquier momento y el horario se libera.
- Reprogramar equivale a cancelar y reservar de nuevo, con la misma regla de 24 h para el turno nuevo.

## 8. Integraciones

- Envío de email por un servicio SMTP gratuito.
- Links `wa.me` para abrir WhatsApp con el mensaje armado (sin la API paga).
- Ninguna integración paga.

## 9. Restricciones

- Entrega: MVP funcional para demo el miércoles 25/11/2026.
- Frontend: TypeScript vanilla.
- Backend: Spring Boot, API REST, Gradle, Hibernate, PostgreSQL.
- Presupuesto cero.
- La demo corre en local (localhost): servidor y base de datos en la notebook.
- Modelo comercial: suscripción mensual fija entre $25.000 y $35.000 ARS por acceso completo a agenda, gestión de pacientes, historia clínica básica y recordatorios.

## 10. Riesgos

- **Plazo**: 8 días para reservas, login, anti-solapamiento y emails.
- **Concurrencia**: dos pacientes reservando el mismo horario a la vez; el backend debe impedir la doble reserva.
- **Suplantación**: sin cuenta, cualquiera que conozca un DNI podría reservar a nombre de otro. Mitigación: el link de gestión va solo al email cargado.
- **Límite de envíos**: los servicios de email gratuitos limitan los mensajes diarios.
- **Datos de salud**: en producción rigen las leyes 25.326, 26.529 y 27.706.
- **Supuesto sin probar**: que el problema y el precio ($25.000–35.000 ARS/mes) resulten atractivos para odontólogos independientes; no hay validación con usuarios reales.

## 11. Preguntas abiertas

- ¿Cuál es la anticipación máxima para reservar (cuántos días o semanas hacia adelante)?
- ¿Cómo se valida el precio y el problema con odontólogos reales después de la demo?
- ¿Dónde se aloja el producto en producción, una vez superada la demo local?
