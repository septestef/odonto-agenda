import { describe, expect, it } from 'vitest'
import { crearRouter, resolverRuta } from './router'
import type { HistorialNavegacion, ResultadoRuta } from './router'

function historialFalso(rutaInicial = '/login') {
  let ruta = rutaInicial
  const push: string[] = []
  const replace: string[] = []
  const oyentes = new Set<() => void>()

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
    suscribirPopstate: (callback) => {
      oyentes.add(callback)
      return () => oyentes.delete(callback)
    },
  }

  return {
    historial,
    push,
    replace,
    simularPopstate(url: string) {
      ruta = url
      for (const oyente of oyentes) {
        oyente()
      }
    },
  }
}

describe('tabla de rutas y parámetros', () => {
  it('captura el slug de /reservar/ana-gomez', () => {
    expect(resolverRuta('/reservar/ana-gomez')).toEqual({
      tipo: 'ruta',
      nombre: 'reservar',
      parametros: { slug: 'ana-gomez' },
    })
  })

  it('captura el token de /turno/abc123', () => {
    expect(resolverRuta('/turno/abc123')).toEqual({
      tipo: 'ruta',
      nombre: 'turno',
      parametros: { token: 'abc123' },
    })
  })

  it.each(['/login', '/agenda', '/config'] as const)('resuelve la ruta fija %s', (ruta) => {
    expect(resolverRuta(ruta)).toEqual({
      tipo: 'ruta',
      nombre: ruta.slice(1),
      parametros: {},
    })
  })

  it('devuelve no encontrado para una ruta inexistente', () => {
    expect(resolverRuta('/no-existe')).toEqual({ tipo: 'no-encontrado', ruta: '/no-existe' })
  })

  it('no confunde un prefijo con una ruta con parámetro', () => {
    expect(resolverRuta('/reservar/ana-gomez/extra')).toEqual({
      tipo: 'no-encontrado',
      ruta: '/reservar/ana-gomez/extra',
    })
  })

  it('ignora la query al capturar el token del link de email', () => {
    expect(resolverRuta('/turno/abc123?utm=email')).toEqual({
      tipo: 'ruta',
      nombre: 'turno',
      parametros: { token: 'abc123' },
    })
  })
})

describe('navegación sin recarga', () => {
  function vistasDe(router: ReturnType<typeof crearRouter>): ResultadoRuta[] {
    const vistas: ResultadoRuta[] = []
    router.suscribir((vista) => vistas.push(vista))
    return vistas
  }

  it('actualiza vista y barra con pushState al seguir un enlace interno', () => {
    const { historial, push } = historialFalso()
    const router = crearRouter(historial)
    const vistas = vistasDe(router)
    router.iniciar()

    router.ir('/agenda')

    expect(push).toEqual(['/agenda'])
    expect(vistas.at(-1)).toEqual({ tipo: 'ruta', nombre: 'agenda', parametros: {} })
  })

  it('restaura la vista al usar el botón atrás del navegador', () => {
    const { historial, simularPopstate, push } = historialFalso('/agenda')
    const router = crearRouter(historial)
    const vistas = vistasDe(router)
    router.iniciar()

    simularPopstate('/config')

    expect(vistas.at(-1)).toEqual({ tipo: 'ruta', nombre: 'config', parametros: {} })
    expect(push).toEqual([])
  })

  it('retrocede de /config a /agenda sin pedir un documento nuevo', () => {
    const { historial, simularPopstate, push, replace } = historialFalso('/config')
    const router = crearRouter(historial)
    const vistas = vistasDe(router)
    router.iniciar()

    simularPopstate('/agenda')

    expect(vistas.at(-1)).toEqual({ tipo: 'ruta', nombre: 'agenda', parametros: {} })
    expect(push).toEqual([])
    expect(replace).toEqual([])
  })

  it('usa replaceState para una redirección sin dejar entrada en el historial', () => {
    const { historial, push, replace } = historialFalso()
    const router = crearRouter(historial)
    const vistas = vistasDe(router)
    router.iniciar()

    router.reemplazar('/login?redirect=%2Fagenda')

    expect(replace).toEqual(['/login?redirect=%2Fagenda'])
    expect(push).toEqual([])
    expect(vistas.at(-1)).toEqual({ tipo: 'ruta', nombre: 'login', parametros: {} })
  })

  it('muestra la vista inicial al iniciar y deja de escuchar popstate al desuscribirse', () => {
    const { historial, simularPopstate } = historialFalso('/agenda')
    const router = crearRouter(historial)
    const vistas = vistasDe(router)

    const desuscribir = router.iniciar()
    expect(vistas.at(-1)).toEqual({ tipo: 'ruta', nombre: 'agenda', parametros: {} })

    desuscribir()
    simularPopstate('/config')
    expect(vistas).toHaveLength(1)
  })

  it('rechaza una URL absoluta sin tocar la barra de direcciones', () => {
    const { historial, push, replace } = historialFalso()
    const router = crearRouter(historial)

    expect(() => router.ir('https://sitio-externo.com/agenda')).toThrow('relativas al origen')
    expect(push).toEqual([])
    expect(replace).toEqual([])
  })
})
