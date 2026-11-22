# Mockups C-11 · `gestion-turno-ui`

- **Design system**: revisión 2 (`design-system/tokens.css`, `brand.js`, `components.html`).
- **Decision**: yes (2026-11-22)
- **Approval**: approved (2026-11-22, by user)
- **Preview**: rendered (2026-11-22, 390×844 y 1440×900, capturas en `previews/`)

> Al crear `design.md` con `/opsx:propose`, copiar estos campos en su sección `## UI Mockups` y enlazar los mockups y las capturas desde `design.md` y `tasks.md` como criterios de aceptación. Los mockups aprobados no se editan. Si cambia el selector de C-10, la aprobación vuelve a `pending`.

## Pantallas

| Archivo | Qué muestra |
|---|---|
| [turno-activo.html](turno-activo.html) | `/turno/{token}` con turno `RESERVADO` y futuro: detalle, "Cambiar horario" y "Cancelar turno". Variante con `puedeReprogramar: false` (RN-SE-04). |
| [cancelar-confirmacion.html](cancelar-confirmacion.html) | Confirmación previa de la cancelación (US-008). Hoja inferior en celular y diálogo centrado en escritorio. |
| [reprogramar.html](reprogramar.html) | Selector de día y horario de la misma prestación, comparación antes/nuevo y "Confirmar cambio" (US-009). |
| [solo-lectura.html](solo-lectura.html) | A: recién cancelado. B: recién reprogramado (el link viejo muestra el original cancelado). C: turno pasado. |
| [estados.html](estados.html) | Carga, 404, 409 `RN-TU-01`, 422 `RN-TU-03`/`RN-TU-11`/`RN-AG-05`, 409 `RN-TU-07`/`RN-SE-02`, 409 `RN-SE-04`, error de red o 500 y envío en curso. |

Los recuadros punteados amarillos (`.nota-mockup`) son notas para quien revisa y no van a la UI real.

## Fuentes

- Contrato de la API: `openspec/changes/archive/2026-11-22-c-09-gestion-turno-token-api/design.md` (D1, D2 y D5).
- `knowledge-base/06_funcionalidades.md` §Épica 4 y `knowledge-base/07_flujos_principales.md` §Flujo 4.
- Alcance de C-11 en `CHANGES.md`.

## Decisiones para revisar

1. **Selector de horarios provisorio.** Se copió el de `components.html` porque el mockup de C-10 todavía no estaba disponible. En el apply se reutiliza el componente de C-10, y si ese mockup se aprueba distinto, este se alinea con él (cambio visual: se vuelve a pedir aprobación).
2. **Piezas nuevas derivadas de los tokens**, porque `components.html` no las define: diálogo o hoja inferior, botón de peligro relleno (`btn--danger-solid`, solo para la confirmación destructiva), skeleton de carga, punto "día de tu turno actual" y badge "Pasado".
3. **Textos propios de la UI por `codigo`**, sin mostrar el `mensaje` crudo del backend, excepto en el 409 `RN-TU-01`, que repite el texto de C-07/C-09.
4. **Después de reprogramar** la página no redirige: vuelve a pedir el turno original (ahora cancelado) y confirma la hora elegida con los datos que ya tenía la pantalla. El link nuevo llega solo por email (D1 de C-09).
5. **Aviso "Este link es personal"** en el lateral, porque el link funciona como credencial (D5 de C-09). La página lleva `<meta name="referrer" content="no-referrer">`.
6. Texto del botón: **"Cambiar horario"** en lugar de "Reprogramar", por ser más claro para el paciente.

## Cómo abrirlos

Los mockups usan rutas relativas a `design-system/`. Si el navegador bloquea `file://`, serví la raíz del repo:

```powershell
python -m http.server 5181 --bind 127.0.0.1
```

y abrí `http://127.0.0.1:5181/openspec/changes/c-11-gestion-turno-ui/mockups/turno-activo.html`.
