# C-14 — WhatsApp en el detalle del turno (US-015)

Estefi aprobó explícitamente la **revisión 2** de [`detalle-whatsapp.html`](detalle-whatsapp.html) del commit `2efaed5` el **2026-11-23**. La aprobación es visual; no aprueba US-014 ni valida el comportamiento de la app real.

## Cómo revisarlo

1. Desde una copia local del repo, abrí `detalle-whatsapp.html` en el navegador, sin moverlo: usa rutas relativas a los tokens, la marca y los estilos del mockup de C-13. No necesitás levantar el backend.
2. En el modo adaptable de las DevTools, elegí **390×844** o **1440×1000** CSS px (DPR 1). En la URL agregá `#<vista>/<estado>`: por ejemplo, `#dia/disponible` o `#semana/apertura-bloqueada`.
3. Para revisar el foco, cerrá el detalle y volvé a abrirlo con el teclado. Probá **Cancelar turno → No, volver** en cualquier estado; la confirmación es independiente de WhatsApp y devuelve el foco a Cancelar.

Todo funciona con datos ficticios y acciones simuladas: no abre WhatsApp, no envía mensajes ni modifica turnos.

## Capturas originales

**390×844:** hoja inferior en Día y Semana. **1440×1000:** panel fijo en Día y cajón lateral derecho en Semana.

| Estado en la URL | Qué muestra | Día 390 | Día 1440 | Semana 390 | Semana 1440 |
|---|---|---|---|---|---|
| `disponible` | Acción secundaria WhatsApp | [PNG](previews/dia-disponible-390x844.png) | [PNG](previews/dia-disponible-1440x1000.png) | [PNG](previews/semana-disponible-390x844.png) | [PNG](previews/semana-disponible-1440x1000.png) |
| `no-disponible` | Acción deshabilitada con explicación | [PNG](previews/dia-no-disponible-390x844.png) | [PNG](previews/dia-no-disponible-1440x1000.png) | [PNG](previews/semana-no-disponible-390x844.png) | [PNG](previews/semana-no-disponible-1440x1000.png) |
| `preparando` | Indicador de preparación | [PNG](previews/dia-preparando-390x844.png) | [PNG](previews/dia-preparando-1440x1000.png) | [PNG](previews/semana-preparando-390x844.png) | [PNG](previews/semana-preparando-1440x1000.png) |
| `error` | Aviso con opción de volver a intentar | [PNG](previews/dia-error-390x844.png) | [PNG](previews/dia-error-1440x1000.png) | [PNG](previews/semana-error-390x844.png) | [PNG](previews/semana-error-1440x1000.png) |
| `apertura-bloqueada` | Aviso y enlace manual «Abrir WhatsApp» | [PNG](previews/dia-apertura-bloqueada-390x844.png) | [PNG](previews/dia-apertura-bloqueada-1440x1000.png) | [PNG](previews/semana-apertura-bloqueada-390x844.png) | [PNG](previews/semana-apertura-bloqueada-1440x1000.png) |

Son los 20 renders de Edge del 2026-11-23 del paquete de revisión `C14-WhatsApp-Verificado.zip`, copiados sin regenerarlos. Se verificaron las dimensiones de los PNG y su igualdad por SHA-256 con los originales. El registro de captura verifica los controles de 44 px, el foco visible y su retorno, y la ausencia de desborde horizontal de página. En Semana móvil, el desplazamiento vertical es de la agenda, no de la hoja de detalle.
