import { afterEach, describe, expect, it, vi } from 'vitest'
import { esFechaValida, fechaLocalArgentina, horaArgentina, rangoHorario, restarDias, sumarDias } from './fechas'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllEnvs()
})

describe('horaArgentina', () => {
  it('convierte un instante UTC a la hora local de Argentina', () => {
    expect(horaArgentina(new Date('2026-11-19T13:30:00Z'))).toBe('10:30')
  })

  it('no cambia aunque el dispositivo esté en otra zona', () => {
    vi.stubEnv('TZ', 'Pacific/Auckland')

    expect(horaArgentina(new Date('2026-11-19T13:30:00Z'))).toBe('10:30')

    vi.unstubAllEnvs()
  })

  it('muestra la medianoche local con dos dígitos', () => {
    expect(horaArgentina(new Date('2026-11-20T03:00:00Z'))).toBe('00:00')
  })

  it('muestra el mediodía local', () => {
    expect(horaArgentina(new Date('2026-11-19T15:00:00Z'))).toBe('12:00')
  })
})

describe('fechaLocalArgentina', () => {
  it('obtiene el día anterior cuando en Argentina todavía no medianoche', () => {
    expect(fechaLocalArgentina(new Date('2026-11-20T02:30:00Z'))).toBe('2026-11-19')
  })

  it('conserva el día al mediodía local', () => {
    expect(fechaLocalArgentina(new Date('2026-11-19T15:00:00Z'))).toBe('2026-11-19')
  })

  it('formatea el 31 de diciembre sin desplazar el año', () => {
    expect(fechaLocalArgentina(new Date('2026-12-31T20:00:00Z'))).toBe('2026-12-31')
  })
})

describe('esFechaValida', () => {
  it('acepta una fecha local bien formada', () => {
    expect(esFechaValida('2026-11-19')).toBe(true)
  })

  it('rechaza un día que no existe en el mes', () => {
    expect(esFechaValida('2026-02-30')).toBe(false)
  })

  it('rechaza un mes fuera de rango', () => {
    expect(esFechaValida('2026-13-01')).toBe(false)
  })

  it('rechaza un formato distinto de YYYY-MM-DD', () => {
    expect(esFechaValida('19/11/2026')).toBe(false)
    expect(esFechaValida('2026-1-19')).toBe(false)
  })

  it('acepta el 29 de febrero de un año bisiesto', () => {
    expect(esFechaValida('2028-02-29')).toBe(true)
    expect(esFechaValida('2026-02-29')).toBe(false)
  })
})

describe('sumarDias', () => {
  it('suma un día cruzando de mes', () => {
    expect(sumarDias('2026-11-30', 1)).toBe('2026-12-01')
  })

  it('respeta el 29 de febrero de un año bisiesto', () => {
    expect(sumarDias('2028-02-28', 1)).toBe('2028-02-29')
  })

  it('cruza de año', () => {
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01')
  })

  it('rechaza una fecha inexistente', () => {
    expect(() => sumarDias('2026-02-30', 1)).toThrow('fecha inválida')
  })
})

describe('restarDias', () => {
  it('resta un día cruzando de año', () => {
    expect(restarDias('2026-01-01', 1)).toBe('2025-12-31')
  })
})

describe('rangoHorario', () => {
  it('formatea un rango con guion medio', () => {
    const inicio = new Date(Date.UTC(2026, 10, 19, 10, 30))
    const fin = new Date(Date.UTC(2026, 10, 19, 11, 15))

    expect(rangoHorario(inicio, fin)).toBe('10:30–11:15')
  })

  it('completa con cero los minutos de una sola cifra', () => {
    const inicio = new Date(Date.UTC(2026, 10, 19, 9, 5))
    const fin = new Date(Date.UTC(2026, 10, 19, 9, 40))

    expect(rangoHorario(inicio, fin)).toBe('09:05–09:40')
  })

  it('formatea la medianoche con dos dígitos', () => {
    const inicio = new Date(Date.UTC(2026, 10, 19, 0, 0))
    const fin = new Date(Date.UTC(2026, 10, 19, 0, 15))

    expect(rangoHorario(inicio, fin)).toBe('00:00–00:15')
  })

  it('no depende del reloj ni de la zona del dispositivo', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2030-06-01T20:00:00Z'))
    vi.stubEnv('TZ', 'Pacific/Auckland')

    const inicio = new Date(Date.UTC(2026, 10, 19, 10, 30))
    const fin = new Date(Date.UTC(2026, 10, 19, 11, 15))

    expect(rangoHorario(inicio, fin)).toBe('10:30–11:15')
  })
})
