import { describe, expect, it } from 'vitest'
// @ts-expect-error: este proyecto no declara tipos de Node; readFileSync se usa solo en el test.
import { readFileSync } from 'node:fs'
import { crearControlLogin, minutosDeBloqueo } from './login'
import { crearClienteApi } from '../../shared/api-client'
import type { FetchLike } from '../../shared/api-client'
import type { OdontologoDto } from '../../shared/guard-sesion'
import { crearGuard } from '../../shared/guard-sesion'
import { crearRouter } from '../../router'
import type { HistorialNavegacion } from '../../router'
import { layout } from '../../shared/ui/layout'
import { crearAdaptadorNodos } from '../../shared/ui/adaptador'
import type { NodoUI } from '../../shared/ui/adaptador'
import type { Descriptor } from '../../shared/ui/componentes'

const ODONTOLOGO: OdontologoDto = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Gómez',
  email: 'ana@consultorio.com',
  slug: 'ana-gomez',
}

function respuesta(cuerpo: unknown, status: number): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function formularioDe(descriptor: Descriptor): Descriptor | undefined {
  return descriptor.hijos?.find((hijo) => hijo.etiqueta === 'form')
}

function alertaDelFormulario(descriptor: Descriptor) {
  return formularioDe(descriptor)?.hijos?.find((hijo) => hijo.atributos?.['role'] === 'alert')
}

async function enviarConRespuesta(cuerpo: unknown, status: number) {
  const control = crearControlLogin({
    cliente: crearClienteApi({ fetch: async () => respuesta(cuerpo, status) }),
    alIngresar: () => {},
  })
  await control.enviar('ana@consultorio.com', 'incorrecta')
  return control
}

describe('formulario de acceso', () => {
  it('envía un POST /api/auth/login con email y contraseña', async () => {
    const pedidos: Array<{ url: string; method: string | undefined; body: unknown }> = []
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push({ url, method: init?.method, body: typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body })
      return respuesta(ODONTOLOGO, 200)
    }
    const ingresos: Array<{ destino: string; odontologo: OdontologoDto }> = []
    const control = crearControlLogin({
      cliente: crearClienteApi({ fetch: fetchFalso }),
      ruta: '/login',
      alIngresar: (destino, odontologo) => ingresos.push({ destino, odontologo }),
    })

    await control.enviar('ana@consultorio.com', 'secreto')

    expect(pedidos).toEqual([
      { url: '/api/auth/login', method: 'POST', body: { email: 'ana@consultorio.com', password: 'secreto' } },
    ])
    expect(ingresos).toEqual([{ destino: '/agenda', odontologo: ODONTOLOGO }])
  })

  it('impide el doble envío y muestra el estado de envío', async () => {
    const pedidos: string[] = []
    let resolver: ((respuesta: Response) => void) | undefined
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      return new Promise<Response>((resolve) => {
        resolver = resolve
      })
    }
    const control = crearControlLogin({
      cliente: crearClienteApi({ fetch: fetchFalso }),
      alIngresar: () => {},
    })

    const primero = control.enviar('ana@consultorio.com', 'secreto')
    const segundo = control.enviar('ana@consultorio.com', 'secreto')
    const formulario = formularioDe(control.descriptor())
    const boton = formulario?.hijos?.[2]

    expect(boton?.texto).toBe('Ingresando…')
    expect(boton?.atributos?.['disabled']).toBe('')
    expect(resolver).toBeTypeOf('function')
    resolver?.(respuesta(ODONTOLOGO, 200))
    await Promise.all([primero, segundo])

    expect(pedidos).toEqual(['POST /api/auth/login'])
  })

  it('no envía el pedido y marca el campo faltante', async () => {
    const pedidos: string[] = []
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      return respuesta(ODONTOLOGO, 200)
    }
    const control = crearControlLogin({
      cliente: crearClienteApi({ fetch: fetchFalso }),
      alIngresar: () => {},
    })

    await control.enviar('', 'secreto')
    const descriptor = control.descriptor()
    const campoEmail = formularioDe(descriptor)?.hijos?.[0]

    expect(pedidos).toEqual([])
    expect(control.estado()).toEqual({ tipo: 'error', mensaje: 'Ingresá tu email.' })
    expect(campoEmail?.hijos?.[1]?.atributos?.['aria-invalid']).toBe('true')
    expect(campoEmail?.hijos?.[2]?.texto).toBe('Ingresá tu email.')
  })
})

describe('error genérico de credenciales', () => {
  it('muestra siempre el mismo mensaje con rol alert ante RN-AU-01', async () => {
    const control = await enviarConRespuesta(
      { codigo: 'RN-AU-01', mensaje: 'El email o la contraseña no son correctos.' },
      401,
    )

    expect(alertaDelFormulario(control.descriptor())).toMatchObject({
      texto: 'El email o la contraseña no son correctos.',
      atributos: { role: 'alert' },
    })
  })

  it('no distingue un email inexistente', async () => {
    const control = await enviarConRespuesta(
      { codigo: 'RN-AU-01', mensaje: 'No existe una cuenta con ese email.' },
      401,
    )

    expect(alertaDelFormulario(control.descriptor())?.texto).toBe('El email o la contraseña no son correctos.')
  })

  it('no distingue una cuenta inactiva', async () => {
    const control = await enviarConRespuesta(
      { codigo: 'RN-AU-01', mensaje: 'La cuenta está inactiva.' },
      401,
    )

    expect(alertaDelFormulario(control.descriptor())?.texto).toBe('El email o la contraseña no son correctos.')
    expect(JSON.stringify(control.descriptor())).not.toContain('inactiva')
  })
})

describe('estado de bloqueo por intentos', () => {
  it('convierte 900 segundos en 15 minutos y deshabilita el botón', async () => {
    const ahora = 1_000_000
    const control = crearControlLogin({
      cliente: crearClienteApi({
        fetch: async () =>
          respuesta(
            {
              codigo: 'RN-AU-03',
              mensaje: 'Demasiados intentos fallidos. Probá de nuevo en unos minutos.',
              detalles: { reintentarEnSegundos: 900, bloqueadoHasta: '2026-11-22T04:00:00Z' },
            },
            429,
          ),
      }),
      reloj: () => ahora,
      alIngresar: () => {},
    })

    await control.enviar('ana@consultorio.com', 'incorrecta')
    const descriptor = control.descriptor(ahora)
    const boton = formularioDe(descriptor)?.hijos?.at(-1)

    expect(alertaDelFormulario(control.descriptor())?.texto).toBe('Demasiados intentos. Probá de nuevo en 15 minutos.')
    expect(boton?.atributos?.['disabled']).toBe('')
  })

  it('redondea hacia arriba 841 y 899 segundos', () => {
    expect(minutosDeBloqueo(841)).toBe(15)
    expect(minutosDeBloqueo(899)).toBe(15)
    expect(minutosDeBloqueo(840)).toBe(14)
  })

  it('habilita el botón cuando vence el plazo sin recargar la página', async () => {
    let ahora = 1_000_000
    const control = crearControlLogin({
      cliente: crearClienteApi({
        fetch: async () =>
          respuesta(
            {
              codigo: 'RN-AU-03',
              mensaje: 'Demasiados intentos fallidos. Probá de nuevo en unos minutos.',
              detalles: { reintentarEnSegundos: 900 },
            },
            429,
          ),
      }),
      reloj: () => ahora,
      alIngresar: () => {},
    })

    await control.enviar('ana@consultorio.com', 'incorrecta')
    expect(formularioDe(control.descriptor(ahora))?.hijos?.at(-1)?.atributos?.['disabled']).toBe('')

    ahora += 900_000
    const descriptor = control.descriptor(ahora)

    expect(formularioDe(descriptor)?.hijos?.at(-1)?.atributos?.['disabled']).toBeUndefined()
    expect(formularioDe(descriptor)?.hijos?.some((hijo) => hijo.atributos?.['role'] === 'alert')).toBe(false)
    expect(control.estado()).toMatchObject({ tipo: 'bloqueado' })
  })

  it('sin segundos utilizables avisa sin inventar un número y sigue bloqueado', async () => {
    const control = crearControlLogin({
      cliente: crearClienteApi({
        fetch: async () =>
          respuesta(
            {
              codigo: 'RN-AU-03',
              mensaje: 'Demasiados intentos fallidos. Probá de nuevo en unos minutos.',
              detalles: { reintentarEnSegundos: '900' },
            },
            429,
          ),
      }),
      reloj: () => 1_000_000,
      alIngresar: () => {},
    })

    await control.enviar('ana@consultorio.com', 'incorrecta')
    const texto = alertaDelFormulario(control.descriptor())?.texto

    expect(texto).toBe('Demasiados intentos. Probá de nuevo más tarde.')
    expect(texto).not.toMatch(/\d/)
    expect(formularioDe(control.descriptor(2_000_000))?.hijos?.at(-1)?.atributos?.['disabled']).toBe('')
  })
})

describe('destino tras el ingreso y cierre de sesión', () => {
  function historialFalso(rutaInicial = '/login') {
    let ruta = rutaInicial
    const push: string[] = []
    const historial: HistorialNavegacion = {
      rutaActual: () => ruta,
      pushState: (url) => {
        ruta = url
        push.push(url)
      },
      replaceState: (url) => {
        ruta = url
      },
      suscribirPopstate: () => () => {},
    }

    return { historial, push, rutaActual: () => ruta }
  }

  function escenario(ruta: string) {
    const pedidos: string[] = []
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (url === '/api/auth/login') {
        return respuesta(ODONTOLOGO, 200)
      }
      if (url === '/api/auth/logout') {
        return new Response(null, { status: 204 })
      }
      return respuesta(
        { codigo: 'NO_AUTENTICADO', mensaje: 'Tu sesión no está iniciada o expiró. Volvé a ingresar.' },
        401,
      )
    }
    const cliente = crearClienteApi({ fetch: fetchFalso })
    const guard = crearGuard(fetchFalso, () => () => {}, cliente)
    const historial = historialFalso()
    const router = crearRouter(historial.historial)
    const control = crearControlLogin({
      cliente,
      guard,
      navegar: router.ir,
      ruta,
      alIngresar: () => {},
    })

    return { control, guard, pedidos, router, historial }
  }

  it('con redirect=/config navega a /config y deja la sesión en caché', async () => {
    const { control, guard, pedidos, router } = escenario('/login?redirect=%2Fconfig')

    await control.enviar('ana@consultorio.com', 'secreto')

    expect(router.rutaActual()).toBe('/config')
    expect(await guard.decidir('/agenda')).toEqual({ tipo: 'mostrar', ruta: '/agenda', odontologo: ODONTOLOGO })
    expect(pedidos).toEqual(['POST /api/auth/login'])
  })

  it('un redirect externo cae a /agenda igualmente con la sesión en caché', async () => {
    const { control, guard, pedidos, router } = escenario('/login?redirect=https%3A%2F%2Fsitio-externo.com')

    await control.enviar('ana@consultorio.com', 'secreto')

    expect(router.rutaActual()).toBe('/agenda')
    expect(await guard.decidir('/config')).toEqual({ tipo: 'mostrar', ruta: '/config', odontologo: ODONTOLOGO })
    expect(pedidos).toEqual(['POST /api/auth/login'])
  })

  it('el logout 204 limpia la caché y vuelve a /login, cuyo layout ofrece Cerrar sesión', async () => {
    const { control, guard, pedidos, router } = escenario('/login')

    await control.enviar('ana@consultorio.com', 'secreto')
    await control.cerrarSesion()

    expect(pedidos).toEqual(['POST /api/auth/login', 'POST /api/auth/logout'])
    expect(router.rutaActual()).toBe('/login')
    expect(await guard.decidir('/agenda')).toEqual({
      tipo: 'redirigir',
      ruta: '/agenda',
      para: '/login?redirect=%2Fagenda',
    })
    const appbar = layout({ odontologo: ODONTOLOGO, ruta: '/agenda', contenido: { etiqueta: 'p', texto: 'Listo' } })
      .hijos?.[1]?.hijos?.[0]?.hijos
    expect(appbar?.find((nodo) => nodo.accion === 'cerrar-sesion')?.texto).toBe('Cerrar sesión')
  })

  it('un redirect a una ruta inexistente también cae a /agenda', async () => {
    const { control, router } = escenario('/login?redirect=%2Fno-existe')

    await control.enviar('ana@consultorio.com', 'secreto')

    expect(router.rutaActual()).toBe('/agenda')
  })

  it('un logout que falla no descarta la sesión ni cambia de ruta', async () => {
    const pedidos: string[] = []
    const fetchFalso: FetchLike = async (url, init) => {
      pedidos.push(`${init?.method ?? 'GET'} ${url}`)
      if (url === '/api/auth/login') {
        return respuesta(ODONTOLOGO, 200)
      }
      throw new TypeError('Failed to fetch')
    }
    const cliente = crearClienteApi({ fetch: fetchFalso })
    const guard = crearGuard(fetchFalso, () => () => {}, cliente)
    const historial = historialFalso()
    const router = crearRouter(historial.historial)
    const control = crearControlLogin({
      cliente,
      guard,
      navegar: router.ir,
      alIngresar: () => {},
    })

    await control.enviar('ana@consultorio.com', 'secreto')
    await expect(control.cerrarSesion()).rejects.toMatchObject({ codigo: 'SIN_CONEXION' })

    expect(router.rutaActual()).toBe('/agenda')
    expect(await guard.decidir('/config')).toEqual({ tipo: 'mostrar', ruta: '/config', odontologo: ODONTOLOGO })
    expect(pedidos).toEqual(['POST /api/auth/login', 'POST /api/auth/logout'])
  })
})

describe('inserción segura de mensajes', () => {
  class NodoFalso implements NodoUI {
    readonly hijos: NodoUI[] = []
    readonly atributos: Record<string, string> = {}
    textContent = ''

    append(...nodos: NodoUI[]): void {
      this.hijos.push(...nodos)
    }

    setAttribute(nombre: string, valor: string): void {
      this.atributos[nombre] = valor
    }

    addEventListener(): void {}
  }

  const documentoFalso = { createElement: () => new NodoFalso() }

  async function mensajeRenderizado(mensaje: string) {
    const control = await enviarConRespuesta({ codigo: 'VALIDACION', mensaje }, 400)
    const descriptor = control.descriptor()
    const destino = new NodoFalso()

    crearAdaptadorNodos(documentoFalso).montar(destino, descriptor)
    const raiz = destino.hijos[0]
    if (!(raiz instanceof NodoFalso)) {
      throw new Error('El adaptador no creó un nodo falso')
    }
    const formulario = raiz.hijos.find((hijo) => hijo instanceof NodoFalso && hijo.atributos['data-accion'] === 'ingresar')
    const candidato =
      formulario instanceof NodoFalso
        ? formulario.hijos.find((hijo) => hijo instanceof NodoFalso && hijo.atributos['role'] === 'alert')
        : undefined
    const aviso = candidato instanceof NodoFalso ? candidato : undefined

    return { descriptor, aviso }
  }

  it('un mensaje con marcas HTML queda como texto literal y sin hijos', async () => {
    const { descriptor, aviso } = await mensajeRenderizado('<script>algo</script>')

    expect(alertaDelFormulario(descriptor)?.texto).toBe('<script>algo</script>')
    expect(aviso?.textContent).toBe('<script>algo</script>')
    expect(aviso?.hijos).toHaveLength(0)
    expect(JSON.stringify(descriptor)).not.toContain('innerHTML')
  })

  it('conserva entidades HTML sin interpretarlas', async () => {
    const mensaje = '&lt;img src=x onerror=alert(1)&gt;'
    const { aviso } = await mensajeRenderizado(mensaje)

    expect(aviso?.textContent).toBe(mensaje)
    expect(aviso?.hijos).toHaveLength(0)
  })

  it('conserva comillas y ángulos como texto', async () => {
    const mensaje = `'"><b>hola</b>`
    const { aviso } = await mensajeRenderizado(mensaje)

    expect(aviso?.textContent).toBe(mensaje)
    expect(aviso?.hijos).toHaveLength(0)
  })
})

describe('textos alineados con la spec y los mockups', () => {
  const rutaMockups = '../../../../openspec/changes/c-04-shell-frontend-y-login/mockups/'
  const escritorio = readFileSync(new URL(`${rutaMockups}login-escritorio.html`, import.meta.url), 'utf8')
  const movil = readFileSync(new URL(`${rutaMockups}login-movil.html`, import.meta.url), 'utf8')

  function textosDe(descriptor: Descriptor): string[] {
    return [
      ...(descriptor.texto === undefined ? [] : [descriptor.texto]),
      ...(descriptor.hijos ?? []).flatMap(textosDe),
    ]
  }

  function controlLimpio() {
    return crearControlLogin({ cliente: crearClienteApi({ fetch: async () => respuesta(ODONTOLOGO, 200) }), alIngresar: () => {} })
  }

  it('el formulario limpio usa los textos que comparten ambos mockups', () => {
    const textos = textosDe(controlLimpio().descriptor())

    for (const texto of [
      'Ingresá a tu agenda',
      'Usá el email y la contraseña de tu cuenta.',
      'Email',
      'Contraseña',
      'Ingresar',
    ]) {
      expect(textos).toContain(texto)
      expect(escritorio).toContain(texto)
      expect(movil).toContain(texto)
    }
  })

  it('el estado 401 muestra el mensaje genérico y el texto de apoyo', async () => {
    const control = await enviarConRespuesta(
      { codigo: 'RN-AU-01', mensaje: 'El email o la contraseña no son correctos.' },
      401,
    )
    const textos = textosDe(control.descriptor())

    expect(textos).toContain('El email o la contraseña no son correctos.')
    expect(textos).toContain('Revisá los datos y probá de nuevo.')
    expect(escritorio).toContain('Revisá los datos y probá de nuevo.')
    expect(movil).toContain('Revisá los datos y probá de nuevo.')
  })

  it('el estado 429 calcula 15 minutos y no copia el 14 ilustrativo', async () => {
    const control = await enviarConRespuesta(
      {
        codigo: 'RN-AU-03',
        mensaje: 'Demasiados intentos fallidos. Probá de nuevo en unos minutos.',
        detalles: { reintentarEnSegundos: 841 },
      },
      429,
    )
    const textos = textosDe(control.descriptor())

    expect(textos).toContain('Demasiados intentos. Probá de nuevo en 15 minutos.')
    expect(textos).toContain('El acceso se habilita solo; no hace falta que hagas nada.')
    expect(textos.join(' ')).not.toContain('14 minutos')
    expect(escritorio).toContain('El acceso se habilita solo; no hace falta que hagas nada.')
    expect(movil).toContain('El acceso se habilita solo; no hace falta que hagas nada.')
  })

  it('no copia literales ilustrativos ni estilos del mockup', () => {
    const texto = JSON.stringify(controlLimpio().descriptor())

    for (const ilustrativo of ['14 minutos', 'Salir', '2px', 'opacity', 'letter-spacing']) {
      expect(texto).not.toContain(ilustrativo)
    }
  })

  it('el mismo mensaje genérico vale para escritorio y móvil', async () => {
    const control = await enviarConRespuesta({ codigo: 'RN-AU-01', mensaje: 'Cuenta inactiva.' }, 401)
    const mensaje = alertaDelFormulario(control.descriptor())?.texto

    expect(mensaje).toBe('El email o la contraseña no son correctos.')
    expect(escritorio).toContain(mensaje)
    expect(movil).toContain(mensaje)
  })
})
