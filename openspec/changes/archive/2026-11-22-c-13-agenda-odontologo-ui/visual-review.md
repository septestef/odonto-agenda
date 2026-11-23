# Revisión visual — C-13 `agenda-odontologo-ui`

## Estado

**Completado (tarea 8.3), 2026-11-22.** El orquestador levantó backend + Vite, hizo
login con el usuario demo y capturó la app real con Chrome headless/CDP a 390×844 y
1440×900. Las diez capturas están en `mockups/verify/` y se compararon contra las
diez aprobadas en `mockups/previews/`; los mockups y las previews no se editaron.

**Resultado:** 0 diferencias bloqueantes, 0 diferencias mayores, 4 diferencias menores
(documentadas más abajo). Durante el verify se detectó y corrigió con TDD el `<h1>`
de la vista semanal: mostraba la fecha ancla («Domingo 29 de noviembre») en vez del
rango visible del mockup («23 al 27 de noviembre»). La corrección calcula el último
día con franjas y respeta el extremo exclusivo de `rangoDeVista`; quedó cubierta por
tests y las capturas finales ya la reflejan.

## Salvedad aprobada (B1)

La copia del **estado 7** se mantuvo exactamente como está en el mockup aprobado:

> **Este turno cambió**  
> Puede que el paciente lo haya cancelado o cambiado de horario. Actualizamos la agenda.

El contrato de C-12 responde `204` de forma idempotente cuando un turno ya estaba
cancelado, así que en la práctica ese texto aparece solo ante `404 NO_ENCONTRADO`
o `409 RN-TU-01`. El usuario resolvió el blocker el 2026-11-22: no se editan los
mockups ni las capturas; se documenta acá la diferencia entre copia y contrato.
El comportamiento funcional (alerta + recarga del rango visible) está implementado
y cubierto por tests.

## Pantallas a comparar

| Pantalla | Mockup | Capturas |
|---|---|---|
| Vista diaria | `mockups/agenda-dia.html` | `previews/agenda-dia-390.png`, `previews/agenda-dia-1440.png` |
| Vista semanal | `mockups/agenda-semana.html` | `previews/agenda-semana-390.png`, `previews/agenda-semana-1440.png` |
| Detalle del turno | `mockups/turno-detalle.html` | `previews/turno-detalle-390.png`, `previews/turno-detalle-1440.png` |
| Confirmación de cancelación | `mockups/cancelar-confirmacion.html` | `previews/cancelar-confirmacion-390.png`, `previews/cancelar-confirmacion-1440.png` |
| Estados | `mockups/estados.html` | `previews/estados-390.png`, `previews/estados-1440.png` |

## Chequeos ya cubiertos por tests

- No se renderizan las notas `.nota-mockup` ni los huecos reservados de C-14/C-15/C-19.
- El detalle cambia de contenedor según ancho y vista; el turno elegido lleva `aria-current`.
- La confirmación declara `role="alertdialog"`, `aria-modal`, `aria-labelledby`,
  `aria-describedby` y `data-cerrar-escape`, con foco inicial en «No, volver».
- Los textos de estado 6, 7 y 8 son los aprobados, sin agenda parcial cuando hay error.
- Todo dato de usuario se emite como texto (`textContent` del adaptador), nunca como HTML.

## Limitación conocida del adaptador compartido

`data-cerrar-escape` declara la acción de cierre con Escape, pero el adaptador de
C-04 solo registra eventos `click`/`submit`; no despacha teclas. Implementar el
cierre real requiere tocar `shared/ui/adaptador.ts` y `bootstrap.ts`, fuera del
alcance de este change. Queda registrado para la revisión visual y un eventual
follow-up, sin bloquear la demo ni las tareas 1.1–8.2.

## Capturas reales

| Pantalla | 390×844 | 1440×900 |
|---|---|---|
| Vista diaria | `mockups/verify/agenda-dia-390.png` | `mockups/verify/agenda-dia-1440.png` |
| Vista semanal | `mockups/verify/agenda-semana-390.png` | `mockups/verify/agenda-semana-1440.png` |
| Detalle del turno | `mockups/verify/turno-detalle-390.png` | `mockups/verify/turno-detalle-1440.png` |
| Confirmación de cancelación | `mockups/verify/cancelar-confirmacion-390.png` | `mockups/verify/cancelar-confirmacion-1440.png` |
| Estados | `mockups/verify/estados-390.png` | `mockups/verify/estados-1440.png` |

Se usó el mismo navegador para las diez tomas, con `deviceScaleFactor=1`. Las
capturas reales son de viewport; varias previews aprobadas son full-page, por lo
que difieren en alto aunque compartan ancho. Esa diferencia es esperada.

## Comparación por pantalla

| Pantalla | Resultado | Detalle |
|---|---|---|
| Vista diaria | ✅ Sin bloqueos | Coinciden el encabezado, las franjas Mañana/Tarde, los huecos «libre», el texto «fuera del horario de atención», los badges de estado/origen, los turnos cancelados atenuados y las acciones Ver/Cancelar. |
| Vista semanal | ✅ Sin bloqueos (corregida) | El `<h1>` final dice «23 al 27 de noviembre», igual al mockup. Coinciden columnas Lun–Vie, resaltado de hoy, grilla por minutos, scroll horizontal en 390 y ausencia de sábado/domingo sin franjas. |
| Detalle del turno | ✅ Sin bloqueos | Coinciden el contenedor según ancho/vista, el `aria-current`, los campos del DTO de C-12 y la omisión de token/hash/auditoría. |
| Confirmación de cancelación | ✅ Sin bloqueos | Textos exactos: «¿Cancelás el turno de …?», «Le avisamos por email … No se puede deshacer.», «Sí, cancelar turno» y «No, volver» con el foco inicial en la última. `role="alertdialog"` presente. |
| Estados | ✅ Sin bloqueos | Coinciden «El domingo no atendés» y «No tenés turnos esta semana». El mockup es un tablero estático con todos los estados; la app real muestra el estado correspondiente a cada carga. |

## Diferencias menores

1. **M1 — Datos demo vs. datos del mockup.** El mockup usa nombres ficticios fijos
   (p. ej. Ana Pérez); la app muestra los turnos reales de la base demo (p. ej.
   Tomás Aguirre, Lucía Fernández, Sofía Benítez). Es sustitución de datos, no de
   diseño. No se alteró la copia ni la estructura.
2. **M2 — Alto de captura.** Las previews de día/semana/estados son full-page y las
   reales son viewport (390×844 / 1440×900), según lo pedido. El ancho y el contenido
   visible coinciden.
3. **M3 — Estados 6/7/8 no disparados en vivo.** Con los datos demo no se produjeron
   `409 RN-TU-07`, `404`/`409 RN-TU-01` ni `500`; sus textos y el mapeo están cubiertos
   por tests de `estados.ts`, `cancelar.ts` y `control.ts`. La captura de estados
   muestra los estados vacíos alcanzables en la demo.
4. **M4 — Salvedades ya conocidas.** Se mantienen la copia B1 del estado 7 y la
   limitación del cierre con Escape del adaptador compartido de C-04; ambas están
   documentadas arriba y no bloquean la demo.

## Resultado técnico de la verificación

| Comando | Resultado |
|---|---|
| `npm --prefix frontend test -- --run` | 25 archivos, 262 tests en verde |
| `npm --prefix frontend run lint` | exit 0, sin errores |
| `npm --prefix frontend run build` | `tsc --noEmit` + Vite, 31 módulos, build correcto |
| `openspec validate c-13-agenda-odontologo-ui --strict` | `Change 'c-13-agenda-odontologo-ui' is valid` |
| `git status -- openspec/changes/c-13-agenda-odontologo-ui/mockups/` | sin cambios en mockups ni previews |

**Conclusión:** la implementación es conforme al diseño aprobado. Queda apta para
commit/PR; el archive se hará recién cuando el PR esté mergeado en `main`.
