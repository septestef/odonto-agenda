# Marca — OdontoAgenda

| Elemento | Valor | Fuente |
|---|---|---|
| Nombre del producto | **OdontoAgenda**. Bajada: "Sistema de gestión" | Provisto por el usuario (2026-11-17) |
| Nombre del proyecto (repo) | turnotardeutn | Carpeta del proyecto |
| Tono visual | Clínico y calmo: blancos, verde azulado suave, mucho aire | Elección del usuario (2026-11-17) |
| Voz | Español rioplatense con voseo ("Elegí un horario", "Cancelá tu turno"). Frases cortas y concretas, sin tecnicismos | Mercado argentino (KB 01) |
| Logo | Diente con calendario y tilde, más el logotipo "ODONTO" (verde azulado) y "AGENDA" (oscuro). `assets/logo.svg` y `assets/logo.png`. Hoy es raster; la versión vectorial está pendiente | Provisto por el usuario (2026-11-17) |
| Imágenes | **(TBD)**. Sin fotos de stock en la v0.1; la interfaz se apoya en tipografía, color y espacio | Presupuesto cero |
| Color principal | Verde azulado `#0f766e` (contraste 5.5:1 sobre blanco) | `tokens.css` |
| Tipografía | Fuente del sistema (system-ui / Segoe UI / Roboto). No se usan fuentes remotas | Presupuesto cero, rendimiento, funcionamiento sin conexión en la demo |

## Reglas de uso

- **Color:** el verde azulado se reserva para acciones principales, la selección y los turnos reservados. El rojo es solo para acciones destructivas (cancelar) y errores.
- **Estados de turno:** cada estado se distingue por color **y** por texto (etiqueta). Nunca solo por color.
- **Paciente:** las pantallas públicas son de una sola columna, pensadas primero para el celular, con una acción principal por paso.
- **Odontólogo:** prioriza la densidad de información en escritorio (agenda semanal) y conserva los objetivos táctiles de 44 px.
- **Logo:** se usa siempre sobre fondo blanco o muy claro, con al menos 44 px de alto en la barra superior para que se lea "Sistema de gestión". No se deforma ni se recolorea.

## Referencias de UX (Discovery)

- **Turnito:** reserva por link sin registro.
- **Dentally:** agenda y lista de espera.
- **Doctoralia:** elección de horario por el paciente.

Detalle en `docs/discovery/informe-discovery.md` §D.
