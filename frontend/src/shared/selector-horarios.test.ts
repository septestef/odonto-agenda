import { describe, expect, it, vi } from 'vitest'
import { selectorHorarios } from './selector-horarios'
import type { NodoUI } from './ui/adaptador'

const HORARIOS = [
  { inicio: '2026-11-27T12:00:00Z', hora: '09:00' },
  { inicio: '2026-11-27T12:15:00Z', hora: '09:15' },
]

function nodoFalso(): NodoUI {
  return {
    textContent: '',
    append: () => {},
    setAttribute: () => {},
    addEventListener: () => {},
  }
}

describe('selector de horarios', () => {
  it('muestra el estado de carga sin manejadores', () => {
    const selector = selectorHorarios({
      horarios: [],
      onSeleccionar: () => {},
      vacio: 'Sin horarios',
      cargando: true,
    })

    expect(selector.descriptor.hijos?.[0]?.clase).toBe('slots')
    expect(selector.descriptor.hijos?.[0]?.atributos?.['aria-hidden']).toBe('true')
    expect(selector.descriptor.hijos?.[1]?.texto).toBe('Cargando horarios disponibles.')
    expect(selector.manejadores).toEqual({})
  })

  it('muestra el estado vacío parametrizable', () => {
    const selector = selectorHorarios({
      horarios: [],
      onSeleccionar: () => {},
      vacio: 'No hay horarios libres este día',
    })

    expect(selector.descriptor).toMatchObject({
      clase: 'empty',
      atributos: { role: 'status' },
      hijos: [{ etiqueta: 'p', texto: 'No hay horarios libres este día' }],
    })
    expect(selector.manejadores).toEqual({})
  })

  it('renderiza un chip por horario y marca el seleccionado con aria-pressed', () => {
    const selector = selectorHorarios({
      horarios: HORARIOS,
      seleccionado: HORARIOS[1],
      onSeleccionar: () => {},
      vacio: '',
    })

    expect(selector.descriptor.clase).toBe('slots')
    expect(selector.descriptor.hijos).toHaveLength(2)
    expect(selector.descriptor.hijos?.[0]).toMatchObject({
      etiqueta: 'button',
      texto: '09:00',
      clase: 'chip',
      atributos: { type: 'button', 'aria-pressed': 'false' },
    })
    expect(selector.descriptor.hijos?.[1]?.atributos?.['aria-pressed']).toBe('true')
  })

  it('avisa el horario correcto al activar cada chip', () => {
    const onSeleccionar = vi.fn()
    const selector = selectorHorarios({
      horarios: HORARIOS,
      onSeleccionar,
      vacio: '',
    })

    selector.manejadores['horario-0']?.(nodoFalso())
    selector.manejadores['horario-1']?.(nodoFalso())

    expect(onSeleccionar).toHaveBeenNthCalledWith(1, HORARIOS[0])
    expect(onSeleccionar).toHaveBeenNthCalledWith(2, HORARIOS[1])
  })

  it('no depende de slug, prestación ni fetch', () => {
    const selector = selectorHorarios({ horarios: HORARIOS, onSeleccionar: () => {}, vacio: '' })

    expect(Object.keys(selector.manejadores)).toEqual(['horario-0', 'horario-1'])
  })
})
