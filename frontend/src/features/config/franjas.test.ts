import { describe, expect, it } from 'vitest'
import { aplanarFranjas, agruparFranjas, ordenarFranjas } from './franjas'
import type { Franja } from './modelos'

const franja = (diaSemana: number, inicio: string, fin: string): Franja => ({ diaSemana, inicio, fin })

const RESPUESTA_API: readonly Franja[] = [
  franja(1, '16:00', '20:00'),
  franja(1, '09:00', '13:00'),
]

describe('mapeo de franjas', () => {
  it('agrupa los siete días ISO y respeta el orden de las franjas del lunes', () => {
    const dias = agruparFranjas(RESPUESTA_API)

    expect(dias.map((dia) => dia.diaSemana)).toEqual([1, 2, 3, 4, 5, 6, 7])
    expect(dias.map((dia) => dia.nombre)).toEqual([
      'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo',
    ])
    expect(dias[0]?.franjas).toEqual([franja(1, '09:00', '13:00'), franja(1, '16:00', '20:00')])
    expect(dias[6]?.franjas).toEqual([])
  })

  it('ordena por día y por inicio antes de agrupar', () => {
    const dias = agruparFranjas([
      franja(2, '16:00', '20:00'),
      franja(1, '16:00', '20:00'),
      franja(1, '09:00', '13:00'),
    ])

    expect(dias[0]?.franjas).toEqual([franja(1, '09:00', '13:00'), franja(1, '16:00', '20:00')])
    expect(dias[1]?.franjas).toEqual([franja(2, '16:00', '20:00')])
  })

  it('round-trip: lista → semana → lista conserva exactamente las franjas', () => {
    const original = [franja(1, '09:00', '13:00'), franja(1, '16:00', '20:00'), franja(5, '10:00', '12:00')]

    expect(aplanarFranjas(agruparFranjas(original))).toEqual(original)
  })

  it('conserva tres franjas desordenadas de un mismo día en el PUT resultante', () => {
    const desordenadas = [
      franja(2, '16:00', '18:00'),
      franja(2, '09:00', '11:00'),
      franja(2, '11:00', '13:00'),
    ]
    const dias = agruparFranjas(desordenadas)

    expect(dias[1]?.franjas).toEqual([
      franja(2, '09:00', '11:00'),
      franja(2, '11:00', '13:00'),
      franja(2, '16:00', '18:00'),
    ])
    expect(aplanarFranjas(dias)).toHaveLength(3)
    expect(aplanarFranjas(dias)).toEqual([
      franja(2, '09:00', '11:00'),
      franja(2, '11:00', '13:00'),
      franja(2, '16:00', '18:00'),
    ])
  })

  it('no agrega ids ni odontologoId al aplanar', () => {
    const plano = aplanarFranjas(agruparFranjas(RESPUESTA_API))

    expect(JSON.stringify(plano)).not.toContain('odontologoId')
    expect(JSON.stringify(plano)).not.toContain('"id"')
  })
})

describe('ordenarFranjas', () => {
  it('ordena por día, inicio y fin de forma estable', () => {
    const entrada = [franja(3, '10:00', '12:00'), franja(1, '09:00', '13:00'), franja(3, '09:00', '11:00')]

    expect(ordenarFranjas(entrada)).toEqual([
      franja(1, '09:00', '13:00'),
      franja(3, '09:00', '11:00'),
      franja(3, '10:00', '12:00'),
    ])
  })
})
