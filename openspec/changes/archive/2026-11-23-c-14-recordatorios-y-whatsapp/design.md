# Design

## Context

C-08 finaliza avisos de reserva, cancelación y reprogramación tras el commit, usando el token efímero del evento cuando corresponde. `ProcesarAviso`, `PlantillasCorreo`, `TransporteCorreo` y `RegistroNotificacion` distinguen preparación, aceptación SMTP y auditoría independiente; el asunto y la plantilla de `RECORDATORIO` aún no están implementados. V2 ya admite el tipo `RECORDATORIO`, guarda `recordatorio_enviado_en` y tiene índice por estado/inicio, pero no registra la toma durable de un intento. C-09 persiste el snapshot `turno.email_gestion` y solo el hash del token. C-12 resuelve el dueño desde sesión y C-13 ya comparte `detalle.ts` entre Día/Semana; `control.ts` controla selección, navegación y cancelación.

Base: la rama `feat/c-14-recordatorios-y-whatsapp` parte de `6e3f102` (PR #20), que es el tip de `main` al 2026-11-23 e incluye C-11 archivado con `gestion-turno-ui` publicada y el fix de C-06. No hace falta rebase antes del Apply. El flujo vigente de C-11 usa el token del link para consultar, cancelar y reprogramar; relee el original tras un 204 y nunca obtiene el link del sucesor de la API. Además, el cliente publicado (`features/turno/api-gestion.ts`) pre-valida un único segmento ASCII base64url de 43 caracteres antes de consultar. Si `main` avanza antes del Apply, actualizar la rama y reconfirmar que no cambien `features/agenda` ni los contratos de C-09/C-11.

## Goals / Non-Goals

**Goals:** separar los gates y los tests por historia; mantener el recordatorio desacoplado de las escrituras de reserva y el acceso a WhatsApp acotado al dueño de sesión; conservar el comportamiento de los links ya enviados, la privacidad y la garantía de ocupación.

**Non-Goals:** cambiar los avisos originales, el contrato de agenda, la cadena de seguridad o la UI pública en esta propuesta; implementar C-15; enviar WhatsApp desde la app; garantizar entrega exactamente una vez; almacenar links para reenvío.

## Decisions

### D1–D3 — US-014 (diferida)

US-014 se difirió a C-21 (`c-21-recordatorio-email-automatico`) por decisión de Estefi del 2026-11-23, antes de la demo. Las decisiones sobre la ventana del scheduler (D1), la credencial del link de gestión (D2) y la toma durable, marca y auditoría (D3) se movieron completas al `design.md` de C-21, junto con el delta de `notificaciones-email` y sus tareas. C-14 se archiva solo con US-015.

### D4 — US-015: consulta privada y mensaje

Proyección de solo lectura por `id + dueño de sesión`, sin `FOR UPDATE`, con inicio, estado, teléfono vigente, prestación y dirección vigente. No ampliar DTO de agenda ni cambiar filtros: la cadena existente ya exige `ODONTOLOGO` para `/api/**` y no exige CSRF en GET. Respetar el advice y la precedencia autenticación → id válido → pertenencia (404 idéntico) → estado/tiempo (409 `RN-NO-06`) → teléfono/dirección (422 `RN-NO-07`). Ambas reglas se agregan en `knowledge-base/05_reglas_de_negocio.md`; `RN-NO-05` sigue describiendo solo cómo se arma la URL.

Proponer WhatsApp solo para `RESERVADO` con inicio estrictamente futuro de origen `ONLINE` o `MANUAL`, sin corte de 24 h; usar la regla de tiempo del servidor, no acoplarla a `puedeCancelar`. Cancelar permanece independiente aun con teléfono/dirección faltantes y conserva su tratamiento de repetición. Para el teléfono, aplicar el mismo formato E.164 que ya valida la reserva (`SolicitudReserva`: `^\+[1-9][0-9]{7,14}$`), extraído a una constante compartida en lugar de duplicar la regex. No se agrega un validador con metadatos de país (libphonenumber): sería más estricto que la reserva y devolvería 422 sobre teléfonos que el sistema ya aceptó, además de sumar una dependencia. No se infiere región, no se convierten formatos nacionales y no se comprueba que exista una cuenta de WhatsApp. Omitir el `+` solo al armar el path. No inventar dirección ni degradar silenciosamente el mensaje si falta. Texto orientativo: «Te recordamos tu turno del {dd/MM/yyyy} a las {HH:mm} para {prestación}. Dirección: {dirección}.». Usar datos del consultorio, sin datos extras del paciente ni tokens. Construir en servidor con origen fijo `https://wa.me` y un único `text` UTF-8; no devolver redirección, no loguear ni cachear la URL y no contactar WhatsApp desde backend.

### D5 — US-015: apertura desde el gesto y estado acotado

Componer en `features/agenda` sobre `detalle.ts`/`control.ts`, usando cliente API existente, tipos estrictos y lectura de `unknown` con validación de forma y destino. No llamar a WhatsApp al abrir el detalle. Al activar el botón, intentar en el mismo gesto una pestaña vacía inerte, separar `opener` antes de cualquier navegación externa y usar una navegación/enlace con `noreferrer`. No navegar si no se puede garantizar ese aislamiento; probar el comportamiento real del navegador (en algunas variantes `noopener` devuelve un handle nulo). Si se bloquea, ofrecer tras el GET un enlace visible «Abrir WhatsApp» con `target="_blank"` y `rel="noopener noreferrer"`, activado por un segundo gesto; no abrir tras el `await` como estrategia única, ni usar un redirect del endpoint.

Una solicitud activa por detalle y sin doble apertura; asociar respuesta a turno y selección vigentes. Cerrar solo la pestaña vacía que esta acción haya creado al fallar, cancelar, cambiar/rerenderizar a otro turno o desmontar; invalidar respuesta y enlace anterior incluso si abortar la red no alcanzó. Si el usuario cierra la pestaña vacía, solo ofrecer enlace manual vigente, nunca recrearla sin gesto. Validar HTTPS, host exacto, sin credenciales/puerto externo/fragmento ni parámetros extra, path internacional de dígitos y `text` único antes de navegar. Representar preparación y error en el detalle: 409/404 indica cambio y permite refrescar agenda sin reintento automático; 422 muestra explicación fija por caso; 401 conserva señal de sesión vencida, 403 respetará manejo actual de acceso, red/5xx o payload inesperado permiten volver a intentar con gesto. No imprimir el link ni el error crudo. Mantener foco, teclado, aviso accesible y el flujo destructivo de Cancelar independiente.

## UI Mockups

- Design system revision: 2
- Decision: yes (2026-11-23)
- Mockups: openspec/changes/c-14-recordatorios-y-whatsapp/mockups/
- Approval: approved (2026-11-23, by user)
- Preview: rendered (2026-11-23, mockups/previews/)
- Visual verify: differences (2026-11-23, mockups/verify/) — las cuatro diferencias eran preexistentes de C-13 y se corrigieron en `fix(C-13)` (PR #22, `0489674`).

Estefi aprobó explícitamente la revisión 2 de [detalle-whatsapp.html](mockups/detalle-whatsapp.html) del commit `2efaed5` el 2026-11-23. Las [20 capturas originales de Edge](mockups/previews/) cubren Día y Semana en cinco estados (disponible, no disponible, preparando, error y apertura bloqueada con alternativa) a **390×844** y **1440×1000**. Se cotejaron con el ZIP original y el registro de captura del HTML aprobado; no se regeneraron. La revisión verificó hoja móvil, panel diario y cajón semanal de escritorio, foco visible y restaurado, controles de 44 px, ausencia de desborde horizontal de página y confirmación de Cancelar independiente. El mockup usa los tokens y `brand.js` del sistema de diseño. El [README](mockups/README.md) explica cómo revisar la evidencia. En código, limitar estilos a agenda; evitar `app.css` y helpers compartidos salvo necesidad comprobada.

La aprobación visual satisface el gate previo a cualquier Apply. La autorización expresa de Apply se limita a **US-015, tareas 2.x y 3.x**, con Strict TDD según 1.3. US-015 se implementó y se mergeó en la PR #21. US-014 se difirió después a C-21.

## Risks / Trade-offs

- **Comunicación a tercero o a número viejo** → solo apertura por gesto, teléfono vigente validado, sin PII adicional, no-store y sin referrer/opener; el odontólogo conserva la decisión final.
- **Dependencias divergentes y reglas de aprobación** → si `main` avanza, actualizar la rama antes del Apply; `planning complete` es un estado de archivos y no levanta el gate de mockup ni la autorización separada de Apply.

## Migration Plan

US-015 no requiere migración de datos: el endpoint es de solo lectura y la acción es local al detalle de la agenda. Para revertirla alcanza con revertir el commit de la PR #21; no deja datos persistidos. El plan de despliegue de US-014 está en C-21.
