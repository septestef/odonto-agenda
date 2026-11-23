import { describe, expect, it } from 'vitest'
import { crearVistaPrestaciones } from './prestaciones'
import type { ApiConfig, DatosPrestacion } from '../api-config'
import type { Prestacion } from '../modelos'
import type { Descriptor } from '../../../shared/ui/componentes'

interface PedidoPrestacion {
  metodo: string
  url: string
  cuerpo: unknown
}

function apiFalsa(opciones: {
  prestaciones?: readonly Prestacion[]
  pedidos: PedidoPrestacion[]
  crear?: (datos: DatosPrestacion) => Prestacion
  editar?: (id: number, datos: DatosPrestacion) => Prestacion
}): ApiConfig {
  let listado = [...(opciones.prestaciones ?? [])]
  return {
    async horarios() {
      return { franjas: [] }
    },
    async guardarHorario() {
      return { franjas: [] }
    },
    async prestaciones() {
      return listado
    },
    async crearPrestacion(datos) {
      opciones.pedidos.push({ metodo: 'POST', url: '/api/prestaciones', cuerpo: datos })
      const creada = opciones.crear === undefined
        ? { id: 3, nombre: datos.nombre, duracionMinutos: datos.duracionMinutos, activa: true }
        : opciones.crear(datos)
      listado = [...listado, creada]
      return creada
    },
    async editarPrestacion(id, datos) {
      opciones.pedidos.push({ metodo: 'PUT', url: `/api/prestaciones/${id}`, cuerpo: datos })
      const editada = opciones.editar === undefined
        ? { id, nombre: datos.nombre, duracionMinutos: datos.duracionMinutos, activa: true }
        : opciones.editar(id, datos)
      listado = listado.map((item) => (item.id === id ? editada : item))
      return editada
    },
    async bajaPrestacion(id) {
      opciones.pedidos.push({ metodo: 'DELETE', url: `/api/prestaciones/${id}`, cuerpo: undefined })
      listado = listado.map((item) => (item.id === id ? { ...item, activa: false } : item))
    },
    async bloqueos() {
      return []
    },
    async crearBloqueo() {
      throw new Error('no usado')
    },
    async bajaBloqueo() {},
  }
}

function textos(descriptor: Descriptor): string[] {
  const encontrados: string[] = []
  function recorrer(nodo: Descriptor): void {
    if (nodo.texto !== undefined && nodo.texto.trim() !== '') {
      encontrados.push(nodo.texto)
    }
    for (const hijo of nodo.hijos ?? []) {
      recorrer(hijo)
    }
  }
  recorrer(descriptor)
  return encontrados
}

function acciones(descriptor: Descriptor): string[] {
  const encontrados: string[] = []
  function recorrer(nodo: Descriptor): void {
    if (nodo.accion !== undefined) {
      encontrados.push(nodo.accion)
    }
    for (const hijo of nodo.hijos ?? []) {
      recorrer(hijo)
    }
  }
  recorrer(descriptor)
  return encontrados
}

describe('vista de prestaciones', () => {
  it('da de alta Limpieza de 45 minutos con un POST de solo nombre y duración', async () => {
    const pedidos: PedidoPrestacion[] = []
    const vista = crearVistaPrestaciones({ api: apiFalsa({ pedidos }) })

    await vista.guardar({ nombre: 'Limpieza', duracionMinutos: 45 })

    expect(pedidos).toEqual([{ metodo: 'POST', url: '/api/prestaciones', cuerpo: { nombre: 'Limpieza', duracionMinutos: 45 } }])
    expect(textos(vista.descriptor())).toContain('45 min · Activa')
  })

  it('avisa que los turnos existentes conservan su duración al editar', async () => {
    const pedidos: PedidoPrestacion[] = []
    const vista = crearVistaPrestaciones({
      api: apiFalsa({
        pedidos,
        prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30, activa: true }],
      }),
    })

    await vista.guardar({ nombre: 'Consulta', duracionMinutos: 45 }, 1)

    expect(pedidos[0]).toEqual({
      metodo: 'PUT',
      url: '/api/prestaciones/1',
      cuerpo: { nombre: 'Consulta', duracionMinutos: 45 },
    })
    const visibles = textos(vista.descriptor())
    expect(visibles).toContain('45 min · Activa')
    expect(visibles).toContain('Cambiar la duración no modifica los turnos ya reservados.')
    expect(visibles).toContain('Los turnos existentes conservan la duración con la que se reservaron.')
  })

  it('da de baja sólo después de confirmar y deja la fila visible como Dada de baja', async () => {
    const pedidos: PedidoPrestacion[] = []
    const vista = crearVistaPrestaciones({
      api: apiFalsa({
        pedidos,
        prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30, activa: true }],
      }),
    })
    await vista.cargar()

    vista.pedirBaja(1)
    expect(pedidos).toEqual([])
    expect(textos(vista.descriptor())).toContain('Volver')

    await vista.confirmarBaja()

    expect(pedidos).toEqual([{ metodo: 'DELETE', url: '/api/prestaciones/1', cuerpo: undefined }])
    expect(textos(vista.descriptor())).toContain('30 min · Dada de baja')
    expect(acciones(vista.descriptor())).not.toContain('reactivar')
  })

  it('rechaza localmente 20 minutos con RN-AG-03, sin pedido ni acción de reactivar', async () => {
    const pedidos: PedidoPrestacion[] = []
    const vista = crearVistaPrestaciones({ api: apiFalsa({ pedidos }) })

    await vista.guardar({ nombre: 'Control', duracionMinutos: 20 })

    const visibles = textos(vista.descriptor())
    expect(pedidos).toEqual([])
    expect(visibles).toContain('La duración tiene que ser un múltiplo de 15 minutos.')
    expect(visibles).toContain('Probá con 15, 30, 45 o 60 minutos.')
    expect(acciones(vista.descriptor())).not.toContain('reactivar')
  })

  it('no envía DELETE ni cambia el estado al volver del diálogo', async () => {
    const pedidos: PedidoPrestacion[] = []
    const vista = crearVistaPrestaciones({
      api: apiFalsa({
        pedidos,
        prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30, activa: true }],
      }),
    })
    await vista.cargar()

    vista.pedirBaja(1)
    vista.cancelarBaja()

    expect(pedidos).toEqual([])
    expect(textos(vista.descriptor())).toContain('30 min · Activa')
    expect(textos(vista.descriptor())).not.toContain('Dada de baja')
  })
})
