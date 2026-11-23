import { describe, expect, it, vi } from 'vitest'
// @ts-expect-error: este proyecto no declara tipos de Node; readFileSync se usa solo en el test.
import { readFileSync } from 'node:fs'
import { arrancar } from './main'
import type { HistorialNavegacion } from './router'
import type { FetchLike } from './shared/api-client'
import type { AdaptadorNodos, ManejadoresUI, NodoUI } from './shared/ui/adaptador'
import type { Descriptor } from './shared/ui/componentes'

const ODONTOLOGO = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Gómez',
  email: 'ana@consultorio.com',
  slug: 'ana-gomez',
}

class NodoInteractivo implements NodoUI {
  readonly atributos: Record<string, string> = {}
  readonly valores: Record<string, string> = {}
  textContent = ''

  append(): void {}

  setAttribute(nombre: string, valor: string): void {
    this.atributos[nombre] = valor
  }

  addEventListener(): void {}

  getAttribute(nombre: string): string | null {
    return this.atributos[nombre] ?? null
  }

  querySelector(selector: string): { value: string } | null {
    const nombre = /\[data-campo="([^"]+)"\]/.exec(selector)?.[1]
    const valor = nombre === undefined ? undefined : this.valores[nombre]
    return valor === undefined ? null : { value: valor }
  }
}

/** Botón con `data-accion="ingresar"` que, como en el DOM, halla su form con `closest`. */
class BotonIngresar extends NodoInteractivo {
  private readonly formulario: NodoInteractivo

  constructor(formulario: NodoInteractivo) {
    super()
    this.formulario = formulario
    this.setAttribute('data-accion', 'ingresar')
  }

  closest(selector: string): NodoUI | null {
    return selector === 'form' ? this.formulario : null
  }
}

function historialFalso(rutaInicial: string) {
  let ruta = rutaInicial
  const push: string[] = []
  const replace: string[] = []
  const historial: HistorialNavegacion = {
    rutaActual: () => ruta,
    pushState: (url) => {
      ruta = url
      push.push(url)
    },
    replaceState: (url) => {
      ruta = url
      replace.push(url)
    },
    suscribirPopstate: () => () => {},
  }

  return { historial, push, replace, rutaActual: () => ruta }
}

function adaptadorFalso() {
  const montajes: Array<{ descriptor: Descriptor; manejadores?: ManejadoresUI }> = []
  const adaptador: AdaptadorNodos = {
    montar(_destino, descriptor, manejadores) {
      montajes.push({ descriptor, manejadores })
    },
  }

  return { adaptador, montajes, ultimo: () => montajes.at(-1) }
}

function respuesta(cuerpo: unknown, status: number): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } })
}

function fetchConSesion(pedidos: string[]): FetchLike {
  return async (url, init) => {
    pedidos.push(`${init?.method ?? 'GET'} ${url}`)
    if (url === '/api/auth/me' || url === '/api/auth/login') {
      return respuesta(ODONTOLOGO, 200)
    }
    return new Response(null, { status: 204 })
  }
}

async function estabilizar(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0))
  await new Promise((resolve) => setTimeout(resolve, 0))
  await new Promise((resolve) => setTimeout(resolve, 0))
}

async function drenarMicrotareas(): Promise<void> {
  for (let vuelta = 0; vuelta < 12; vuelta += 1) {
    await Promise.resolve()
  }
}

function nodosDe(descriptor: Descriptor | undefined): Descriptor[] {
  if (descriptor === undefined) {
    return []
  }
  return [descriptor, ...(descriptor.hijos ?? []).flatMap(nodosDe)]
}

function textosDe(descriptor: Descriptor | undefined): string[] {
  return nodosDe(descriptor)
    .map((nodo) => nodo.texto)
    .filter((texto): texto is string => texto !== undefined)
}

function alertaDe(descriptor: Descriptor | undefined): Descriptor | undefined {
  return nodosDe(descriptor).find((nodo) => nodo.atributos?.['role'] === 'alert')
}

function botonIngresarDe(descriptor: Descriptor | undefined): Descriptor | undefined {
  return nodosDe(descriptor).find(
    (nodo) => nodo.etiqueta === 'button' && (nodo.texto === 'Ingresar' || nodo.texto === 'Ingresando…'),
  )
}

function formularioCon(email: string, contraseña: string): NodoInteractivo {
  const formulario = new NodoInteractivo()
  formulario.valores['email'] = email
  formulario.valores['password'] = contraseña
  return formulario
}

function fetchConLogin(cuerpo: unknown, status: number, pedidos: string[] = []): FetchLike {
  return async (url, init) => {
    pedidos.push(`${init?.method ?? 'GET'} ${url}`)
    if (url === '/api/auth/login') {
      return respuesta(cuerpo, status)
    }
    return new Response(null, { status: 204 })
  }
}

interface PedidoRegistrado {
  url: string
  metodo: string | undefined
  cuerpo: unknown
}

function fetchQueRegistraPedidos(pedidos: PedidoRegistrado[]): FetchLike {
  return async (url, init) => {
    const cuerpo: unknown = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    pedidos.push({ url, metodo: init?.method, cuerpo })
    return respuesta(ODONTOLOGO, 200)
  }
}

describe('arranque de la aplicación', () => {
  it('monta el acceso en una ruta pública sin consultar /me', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/login')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchConSesion(pedidos) })
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('login')
    expect(pedidos).toEqual([])
    expect(historial.push).toEqual([])
    expect(historial.replace).toEqual([])
    detener()
  })

  it('monta el layout del odontólogo en una ruta protegida con sesión', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/agenda')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchConSesion(pedidos) })
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('layout')
    expect(ultimo()?.descriptor.hijos?.[2]?.hijos?.[0]?.clase).toBe('marcador')
    expect(pedidos).toEqual(['GET /api/auth/me'])
    expect(historial.replace).toEqual([])
    detener()
  })

  it('redirige con replaceState a login cuando no hay sesión', async () => {
    const pedidos: string[] = []
    const fetchSinSesion: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      return respuesta(
        { codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' },
        401,
      )
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/config')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchSinSesion })
    await estabilizar()

    expect(historial.replace).toEqual(['/login?redirect=%2Fconfig'])
    expect(historial.push).toEqual([])
    expect(ultimo()?.descriptor.clase).toBe('login')
    detener()
  })

  it('muestra error de carga con reintento y recupera la vista sin recarga', async () => {
    let fallar = true
    const pedidos: string[] = []
    const fetchConFalla: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (fallar) {
        throw new TypeError('Failed to fetch')
      }
      return respuesta(ODONTOLOGO, 200)
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/agenda')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchConFalla })
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('error-carga')
    expect(ultimo()?.descriptor.hijos?.at(-1)?.accion).toBe('reintentar')

    fallar = false
    ultimo()?.manejadores?.['reintentar']?.(new NodoInteractivo())
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('layout')
    expect(pedidos).toEqual(['GET /api/auth/me', 'GET /api/auth/me'])
    expect(historial.push).toEqual([])
    expect(historial.replace).toEqual([])
    detener()
  })

  it('envía el acceso desde el formulario y navega sin recargar el documento', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/login?redirect=%2Fconfig')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchConSesion(pedidos) })
    await estabilizar()
    const formulario = new NodoInteractivo()
    formulario.valores['email'] = 'ana@consultorio.com'
    formulario.valores['password'] = 'secreto'

    ultimo()?.manejadores?.['ingresar']?.(formulario)
    await estabilizar()

    expect(pedidos).toEqual([
      'POST /api/auth/login',
      'GET /api/horarios',
      'GET /api/prestaciones',
      'GET /api/bloqueos',
    ])
    expect(historial.push).toEqual(['/config'])
    expect(historial.replace).toEqual([])
    expect(ultimo()?.descriptor.clase).toBe('layout')
    detener()
  })

  it('muestra la vista no encontrado dentro del layout para una ruta protegida desconocida', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/no-existe')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchConSesion(pedidos) })
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('layout')
    expect(ultimo()?.descriptor.hijos?.[2]?.hijos?.[0]?.clase).toBe('no-encontrado')
    expect(pedidos).toEqual(['GET /api/auth/me'])
    detener()
  })

  it('muestra error de carga ante una falla del servidor en /me', async () => {
    const fetchInvalido: FetchLike = async () =>
      respuesta({ codigo: 'ERROR_INTERNO', mensaje: 'No pudimos cargar tu sesión.' }, 500)
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/agenda').historial,
      fetch: fetchInvalido,
    })
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('error-carga')
    detener()
  })

  it('cierra la sesión desde el app bar y vuelve a /login', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/agenda')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchConSesion(pedidos) })
    await estabilizar()

    ultimo()?.manejadores?.['cerrar-sesion']?.(new NodoInteractivo())
    await estabilizar()

    expect(pedidos).toEqual(['GET /api/auth/me', 'POST /api/auth/logout'])
    expect(historial.push).toEqual(['/login'])
    expect(ultimo()?.descriptor.clase).toBe('login')
    detener()
  })

  it('propaga el lector de cookie inyectado al logout real', async () => {
    const cabecerasLogout: Headers[] = []
    const fetchQueRegistra: FetchLike = async (url, init) => {
      if (url === '/api/auth/logout') {
        cabecerasLogout.push(new Headers(init?.headers))
      }
      if (url === '/api/auth/me') {
        return respuesta(ODONTOLOGO, 200)
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/agenda').historial,
      fetch: fetchQueRegistra,
      leerCookie: () => 'token-csrf-real',
    })
    await estabilizar()

    await ultimo()?.manejadores?.['cerrar-sesion']?.(new NodoInteractivo())
    await estabilizar()

    expect(cabecerasLogout).toHaveLength(1)
    expect(cabecerasLogout[0]?.get('X-XSRF-TOKEN')).toBe('token-csrf-real')
    detener()
  })

  it('no envía CSRF al login público aunque haya un lector inyectado', async () => {
    const cabecerasLogin: Headers[] = []
    const fetchQueRegistra: FetchLike = async (url, init) => {
      if (url === '/api/auth/login') {
        cabecerasLogin.push(new Headers(init?.headers))
        return respuesta(ODONTOLOGO, 200)
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchQueRegistra,
      leerCookie: () => 'token-csrf-real',
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
    await estabilizar()

    expect(cabecerasLogin).toHaveLength(1)
    expect(cabecerasLogin[0]?.get('X-XSRF-TOKEN')).toBeNull()
    detener()
  })

  it('no inventa el header CSRF en el logout sin lector inyectado', async () => {
    const cabecerasLogout: Headers[] = []
    const fetchQueRegistra: FetchLike = async (url, init) => {
      if (url === '/api/auth/logout') {
        cabecerasLogout.push(new Headers(init?.headers))
      }
      if (url === '/api/auth/me') {
        return respuesta(ODONTOLOGO, 200)
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/agenda').historial,
      fetch: fetchQueRegistra,
    })
    await estabilizar()

    await ultimo()?.manejadores?.['cerrar-sesion']?.(new NodoInteractivo())
    await estabilizar()

    expect(cabecerasLogout).toHaveLength(1)
    expect(cabecerasLogout[0]?.get('X-XSRF-TOKEN')).toBeNull()
    detener()
  })

  it('sigue enlaces internos del layout con pushState', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/agenda')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchConSesion(pedidos) })
    await estabilizar()
    const enlace = new NodoInteractivo()
    enlace.setAttribute('href', '/config')

    ultimo()?.manejadores?.['navegar']?.(enlace)
    await estabilizar()

    expect(historial.push).toEqual(['/config'])
    expect(ultimo()?.descriptor.hijos?.[1]?.hijos?.[0]?.hijos?.[3]?.texto).toBe('Ana Gómez')
    detener()
  })

  it('importa app.css y no consulta el documento ni fuerza navegación de documento', () => {
    const codigo = readFileSync(new URL('./main.ts', import.meta.url), 'utf8')

    expect(codigo).toContain("import './estilos/app.css'")
    expect(codigo).toContain('export function arrancar')
    expect(codigo).not.toContain('document.querySelector')
    expect(codigo).not.toContain('location.href')
    expect(codigo).not.toContain('window.location')
  })

  it('el punto de entrada real consulta el documento y delega en arrancar', () => {
    const codigo = readFileSync(new URL('./bootstrap.ts', import.meta.url), 'utf8')

    expect(codigo).toContain("document.querySelector<HTMLElement>('#app')")
    expect(codigo).toContain('arrancar(app, crearAdaptadorNodos(document)')
    expect(codigo).toContain('pushState')
    expect(codigo).toContain('replaceState')
    expect(codigo).toContain('popstate')
  })

  it('el punto de entrada real inyecta un lector que lee document.cookie', () => {
    const codigo = readFileSync(new URL('./bootstrap.ts', import.meta.url), 'utf8')

    expect(codigo).toContain('leerCookieDeCadena(document.cookie, nombre)')
    expect(codigo).toContain('leerCookie:')
  })

  it('el documento carga el punto de entrada real', () => {
    const documento = readFileSync(new URL('../index.html', import.meta.url), 'utf8')

    expect(documento).toContain('src="/src/bootstrap.ts"')
    expect(documento).not.toContain('src="/src/main.ts"')
  })

  it('la navegación por popstate vuelve a renderizar sin recargar', async () => {
    let ruta = '/agenda'
    const callbacks: Array<() => void> = []
    const pedidos: string[] = []
    const historial: HistorialNavegacion = {
      rutaActual: () => ruta,
      pushState: (url) => {
        ruta = url
      },
      replaceState: (url) => {
        ruta = url
      },
      suscribirPopstate: (callback) => {
        callbacks.push(callback)
        return () => {}
      },
    }
    const { adaptador, montajes } = adaptadorFalso()
    const detener = arrancar({}, adaptador, { historial, fetch: fetchConSesion(pedidos) })
    await estabilizar()

    expect(montajes.at(-1)?.descriptor.clase).toBe('layout')

    ruta = '/config'
    for (const callback of callbacks) {
      callback()
    }
    await estabilizar()

    expect(montajes.at(-1)?.descriptor.clase).toBe('layout')
    expect(pedidos).toEqual([
      'GET /api/auth/me',
      'GET /api/horarios',
      'GET /api/prestaciones',
      'GET /api/bloqueos',
    ])
    detener()
  })
})

describe('regresiones del login integrado', () => {
  it('muestra el mensaje canónico con rol alert tras un 401 RN-AU-01', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchConLogin({ codigo: 'RN-AU-01', mensaje: 'Cuenta inactiva.' }, 401, pedidos),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
    await estabilizar()

    const aviso = alertaDe(ultimo()?.descriptor)
    expect(aviso?.texto).toBe('El email o la contraseña no son correctos.')
    expect(pedidos).toEqual(['POST /api/auth/login'])
    detener()
  })

  it('muestra el mismo mensaje y el mismo rol ante otro motivo de RN-AU-01', async () => {
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchConLogin({ codigo: 'RN-AU-01', mensaje: 'El email no existe.' }, 401),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('otro@consultorio.com', 'otra'))
    await estabilizar()

    expect(alertaDe(ultimo()?.descriptor)?.atributos?.['role']).toBe('alert')
    expect(alertaDe(ultimo()?.descriptor)?.texto).toBe('El email o la contraseña no son correctos.')
    detener()
  })

  it('bloquea 15 minutos y deshabilita el botón ante un 429 con 900 segundos', async () => {
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchConLogin(
        {
          codigo: 'RN-AU-03',
          mensaje: 'Demasiados intentos fallidos. Probá de nuevo en unos minutos.',
          detalles: { reintentarEnSegundos: 900 },
        },
        429,
      ),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
    await estabilizar()

    expect(textosDe(ultimo()?.descriptor)).toContain('Demasiados intentos. Probá de nuevo en 15 minutos.')
    expect(botonIngresarDe(ultimo()?.descriptor)?.atributos?.['disabled']).toBe('')
    detener()
  })

  it('redondea hacia arriba 841 segundos a 15 minutos en la vista integrada', async () => {
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchConLogin(
        {
          codigo: 'RN-AU-03',
          mensaje: 'Demasiados intentos fallidos.',
          detalles: { reintentarEnSegundos: 841 },
        },
        429,
      ),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
    await estabilizar()

    expect(textosDe(ultimo()?.descriptor)).toContain('Demasiados intentos. Probá de nuevo en 15 minutos.')
    expect(textosDe(ultimo()?.descriptor)).not.toContain('14 minutos')
    detener()
  })

  it('marca el campo faltante y no envía el pedido cuando el email está vacío', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchConLogin(ODONTOLOGO, 200, pedidos),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('', 'secreto'))
    await estabilizar()

    expect(pedidos).toEqual([])
    expect(textosDe(ultimo()?.descriptor)).toContain('Ingresá tu email.')
    expect(alertaDe(ultimo()?.descriptor)?.texto).toBe('Ingresá tu email.')
    detener()
  })

  it('marca la contraseña faltante sin consultar la API', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchConLogin(ODONTOLOGO, 200, pedidos),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', ''))
    await estabilizar()

    expect(pedidos).toEqual([])
    expect(alertaDe(ultimo()?.descriptor)?.texto).toBe('Ingresá tu contraseña.')
    detener()
  })

  it('muestra Ingresando… con el botón deshabilitado mientras el pedido está en curso', async () => {
    let resolver!: (respuesta: Response) => void
    const pedidoPendiente = new Promise<Response>((resolverPendiente) => {
      resolver = resolverPendiente
    })
    const pedidos: string[] = []
    const fetchPendiente: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (url === '/api/auth/login') {
        return pedidoPendiente
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, { historial: historialFalso('/login').historial, fetch: fetchPendiente })
    await estabilizar()

    void ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
    await estabilizar()

    expect(textosDe(ultimo()?.descriptor)).toContain('Ingresando…')
    expect(botonIngresarDe(ultimo()?.descriptor)?.texto).toBe('Ingresando…')
    expect(botonIngresarDe(ultimo()?.descriptor)?.atributos?.['disabled']).toBe('')

    resolver(respuesta(ODONTOLOGO, 200))
    await estabilizar()
    detener()
  })

  it('habilita el botón y quita el aviso al vencer el bloqueo con el reloj falso', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-22T10:00:00Z'))
    try {
      const { adaptador, ultimo } = adaptadorFalso()
      const detener = arrancar({}, adaptador, {
        historial: historialFalso('/login').historial,
        fetch: fetchConLogin(
          {
            codigo: 'RN-AU-03',
            mensaje: 'Demasiados intentos fallidos.',
            detalles: { reintentarEnSegundos: 900 },
          },
          429,
        ),
      })
      await drenarMicrotareas()

      await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
      await drenarMicrotareas()

      expect(textosDe(ultimo()?.descriptor)).toContain('Demasiados intentos. Probá de nuevo en 15 minutos.')
      expect(botonIngresarDe(ultimo()?.descriptor)?.atributos?.['disabled']).toBe('')

      vi.advanceTimersByTime(900_000)
      await drenarMicrotareas()

      expect(textosDe(ultimo()?.descriptor)).not.toContain('Demasiados intentos. Probá de nuevo en 15 minutos.')
      expect(botonIngresarDe(ultimo()?.descriptor)?.texto).toBe('Ingresar')
      expect(botonIngresarDe(ultimo()?.descriptor)?.atributos?.['disabled']).toBeUndefined()
      detener()
    } finally {
      vi.useRealTimers()
    }
  })

  it('también habilita el botón al vencer un bloqueo más corto', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-22T10:00:00Z'))
    try {
      const { adaptador, ultimo } = adaptadorFalso()
      const detener = arrancar({}, adaptador, {
        historial: historialFalso('/login').historial,
        fetch: fetchConLogin(
          {
            codigo: 'RN-AU-03',
            mensaje: 'Demasiados intentos fallidos.',
            detalles: { reintentarEnSegundos: 60 },
          },
          429,
        ),
      })
      await drenarMicrotareas()

      await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
      await drenarMicrotareas()

      expect(botonIngresarDe(ultimo()?.descriptor)?.atributos?.['disabled']).toBe('')

      vi.advanceTimersByTime(60_000)
      await drenarMicrotareas()

      expect(botonIngresarDe(ultimo()?.descriptor)?.atributos?.['disabled']).toBeUndefined()
      detener()
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('regresiones de rutas públicas y cliente compartido', () => {
  it('monta la reserva pública de /reservar/{slug} sin sesión y sin consultar /me', async () => {
    const pedidos: string[] = []
    const perfil = {
      slug: 'ana-gomez',
      nombre: 'Ana',
      apellido: 'Gómez',
      direccionConsultorio: 'Av. Siempreviva 742',
      zonaHoraria: 'America/Argentina/Buenos_Aires',
      fechaMinima: '2026-11-26',
      fechaMaxima: '2027-01-24',
      prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }],
    }
    const fetchPublico: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (url === '/api/public/ana-gomez') {
        return respuesta(perfil, 200)
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/reservar/ana-gomez').historial,
      fetch: fetchPublico,
    })
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('reserva-publica')
    expect(textosDe(ultimo()?.descriptor)).toContain('Ana Gómez')
    expect(textosDe(ultimo()?.descriptor)).toContain('Consulta')
    expect(pedidos).toEqual(['GET /api/public/ana-gomez'])
    detener()
  })

  it('un /turno/{token} mal formado muestra "No encontramos este turno" sin pedir nada', async () => {
    const pedidos: string[] = []
    const fetchSinSesion: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      return respuesta(
        { codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' },
        401,
      )
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/turno/abc123').historial,
      fetch: fetchSinSesion,
    })
    await estabilizar()

    expect(ultimo()?.descriptor.clase).toBe('reserva-publica gestion-turno')
    expect(textosDe(ultimo()?.descriptor)).toContain('No encontramos este turno')
    expect(pedidos).toEqual([])
    detener()
  })

  it('monta la gestión de /turno/{token} sin sesión y solo consulta la API del turno', async () => {
    const token = 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-AbCdE'
    const pedidos: string[] = []
    const fetchPublico: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      return respuesta(
        {
          inicio: '2026-11-27T09:00:00-03:00',
          fin: '2026-11-27T09:30:00-03:00',
          estado: 'RESERVADO',
          prestacion: { id: 1, nombre: 'Consulta' },
          odontologo: { slug: 'demo', nombre: 'Ana', apellido: 'Pérez', direccionConsultorio: null },
          puedeCancelar: true,
          puedeReprogramar: true,
        },
        200,
      )
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso(`/turno/${token}`).historial,
      fetch: fetchPublico,
    })
    await estabilizar()

    expect(textosDe(ultimo()?.descriptor)).toContain('Gestioná tu turno')
    expect(textosDe(ultimo()?.descriptor)).toContain('Consulta')
    expect(pedidos).toEqual([`GET /api/public/turnos/${token}`])
    detener()
  })

  it('un 401 NO_AUTENTICADO del cliente compartido descarta la caché y fuerza un nuevo /me', async () => {
    let consultasMe = 0
    const pedidos: string[] = []
    const fetchMixto: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (url === '/api/auth/me') {
        consultasMe += 1
        return respuesta(ODONTOLOGO, 200)
      }
      if (url === '/api/auth/login') {
        return respuesta(
          { codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' },
          401,
        )
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const historial = historialFalso('/agenda')
    const detener = arrancar({}, adaptador, { historial: historial.historial, fetch: fetchMixto })
    await estabilizar()
    expect(consultasMe).toBe(1)

    const enlaceLogin = new NodoInteractivo()
    enlaceLogin.setAttribute('href', '/login')
    ultimo()?.manejadores?.['navegar']?.(enlaceLogin)
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
    await estabilizar()

    const enlaceAgenda = new NodoInteractivo()
    enlaceAgenda.setAttribute('href', '/agenda')
    ultimo()?.manejadores?.['navegar']?.(enlaceAgenda)
    await estabilizar()

    expect(consultasMe).toBe(2)
    expect(pedidos.filter((pedido) => pedido === 'GET /api/auth/me')).toHaveLength(2)
    detener()
  })

  it('un 401 NO_AUTENTICADO al cerrar sesión descarta la caché solo si el cliente es compartido', async () => {
    let consultasMe = 0
    let cierres = 0
    const fetchMixto: FetchLike = async (url) => {
      if (url === '/api/auth/me') {
        consultasMe += 1
        return respuesta(ODONTOLOGO, 200)
      }
      if (url === '/api/auth/logout') {
        cierres += 1
        return respuesta(
          { codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' },
          401,
        )
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/agenda').historial,
      fetch: fetchMixto,
    })
    await estabilizar()
    expect(consultasMe).toBe(1)

    await expect(ultimo()?.manejadores?.['cerrar-sesion']?.(new NodoInteractivo())).rejects.toMatchObject({
      codigo: 'NO_AUTENTICADO',
    })

    const enlaceConfig = new NodoInteractivo()
    enlaceConfig.setAttribute('href', '/config')
    ultimo()?.manejadores?.['navegar']?.(enlaceConfig)
    await estabilizar()

    expect(cierres).toBe(1)
    expect(consultasMe).toBe(2)
    detener()
  })

  it('un 401 de credenciales no descarta la caché del guard', async () => {
    let consultasMe = 0
    const fetchMixto: FetchLike = async (url) => {
      if (url === '/api/auth/me') {
        consultasMe += 1
        return respuesta(ODONTOLOGO, 200)
      }
      if (url === '/api/auth/login') {
        return respuesta({ codigo: 'RN-AU-01', mensaje: 'Credenciales inválidas.' }, 401)
      }
      return new Response(null, { status: 204 })
    }
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/agenda').historial,
      fetch: fetchMixto,
    })
    await estabilizar()
    expect(consultasMe).toBe(1)

    const enlaceLogin = new NodoInteractivo()
    enlaceLogin.setAttribute('href', '/login')
    ultimo()?.manejadores?.['navegar']?.(enlaceLogin)
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('ana@consultorio.com', 'secreto'))
    await estabilizar()

    const enlaceAgenda = new NodoInteractivo()
    enlaceAgenda.setAttribute('href', '/agenda')
    ultimo()?.manejadores?.['navegar']?.(enlaceAgenda)
    await estabilizar()

    expect(consultasMe).toBe(1)
    detener()
  })
})

describe('cableamiento del botón Ingresar', () => {
  it('envía el email y la contraseña del formulario al hacer clic en el botón', async () => {
    const pedidos: PedidoRegistrado[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchQueRegistraPedidos(pedidos),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(new BotonIngresar(formularioCon('ana@consultorio.com', 'secreto')))
    await estabilizar()

    expect(pedidos).toEqual([
      {
        url: '/api/auth/login',
        metodo: 'POST',
        cuerpo: { email: 'ana@consultorio.com', password: 'secreto' },
      },
    ])
    detener()
  })

  it('mantiene el submit directo: lee los campos del propio formulario', async () => {
    const pedidos: PedidoRegistrado[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchQueRegistraPedidos(pedidos),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(formularioCon('otra@consultorio.com', 'otra-clave'))
    await estabilizar()

    expect(pedidos).toEqual([
      {
        url: '/api/auth/login',
        metodo: 'POST',
        cuerpo: { email: 'otra@consultorio.com', password: 'otra-clave' },
      },
    ])
    detener()
  })

  it('un nodo sin closest y sin campos sigue mostrando el error de validación', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchConLogin(ODONTOLOGO, 200, pedidos),
    })
    await estabilizar()

    await ultimo()?.manejadores?.['ingresar']?.(new NodoInteractivo())
    await estabilizar()

    expect(pedidos).toEqual([])
    expect(alertaDe(ultimo()?.descriptor)?.texto).toBe('Ingresá tu email.')
    detener()
  })

  it('prioriza los campos del formulario incluso si el botón tiene valores propios', async () => {
    const pedidos: PedidoRegistrado[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/login').historial,
      fetch: fetchQueRegistraPedidos(pedidos),
    })
    await estabilizar()
    const boton = new BotonIngresar(formularioCon('ana@consultorio.com', 'secreto'))
    boton.valores['email'] = 'valor-del-boton@consultorio.com'
    boton.valores['password'] = 'clave-del-boton'

    await ultimo()?.manejadores?.['ingresar']?.(boton)
    await estabilizar()

    expect(pedidos).toEqual([
      {
        url: '/api/auth/login',
        metodo: 'POST',
        cuerpo: { email: 'ana@consultorio.com', password: 'secreto' },
      },
    ])
    detener()
  })
})
