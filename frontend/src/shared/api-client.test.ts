import { describe, expect, it } from 'vitest'
import { crearClienteApi } from './api-client'
import type { FetchLike, LectorCookie } from './api-client'
import type { ErrorApi } from './error-api'

function esErrorApi(valor: unknown): valor is ErrorApi {
  return typeof valor === 'object' && valor !== null && 'codigo' in valor && 'mensaje' in valor
}

async function capturarError(promesa: Promise<unknown>): Promise<ErrorApi> {
  try {
    await promesa
  } catch (error: unknown) {
    if (esErrorApi(error)) {
      return error
    }
  }

  throw new Error('Se esperaba un error tipado de la API')
}

function clienteQueResponde(cuerpo: unknown, status: number) {
  const fetchFalso: FetchLike = async () =>
    new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } })

  return crearClienteApi({ fetch: fetchFalso })
}

function valorHeader(init: RequestInit | undefined, nombre: string): string | null {
  return new Headers(init?.headers).get(nombre)
}

function clienteQueRegistra(leerCookie: LectorCookie = () => 'abc') {
  const pedidos: Array<RequestInit | undefined> = []
  const urls: string[] = []
  const fetchFalso: FetchLike = async (url, init) => {
    urls.push(url)
    pedidos.push(init)
    return new Response(null, { status: 204 })
  }

  return { cliente: crearClienteApi({ fetch: fetchFalso, leerCookie }), pedidos, urls }
}

describe('CSRF en escrituras', () => {
  it('reenvía la cookie XSRF-TOKEN en un POST protegido', async () => {
    const { cliente, pedidos } = clienteQueRegistra()

    await cliente.pedir('POST', '/api/auth/logout')

    expect(valorHeader(pedidos[0], 'X-XSRF-TOKEN')).toBe('abc')
  })

  it.each(['PUT', 'DELETE'] as const)('reenvía la cookie en %s', (metodo) => {
    const { cliente, pedidos } = clienteQueRegistra(() => 'xyz')

    return cliente.pedir(metodo, '/api/auth/logout').then(() => {
      expect(valorHeader(pedidos[0], 'X-XSRF-TOKEN')).toBe('xyz')
    })
  })

  it('no inventa un header cuando no hay cookie CSRF', async () => {
    const { cliente, pedidos } = clienteQueRegistra(() => undefined)

    await cliente.pedir('POST', '/api/auth/logout')

    expect(valorHeader(pedidos[0], 'X-XSRF-TOKEN')).toBeNull()
  })
})

describe('pedidos sin header CSRF', () => {
  it('no envía el header en GET /api/auth/me', async () => {
    const { cliente, pedidos } = clienteQueRegistra()

    await cliente.pedir('GET', '/api/auth/me')

    expect(valorHeader(pedidos[0], 'X-XSRF-TOKEN')).toBeNull()
  })

  it('no envía el header en el POST público de login', async () => {
    const { cliente, pedidos } = clienteQueRegistra()

    await cliente.pedir('POST', '/api/auth/login', { email: 'ana@consultorio.com', password: 'secreto' })

    expect(valorHeader(pedidos[0], 'X-XSRF-TOKEN')).toBeNull()
  })

  it.each([
    ['HEAD', '/api/auth/me'],
    ['GET', '/api/agenda'],
  ] as const)('no envía el header en %s %s', async (metodo, ruta) => {
    const { cliente, pedidos } = clienteQueRegistra()

    await cliente.pedir(metodo, ruta)

    expect(valorHeader(pedidos[0], 'X-XSRF-TOKEN')).toBeNull()
  })
})

describe('pedidos al mismo origen', () => {
  it('usa credenciales same-origin y una ruta relativa en una lectura', async () => {
    const { cliente, pedidos, urls } = clienteQueRegistra()

    await cliente.pedir('GET', '/api/auth/me')

    expect(pedidos[0]?.credentials).toBe('same-origin')
    expect(urls[0]).toBe('/api/auth/me')
  })

  it('usa credenciales same-origin en una escritura pública', async () => {
    const { cliente, pedidos } = clienteQueRegistra()

    await cliente.pedir('POST', '/api/auth/login', { email: 'ana@consultorio.com', password: 'secreto' })

    expect(pedidos[0]?.credentials).toBe('same-origin')
  })

  it.each(['https://sitio-externo.com/api/auth/me', '//sitio-externo.com/api/auth/me', 'api/auth/me'])(
    'rechaza la ruta no relativa %s antes de pedirla',
    async (ruta) => {
      const { cliente, urls } = clienteQueRegistra()

      await expect(cliente.pedir('GET', ruta)).rejects.toThrow('relativas al origen')
      expect(urls).toEqual([])
    },
  )
})

describe('errores tipados', () => {
  it('conserva código, mensaje y detalles de un 429', async () => {
    const detalles = { reintentarEnSegundos: 900, bloqueadoHasta: '2026-11-19T15:00:00Z' }
    const cliente = clienteQueResponde(
      { codigo: 'RN-AU-03', mensaje: 'Demasiados intentos fallidos. Probá de nuevo en unos minutos.', detalles },
      429,
    )

    const error = await capturarError(cliente.pedir('POST', '/api/auth/login', { email: 'a@b.com', password: 'x' }))

    expect(error.codigo).toBe('RN-AU-03')
    expect(error.mensaje).toBe('Demasiados intentos fallidos. Probá de nuevo en unos minutos.')
    expect(error.detalles).toEqual(detalles)
  })

  it('expone el código de acceso denegado del servidor', async () => {
    const cliente = clienteQueResponde(
      { codigo: 'ACCESO_DENEGADO', mensaje: 'No se pudo validar la protección del pedido.' },
      403,
    )

    const error = await capturarError(cliente.pedir('POST', '/api/auth/logout'))

    expect(error.codigo).toBe('ACCESO_DENEGADO')
  })

  it('usa el error genérico cuando el cuerpo no tiene codigo y mensaje', async () => {
    const cliente = clienteQueResponde({ trace: 'Internal Server Error' }, 500)

    const error = await capturarError(cliente.pedir('GET', '/api/auth/me'))

    expect(error.codigo).toBe('ERROR_DESCONOCIDO')
    expect(error.mensaje).toBe('Ocurrió un error inesperado. Probá de nuevo en unos minutos.')
  })

  it.each([null, [], {}] as const)('quita la clave detalles cuando llega %j', async (detalles) => {
    const cliente = clienteQueResponde({ codigo: 'RN-AU-03', mensaje: 'Demasiados intentos.', detalles }, 429)

    const error = await capturarError(cliente.pedir('POST', '/api/auth/login'))

    expect('detalles' in error).toBe(false)
  })

  it('conserva un arreglo no vacío de errores de validación', async () => {
    const detalles = [{ campo: 'email', mensaje: 'es obligatorio' }]
    const cliente = clienteQueResponde({ codigo: 'VALIDACION', mensaje: 'Hay datos inválidos', detalles }, 400)

    const error = await capturarError(cliente.pedir('POST', '/api/auth/login'))

    expect(error.detalles).toEqual(detalles)
  })
})

describe('cuerpo vacío y falla de red', () => {
  it('considera exitoso un 204 sin cuerpo', async () => {
    const cliente = crearClienteApi({ fetch: async () => new Response(null, { status: 204 }) })

    await expect(cliente.pedir('POST', '/api/auth/logout')).resolves.toBeUndefined()
  })

  it.each([
    ['TypeError de fetch', () => Promise.reject(new TypeError('Failed to fetch'))],
    ['rejection sin forma de Error', () => Promise.reject({ reason: 'network' })],
  ])('convierte %s en SIN_CONEXION', async (_nombre, fetchFalso) => {
    const cliente = crearClienteApi({ fetch: fetchFalso })

    const error = await capturarError(cliente.pedir('GET', '/api/auth/me'))

    expect(error.codigo).toBe('SIN_CONEXION')
    expect(error.mensaje).toBe('Revisá tu conexión e intentá de nuevo.')
  })

  it('convierte una respuesta sin ok ni status en SIN_CONEXION', async () => {
    const fetchFalso = (async () => ({ body: null })) as unknown as FetchLike
    const cliente = crearClienteApi({ fetch: fetchFalso })

    const error = await capturarError(cliente.pedir('GET', '/api/auth/me'))

    expect(error.codigo).toBe('SIN_CONEXION')
  })
})

describe('sesión vencida', () => {
  function clienteCon401(codigo: string, mensaje: string, notificar?: () => void): ReturnType<typeof crearClienteApi> {
    return crearClienteApi({
      fetch: async () => new Response(JSON.stringify({ codigo, mensaje }), { status: 401 }),
      ...(notificar === undefined ? {} : { notificarSesionVencida: notificar }),
    })
  }

  it('publica la señal una sola vez en 401 NO_AUTENTICADO', async () => {
    let llamadas = 0
    const cliente = clienteCon401(
      'NO_AUTENTICADO',
      'Tu sesión no está iniciada o expiró. Volvé a ingresar.',
      () => {
        llamadas += 1
      },
    )

    const error = await capturarError(cliente.pedir('GET', '/api/auth/me'))

    expect(error.codigo).toBe('NO_AUTENTICADO')
    expect(llamadas).toBe(1)
  })

  it('publica la señal en un 401 NO_AUTENTICADO de otra ruta protegida', async () => {
    let llamadas = 0
    const cliente = clienteCon401('NO_AUTENTICADO', 'Volve a ingresar.', () => {
      llamadas += 1
    })

    const error = await capturarError(cliente.pedir('GET', '/api/agenda'))

    expect(error.codigo).toBe('NO_AUTENTICADO')
    expect(llamadas).toBe(1)
  })

  it('entrega la señal a los suscriptores y deja de hacerlo al desuscribirse', async () => {
    const cliente = clienteCon401('NO_AUTENTICADO', 'Volve a ingresar.')
    let fijas = 0
    let temporales = 0
    cliente.suscribirSesionVencida(() => {
      fijas += 1
    })
    const quitar = cliente.suscribirSesionVencida(() => {
      temporales += 1
    })

    await capturarError(cliente.pedir('GET', '/api/auth/me'))
    quitar()
    await capturarError(cliente.pedir('GET', '/api/auth/me'))

    expect(fijas).toBe(2)
    expect(temporales).toBe(1)
  })

  it('no publica la señal en 401 de credenciales', async () => {
    let llamadas = 0
    const cliente = clienteCon401('RN-AU-01', 'El email o la contraseña no son correctos.', () => {
      llamadas += 1
    })

    const error = await capturarError(cliente.pedir('POST', '/api/auth/login'))

    expect(error.codigo).toBe('RN-AU-01')
    expect(llamadas).toBe(0)
  })
})
