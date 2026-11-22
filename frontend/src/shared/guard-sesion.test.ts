import { describe, expect, it } from 'vitest'
import { crearGuard, destinoDesdeLogin, estadoTrasFallo } from './guard-sesion'
import type { ObservadorSesionVencida } from './guard-sesion'
import { crearClienteApi } from './api-client'
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

  it('descarta la caché ante una 401 real del api-client y no la recupera con /me en 401', async () => {
    const pedidos: string[] = []
    let haySesion = true
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)

      if (url === '/api/agenda') {
        return new Response(
          JSON.stringify({ codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' }),
          { status: 401 },
        )
      }

      return haySesion
        ? new Response(JSON.stringify(ODONTOLOGO), { status: 200 })
        : new Response(
            JSON.stringify({ codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' }),
            { status: 401 },
          )
    }
    const observador = observadorFalso()
    const cliente = crearClienteApi({ fetch: fetchFalso, notificarSesionVencida: observador.disparar })
    const guard = crearGuard(fetchFalso, observador.observador)

    await guard.decidir('/agenda')
    await expect(cliente.pedir('GET', '/api/agenda')).rejects.toMatchObject({ codigo: 'NO_AUTENTICADO' })
    haySesion = false
    const decision = await guard.decidir('/config')
    haySesion = true
    const siguiente = await guard.decidir('/agenda')

    expect(decision).toEqual({ tipo: 'redirigir', ruta: '/config', para: '/login?redirect=%2Fconfig' })
    expect(siguiente).toEqual({ tipo: 'mostrar', ruta: '/agenda', odontologo: ODONTOLOGO })
    expect(pedidos).toEqual([
      'GET /api/auth/me',
      'GET /api/agenda',
      'GET /api/auth/me',
      'GET /api/auth/me',
    ])
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

describe('error de carga del guard', () => {
  it('muestra error de carga ante una falla de red en /me, sin redirigir', async () => {
    const fetchQueFalla: FetchLike = async () => {
      throw new TypeError('Failed to fetch')
    }
    const guard = crearGuard(fetchQueFalla, observadorFalso().observador)

    const decision = await guard.decidir('/agenda')

    expect(decision).toEqual({ tipo: 'error-carga', ruta: '/agenda' })
  })

  it('reintenta con éxito después de la falla de red', async () => {
    const pedidos: string[] = []
    let fallar = true
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (fallar) {
        throw new TypeError('Failed to fetch')
      }

      return new Response(JSON.stringify(ODONTOLOGO), { status: 200 })
    }
    const guard = crearGuard(fetchFalso, observadorFalso().observador)

    await guard.decidir('/agenda')
    fallar = false
    const reintento = await guard.decidir('/agenda')

    expect(reintento).toEqual({ tipo: 'mostrar', ruta: '/agenda', odontologo: ODONTOLOGO })
    expect(pedidos).toEqual(['GET /api/auth/me', 'GET /api/auth/me'])
  })

  it('reintenta y redirige si el reintento responde 401 NO_AUTENTICADO', async () => {
    const pedidos: string[] = []
    let fallar = true
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (fallar) {
        throw new TypeError('Failed to fetch')
      }

      return new Response(
        JSON.stringify({ codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' }),
        { status: 401 },
      )
    }
    const guard = crearGuard(fetchFalso, observadorFalso().observador)

    await guard.decidir('/agenda')
    fallar = false
    const reintento = await guard.decidir('/config')

    expect(reintento).toEqual({ tipo: 'redirigir', ruta: '/config', para: '/login?redirect=%2Fconfig' })
    expect(pedidos).toEqual(['GET /api/auth/me', 'GET /api/auth/me'])
  })

  it('muestra una ruta pública aunque haya un error de carga pendiente', async () => {
    const pedidos: string[] = []
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      throw new TypeError('Failed to fetch')
    }
    const guard = crearGuard(fetchFalso, observadorFalso().observador)

    await guard.decidir('/agenda')
    const publica = await guard.decidir('/reservar/ana-gomez')

    expect(publica).toEqual({ tipo: 'mostrar', ruta: '/reservar/ana-gomez' })
    expect(pedidos).toEqual(['GET /api/auth/me'])
  })

  it('distingue el 401 de la falla de red en el estado puro', () => {
    expect(estadoTrasFallo({ codigo: 'NO_AUTENTICADO' })).toEqual({ tipo: 'sin-sesion' })
    expect(estadoTrasFallo({ codigo: 'SIN_CONEXION' })).toEqual({ tipo: 'error-carga' })
    expect(estadoTrasFallo(undefined)).toEqual({ tipo: 'error-carga' })
  })
})

describe('destino redirect validado', () => {
  it('descarta un destino externo y vuelve a /agenda', () => {
    expect(destinoDesdeLogin('/login?redirect=https%3A%2F%2Fsitio-externo.com')).toBe('/agenda')
  })

  it('conserva un destino interno declarado', () => {
    expect(destinoDesdeLogin('/login?redirect=%2Fconfig')).toBe('/config')
  })

  it('descarta un protocolo relativo al dominio', () => {
    expect(destinoDesdeLogin('/login?redirect=%2F%2Fotro-dominio')).toBe('/agenda')
  })

  it('descarta un esquema javascript', () => {
    expect(destinoDesdeLogin('/login?redirect=javascript%3Aalert(1)')).toBe('/agenda')
  })

  it('vuelve a /agenda cuando no hay parámetro', () => {
    expect(destinoDesdeLogin('/login')).toBe('/agenda')
  })

  it('descarta una barra invertida que el navegador trataría como dominio', () => {
    expect(destinoDesdeLogin('/login?redirect=%2F%5Cevil.com')).toBe('/agenda')
  })
})
