import { describe, expect, it, vi } from 'vitest'
// @ts-expect-error: este proyecto no declara tipos de Node; readFileSync se usa solo en el test.
import { readFileSync } from 'node:fs'
import { crearControlNavegacion, estadoInicial, rangoDeVista, vistaInicial } from './navegacion'

describe('vista inicial de la agenda', () => {
  it('elige día en 390 px y semana en 1440 px con fecha local de Argentina', () => {
    expect(vistaInicial(390)).toBe('dia')
    expect(vistaInicial(1440)).toBe('semana')

    // 02:30 UTC del 25/11 todavía es 23:30 del 24/11 en Buenos Aires.
    const ahora = new Date('2026-11-25T02:30:00Z')

    expect(estadoInicial(390, ahora)).toEqual({ vista: 'dia', fecha: '2026-11-24' })
    expect(estadoInicial(1440, ahora)).toEqual({ vista: 'semana', fecha: '2026-11-24' })
  })

  it('usa el umbral 720 px inclusive para la semana y respeta el fin de año', () => {
    expect(vistaInicial(719)).toBe('dia')
    expect(vistaInicial(720)).toBe('semana')

    const ahora = new Date('2027-01-01T02:00:00Z')
    expect(estadoInicial(390, ahora).fecha).toBe('2026-12-31')
  })
})

describe('rangoDeVista', () => {
  it('día devuelve [F, F+1] y semana [lunes, lunes+7] cruzando mes y año', () => {
    expect(rangoDeVista('dia', '2026-11-25')).toEqual({ desde: '2026-11-25', hasta: '2026-11-26' })
    expect(rangoDeVista('dia', '2026-12-31')).toEqual({ desde: '2026-12-31', hasta: '2027-01-01' })
    expect(rangoDeVista('semana', '2026-11-25')).toEqual({ desde: '2026-11-23', hasta: '2026-11-30' })
    expect(rangoDeVista('semana', '2026-11-29')).toEqual({ desde: '2026-11-23', hasta: '2026-11-30' })
    expect(rangoDeVista('semana', '2027-01-01')).toEqual({ desde: '2026-12-28', hasta: '2027-01-04' })
  })

  it('respeta el lunes cuando la fecha ya es lunes y nunca supera 31 días', () => {
    const rango = rangoDeVista('semana', '2026-11-23')
    expect(rango).toEqual({ desde: '2026-11-23', hasta: '2026-11-30' })

    for (const vista of ['dia', 'semana'] as const) {
      const { desde, hasta } = rangoDeVista(vista, '2026-03-01')
      const dias = (Date.parse(`${hasta}T00:00Z`) - Date.parse(`${desde}T00:00Z`)) / 86_400_000
      expect(dias).toBeGreaterThanOrEqual(1)
      expect(dias).toBeLessThanOrEqual(31)
    }
  })
})

describe('control de navegación', () => {
  const hoy = new Date('2026-11-25T14:00:00Z')

  it('avanza un día, vuelve a hoy y vuelve a pedir el rango en cada cambio', () => {
    const alCambiar = vi.fn()
    const control = crearControlNavegacion({ ancho: 390, ahora: hoy, alCambiar })

    expect(control.estado()).toEqual({ vista: 'dia', fecha: '2026-11-25' })
    expect(control.puedeIrHoy()).toBe(false)

    control.siguiente()
    expect(control.estado()).toEqual({ vista: 'dia', fecha: '2026-11-26' })
    expect(control.puedeIrHoy()).toBe(true)
    expect(alCambiar).toHaveBeenLastCalledWith(rangoDeVista('dia', '2026-11-26'))

    control.anterior()
    expect(control.estado().fecha).toBe('2026-11-25')

    control.siguiente()
    control.hoy()
    expect(control.estado().fecha).toBe('2026-11-25')
    expect(control.puedeIrHoy()).toBe(false)
    expect(alCambiar).toHaveBeenLastCalledWith(rangoDeVista('dia', '2026-11-25'))
  })

  it('desplaza una semana y conserva la fecha al cambiar de vista', () => {
    const alCambiar = vi.fn()
    const control = crearControlNavegacion({ ancho: 1440, ahora: hoy, alCambiar })

    control.siguiente()
    expect(control.estado()).toEqual({ vista: 'semana', fecha: '2026-12-02' })
    expect(alCambiar).toHaveBeenLastCalledWith(rangoDeVista('semana', '2026-12-02'))

    control.cambiarVista('dia')
    expect(control.estado()).toEqual({ vista: 'dia', fecha: '2026-12-02' })
    expect(alCambiar).toHaveBeenLastCalledWith(rangoDeVista('dia', '2026-12-02'))

    control.hoy()
    control.cambiarVista('semana')
    expect(control.estado()).toEqual({ vista: 'semana', fecha: '2026-11-25' })

    control.hoy()
    expect(alCambiar).toHaveBeenCalledTimes(4)
  })

  it('no persiste la vista ni la fecha fuera de la sesión', () => {
    const fuente = readFileSync(new URL('./navegacion.ts', import.meta.url), 'utf8')
    expect(fuente).not.toContain('localStorage')
    expect(fuente).not.toContain('sessionStorage')
    expect(fuente).not.toContain('pushState')
  })
})
