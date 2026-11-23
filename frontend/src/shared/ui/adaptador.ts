import type { Descriptor } from './componentes'

/** Parte del evento del DOM que necesita el adaptador; lo satisface un `Event` real. */
export interface EventoUI {
  preventDefault(): void
  /** Tecla de un `keydown`; ausente en los demás eventos. */
  key?: string
}

/** Contrato mínimo que satisfacen tanto los nodos reales como los falsos de los tests. */
export interface NodoUI {
  textContent: string | null
  value?: string
  append(...nodos: NodoUI[]): void
  setAttribute(nombre: string, valor: string): void
  addEventListener(tipo: string, callback: (evento?: EventoUI) => void): void
  focus?(): void
}

export interface DocumentoUI {
  createElement(etiqueta: string): NodoUI
}

export type ManejadoresUI = Readonly<Record<string, (nodo: NodoUI) => void>>

export interface AdaptadorNodos {
  montar(destino: unknown, descriptor: Descriptor, manejadores?: ManejadoresUI): void
}

function esNodo(valor: unknown): valor is NodoUI {
  if (typeof valor !== 'object' || valor === null) {
    return false
  }
  const candidato: Record<string, unknown> = valor as Record<string, unknown>

  return (
    'textContent' in candidato &&
    typeof candidato['append'] === 'function' &&
    typeof candidato['setAttribute'] === 'function' &&
    typeof candidato['addEventListener'] === 'function'
  )
}

export function crearAdaptadorNodos(documento: DocumentoUI): AdaptadorNodos {
  function aplicar(nodo: NodoUI, descriptor: Descriptor, manejadores: ManejadoresUI | undefined): void {
    if (descriptor.clase !== undefined) {
      nodo.setAttribute('class', descriptor.clase)
    }
    for (const [nombre, valor] of Object.entries(descriptor.atributos ?? {})) {
      nodo.setAttribute(nombre, valor)
    }
    if (descriptor.texto !== undefined) {
      nodo.textContent = descriptor.texto
    }
    if (descriptor.enfocar === true) {
      queueMicrotask(() => {
        nodo.focus?.()
      })
    }
    if (descriptor.accion !== undefined) {
      nodo.setAttribute('data-accion', descriptor.accion)
      const manejador = manejadores?.[descriptor.accion]
      if (manejador !== undefined) {
        const tipo =
          descriptor.etiqueta === 'form' ? 'submit' : descriptor.etiqueta === 'input' ? 'change' : 'click'
        nodo.addEventListener(tipo, (evento?: EventoUI) => {
          if (tipo === 'submit') {
            evento?.preventDefault()
          }
          manejador(nodo)
        })
      }
    }
    const teclas = descriptor.teclas
    if (teclas !== undefined) {
      nodo.addEventListener('keydown', (evento?: EventoUI) => {
        const accion = evento?.key === undefined ? undefined : teclas[evento.key]
        const manejador = accion === undefined ? undefined : manejadores?.[accion]
        if (manejador !== undefined) {
          evento?.preventDefault()
          manejador(nodo)
        }
      })
    }
  }

  function construir(descriptor: Descriptor, manejadores: ManejadoresUI | undefined): NodoUI {
    const nodo = documento.createElement(descriptor.etiqueta)
    aplicar(nodo, descriptor, manejadores)

    for (const hijo of descriptor.hijos ?? []) {
      nodo.append(construir(hijo, manejadores))
    }

    return nodo
  }

  return {
    montar(destino, descriptor, manejadores) {
      if (!esNodo(destino)) {
        throw new Error('El destino del adaptador debe ser un nodo')
      }
      destino.textContent = ''
      destino.append(construir(descriptor, manejadores))
    },
  }
}
