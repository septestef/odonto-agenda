import { describe, expect, it } from 'vitest'
import { crearGuard } from './guard-sesion'
import type { ObservadorSesionVencida } from './guard-sesion'
import type { FetchLike } from './api-client'

const ODONTOLOGO = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Gómez',
  email: 'ana@consultorio.com',
  slug: 'ana-gomez',
}

function observadorFalso() {
  const callbacks: Array<() => void> = []
  const observador: ObservadorSesionVencida = (callback) => {
    callbacks.push(callback)

    return () => {
      const indice = callbacks.indexOf(callback)
      if (indice >= 0) {
        callbacks.splice(indice, 1)
      }
    }
  }

  return {
    observador,
    disparar() {
      for (const callback of callbacks) {
        callback()
      }
    },
  }
}

function fetchQueResponde(cuerpo: unknown, status: number, pedidos: string[]): FetchLike {
  return async (url, init) => {
    pedidos.push(`${init?.method ?? 'GET'} ${url}`)

    return new Response(JSON.stringify(cuerpo), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  }
}

describe('guard de sesión con caché', () => {
  it('muestra la ruta con /me en 200 y no repite la consulta al cambiar de ruta', async () => {
    const pedidos: string[] = []
    const guard = crearGuard(fetchQueResponde(ODONTOLOGO, 200, pedidos), observadorFalso().observador)

    const primera = await guard.decidir('/agenda')
    const segunda = await guard.decidir('/config')

    expect(primera).toEqual({ tipo: 'mostrar', ruta: '/agenda', odontologo: ODONTOLOGO })
    expect(segunda).toEqual({ tipo: 'mostrar', ruta: '/config', odontologo: ODONTOLOGO })
    expect(pedidos).toEqual(['GET /api/auth/me'])
  })

  it('descarta la caché y redirige cuando /me responde 401 NO_AUTENTICADO', async () => {
    const pedidos: string[] = []
    let haySesion = true
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)

      return haySesion
        ? new Response(JSON.stringify(ODONTOLOGO), { status: 200 })
        : new Response(
            JSON.stringify({ codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' }),
            { status: 401 },
          )
    }
    const guard = crearGuard(fetchFalso, observadorFalso().observador)

    await guard.decidir('/agenda')
    guard.descartarCache()
    haySesion = false
    const decision = await guard.decidir('/config')
    const siguiente = await guard.decidir('/agenda')

    expect(decision).toEqual({ tipo: 'redirigir', ruta: '/config', para: '/login?redirect=%2Fconfig' })
    expect(siguiente).toEqual({ tipo: 'redirigir', ruta: '/agenda', para: '/login?redirect=%2Fagenda' })
    expect(pedidos).toEqual(['GET /api/auth/me', 'GET /api/auth/me', 'GET /api/auth/me'])
  })

  it('descarta la caché al recibir la señal de sesión vencida del api-client', async () => {
    const pedidos: string[] = []
    const observador = observadorFalso()
    const guard = crearGuard(fetchQueResponde(ODONTOLOGO, 200, pedidos), observador.observador)

    await guard.decidir('/agenda')
    observador.disparar()
    await guard.decidir('/config')

    expect(pedidos).toEqual(['GET /api/auth/me', 'GET /api/auth/me'])
  })

  it('limpia la caché al cerrar sesión', async () => {
    const pedidos: string[] = []
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)

      return url === '/api/auth/logout'
        ? new Response(null, { status: 204 })
        : new Response(JSON.stringify(ODONTOLOGO), { status: 200 })
    }
    const guard = crearGuard(fetchFalso, observadorFalso().observador)

    await guard.decidir('/agenda')
    await guard.cerrarSesion()
    const decision = await guard.decidir('/config')

    expect(decision.tipo).toBe('mostrar')
    expect(pedidos).toEqual(['GET /api/auth/me', 'POST /api/auth/logout', 'GET /api/auth/me'])
  })
})

describe('redirección y rutas públicas', () => {
  function guardSinSesion(pedidos: string[]) {
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)

      return new Response(
        JSON.stringify({
          codigo: 'NO_AUTENTICADO',
          mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.',
        }),
        { status: 401 },
      )
    }

    return crearGuard(fetchFalso, observadorFalso().observador)
  }

  it('redirige a /login conservando /agenda como destino', async () => {
    const pedidos: string[] = []

    const decision = await guardSinSesion(pedidos).decidir('/agenda')

    expect(decision).toEqual({ tipo: 'redirigir', ruta: '/agenda', para: '/login?redirect=%2Fagenda' })
    expect(pedidos).toEqual(['GET /api/auth/me'])
  })

  it('no consulta /me en /reservar/ana-gomez', async () => {
    const pedidos: string[] = []

    const decision = await guardSinSesion(pedidos).decidir('/reservar/ana-gomez')

    expect(decision).toEqual({ tipo: 'mostrar', ruta: '/reservar/ana-gomez' })
    expect(pedidos).toEqual([])
  })

  it('conserva /config como destino de la redirección', async () => {
    const pedidos: string[] = []

    const decision = await guardSinSesion(pedidos).decidir('/config')

    expect(decision).toEqual({ tipo: 'redirigir', ruta: '/config', para: '/login?redirect=%2Fconfig' })
  })

  it('no consulta /me en /turno/abc123', async () => {
    const pedidos: string[] = []

    const decision = await guardSinSesion(pedidos).decidir('/turno/abc123')

    expect(decision).toEqual({ tipo: 'mostrar', ruta: '/turno/abc123' })
    expect(pedidos).toEqual([])
  })
})
