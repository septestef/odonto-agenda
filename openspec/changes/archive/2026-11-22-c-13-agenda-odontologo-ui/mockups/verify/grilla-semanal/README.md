# Grilla semanal rota en la app real — verificación del fix (2026-11-23)

App real con perfil `demo` en `localhost:8080/agenda`, cuenta `demo@example.com`, semana del 23 al 27/11/2026.
Referencia: `../../agenda-semana.html` y `../../previews/agenda-semana-{1440,390}.png` (design system revisión 2).

## Síntoma (capturas `antes-*`)

- `.semana` medía 69 px de alto; las cabeceras de los días y los turnos quedaban en una sola fila, con los turnos
  apretados a la derecha y fuera de su columna.
- Medido a 1440: `grid-template-columns` resolvía a `40px` + **12 pistas de ~101 px** + `0px`.

## Causa

Venía de `1b05006` (feat C-13), no de `0489674` (fix del panel de detalle) ni de la integración de C-14:

1. `grid-template-columns: … repeat(auto-fit, minmax(var(--semana-col), 1fr))` crea tantas pistas como entran en el
   ancho, así que la colocación automática ponía las 12 celdas (cabeceras, horas y días) en la primera fila.
2. `.semana__horas` y `.semana__col` no tenían alto: todo su contenido es `position: absolute`, así que colapsaban
   al `min-height` (40 px).
3. La escala era `--min: 2px` (1 h = 120 px) en vez de la del mockup (`--space-4` cada 15 min, 1 h = 64 px).

## Fix

- `vista-semana.ts` declara en la grilla `--dias` (días con franjas) y `--minutos` (apertura a cierre).
- `app.css` usa `repeat(var(--dias), minmax(var(--semana-col), 1fr))`, alto `calc(var(--min) * var(--minutos))`
  y la geometría del mockup con tokens: horas de 56 px, días de 124 px como mínimo, cabeceras, eventos, bloqueos y
  línea de ahora.
- `scroll-padding-inline-start: var(--semana-horas)`: a 390 el desplazamiento hasta hoy ya no tapa la columna de horas.

## Resultado (capturas `despues-*`)

| Pantalla | Viewport | Resultado |
|---|---|---|
| Semana | 1440x900 | Coincide: 56 px + 5 × 222 px, columnas de 704 px (11 h), 1 h = 64 px |
| Semana | 390x844 | Coincide: 56 px + 5 × 124 px con desplazamiento horizontal interno, sin desborde de página, horas visibles |
| Día | 390x844 | Sin cambios |
| Detalle (cajón) | 1440x900 | Abre al tocar un turno de la grilla |
| Cancelar | 1440x900 | Abre la confirmación; se cerró con "No, volver" (no se canceló nada) |
| WhatsApp | 1440x900 | El pane bloquea el popup y aparece "Abrir WhatsApp" de respaldo, igual que en C-14 |

Diferencias menores que se mantienen: los datos de la demo no son los del mockup, y la columna de hoy depende del día real.
