import { describe, expect, it, vi } from 'vitest'
import type { ClienteApi } from '../../shared/api-client'
import { crearApiManual, DIAS_HORIZONTE } from './api'
import type { SolicitudTurnoManual } from './modelo'

type Respuesta = unknown

function clienteQueDevuelve(respuestas: readonly Respuesta[]): {
  cliente: ClienteApi
  pedidos: Array<{ metodo: string; url: string; cuerpo?: unknown }>
} {
  const pedidos: Array<{ metodo: string; url: string; cuerpo?: unknown }> = []
  let indice = 0
  const cliente = {
    pedir: vi.fn(async (metodo: string, url: string, cuerpo?: unknown) => {
      pedidos.push({ metodo, url, cuerpo })
      const respuesta = respuestas[indice]
      indice += 1
      if (respuesta instanceof Error) {
        throw respuesta
      }
      return respuesta
    }),
  } as unknown as ClienteApi
  return { cliente, pedidos }
}

describe('api manual', () => {
  it('filtra prestaciones activas y deriva la ventana de 60 días en hora Argentina', async () => {
    const { cliente } = clienteQueDevuelve([
      {
        prestaciones: [
          { id: 1, nombre: 'Consulta', duracionMinutos: 30, activa: true },
          { id: 2, nombre: 'Control', duracionMinutos: 15, activa: true },
          { id: 3, nombre: 'Inactiva', duracionMinutos: 60, activa: false },
        ],
      },
    ])
    const api = crearApiManual(cliente, () => new Date('2026-11-23T02:30:00.000Z'))

    const perfil = await api.perfil()

    expect(perfil).toEqual({
      fechaMinima: '2026-11-22',
      fechaMaxima: '2027-01-21',
      prestaciones: [
        { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
        { id: 2, nombre: 'Control', duracionMinutos: 15 },
      ],
    })
    expect(DIAS_HORIZONTE).toBe(60)
  })

  it('pide disponibilidad autenticada con prestación y fecha', async () => {
    const disponibilidad = {
      fecha: '2026-11-25',
      prestacionId: 7,
      duracionMinutos: 30,
      horarios: [{ inicio: '2026-11-25T09:15:00-03:00', hora: '09:15' }],
    }
    const { cliente, pedidos } = clienteQueDevuelve([disponibilidad])
    const api = crearApiManual(cliente)

    expect(await api.disponibilidad(7, '2026-11-25')).toEqual(disponibilidad)
    expect(pedidos[0]).toMatchObject({
      metodo: 'GET',
      url: '/api/disponibilidad?prestacionId=7&fecha=2026-11-25',
    })
  })

  it('publica el turno por POST /api/turnos sin id de odontólogo', async () => {
    const confirmacion = {
      inicio: '2026-11-25T09:15:00-03:00',
      fin: '2026-11-25T09:45:00-03:00',
      prestacion: { id: 7, nombre: 'Consulta', duracionMinutos: 30 },
      odontologo: { nombre: 'Ana', apellido: 'Gómez', direccionConsultorio: null },
      email: 'paciente@ejemplo.com',
    }
    const { cliente, pedidos } = clienteQueDevuelve([confirmacion])
    const api = crearApiManual(cliente)
    const solicitud: SolicitudTurnoManual = {
      prestacionId: 7,
      inicio: '2026-11-25T09:15:00-03:00',
      dni: '30123456',
      nombre: 'Luis',
      apellido: 'Ramírez',
      telefono: '+5493511234567',
      email: 'paciente@ejemplo.com',
    }

    expect(await api.crear(solicitud)).toEqual(confirmacion)
    expect(pedidos[0]).toEqual({ metodo: 'POST', url: '/api/turnos', cuerpo: solicitud })
  })

  it('rechaza respuestas con formato inesperado', async () => {
    const { cliente } = clienteQueDevuelve([{ prestaciones: [{ id: '7', nombre: 'Consulta' }] }])
    const api = crearApiManual(cliente)

    await expect(api.perfil()).rejects.toThrow('formato esperado')
  })

  it('propaga los errores de la API sin convertirlos', async () => {
    const error = Object.assign(new Error('Conflicto'), { codigo: 'RN-TU-01' })
    const { cliente } = clienteQueDevuelve([error])
    const api = crearApiManual(cliente)

    await expect(api.crear({
      prestacionId: 7,
      inicio: '2026-11-25T09:15:00-03:00',
      dni: '30123456',
      nombre: 'Luis',
      apellido: 'Ramírez',
      telefono: '+5493511234567',
      email: 'paciente@ejemplo.com',
    })).rejects.toBe(error)
  })
})
