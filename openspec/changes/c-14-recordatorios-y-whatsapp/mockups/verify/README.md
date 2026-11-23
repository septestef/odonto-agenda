# US-015 — Verificación visual acotada (3.3)

20 capturas de la **app Vite real en Edge**, corrida `evidence-HsIq6K`: Día y Semana × disponible, no disponible, preparando, error y apertura bloqueada × 390×844 y 1440×1000. Se usaron fixtures HTTP sintéticos; no se contactó WhatsApp ni se envió ningún mensaje. Se verificaron los nombres, las dimensiones PNG y la igualdad SHA-256 de las 20 copias contra sus originales. No se incluyeron respuestas HTTP ni informes brutos.

**Resultado dentro del alcance de US-015:** los cinco estados y sus textos se verificaron en las cuatro combinaciones de vista y tamaño, sin desborde horizontal y con controles del detalle de al menos 44 px. Se verificó foco de teclado visible y restaurado en los cuatro casos disponibles. Cancelar mantuvo su confirmación y retorno de foco en 20/20; en los cuatro casos no disponibles también se verificó el POST con respuesta simulada. El doble clic durante la preparación produjo un solo GET y una sola reserva de pestaña; la respuesta tardía se descartó al cerrar y reabrir el detalle.

**Apertura:** cuatro casos directos y cuatro mediante enlace manual. En los ocho se comprobó `window.opener === null`, ausencia del encabezado `Referer`, `document.referrer` vacío y prellenado sintético mediante un documento inerte interceptado antes de la red. En los casos de bloqueo nativo no se abrió ninguna pestaña hasta accionar el enlace manual. La agenda conservó el detalle; los errores cerraron la pestaña pendiente sin navegar.

## Comparación con el mockup y el baseline

No se declara paridad total con el [mockup de `2efaed5`](../detalle-whatsapp.html) ni una nueva aprobación. El diagnóstico CDP comparó el baseline C-13 `fb3f43e` con el candidato C-14 usando los mismos viewports (DPR 1) y dos fixtures. Las cuatro diferencias siguientes **ya existían en C-13**:

| Desvío respecto del mockup | CDP: C-13 → C-14 |
| --- | --- |
| Panel Día superpuesto en escritorio, no lateral en el flujo | Panel fijo `x=816, y=40, ancho=592 px` → mismos valores. En ambos, el panel intercepta el centro del botón Semana y de la navegación de fecha. |
| Sin atenuación del fondo y cajón semanal más ancho | Sin capa de fondo en hoja ni cajón → sin cambios. Cajón `x=880, y=0, ancho=560, alto=1000 px` → mismos valores; el mockup mide 420 px de ancho. Las hojas de ambas versiones llegan a `y=844`. |
| Confirmación móvil centrada, no anclada abajo | Caja `x=16, y=270, 358×304 px`, borde inferior `y=574` → misma geometría. En el mockup el borde inferior es `y=844`. |
| Grilla semanal comprimida | A 1440 px: **102 → 102 px** de alto con siete días de horario; **62 → 62 px** con cinco días y dos turnos (contenido desplazable de 1090 y 970 px, respectivamente). A 390 px: **202 → 202 px** y **162 → 162 px**. Las medidas no cambian al abrir el detalle; el scroll inicial era cero. |

No se observó regresión respecto de `fb3f43e` en esos cuatro puntos. Esta evidencia cubre la verificación de navegador de US-015, no la del backend ni un rediseño de C-13.
