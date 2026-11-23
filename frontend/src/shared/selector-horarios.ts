import type { ManejadoresUI, NodoUI } from './ui/adaptador'
import type { Descriptor } from './ui/componentes'

export interface HorarioSeleccionable {
  inicio: string
  hora: string
}

export interface OpcionesSelectorHorarios {
  horarios: readonly HorarioSeleccionable[]
  seleccionado?: HorarioSeleccionable
  onSeleccionar: (horario: HorarioSeleccionable) => void
  vacio: string
  cargando?: boolean
}

export interface SelectorHorarios {
  descriptor: Descriptor
  manejadores: ManejadoresUI
}

function skeleton(): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'slots',
    atributos: { 'aria-hidden': 'true' },
    hijos: [1, 2, 3, 4, 5, 6].map(() => ({
      etiqueta: 'div',
      clase: 'skeleton skeleton--slot',
    })),
  }
}

function vacio(texto: string): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'empty',
    atributos: { role: 'status' },
    hijos: [{ etiqueta: 'p', texto }],
  }
}

/**
 * Selector de horarios reutilizable. No conoce slugs, prestaciones ni fetch:
 * solo recibe los horarios y avisa cuál se eligió.
 */
export function selectorHorarios(opciones: OpcionesSelectorHorarios): SelectorHorarios {
  if (opciones.cargando === true) {
    return {
      descriptor: {
        etiqueta: 'div',
        hijos: [
          skeleton(),
          { etiqueta: 'p', texto: 'Cargando horarios disponibles.', clase: 'sr-only', atributos: { 'aria-live': 'polite' } },
        ],
      },
      manejadores: {},
    }
  }

  if (opciones.horarios.length === 0) {
    return { descriptor: vacio(opciones.vacio), manejadores: {} }
  }

  const manejadores: Record<string, (nodo: NodoUI) => void> = {}
  const chips = opciones.horarios.map((horario, indice) => {
    const accion = `horario-${indice}`
    manejadores[accion] = () => opciones.onSeleccionar(horario)

    return {
      etiqueta: 'button' as const,
      texto: horario.hora,
      clase: 'chip',
      atributos: {
        type: 'button',
        'aria-pressed': String(horario.inicio === opciones.seleccionado?.inicio),
      },
      accion,
    }
  })

  return {
    descriptor: {
      etiqueta: 'div',
      clase: 'slots',
      atributos: { role: 'group', 'aria-label': 'Elegí un horario' },
      hijos: chips,
    },
    manejadores,
  }
}
