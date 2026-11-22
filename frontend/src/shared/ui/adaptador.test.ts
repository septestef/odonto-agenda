import { describe, expect, it, vi } from 'vitest'
import { crearAdaptadorNodos } from './adaptador'
import type { NodoUI } from './adaptador'
import type { Descriptor } from './componentes'

class NodoFalso implements NodoUI {
  readonly hijos: NodoUI[] = []
  readonly atributos: Record<string, string> = {}
  readonly escuchas: Record<string, Array<() => void>> = {}
  private texto = ''
  readonly etiqueta: string

  constructor(etiqueta: string) {
    this.etiqueta = etiqueta
  }

  get textContent(): string | null {
    return this.texto
  }

  set textContent(valor: string | null) {
    this.texto = valor ?? ''
    this.hijos.length = 0
  }

  append(...nodos: NodoUI[]): void {
    this.hijos.push(...nodos)
  }

  setAttribute(nombre: string, valor: string): void {
    this.atributos[nombre] = valor
  }

  addEventListener(tipo: string, callback: () => void): void {
    const lista = this.escuchas[tipo]
    if (lista === undefined) {
      this.escuchas[tipo] = [callback]
      return
    }
    lista.push(callback)
  }
}

function documentoFalso() {
  const creados: NodoFalso[] = []

  return {
    creados,
    documento: {
      createElement(etiqueta: string): NodoUI {
        const nodo = new NodoFalso(etiqueta)
        creados.push(nodo)
        return nodo
      },
    },
  }
}

describe('adaptador a nodos con document falso', () => {
  it('convierte un descriptor a nodos usando textContent y atributos', () => {
    const { documento, creados } = documentoFalso()
    const destino = new NodoFalso('main')
    const descriptor: Descriptor = {
      etiqueta: 'button',
      texto: 'Cerrar sesión',
      clase: 'btn',
      atributos: { type: 'button', 'aria-label': 'Cerrar sesión' },
      accion: 'cerrar-sesion',
    }

    crearAdaptadorNodos(documento).montar(destino, descriptor)

    expect(destino.hijos).toHaveLength(1)
    expect(destino.hijos[0]?.textContent).toBe('Cerrar sesión')
    expect(creados[0]?.atributos).toMatchObject({
      class: 'btn',
      type: 'button',
      'aria-label': 'Cerrar sesión',
      'data-accion': 'cerrar-sesion',
    })
  })

  it('conecta las acciones declaradas con los manejadores inyectados', () => {
    const { documento } = documentoFalso()
    const destino = new NodoFalso('main')
    const manejador = vi.fn()

    crearAdaptadorNodos(documento).montar(
      destino,
      { etiqueta: 'button', texto: 'Ingresar', accion: 'ingresar' },
      { ingresar: manejador },
    )
    const boton = destino.hijos[0]
    if (!(boton instanceof NodoFalso)) {
      throw new Error('El adaptador no creó un nodo falso')
    }
    for (const callback of boton.escuchas['click'] ?? []) {
      callback()
    }

    expect(manejador).toHaveBeenCalledTimes(1)
  })

  it('inserta marcas HTML como texto literal y sin hijos', () => {
    const { documento } = documentoFalso()
    const destino = new NodoFalso('main')

    crearAdaptadorNodos(documento).montar(destino, {
      etiqueta: 'div',
      texto: '<script>algo</script>',
      clase: 'alerta',
    })

    const raiz = destino.hijos[0]
    if (!(raiz instanceof NodoFalso)) {
      throw new Error('El adaptador no creó un nodo falso')
    }
    expect(raiz.textContent).toBe('<script>algo</script>')
    expect(raiz.hijos).toHaveLength(0)
  })

  it('aplica atributos aria y conserva el árbol de hijos', () => {
    const { documento } = documentoFalso()
    const destino = new NodoFalso('main')

    crearAdaptadorNodos(documento).montar(destino, {
      etiqueta: 'div',
      atributos: { role: 'alert', 'aria-live': 'assertive' },
      hijos: [
        { etiqueta: 'strong', texto: 'Atención' },
        { etiqueta: 'span', texto: 'Revisá los datos.' },
      ],
    })

    const raiz = destino.hijos[0]
    if (!(raiz instanceof NodoFalso)) {
      throw new Error('El adaptador no creó un nodo falso')
    }
    expect(raiz.atributos).toMatchObject({ role: 'alert', 'aria-live': 'assertive' })
    expect(raiz.hijos.map((hijo) => hijo.textContent)).toEqual(['Atención', 'Revisá los datos.'])
  })

  it('rechaza un destino que no sea un nodo', () => {
    const { documento } = documentoFalso()

    expect(() => crearAdaptadorNodos(documento).montar(null, { etiqueta: 'p', texto: 'Hola' })).toThrow(
      'debe ser un nodo',
    )
  })
})
