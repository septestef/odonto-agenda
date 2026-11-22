import { describe, expect, it } from 'vitest'
import { desglosarInstante, fechaVisible, instanteConOffset, rangoVisible } from './instantes'

describe('armado de instantes con offset de Argentina', () => {
  it('arma el rango del 27/11 de 10:00 a 12:00 con offset vigente', () => {
    expect(instanteConOffset('2026-11-27', '10:00')).toBe('2026-11-27T10:00:00-03:00')
    expect(instanteConOffset('2026-11-27', '12:00')).toBe('2026-11-27T12:00:00-03:00')
  })

  it('respeta el cruce de mes y año', () => {
    expect(instanteConOffset('2026-12-31', '23:59')).toBe('2026-12-31T23:59:00-03:00')
    expect(instanteConOffset('2027-01-01', '00:30')).toBe('2027-01-01T00:30:00-03:00')
  })

  it('calcula el offset según la fecha, sin fijarlo en -03:00', () => {
    expect(instanteConOffset('2009-01-15', '12:00')).toBe('2009-01-15T12:00:00-02:00')
    expect(instanteConOffset('2009-07-15', '12:00')).toBe('2009-07-15T12:00:00-03:00')
  })

  it('muestra fecha y rango en hora de Argentina', () => {
    const inicio = instanteConOffset('2026-11-27', '10:00')
    const fin = instanteConOffset('2026-11-27', '12:00')

    expect(fechaVisible(inicio)).toBe('27/11/2026')
    expect(rangoVisible(inicio, fin)).toBe('10:00–12:00')
  })

  it('desglosa un instante recibido con offset', () => {
    expect(desglosarInstante('2027-01-01T00:30:00-03:00')).toEqual({ fecha: '2027-01-01', hora: '00:30' })
  })
})
