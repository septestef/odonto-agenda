import { describe, expect, it } from 'vitest'
import { validarDuracion, validarFranjas, validarRangoBloqueo } from './validadores'
import type { Franja } from './modelos'

const franja = (diaSemana: number, inicio: string, fin: string): Franja => ({ diaSemana, inicio, fin })

describe('validarFranjas', () => {
  it('rechaza una franja invertida con el mensaje del mockup', () => {
    const resultado = validarFranjas([franja(1, '13:00', '09:00')])

    expect(resultado.valido).toBe(false)
    expect(resultado.codigo).toBe('RN-AG-01')
    expect(resultado.errores['franjas[0]']).toBe('La hora de inicio debe ser anterior a la de fin.')
    expect(resultado.primero).toBe('franjas[0]')
  })

  it('rechaza horas iguales', () => {
    const resultado = validarFranjas([franja(1, '09:00', '09:00')])

    expect(resultado.valido).toBe(false)
    expect(resultado.errores['franjas[0]']).toBe('La hora de inicio debe ser anterior a la de fin.')
  })

  it('rechaza días fuera de 1–7', () => {
    expect(validarFranjas([franja(0, '09:00', '10:00')]).errores['franjas[0]']).toBe(
      'Elegí un día entre el lunes y el domingo.',
    )
    expect(validarFranjas([franja(8, '09:00', '10:00')]).errores['franjas[0]']).toBe(
      'Elegí un día entre el lunes y el domingo.',
    )
  })

  it('rechaza formatos que no son HH:mm', () => {
    const resultado = validarFranjas([franja(1, '9:00', '10:00')])

    expect(resultado.valido).toBe(false)
    expect(resultado.errores['franjas[0]']).toBe('Usá el formato HH:mm.')
  })

  it('acepta una franja válida', () => {
    expect(validarFranjas([franja(1, '09:00', '13:00')]).valido).toBe(true)
  })

  it('rechaza franjas solapadas del mismo día y marca ambas', () => {
    const resultado = validarFranjas([franja(3, '09:00', '11:00'), franja(3, '10:00', '12:00')])

    expect(resultado.valido).toBe(false)
    expect(resultado.codigo).toBe('RN-AG-01')
    expect(resultado.errores['franjas[0]']).toBe('Se superpone con la otra franja.')
    expect(resultado.errores['franjas[1]']).toBe('Se superpone con la otra franja.')
  })

  it('acepta franjas contiguas del mismo día', () => {
    expect(validarFranjas([franja(3, '09:00', '11:00'), franja(3, '11:00', '13:00')]).valido).toBe(true)
  })

  it('acepta la misma hora en días distintos', () => {
    expect(validarFranjas([franja(1, '09:00', '11:00'), franja(2, '09:00', '11:00')]).valido).toBe(true)
  })
})

describe('validarDuracion', () => {
  it('rechaza 0 y 20, y acepta 30, 45 y 60', () => {
    expect(validarDuracion(0).errores['duracionMinutos']).toBe('La duración tiene que ser un múltiplo de 15 minutos.')
    expect(validarDuracion(20).errores['duracionMinutos']).toBe('La duración tiene que ser un múltiplo de 15 minutos.')
    expect(validarDuracion(20).codigo).toBe('RN-AG-03')
    expect(validarDuracion(30).valido).toBe(true)
    expect(validarDuracion(45).valido).toBe(true)
    expect(validarDuracion(60).valido).toBe(true)
  })
})

describe('validarRangoBloqueo', () => {
  it('rechaza un rango invertido y marca el campo fin', () => {
    const resultado = validarRangoBloqueo(
      { fecha: '2026-11-27', hora: '12:00' },
      { fecha: '2026-11-27', hora: '10:00' },
    )

    expect(resultado.valido).toBe(false)
    expect(resultado.errores['fin']).toBe('La hora de fin debe ser posterior a la de inicio.')
    expect(resultado.primero).toBe('fin')
  })

  it('acepta un rango válido', () => {
    const resultado = validarRangoBloqueo(
      { fecha: '2026-11-27', hora: '10:00' },
      { fecha: '2026-11-27', hora: '12:00' },
    )

    expect(resultado.valido).toBe(true)
    expect(resultado.errores).toEqual({})
  })
})
