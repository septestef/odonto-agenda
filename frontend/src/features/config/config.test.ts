import { describe, expect, it } from 'vitest'
import { crearConfig } from './config'
import type { ApiConfig } from './api-config'
import type { NodoUI } from '../../shared/ui/adaptador'
import type { Descriptor } from '../../shared/ui/componentes'

type SeccionFallida = 'horarios' | 'prestaciones' | 'bloqueos'

function errorDeRed(): Error {
  const mensaje = 'Revisá tu conexión e intentá de nuevo.'
  return Object.assign(new Error(mensaje), { codigo: 'SIN_CONEXION', mensaje })
}

function apiFalsa(opciones: { pedidos: string[]; fallar?: SeccionFallida }): ApiConfig {
  const { pedidos, fallar } = opciones
  function maybeFail(seccion: SeccionFallida): void {
    if (fallar === seccion) {
      throw errorDeRed()
    }
  }
  return {
    async horarios() {
      pedidos.push('GET /api/horarios')
      maybeFail('horarios')
      return { franjas: [{ diaSemana: 1, inicio: '09:00', fin: '13:00' }] }
    },
    async guardarHorario(franjas) {
      return { franjas }
    },
    async prestaciones() {
      pedidos.push('GET /api/prestaciones')
      maybeFail('prestaciones')
      return [{ id: 1, nombre: 'Consulta', duracionMinutos: 30, activa: true }]
    },
    async crearPrestacion() {
      throw new Error('no usado')
    },
    async editarPrestacion() {
      throw new Error('no usado')
    },
    async bajaPrestacion() {},
    async bloqueos() {
      pedidos.push('GET /api/bloqueos')
      maybeFail('bloqueos')
      return []
    },
    async crearBloqueo() {
      throw new Error('no usado')
    },
    async bajaBloqueo() {},
  }
}

function nodosDe(descriptor: Descriptor): Descriptor[] {
  return [descriptor, ...(descriptor.hijos ?? []).flatMap(nodosDe)]
}

function textos(descriptor: Descriptor): string[] {
  return nodosDe(descriptor)
    .map((nodo) => nodo.texto)
    .filter((texto): texto is string => texto !== undefined && texto.trim() !== '')
}

function acciones(descriptor: Descriptor): string[] {
  return nodosDe(descriptor)
    .map((nodo) => nodo.accion)
    .filter((accion): accion is string => accion !== undefined)
}

function activo(descriptor: Descriptor): Descriptor | undefined {
  return nodosDe(descriptor).find((nodo) => nodo.atributos?.['aria-current'] === 'page')
}

interface NodoConAtributos extends NodoUI {
  getAttribute(nombre: string): string | null
}

function pestaña(seccion: string): NodoConAtributos {
  return {
    textContent: null,
    append() {},
    setAttribute() {},
    addEventListener() {},
    getAttribute(nombre) {
      return nombre === 'data-seccion' ? seccion : null
    },
  }
}

function contenedor(descriptor: Descriptor): Descriptor | undefined {
  return nodosDe(descriptor).find((nodo) => nodo.clase === 'config__seccion')
}

describe('fachada de configuración', () => {
  it('muestra las tres secciones navegables con la activa marcada para lectores de pantalla', async () => {
    const vista = crearConfig({ api: apiFalsa({ pedidos: [] }) })
    await vista.cargar()

    const visibles = textos(vista.descriptor())
    expect(visibles).toContain('Configuración')
    expect(visibles).toContain('Horarios, prestaciones y bloqueos de tu agenda.')
    expect(visibles).toContain('Horarios')
    expect(visibles).toContain('Prestaciones')
    expect(visibles).toContain('Bloqueos')
    expect(vista.seccionActiva()).toBe('horarios')
    expect(activo(vista.descriptor())?.texto).toBe('Horarios')

    vista.seleccionar('bloqueos')

    expect(vista.seccionActiva()).toBe('bloqueos')
    expect(activo(vista.descriptor())?.texto).toBe('Bloqueos')
    expect(acciones(vista.descriptor())).toContain('seleccionar-seccion')
  })

  it('pide solo el endpoint de lectura de cada sección y muestra carga mientras espera', async () => {
    const pedidos: string[] = []
    const vista = crearConfig({ api: apiFalsa({ pedidos }) })

    const cargando = vista.cargar()
    expect(textos(vista.descriptor())).toContain('Cargando horarios…')
    await cargando

    expect(pedidos).toEqual(['GET /api/horarios', 'GET /api/prestaciones', 'GET /api/bloqueos'])
  })

  it('permite recorrer las pestañas con el teclado y mueve el foco al contenedor de la sección', async () => {
    const vista = crearConfig({ api: apiFalsa({ pedidos: [] }) })
    await vista.cargar()

    const pestañas = nodosDe(vista.descriptor()).filter((nodo) => nodo.accion === 'seleccionar-seccion')
    expect(pestañas).toHaveLength(3)
    for (const pestañaDescriptor of pestañas) {
      expect(pestañaDescriptor.etiqueta).toBe('a')
      expect(pestañaDescriptor.atributos?.['href']).toBe(`#${pestañaDescriptor.atributos?.['data-seccion']}`)
      expect(pestañaDescriptor.atributos?.['tabindex']).toBeUndefined()
    }
    expect(contenedor(vista.descriptor())).toMatchObject({
      atributos: { id: 'horarios', tabindex: '-1' },
    })

    vista.manejadores()['seleccionar-seccion']?.(pestaña('prestaciones'))

    expect(activo(vista.descriptor())?.texto).toBe('Prestaciones')
    expect(contenedor(vista.descriptor())).toMatchObject({
      atributos: { id: 'prestaciones', tabindex: '-1' },
    })
    expect(vista.seccionActiva()).toBe('prestaciones')
  })

  it('ignora activaciones sin sección conocida y conserva la pestaña activa', async () => {
    const vista = crearConfig({ api: apiFalsa({ pedidos: [] }) })
    await vista.cargar()

    vista.manejadores()['seleccionar-seccion']?.(pestaña('otra'))
    vista.manejadores()['seleccionar-seccion']?.(pestaña(''))

    expect(vista.seccionActiva()).toBe('horarios')
    expect(activo(vista.descriptor())?.texto).toBe('Horarios')
  })

  it('muestra reintento en la sección fallida sin descartar los datos de las otras', async () => {
    const vista = crearConfig({ api: apiFalsa({ pedidos: [], fallar: 'prestaciones' }) })
    await vista.cargar()

    vista.seleccionar('prestaciones')
    expect(textos(vista.descriptor())).toContain('Revisá tu conexión e intentá de nuevo.')
    expect(acciones(vista.descriptor())).toContain('reintentar-prestaciones')

    vista.seleccionar('horarios')
    expect(textos(vista.descriptor())).toContain('Horario semanal')
    expect(textos(vista.descriptor())).toContain('09:00–13:00')

    vista.seleccionar('bloqueos')
    expect(textos(vista.descriptor())).toContain('Tus bloqueos')
    expect(activo(vista.descriptor())?.texto).toBe('Bloqueos')
  })
})
