# Mockups — C-15 `turno-manual-odontologo`

- Design system: revisión 2 (`design-system/README.md`, aprobada por el usuario el 2026-11-17).
- Decisión: **sí** (política `system-and-mockups`), 2026-11-23.
- Aprobación: approved (2026-11-23, by user) — aprobación explícita recibida antes del apply.
- Preview: rendered (2026-11-23), capturas en `previews/`.
- fuentes: mockups aprobados de C-13 (`agenda-dia.html`, `turno-detalle.html`) y C-10 (`reserva-formulario.html`, `reserva-conflicto.html`), `design-system/tokens.css`, `design-system/components.html`, `design-system/brand.js` y el logo en `design-system/assets/logo.svg`.

## Pantallas

| Archivo | Pantalla y estados |
|---|---|
| `agenda-con-nuevo-turno.html` | Agenda diaria con el botón “Nuevo turno” en la cabecera, con turnos online y manuales, línea de ahora, bloqueo y panel de detalle. |
| `formulario-turno-manual.html` | Diálogo “Nuevo turno” en el paso de datos del paciente, con resumen y aviso de email. Variantes: sin horarios, validación local, envío y errores de campo del servidor. |
| `conflicto-y-exito.html` | Conflicto 409 `RN-TU-01` con horarios refrescados y datos conservados; rechazos `RN-AG-05`, `RN-TU-11` y sesión/red; éxito con vuelta a la agenda, turno `MANUAL` y toast. |

## Decisiones visuales

- Se preserva el shell y la agenda de C-13; solo se agrega el botón “Nuevo turno” a la cabecera y se deja intacto el lugar reservado de C-14. No se reformatea la grilla.
- El formulario reutiliza los campos y el resumen de C-10, con texto en español rioplatense con voseo. Los horarios se eligen con chips y la navegación de días es la compartida.
- Los errores no reemplazan la información ya cargada: el conflicto muestra el mensaje exacto “Ese horario acaba de ser tomado, elegí otro” y actualiza solo los horarios.
- Todos los valores visuales vienen de `tokens.css`; no se usan colores, tipografías ni espaciados hardcodeados. El nombre de marca se toma de `brand.js` y el logo del path estable.

## Cómo previsualizar

Abrir los HTML con un navegador (por ejemplo, `agenda-con-nuevo-turno.html`) y usar el modo desarrollador para fijar 390×844 y 1440×900. Si `file://` bloquea los recursos, servir la **raíz del repo** porque los HTML referencian `design-system/`:

```
python -m http.server 5182 --bind 127.0.0.1
```

y abrir `http://127.0.0.1:5182/openspec/changes/c-15-turno-manual-odontologo/mockups/agenda-con-nuevo-turno.html`. Las capturas ya renderizadas están en `previews/*-390.png` y `previews/*-1440.png` (se generaron con Playwright a 390×844 y 1440×900, full page).
