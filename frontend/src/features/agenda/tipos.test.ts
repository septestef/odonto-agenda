import { describe, expect, it } from 'vitest'
import { leerTurnos } from './tipos'

describe('contratos de datos de la agenda', () => {
  it('acepta el DTO P2 de C-12 y descarta claves sensibles', () => {
    const cuerpo: unknown = [
      {
        id: 42,
        inicio: '2026-11-25T09:00:00-03:00',
        fin: '2026-11-25T09:30:00-03:00',
        estado: 'RESERVADO',
        origen: 'ONLINE',
        paciente: {
          id: 7,
          dni: '30123456',
          nombre: 'Ana',
          apellido: 'Pérez',
          telefono: '+5493511234567',
          email: 'ana@ejemplo.com',
        },
        prestacion: { id: 3, nombre: 'Consulta', duracionMinutos: 30 },
        canceladoPor: null,
        canceladoEn: null,
        tokenHash: 'hash-secreto',
        emailGestion: 'privado@ejemplo.com',
        odontologoId: 99,
      },
    ]

    const turnos = leerTurnos(cuerpo)

    expect(turnos).toHaveLength(1)
    expect(turnos[0]).toMatchObject({
      id: 42,
      inicio: '2026-11-25T09:00:00-03:00',
      estado: 'RESERVADO',
      origen: 'ONLINE',
      paciente: { dni: '30123456', nombre: 'Ana' },
      prestacion: { nombre: 'Consulta', duracionMinutos: 30 },
      canceladoPor: null,
      canceladoEn: null,
    })
    expect(Object.keys(turnos[0] ?? {})).not.toContain('tokenHash')
    expect(JSON.stringify(turnos)).not.toContain('hash-secreto')
    expect(JSON.stringify(turnos)).not.toContain('privado@ejemplo.com')
  })

  it('conserva un cancelado con su autor y omite datos de paciente ausentes', () => {
    const cuerpo: unknown = [
      {
        id: 7,
        inicio: '2026-11-25T16:00:00-03:00',
        fin: '2026-11-25T16:30:00-03:00',
        estado: 'CANCELADO',
        origen: 'MANUAL',
        paciente: { dni: '28123456', nombre: 'María' },
        prestacion: { nombre: 'Consulta', duracionMinutos: 30 },
        canceladoPor: 'PACIENTE',
        canceladoEn: '2026-11-22T10:00:00-03:00',
      },
    ]

    const turno = leerTurnos(cuerpo)[0]

    expect(turno).toMatchObject({
      estado: 'CANCELADO',
      origen: 'MANUAL',
      canceladoPor: 'PACIENTE',
      canceladoEn: '2026-11-22T10:00:00-03:00',
    })
    expect(turno?.paciente).toEqual({ dni: '28123456', nombre: 'María' })
    expect(turno?.paciente.email).toBeUndefined()
  })

  it('rechaza una respuesta que no es un array o un turno sin identificar', () => {
    expect(() => leerTurnos({ turnos: [] })).toThrow('La agenda devolvió datos que no pudimos interpretar.')
    expect(() => leerTurnos([{ id: '42', estado: 'RESERVADO' }])).toThrow(
      'La agenda devolvió datos que no pudimos interpretar.',
    )
  })
})
