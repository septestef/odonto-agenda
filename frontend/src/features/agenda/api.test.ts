import { describe, expect, it, vi } from 'vitest'
import type { ClienteApi } from '../../shared/api-client'
import { consultarAgenda, consultarBloqueos, consultarHorarios, urlAgenda } from './api'

function clienteQueDevuelve(cuerpo: unknown): ClienteApi {
  const pedir = vi.fn(async () => cuerpo) as unknown as ClienteApi['pedir']
  return {
    pedir,
    suscribirSesionVencida: () => () => {},
  }
}

function errorApi(codigo: string, mensaje: string): Error & { codigo: string; mensaje: string } {
  return Object.assign(new Error(mensaje), { codigo, mensaje })
}

describe('configuración de la agenda', () => {
  it('lee la forma `{franjas:[...]}` de C-05', async () => {
    const cliente = clienteQueDevuelve({
      franjas: [
        { diaSemana: 1, inicio: '09:00', fin: '13:00' },
        { diaSemana: 1, inicio: '16:00', fin: '19:00' },
      ],
    })

    const franjas = await consultarHorarios(cliente)

    expect(cliente.pedir).toHaveBeenCalledWith('GET', '/api/horarios')
    expect(franjas).toEqual([
      { diaSemana: 1, inicio: '09:00', fin: '13:00' },
      { diaSemana: 1, inicio: '16:00', fin: '19:00' },
    ])
  })

  it('lee bloqueos con `turnosAfectados` y filtra solo los que tocan el rango visible', async () => {
    const cliente = clienteQueDevuelve({
      bloqueos: [
        {
          id: 7,
          inicio: '2026-11-25T11:00:00-03:00',
          fin: '2026-11-25T12:00:00-03:00',
          motivo: 'Trámite personal',
          turnosAfectados: [
            {
              id: 12,
              inicio: '2026-11-25T11:15:00-03:00',
              fin: '2026-11-25T12:00:00-03:00',
              estado: 'RESERVADO',
              pacienteNombre: 'Martín',
              pacienteApellido: 'Sosa',
              prestacionNombre: 'Limpieza',
            },
          ],
          emailGestion: 'privado@ejemplo.com',
        },
        {
          id: 8,
          inicio: '2026-11-28T10:00:00-03:00',
          fin: '2026-11-28T12:00:00-03:00',
          motivo: 'Fuera del rango',
          turnosAfectados: [],
        },
      ],
    })

    const bloqueos = await consultarBloqueos(cliente, '2026-11-25', '2026-11-26')

    expect(cliente.pedir).toHaveBeenCalledWith('GET', '/api/bloqueos')
    expect(bloqueos).toHaveLength(1)
    expect(bloqueos[0]?.turnosAfectados[0]).toMatchObject({
      id: 12,
      pacienteNombre: 'Martín',
      prestacionNombre: 'Limpieza',
    })
    expect(JSON.stringify(bloqueos)).not.toContain('privado@ejemplo.com')
  })

  it('excluye un bloqueo que termina justo al empezar el rango y acepta lista vacía', async () => {
    const cliente = clienteQueDevuelve({
      bloqueos: [
        {
          id: 1,
          inicio: '2026-11-24T10:00:00-03:00',
          fin: '2026-11-25T00:00:00-03:00',
          motivo: ' anterior',
          turnosAfectados: [],
        },
      ],
    })

    expect(await consultarBloqueos(cliente, '2026-11-25', '2026-11-26')).toEqual([])
    expect(await consultarHorarios(clienteQueDevuelve({ franjas: [] }))).toEqual([])
  })
})

describe('consultarAgenda', () => {
  it('pide el rango semiabierto y conserva el orden por inicio e id', async () => {
    const cliente = clienteQueDevuelve([
      { id: 9, inicio: '2026-11-25T11:00:00-03:00', fin: '2026-11-25T11:30:00-03:00', estado: 'RESERVADO', origen: 'ONLINE' },
      { id: 2, inicio: '2026-11-25T09:00:00-03:00', fin: '2026-11-25T09:30:00-03:00', estado: 'RESERVADO', origen: 'MANUAL' },
      { id: 3, inicio: '2026-11-25T09:00:00-03:00', fin: '2026-11-25T10:00:00-03:00', estado: 'CANCELADO', origen: 'ONLINE' },
    ])

    const turnos = await consultarAgenda(cliente, '2026-11-25', '2026-11-26')

    expect(urlAgenda('2026-11-25', '2026-11-26')).toBe('/api/turnos?desde=2026-11-25&hasta=2026-11-26')
    expect(cliente.pedir).toHaveBeenCalledWith('GET', '/api/turnos?desde=2026-11-25&hasta=2026-11-26')
    expect(turnos.map((turno) => turno.id)).toEqual([2, 3, 9])
  })

  it('devuelve una lista vacía sin inventar turnos', async () => {
    const turnos = await consultarAgenda(clienteQueDevuelve([]), '2026-11-25', '2026-11-26')
    expect(turnos).toEqual([])
  })

  it('propaga errores 401, 403 y 500 sin convertirlos en un estado falso', async () => {
    const cliente: ClienteApi = {
      pedir: vi.fn(async () => {
        throw errorApi('NO_AUTENTICADO', 'Tu sesión no está iniciada o expiró. Volvé a ingresar.')
      }),
      suscribirSesionVencida: () => () => {},
    }

    await expect(consultarAgenda(cliente, '2026-11-25', '2026-11-26')).rejects.toMatchObject({
      codigo: 'NO_AUTENTICADO',
    })

    const sinAcceso = clienteQueDevuelve([])
    vi.mocked(sinAcceso.pedir).mockRejectedValueOnce(errorApi('ACCESO_DENEGADO', 'Acceso denegado.'))
    await expect(consultarAgenda(sinAcceso, '2026-11-25', '2026-11-26')).rejects.toMatchObject({
      codigo: 'ACCESO_DENEGADO',
    })

    const fallaInterna = clienteQueDevuelve([])
    vi.mocked(fallaInterna.pedir).mockRejectedValueOnce(errorApi('ERROR_INTERNO', 'Falla del servidor.'))
    await expect(consultarAgenda(fallaInterna, '2026-11-25', '2026-11-26')).rejects.toMatchObject({
      codigo: 'ERROR_INTERNO',
    })
  })
})
