import { describe, expect, it } from 'vitest'
import { crearApiConfig } from './api-config'
import { crearClienteApi } from '../../shared/api-client'
import type { FetchLike } from '../../shared/api-client'

interface Pedido {
  url: string
  method: string | undefined
  body: unknown
  credentials: string | undefined
  csrf: string | null
}

function respuesta(cuerpo: unknown, status: number): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } })
}

function clienteDePrueba() {
  const pedidos: Pedido[] = []
  const fetchFalso: FetchLike = async (url, init) => {
    const cabeceras = new Headers(init?.headers)
    pedidos.push({
      url,
      method: init?.method,
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body,
      credentials: init?.credentials,
      csrf: cabeceras.get('X-XSRF-TOKEN'),
    })
    if (url === '/api/horarios') {
      return respuesta({ franjas: [] }, init?.method === 'PUT' ? 200 : 200)
    }
    if (url.startsWith('/api/prestaciones')) {
      if (init?.method === 'DELETE') {
        return new Response(null, { status: 204 })
      }
      if (init?.method === 'GET') {
        return respuesta({ prestaciones: [] }, 200)
      }
      return respuesta({ id: 3, nombre: 'Limpieza', duracionMinutos: 45, activa: true }, init?.method === 'PUT' ? 200 : 201)
    }
    if (url.startsWith('/api/bloqueos')) {
      if (init?.method === 'DELETE') {
        return new Response(null, { status: 204 })
      }
      if (init?.method === 'GET') {
        return respuesta({ bloqueos: [] }, 200)
      }
      return respuesta(
        {
          id: 7,
          inicio: '2026-11-27T10:00:00-03:00',
          fin: '2026-11-27T12:00:00-03:00',
          turnosAfectados: [],
        },
        201,
      )
    }
    return respuesta({}, 404)
  }
  const cliente = crearClienteApi({ fetch: fetchFalso, leerCookie: () => 'token-csrf' })
  return { api: crearApiConfig(cliente), pedidos }
}

describe('api-config', () => {
  it('usa las rutas y métodos de configuración sin identificador de odontólogo', async () => {
    const { api, pedidos } = clienteDePrueba()

    await api.horarios()
    await api.guardarHorario([{ diaSemana: 2, inicio: '09:00', fin: '13:00' }])
    await api.prestaciones()
    await api.crearPrestacion({ nombre: 'Limpieza', duracionMinutos: 45 })
    await api.editarPrestacion(3, { nombre: 'Limpieza', duracionMinutos: 60 })
    await api.bajaPrestacion(3)
    await api.bloqueos()
    await api.crearBloqueo({
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
      motivo: 'Control',
    })
    await api.bajaBloqueo(7)

    expect(pedidos.map((pedido) => `${pedido.method} ${pedido.url}`)).toEqual([
      'GET /api/horarios',
      'PUT /api/horarios',
      'GET /api/prestaciones',
      'POST /api/prestaciones',
      'PUT /api/prestaciones/3',
      'DELETE /api/prestaciones/3',
      'GET /api/bloqueos',
      'POST /api/bloqueos',
      'DELETE /api/bloqueos/7',
    ])
    expect(pedidos[1]?.body).toEqual({ franjas: [{ diaSemana: 2, inicio: '09:00', fin: '13:00' }] })
    expect(pedidos[4]?.body).toEqual({ nombre: 'Limpieza', duracionMinutos: 60 })
    expect(pedidos[7]?.body).toEqual({
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
      motivo: 'Control',
    })
    for (const pedido of pedidos) {
      expect(pedido.credentials).toBe('same-origin')
      expect(JSON.stringify(pedido.body ?? {}) + pedido.url).not.toContain('odontologoId')
    }
    for (const pedido of pedidos.filter((item) => item.method !== 'GET')) {
      expect(pedido.csrf).toBe('token-csrf')
    }
  })

  it('omite el motivo cuando está vacío', async () => {
    const { api, pedidos } = clienteDePrueba()

    await api.crearBloqueo({ inicio: '2026-11-27T10:00:00-03:00', fin: '2026-11-27T12:00:00-03:00' })

    expect(pedidos[0]?.body).toEqual({
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
    })
  })
})
