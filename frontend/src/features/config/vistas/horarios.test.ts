import { describe, expect, it } from 'vitest'
import { crearVistaHorarios } from './horarios'
import type { ApiConfig } from '../api-config'
import type { DiaSemana, Franja, Horario } from '../modelos'
import type { Descriptor } from '../../../shared/ui/componentes'

interface ErrorApiFalso extends Error {
  codigo: string
  mensaje: string
  detalles?: unknown
}

function errorApi(codigo: string, mensaje: string, detalles?: unknown): ErrorApiFalso {
  return Object.assign(new Error(mensaje), { codigo, mensaje, ...(detalles === undefined ? {} : { detalles }) })
}

function apiFalsa(opciones: {
  horario?: Horario
  alGuardar?: (franjas: readonly Franja[]) => Promise<Horario> | Horario
} = {}): ApiConfig {
  const horario = opciones.horario ?? { franjas: [] }
  return {
    async horarios() {
      return horario
    },
    async guardarHorario(franjas) {
      return opciones.alGuardar === undefined ? { franjas } : opciones.alGuardar(franjas)
    },
    async prestaciones() {
      return []
    },
    async crearPrestacion() {
      throw new Error('no usado')
    },
    async editarPrestacion() {
      throw new Error('no usado')
    },
    async bajaPrestacion() {},
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

function atributosPorCampo(descriptor: Descriptor): Readonly<Record<string, Readonly<Record<string, string>>>> {
  const encontrados: Record<string, Record<string, string>> = {}
  function recorrer(nodo: Descriptor): void {
    const campo = nodo.atributos?.['data-campo']
    if (campo !== undefined && nodo.atributos !== undefined) {
      encontrados[campo] = { ...nodo.atributos }
    }
    for (const hijo of nodo.hijos ?? []) {
      recorrer(hijo)
    }
  }
  recorrer(descriptor)
  return encontrados
}

const HORARIO_INICIAL: Horario = {
  franjas: [
    { diaSemana: 1, inicio: '09:00', fin: '13:00' },
    { diaSemana: 1, inicio: '16:00', fin: '20:00' },
  ],
}

describe('vista de horarios', () => {
  it('carga dos franjas el lunes y deja el domingo sin atención', async () => {
    const vista = crearVistaHorarios({ api: apiFalsa({ horario: HORARIO_INICIAL }) })

    await vista.cargar()

    const visibles = textos(vista.descriptor())
    expect(visibles).toContain('Horario semanal')
    expect(visibles).toContain('Lunes')
    expect(visibles).toContain('Domingo')
    expect(visibles).toContain('09:00–13:00')
    expect(visibles).toContain('16:00–20:00')
    expect(visibles.filter((texto) => texto === 'Sin atención')).toHaveLength(6)
    expect(visibles).toContain('Guardar horario')
  })

  it('respeta los textos visibles y el orden de bloques de los mockups aprobados', async () => {
    const vista = crearVistaHorarios({ api: apiFalsa({ horario: HORARIO_INICIAL }) })
    await vista.cargar()

    const visibles = textos(vista.descriptor())
    const esperados = [
      'Horario semanal',
      'Definí las franjas en las que atendés. Todo se muestra en hora de Argentina.',
      'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo',
      'Sin atención', 'Agregar franja', 'Guardar horario', 'Descartar cambios',
    ]
    for (const texto of esperados) {
      expect(visibles).toContain(texto)
    }
    expect(visibles.indexOf('Lunes')).toBeLessThan(visibles.indexOf('Martes'))
    expect(visibles.indexOf('Sábado')).toBeLessThan(visibles.indexOf('Domingo'))
  })

  it('guarda el horario completo con un único PUT y muestra confirmación', async () => {
    const pedidos: Array<readonly Franja[]> = []
    const vista = crearVistaHorarios({
      api: apiFalsa({
        horario: HORARIO_INICIAL,
        alGuardar: async (franjas) => {
          pedidos.push(franjas)
          return { franjas }
        },
      }),
    })
    await vista.cargar()
    const estado = vista.estado()

    await vista.guardar(estado.dias)

    expect(pedidos).toEqual([HORARIO_INICIAL.franjas])
    expect(textos(vista.descriptor())).toContain('Cambios guardados')
  })

  it('rechaza localmente las franjas solapadas del mockup sin emitir PUT', async () => {
    let pedidos = 0
    const vista = crearVistaHorarios({
      api: apiFalsa({
        alGuardar: async (franjas) => {
          pedidos += 1
          return { franjas }
        },
      }),
    })
    const borrador: readonly DiaSemana[] = [
      { diaSemana: 1, nombre: 'Lunes', franjas: [
        { diaSemana: 1, inicio: '09:00', fin: '13:00' },
        { diaSemana: 1, inicio: '16:00', fin: '20:00' },
      ] },
      { diaSemana: 3, nombre: 'Miércoles', franjas: [
        { diaSemana: 3, inicio: '09:00', fin: '11:00' },
        { diaSemana: 3, inicio: '10:00', fin: '12:00' },
      ] },
    ]

    await vista.guardar(borrador)

    const descriptor = vista.descriptor()
    const campos = atributosPorCampo(descriptor)
    expect(pedidos).toBe(0)
    expect(textos(descriptor)).toContain('Las franjas del mismo día no pueden solaparse')
    expect(textos(descriptor).filter((texto) => texto === 'Se superpone con la otra franja.')).toHaveLength(2)
    expect(campos['franjas[2].inicio']?.['aria-invalid']).toBe('true')
    expect(campos['franjas[3].inicio']?.['aria-invalid']).toBe('true')
    expect(campos['franjas[2].inicio']).toHaveProperty('autofocus')
    expect(campos['franjas[3].inicio']).not.toHaveProperty('autofocus')
  })

  it('conserva el borrador y muestra el mensaje del servidor ante un 422', async () => {
    const borrador: readonly DiaSemana[] = [
      { diaSemana: 1, nombre: 'Lunes', franjas: [{ diaSemana: 1, inicio: '09:00', fin: '13:00' }] },
    ]
    const vista = crearVistaHorarios({
      api: apiFalsa({
        alGuardar: async () => {
          throw errorApi('RN-AG-01', 'Las franjas del mismo día no pueden solaparse', [
            { campo: 'franjas[0]', mensaje: 'La hora de inicio debe ser anterior a la de fin.' },
          ])
        },
      }),
    })

    await vista.guardar(borrador)

    const estado = vista.estado()
    expect(estado.tipo).toBe('error')
    if (estado.tipo === 'error') {
      expect(estado.dias).toEqual(borrador)
    }
    expect(textos(vista.descriptor())).toContain('Las franjas del mismo día no pueden solaparse')
    expect(atributosPorCampo(vista.descriptor())['franjas[0].inicio']?.['aria-invalid']).toBe('true')
  })
})
