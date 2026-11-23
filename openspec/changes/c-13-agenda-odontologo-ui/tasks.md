# Tasks — C-13 `agenda-odontologo-ui`

## 1. Estructura y contratos de datos

- [x] 1.1 Crear `frontend/src/features/agenda/` con los tipos derivados de `unknown` (sin `any`) para `TurnoAgendaRespuesta` (P2 de C-12), franjas y bloqueos con `turnosAfectados` (D2/D6 de C-05); verificar con `cd frontend && npm run build` en modo strict y con `npm run lint` que no haya `any`.
- [x] 1.2 Implementar `consultarAgenda(desde, hasta)` sobre `GET /api/turnos?desde=&hasta=` reutilizando `shared/api-client.ts`; verificar con un test que arma la URL con rango semiabierto, conserva el orden por `inicio,id` y propaga 401/403/500 como `ErrorApi`.
- [x] 1.3 Implementar `consultarHorarios()` y `consultarBloqueos()` reutilizando el `api-client`; verificar con tests que validan la forma de `{franjas:[...]}` y `{bloqueos:[...]}`, y que filtran los bloqueos por superposición con el rango visible.

## 2. Vista por defecto, rangos y navegación

- [x] 2.1 Implementar la elección inicial de vista (día con ancho < 720 px, semana con ancho ≥ 720 px) y la fecha inicial `fechaLocalArgentina(new Date())`; verificar con un test que inyecta ancho y reloj fijo y comprueba el default en 390 y 1440 px, conforme a `mockups/agenda-dia.html + previews/agenda-dia-390.png / previews/agenda-dia-1440.png` y `mockups/agenda-semana.html + previews/agenda-semana-390.png / previews/agenda-semana-1440.png`.
- [x] 2.2 Implementar `rangoDeVista(vista, fecha)` que devuelve `[F, F+1]` para día y `[lunes, lunes+7]` para semana; verificar con un test que cruce mes y año, calcule el lunes local y garantice que el rango nunca supere los 31 días de P1.
- [x] 2.3 Implementar navegación anterior/siguiente, "Hoy" deshabilitado sobre la fecha actual y conservación de la otra elección al cambiar de vista; verificar con un test que avance un día y vuelva a hoy reconsultando el rango, sin persistir en URL ni `localStorage`, según `mockups/agenda-dia.html + previews/agenda-dia-390.png / previews/agenda-dia-1440.png` y `mockups/agenda-semana.html + previews/agenda-semana-390.png / previews/agenda-semana-1440.png`.

## 3. Vista diaria

- [x] 3.1 Renderizar las franjas agrupadas por período, horas `inicio–fin`, badges textuales de estado y origen, y la línea "ahora"; verificar con un test de descriptores ordenados y contra `mockups/agenda-dia.html + previews/agenda-dia-390.png / previews/agenda-dia-1440.png`.
- [x] 3.2 Renderizar huecos "libre" entre eventos, "fuera del horario de atención" entre franjas, turnos cancelados tachados y turnos pasados/en curso atenuados sin acción Cancelar; verificar con un test de fusión turnos/franjas y contra `mockups/agenda-dia.html + previews/agenda-dia-390.png / previews/agenda-dia-1440.png`.
- [x] 3.3 Renderizar bloqueos con su `motivo`, los `turnosAfectados` marcados y el aviso "El bloqueo no lo cancela: decidí vos" sin cancelarlos automáticamente; verificar con un test y contra `mockups/agenda-dia.html + previews/agenda-dia-390.png / previews/agenda-dia-1440.png`.

## 4. Vista semanal

- [x] 4.1 Renderizar la grilla Lun–Vie con el día actual resaltado, columnas solo para días con franjas y scroll horizontal en celular que arranca en hoy; verificar con un test de armado de columnas y contra `mockups/agenda-semana.html + previews/agenda-semana-390.png / previews/agenda-semana-1440.png`.
- [x] 4.2 Posicionar turnos y bloqueos por minutos desde la primera apertura al último cierre, con borde de aviso en turnos afectados y el resumen semanal que invita a revisarlos o cancelarlos; verificar con un test de posiciones y contra `mockups/agenda-semana.html + previews/agenda-semana-390.png / previews/agenda-semana-1440.png`.

## 5. Detalle del turno

- [x] 5.1 Implementar un único componente de detalle con panel fijo en día de escritorio (≥ 1100 px), hoja desde abajo en celular y cajón a la derecha en semana de escritorio, con `aria-current` en el turno elegido; verificar contra `mockups/turno-detalle.html + previews/turno-detalle-390.png / previews/turno-detalle-1440.png`.
- [x] 5.2 Renderizar solo campos del DTO P2 de C-12 (paciente, DNI, teléfono, email, prestación, duración, horario local, estado, origen, `canceladoPor`/`canceladoEn`), omitiendo filas ausentes sin rediseñar y sin token, hash, `email_gestion`, `odontologoId` ni auditoría; verificar con un test de descriptores y contra `mockups/turno-detalle.html + previews/turno-detalle-390.png / previews/turno-detalle-1440.png`.

## 6. Cancelación y confirmación

- [x] 6.1 Implementar el diálogo de confirmación con paciente, fecha y hora locales, prestación y origen, foco inicial en "No, volver", cierre con Escape y omisión de la frase del horario libre cuando el turno está en un bloqueo; verificar contra `mockups/cancelar-confirmacion.html + previews/cancelar-confirmacion-390.png / previews/cancelar-confirmacion-1440.png`.
- [x] 6.2 Implementar el `POST /api/turnos/{id}/cancelar` sin cuerpo y con CSRF del `api-client`, con mapeo de 204 (inclusive repetido) a éxito + recarga + toast "Turno cancelado", 409 `RN-TU-07` al estado 6, 404 `NO_ENCONTRADO` y 409 `RN-TU-01` al estado 7, y 500 `ERROR_INTERNO`/`SIN_CONEXION` al estado 8, sin reintento automático; verificar con un test de mapeo de errores y contra `mockups/cancelar-confirmacion.html + previews/cancelar-confirmacion-390.png / previews/cancelar-confirmacion-1440.png`.
- [x] 6.3 Ocultar Cancelar en turnos `CANCELADO`, `ATENDIDO`, `AUSENTE` o ya iniciados según el "ahora" visual, delegando la decisión final al 409 del servidor; verificar con un test de visibilidad y contra `mockups/agenda-dia.html + previews/agenda-dia-390.png / previews/agenda-dia-1440.png` y `mockups/turno-detalle.html + previews/turno-detalle-390.png / previews/turno-detalle-1440.png`.

## 7. Estados de carga, vacío y error

- [x] 7.1 Implementar carga, "Todavía no cargaste tus horarios" con enlace a `/config`, "No tenés turnos este día" (o su equivalente semanal) y día sin franja de atención; verificar con un test de selección de estado y contra `mockups/estados.html + previews/estados-390.png / previews/estados-1440.png`.
- [x] 7.2 Implementar los estados 6 ("Este turno ya empezó"), 7 ("Este turno cambió") y 8 / error de red ("No pudimos cargar la agenda" con Reintentar) sobre fallos de `GET /api/turnos`, sin alterar la copia aprobada ni dibujar una agenda parcial; verificar con un test de mapeo y contra `mockups/estados.html + previews/estados-390.png / previews/estados-1440.png`.

## 8. Verificación de integración

- [x] 8.1 Ejecutar `cd frontend && npm test` y verificar que toda la suite pase en verde, sin recortar tests por falta de tiempo.
- [x] 8.2 Ejecutar `cd frontend && npm run build` y verificar que compile en modo strict sin `any` y que el build de Vite termine correctamente.
- [x] 8.3 Comparar manualmente las cinco pantallas contra las diez capturas en 390×844 y 1440×900, verificando que no se rendericen las notas `.nota-mockup` ni los huecos reservados, y registrar el resultado antes de proponer el archive.

Nota: no hay tareas para WhatsApp (C-14), "Nuevo turno" (C-15) ni "Marcar atendido/ausente" (C-19); el diseño solo reserva el espacio (D8). La copia del estado 7 queda sujeta al blocker B1 del proposal, sin cambios en los mockups aprobados.
