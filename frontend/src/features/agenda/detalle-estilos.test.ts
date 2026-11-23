import { describe, expect, it } from 'vitest'
// @ts-expect-error: este proyecto no declara tipos de Node; readFileSync se usa solo en el test.
import { readFileSync } from 'node:fs'

/**
 * Regresión de los desvíos de C-13 medidos en la verificación de C-14
 * (`mockups/verify/README.md`): vitest no calcula layout, así que se fija el
 * contrato CSS que produce la geometría del mockup aprobado.
 */
const css: string = readFileSync(new URL('../../estilos/app.css', import.meta.url), 'utf8')

function regla(selector: string, desde = 0): string {
  const inicio = css.indexOf(`${selector} {`, desde)
  expect(inicio, `falta la regla ${selector}`).toBeGreaterThanOrEqual(0)
  return css.slice(inicio, css.indexOf('}', inicio))
}

function pixeles(expresion: string): number {
  // --space-10 = 2.5rem = 40 px con la raíz por defecto.
  const coincidencia = /calc\(var\(--space-10\) \* ([\d.]+)\)/.exec(expresion)
  expect(coincidencia, `no es un múltiplo de --space-10: ${expresion}`).not.toBeNull()
  return Number(coincidencia?.[1]) * 40
}

describe('geometría del detalle de la agenda (mockup C-13)', () => {
  it('el panel Día no es fijo: va sticky en una columna de 380 px del flujo', () => {
    const panel = regla('.agenda .detalle--panel')
    expect(panel).toContain('position: sticky')
    expect(panel).not.toContain('right:')
    expect(regla('.agenda .agenda-cuerpo--con-panel')).toContain('grid-template-columns: minmax(0, 1fr) var(--agenda-panel-ancho)')
    expect(pixeles(/--agenda-panel-ancho: ([^;]+);/.exec(css)?.[1] ?? '')).toBe(380)
  })

  it('el cajón mide 420 px como el mockup, no 560', () => {
    expect(regla('.agenda .detalle--cajon')).toContain('width: var(--agenda-cajon-ancho)')
    expect(pixeles(/--agenda-cajon-ancho: ([^;]+);/.exec(css)?.[1] ?? '')).toBe(420)
  })

  it('hay velo de fondo bajo el detalle y otro por encima para la confirmación', () => {
    const velo = regla('.agenda .velo')
    expect(velo).toContain('position: fixed')
    expect(velo).toContain('inset: 0')
    expect(velo).toContain('opacity: 0.45')
    const zDetalle = Number(/z-index: (\d+)/.exec(regla('.agenda .detalle'))?.[1])
    const zVelo = Number(/z-index: (\d+)/.exec(velo)?.[1])
    const zVeloAlto = Number(/z-index: (\d+)/.exec(regla('.agenda .velo--confirmacion'))?.[1])
    const zDialogo = Number(/z-index: (\d+)/.exec(regla('.agenda .dialogo-confirmacion'))?.[1])
    expect(zVelo).toBeLessThan(zDetalle)
    expect(zVeloAlto).toBeGreaterThan(zDetalle)
    expect(zVeloAlto).toBeLessThan(zDialogo)
  })

  it('en celular la confirmación de Cancelar queda anclada abajo', () => {
    const media = css.lastIndexOf('@media (max-width: 559px)')
    expect(media).toBeGreaterThanOrEqual(0)
    const anclada = regla('.agenda .dialogo-confirmacion', media)
    expect(anclada).toContain('bottom: 0')
    expect(anclada).toContain('top: auto')
    expect(anclada).toContain('transform: none')
  })

  it('usa solo variables del design system en las reglas nuevas', () => {
    for (const selector of ['.agenda .velo', '.agenda .detalle--panel', '.agenda .detalle--cajon', '.agenda .agenda-cuerpo--con-panel']) {
      expect(regla(selector)).not.toMatch(/#[0-9a-f]{3,8}\b|\b\d+px\b/i)
    }
  })
})
