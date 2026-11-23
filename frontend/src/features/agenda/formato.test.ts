import { describe, expect, it } from 'vitest'
import { tituloFecha, tituloRango } from './formato'

describe('tituloRango', () => {
  it('muestra el rango dentro de un mismo mes con el estilo de tituloFecha', () => {
    // Rango semiabierto [lunes 23, sábado 28): el último día visible es viernes 27.
    expect(tituloRango('2026-11-23', '2026-11-28')).toBe('23 al 27 de noviembre')
    expect(tituloFecha('2026-11-27')).toBe('Viernes 27 de noviembre')
  })

  it('muestra ambos meses cuando el rango los cruza', () => {
    expect(tituloRango('2026-11-30', '2026-12-04')).toBe('30 de noviembre al 3 de diciembre')
    expect(tituloRango('2026-01-30', '2026-02-03')).toBe('30 de enero al 2 de febrero')
  })

  it('incluye el año en ambos extremos cuando el rango cruza el año', () => {
    expect(tituloRango('2026-12-28', '2027-01-02')).toBe('28 de diciembre de 2026 al 1 de enero de 2027')
    expect(tituloRango('2026-12-30', '2027-01-04')).toBe('30 de diciembre de 2026 al 3 de enero de 2027')
  })

  it('no rellena con cero los días de un solo dígito', () => {
    expect(tituloRango('2026-01-01', '2026-01-06')).toBe('1 al 5 de enero')
    expect(tituloRango('2026-09-03', '2026-10-02')).toBe('3 de septiembre al 1 de octubre')
  })
})
