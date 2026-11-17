# Sistema de diseño — OdontoAgenda

Mode: system-and-mockups
Revision: 2
Approval: approved (2026-11-17, by user)
Decision source: el usuario eligió "Sistema + mockups" en la fase de fundación (2026-11-17). Aprobó la revisión 1 y en el mismo mensaje entregó el nombre y el logo definitivos, que se aplicaron como revisión 2.

## Historial

| Revisión | Cambio |
|---|---|
| 1 | Base inicial: tokens, componentes, nombre y logo provisionales |
| 2 | Nombre definitivo **OdontoAgenda** y logo provisto por el usuario. Los tokens no cambiaron |

## Propósito

Esta carpeta es la base visual única del producto. Las pantallas reales y los mockups de cada change importan `tokens.css` y `brand.js`, y referencian `assets/logo.svg`. Con la política `system-and-mockups`, todo cambio visual tiene un mockup HTML aprobado antes de implementarse (`openspec/changes/<name>/mockups/`).

## Archivos

| Archivo | Contenido |
|---|---|
| `tokens.css` | Variables CSS de color, tipografía, espaciado, radios, sombras, foco y movimiento, con soporte de movimiento reducido |
| `brand.md` | Nombre, tono, voz, logo, imágenes y reglas de uso |
| `brand.js` | Constante con el nombre (`OdontoAgenda`) y binding para `[data-brand-name]` |
| `components.html` | Referencia visual: color, tipografía, botones, formulario, flujo de reserva, mensajes, tarjetas de turno, agenda semanal y estado vacío |
| `assets/logo.png` | Logo original provisto por el usuario (1024×683, raster) |
| `assets/logo.svg` | Ruta estable: el mismo PNG embebido y recortado al contenido (proporción aproximada 3:1) |
| `previews/` | Capturas renderizadas a 390×844 y 1440×900 |

## Fuentes

- Nombre y logo: provistos por el usuario el 2026-11-17.
- Tono "clínico y calmo": elegido por el usuario el 2026-11-17. El verde azulado del logo coincide con `--color-primary-600`.
- Actores, pantallas y reglas: `knowledge-base/01`, `03`, `05` y `06`.
- Referencias de UX: `discovery/research-report.md` §D.

## Decisiones abiertas

- **Logo vectorial**: el logo es raster. Para tamaños chicos (favicon, menos de 32 px) y para impresión conviene una versión SVG vectorial y un isotipo (el diente con el calendario) por separado. No bloquea la demo.
- **Imágenes o ilustraciones**: no hay; no son necesarias para la v0.1.
- **Modo oscuro**: no está definido para la v0.1.

## Cómo reemplazar

1. **Nombre**: cambiar `BRAND_NAME` en `brand.js` y la constante equivalente del frontend (`frontend/src/shared/brand.ts`).
2. **Logo**: reemplazar `assets/logo.svg` (y `assets/logo.png`), manteniendo las rutas. Si cambia la proporción, ajustar el `viewBox`.
3. **Colores u otros valores**: editar solo `tokens.css`. Cualquier cambio sube `Revision:`, vuelve `Approval:` a `pending` y obliga a re-renderizar los mockups afectados.

## Accesibilidad (base)

- **Contraste**: texto principal 15:1, secundario 6.1:1, botón primario 5.5:1.
- **Foco**: anillo visible de 2 px en el color primario en todos los controles.
- **Objetivos táctiles**: 44 px como mínimo. Campos con 16 px de texto como mínimo.
- **Estados**: los estados de turno nunca se distinguen solo por color.
- **Logo**: como incluye el nombre, en la barra superior el texto de marca queda solo para lectores de pantalla.
