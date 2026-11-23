# Mockups C-13 · `agenda-odontologo-ui`

- **Design system**: revisión 2 (`design-system/tokens.css`, `brand.js`, `components.html`). Shell copiado del mockup aprobado de C-04.
- **Decision**: yes (2026-11-22)
- **Approval**: approved (2026-11-22, by user)
- **Preview**: rendered (2026-11-22, 390×844 y 1440×900, capturas en `previews/`)

> Al crear `design.md` con `/opsx:propose`, copiar estos campos en su sección `## UI Mockups` y enlazar los mockups y las capturas desde `design.md` y `tasks.md` como criterios de aceptación. Los mockups aprobados no se editan. Si el contrato de C-12 obliga a cambiar algo visible (más allá de omitir datos del detalle), la aprobación vuelve a `pending`.

## Pantallas

| Archivo | Qué muestra |
|---|---|
| [agenda-dia.html](agenda-dia.html) | Vista diaria (US-010): franjas de atención, turnos con paciente, prestación, horario, estado y origen, huecos libres, línea "ahora", fuera de horario, un bloqueo con un turno adentro y cancelación desde la lista. En escritorio, panel fijo con el detalle del turno elegido. |
| [agenda-semana.html](agenda-semana.html) | Vista semanal: grilla de lunes a viernes con turnos, bloqueos, fuera de horario, el día de hoy resaltado y aviso de turnos dentro de bloqueos. En celular se desplaza en horizontal. |
| [turno-detalle.html](turno-detalle.html) | Detalle del turno: hoja desde abajo en celular y cajón a la derecha en escritorio. Incluye el aviso de turno dentro de un bloqueo. |
| [cancelar-confirmacion.html](cancelar-confirmacion.html) | Confirmación antes de cancelar (US-012, RN-TU-09), con el mismo diálogo derivado que C-11. |
| [estados.html](estados.html) | Carga, sin horario cargado, día sin turnos, día que no se atiende, aviso de turno cancelado, 409 `RN-TU-07`, turno que cambió (404/409) y error de red. |

Los recuadros punteados amarillos (`.nota-mockup`) son notas para quien revisa y no van a la UI real.

## Fuentes

- Contratos de horarios y bloqueos (con `turnosAfectados`): `openspec/changes/archive/2026-11-22-c-05-configuracion-agenda-api/design.md` (D2, D3 y D6).
- Alcance de C-12 y C-13 en `CHANGES.md`; `knowledge-base/06_funcionalidades.md` §Épica 5; `knowledge-base/05_reglas_de_negocio.md` RN-AG-02 y RN-TU-09.
- Shell, toast y estado vacío: `openspec/changes/archive/2026-11-22-c-04-shell-frontend-y-login/mockups/layout-base.html`.

## Decisiones para revisar

1. **Contrato de C-12 todavía no disponible.** Los campos del turno (paciente con DNI, teléfono y email; prestación; horario; estado; origen) y los códigos de error al cancelar salen del alcance en `CHANGES.md`. Si C-12 expone menos datos, el detalle se recorta sin cambiar el diseño.
2. **Vista por defecto**: día en celular y semana en escritorio (a confirmar en la propuesta). En celular, la grilla semanal tiene que arrancar desplazada hasta el día de hoy.
3. **Turnos pasados o en curso**: atenuados y sin "Cancelar". Los cancelados se muestran tachados en la lista.
4. **Bloqueos**: se ven en la lista y en la grilla; los turnos que quedan adentro se marcan (borde ámbar en la semana, aviso con acción de cancelar en el día y en el detalle). El bloqueo no cancela nada (RN-AG-02).
5. **Piezas nuevas derivadas de los tokens**: selector Día/Semana, panel y cajón de detalle, línea "ahora", fila "libre", badge "Bloqueo", grilla semanal posicionada por minutos. El diálogo y el botón de peligro relleno son los mismos que C-11.
6. **Fuera de alcance, con lugar reservado**: el botón de WhatsApp (C-14) y "Nuevo turno" (C-15). "Marcar atendido/ausente" es post-demo (C-19).

## Cómo abrirlos

Si el navegador bloquea `file://`, serví la raíz del repo:

```powershell
python -m http.server 5182 --bind 127.0.0.1
```

y abrí `http://127.0.0.1:5182/openspec/changes/c-13-agenda-odontologo-ui/mockups/agenda-dia.html`.
