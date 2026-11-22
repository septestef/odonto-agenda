import type { ManejadoresUI, NodoUI } from '../../shared/ui/adaptador'
import type { Descriptor } from '../../shared/ui/componentes'
import type { ApiConfig } from './api-config'
import { crearVistaBloqueos } from './vistas/bloqueos'
import { crearVistaHorarios } from './vistas/horarios'
import { crearVistaPrestaciones } from './vistas/prestaciones'

export type SeccionConfig = 'horarios' | 'prestaciones' | 'bloqueos'

interface OpcionSeccion {
  readonly id: SeccionConfig
  readonly texto: string
  readonly href: string
}

const SECCIONES: readonly OpcionSeccion[] = [
  { id: 'horarios', texto: 'Horarios', href: '#horarios' },
  { id: 'prestaciones', texto: 'Prestaciones', href: '#prestaciones' },
  { id: 'bloqueos', texto: 'Bloqueos', href: '#bloqueos' },
]

function esSeccion(valor: string | undefined): valor is SeccionConfig {
  return valor === 'horarios' || valor === 'prestaciones' || valor === 'bloqueos'
}

export interface OpcionesConfig {
  readonly api: ApiConfig
  readonly alCambiar?: () => void
}

export interface ControlConfig {
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
  seccionActiva(): SeccionConfig
  seleccionar(seccion: SeccionConfig): void
  cargar(): Promise<void>
}

/** Compone las tres vistas y coordina qué sección se muestra dentro del layout. */
export function crearConfig(opciones: OpcionesConfig): ControlConfig {
  let seccion: SeccionConfig = 'horarios'

  function notificar(): void {
    opciones.alCambiar?.()
  }

  const horarios = crearVistaHorarios({ api: opciones.api, alCambiar: notificar })
  const prestaciones = crearVistaPrestaciones({ api: opciones.api, alCambiar: notificar })
  const bloqueos = crearVistaBloqueos({ api: opciones.api, alCambiar: notificar })

  function vistaActiva(): Descriptor {
    if (seccion === 'prestaciones') {
      return prestaciones.descriptor()
    }
    if (seccion === 'bloqueos') {
      return bloqueos.descriptor()
    }
    return horarios.descriptor()
  }

  const control: ControlConfig = {
    seccionActiva: () => seccion,

    seleccionar(nueva) {
      seccion = nueva
      notificar()
    },

    descriptor(): Descriptor {
      const resultado: Descriptor = {
        etiqueta: 'div',
        clase: 'config',
        hijos: [
          {
            etiqueta: 'div',
            clase: 'page-head',
            hijos: [
              { etiqueta: 'h1', texto: 'Configuración' },
              { etiqueta: 'p', texto: 'Horarios, prestaciones y bloqueos de tu agenda.' },
            ],
          },
          {
            etiqueta: 'nav',
            clase: 'tabs',
            atributos: { 'aria-label': 'Secciones de configuración' },
            hijos: SECCIONES.map((opcion) => ({
              etiqueta: 'a' as const,
              texto: opcion.texto,
              clase: 'tab',
              atributos: {
                href: opcion.href,
                'data-seccion': opcion.id,
                ...(opcion.id === seccion ? { 'aria-current': 'page' } : {}),
              },
              accion: 'seleccionar-seccion',
            })),
          },
          {
            etiqueta: 'section',
            clase: 'config__seccion',
            atributos: { id: seccion, tabindex: '-1' },
            hijos: [vistaActiva()],
          },
        ],
      }
      return resultado
    },

    manejadores() {
      const consultable = (nodo: NodoUI): string | undefined => {
        const nodoConAtributos = nodo as Partial<{ getAttribute(nombre: string): string | null }>
        return nodoConAtributos.getAttribute?.('data-seccion') ?? undefined
      }

      return {
        ...horarios.manejadores(),
        ...prestaciones.manejadores(),
        ...bloqueos.manejadores(),
        'seleccionar-seccion'(nodo: NodoUI) {
          const valor = consultable(nodo)
          if (esSeccion(valor)) {
            control.seleccionar(valor)
          }
        },
      }
    },

    async cargar() {
      await Promise.all([horarios.cargar(), prestaciones.cargar(), bloqueos.cargar()])
    },
  }

  return control
}
