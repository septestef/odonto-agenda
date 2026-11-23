import { describe, expect, it } from 'vitest'
import { arrancar } from './main'
import type { HistorialNavegacion } from './router'
import type { FetchLike } from './shared/api-client'
import type { AdaptadorNodos, ManejadoresUI } from './shared/ui/adaptador'
import type { Descriptor } from './shared/ui/componentes'

const ODONTOLOGO = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Gómez',
  email: 'ana@consultorio.com',
  slug: 'ana-gomez',
}

function historialFalso(rutaInicial: string): HistorialNavegacion {
  let ruta = rutaInicial
  return {
    rutaActual: () => ruta,
    pushState: (url) => {
      ruta = url
    },
    replaceState: (url) => {
      ruta = url
    },
    suscribirPopstate: () => () => {},
  }
}

function adaptadorFalso() {
  const montajes: Array<{ descriptor: Descriptor; manejadores?: ManejadoresUI }> = []
  const adaptador: AdaptadorNodos = {
    montar(_destino, descriptor, manejadores) {
      montajes.push({ descriptor, manejadores })
    },
  }
  return { adaptador, ultimo: () => montajes.at(-1) }
}

function respuesta(cuerpo: unknown, status: number): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } })
}

function fetchConConfig(pedidos: string[]): FetchLike {
  return async (url, init) => {
    pedidos.push(`${init?.method ?? 'GET'} ${url}`)
    if (url === '/api/auth/me') {
      return respuesta(ODONTOLOGO, 200)
    }
    if (url === '/api/horarios') {
      return respuesta({ franjas: [{ diaSemana: 1, inicio: '09:00', fin: '13:00' }] }, 200)
    }
    if (url === '/api/prestaciones') {
      return respuesta({ prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30, activa: true }] }, 200)
    }
    if (url === '/api/bloqueos') {
      return respuesta({ bloqueos: [] }, 200)
    }
    return new Response(null, { status: 204 })
  }
}

async function estabilizar(): Promise<void> {
  for (let vuelta = 0; vuelta < 4; vuelta += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

function nodosDe(descriptor: Descriptor | undefined): Descriptor[] {
  if (descriptor === undefined) {
    return []
  }
  return [descriptor, ...(descriptor.hijos ?? []).flatMap(nodosDe)]
}

function textos(descriptor: Descriptor | undefined): string[] {
  return nodosDe(descriptor)
    .map((nodo) => nodo.texto)
    .filter((texto): texto is string => texto !== undefined && texto.trim() !== '')
}

describe('cableado de /config en el shell', () => {
  it('monta la configuración real con sus tres pedidos y sin el marcador', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/config'),
      fetch: fetchConConfig(pedidos),
    })
    await estabilizar()

    const visibles = textos(ultimo()?.descriptor)
    expect(visibles).toContain('Configuración')
    expect(visibles).toContain('Horario semanal')
    expect(visibles).toContain('Prestaciones')
    expect(visibles).toContain('Bloqueos')
    expect(visibles).not.toContain('Pantalla de marcador')
    expect(nodosDe(ultimo()?.descriptor).some((nodo) => nodo.clase === 'marcador')).toBe(false)
    expect(pedidos).toEqual([
      'GET /api/auth/me',
      'GET /api/horarios',
      'GET /api/prestaciones',
      'GET /api/bloqueos',
    ])
    detener()
  })

  it('marca Configuración como página actual y no marca Agenda', async () => {
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/config'),
      fetch: fetchConConfig([]),
    })
    await estabilizar()

    const enlaces = nodosDe(ultimo()?.descriptor).filter((nodo) => nodo.etiqueta === 'a')
    expect(enlaces.find((nodo) => nodo.texto === 'Configuración')?.atributos?.['aria-current']).toBe('page')
    expect(enlaces.find((nodo) => nodo.texto === 'Agenda')?.atributos?.['aria-current']).toBeUndefined()
    detener()
  })

  it('conserva el salto al contenido del shell y el destino con foco en /config', async () => {
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/config'),
      fetch: fetchConConfig([]),
    })
    await estabilizar()

    const raiz = ultimo()?.descriptor
    expect(raiz?.clase).toBe('layout')
    expect(nodosDe(raiz).find((nodo) => nodo.texto === 'Saltar al contenido')).toMatchObject({
      etiqueta: 'a',
      atributos: { href: '#contenido' },
    })
    expect(nodosDe(raiz).find((nodo) => nodo.etiqueta === 'main')).toMatchObject({
      atributos: { id: 'contenido', tabindex: '-1' },
    })
    detener()
  })

  it('monta la agenda (C-13) en /agenda sin cargar el control de configuración', async () => {
    const pedidos: string[] = []
    const { adaptador, ultimo } = adaptadorFalso()
    const detener = arrancar({}, adaptador, {
      historial: historialFalso('/agenda'),
      fetch: fetchConConfig(pedidos),
    })
    await estabilizar()

    expect(ultimo()?.descriptor.hijos?.[2]?.hijos?.[0]?.clase).toBe('agenda')
    expect(pedidos[0]).toBe('GET /api/auth/me')
    // La agenda lee horarios y bloqueos, pero nunca las prestaciones de /config.
    expect(pedidos).not.toContain('GET /api/prestaciones')
    detener()
  })
})
