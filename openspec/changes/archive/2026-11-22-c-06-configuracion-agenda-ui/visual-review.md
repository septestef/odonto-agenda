# Revisión visual — C-06

Fecha: 2026-11-22. Mockups: seis archivos aprobados y congelados en `mockups/` (sin modificaciones).

## Método

- Se extrajeron los textos visibles, en orden de lectura, de los seis mockups (1440×900 y 360×844) y se compararon con las secuencias de texto que producen los descriptors reales de `vistas/horarios.ts`, `vistas/prestaciones.ts`, `vistas/bloqueos.ts` y `config.ts`. Esas secuencias están cubiertas por los tests de cada vista.
- Se verificó que los estados visibles (carga, guardado, error, sin atención, dada de baja, turnos afectados, avisos) y el orden de bloques coinciden con el mockup correspondiente.
- `frontend/src/estilos/app.css` aplica las mismas reglas visuales que los mockups usando exclusivamente `var(--...)` de `design-system/tokens.css`; no se agregaron colores, espaciados, radios ni fuentes literales.
- Limitación: el browser integrado no estaba conectado a la sesión, por lo que no se pudieron capturar screenshots de la UI real en los dos viewports. La comparación es estructural (textos, estados, orden y reglas de estilo), no pixel a pixel.

## Resultado por pantalla

| Pantalla | Textos y orden | Estados | Reglas visuales |
|---|---|---|---|
| Horarios escritorio | Coinciden: encabezado, descripción, alertas, siete días, franjas, "Agregar franja", "Guardar horario", "Descartar cambios" | carga / error / guardando / guardado | grilla de siete columnas, tarjetas de día y franja |
| Horarios móvil | Coinciden el mismo contenido, reordenado en una columna | ídem | una columna, botones a ancho completo |
| Prestaciones escritorio | Coinciden: "Nueva prestación", alerta de duración, campos, "Tus prestaciones", aviso y listado | alta / edición / baja confirmada / inactiva / error | listado en filas, badges de estado |
| Prestaciones móvil | Mismo contenido en una columna | ídem | una columna |
| Bloqueos escritorio | Coinciden: resumen de turnos, aviso, "Nuevo bloqueo", formulario, "Tus bloqueos" y turnos afectados | sin turnos / con turnos / baja / error de rango | alertas antes del encabezado, tarjetas de bloqueo y turno |
| Bloqueos móvil | Mismo contenido y orden; el error de rango incluye "Corregí el rango antes de agregar el bloqueo." | ídem | una columna |

## Desvíos documentados (sin editar mockups)

1. Los mockups muestran textos ilustrativos de relleno en el alert de solape de horarios ("En miércoles, 10:00–12:00 se superpone con 09:00–11:00. Corregila y guardá de nuevo."). La UI real muestra el título canónico "Las franjas del mismo día no pueden solaparse" y el mensaje por campo "Se superpone con la otra franja.", tal como exige el spec. Es un texto de ejemplo del mockup, no un requisito.
2. Los mockups móviles muestran "Salir" en la barra superior; el shell real conserva "Cerrar sesión" de C-04. El spec de este change prohíbe cambiar `layout.ts`, por lo que se mantuvo el texto del shell aprobado en C-04.
3. El pie "Mockup estático · PENDIENTE DE APROBACIÓN del usuario · design system revisión 2 · valores en tokens.css" y el título del documento son propios del artefacto mockup; no aparecen en la aplicación.
4. Los datos de los mockups son fijos; la UI real obtiene horarios, prestaciones y bloqueos de la API. Los textos y estados son los mismos, pero los valores concretos dependen de la respuesta del backend.
5. Los nombres de clases CSS de los mockups (`.bloque`, `.field`, `.alert`, `.btn--primary`) difieren de los descriptors reales (`.seccion-config`, `.campo`, `.alerta`, `.btn--primaria`). `app.css` replica las mismas reglas visuales para las clases reales; no hay diferencia visible.
