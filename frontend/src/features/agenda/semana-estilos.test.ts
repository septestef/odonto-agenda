import { describe, expect, it } from 'vitest'
// @ts-expect-error: este proyecto no declara tipos de Node; readFileSync se usa solo en el test.
import { readFileSync } from 'node:fs'

/**
 * Regresión de la grilla semanal rota en la app real (2026-11-23): con
 * `repeat(auto-fit, …)` todas las celdas caían en una sola fila y las columnas
 * sin alto colapsaban a unos 40 px. Vitest no calcula layout, así que se fija
 * el contrato CSS que reproduce la geometría del mockup aprobado de C-13
 * (`agenda-semana.html`: 56 px de horas, columnas de 124 px, 1 h = 64 px).
 */
const css: string = readFileSync(new URL('../../estilos/app.css', import.meta.url), 'utf8')

function regla(selector: string): string {
  const inicio = css.indexOf(`${selector} {`)
  expect(inicio, `falta la regla ${selector}`).toBeGreaterThanOrEqual(0)
  return css.slice(inicio, css.indexOf('}', inicio))
}

describe('geometría de la grilla semanal (mockup C-13)', () => {
  it('fija una columna por día abierto en vez de auto-fit', () => {
    const grid = regla('.agenda .semana__grid')
    expect(grid).not.toContain('auto-fit')
    expect(grid).toContain('grid-template-columns: var(--semana-horas) repeat(var(--dias), minmax(var(--semana-col), 1fr))')
    expect(grid).toContain('min-width: calc(var(--semana-horas) + var(--dias) * var(--semana-col))')
  })

  it('las columnas de horas y de días miden los minutos visibles', () => {
    expect(regla('.agenda .semana__horas, .agenda .semana__col')).toContain('height: calc(var(--min) * var(--minutos))')
  })

  it('al desplazarse hasta hoy en celular deja visible la columna de horas', () => {
    expect(regla('.agenda .semana')).toContain('scroll-padding-inline-start: var(--semana-horas)')
  })

  it('usa la escala del mockup: 15 minutos por --space-4, horas de 56 px y columnas de 124 px', () => {
    expect(regla('.agenda .semana__grid')).toContain('--min: calc(var(--space-4) / 15)')
    expect(css).toContain('--semana-horas: calc(var(--space-12) + var(--space-2));')
    expect(css).toContain('--semana-col: calc(var(--space-1) * 31);')
  })
})
