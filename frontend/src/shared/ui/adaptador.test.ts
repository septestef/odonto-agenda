import { describe, expect, it, vi } from 'vitest'
import { crearAdaptadorNodos } from './adaptador'
import type { EventoUI, NodoUI } from './adaptador'
import type { Descriptor } from './componentes'

class NodoFalso implements NodoUI {
  readonly hijos: NodoUI[] = []
  readonly atributos: Record<string, string> = {}
  readonly escuchas: Record<string, Array<(evento?: EventoUI) => void>> = {}
  readonly focus = vi.fn()
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

  addEventListener(tipo: string, callback: (evento?: EventoUI) => void): void {
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

  it('previene el submit nativo del formulario y ejecuta el manejador', () => {
    const { documento } = documentoFalso()
    const destino = new NodoFalso('main')
    const manejador = vi.fn()
    const preventDefault = vi.fn()

    crearAdaptadorNodos(documento).montar(
      destino,
      { etiqueta: 'form', accion: 'ingresar', atributos: { 'data-campo': 'email' } },
      { ingresar: manejador },
    )
    const formulario = destino.hijos[0]
    if (!(formulario instanceof NodoFalso)) {
      throw new Error('El adaptador no creó un nodo falso')
    }
    for (const callback of formulario.escuchas['submit'] ?? []) {
      callback({ preventDefault })
    }

    expect(preventDefault).toHaveBeenCalledTimes(1)
    expect(manejador).toHaveBeenCalledTimes(1)
    expect(manejador).toHaveBeenCalledWith(formulario)
  })

  it('conserva los dos caminos de ingreso del formulario de login', () => {
    const { documento, creados } = documentoFalso()
    const destino = new NodoFalso('main')
    const manejador = vi.fn()
    const preventDefaultSubmit = vi.fn()
    const preventDefaultBoton = vi.fn()
    const descriptor: Descriptor = {
      etiqueta: 'form',
      accion: 'ingresar',
      hijos: [
        { etiqueta: 'input', atributos: { type: 'email', name: 'email' } },
        { etiqueta: 'input', atributos: { type: 'password', name: 'password' } },
        { etiqueta: 'button', texto: 'Ingresar', accion: 'ingresar', atributos: { type: 'submit' } },
      ],
    }

    crearAdaptadorNodos(documento).montar(destino, descriptor, { ingresar: manejador })
    const formulario = creados[0]
    const boton = creados[3]
    if (!(formulario instanceof NodoFalso) || !(boton instanceof NodoFalso)) {
      throw new Error('El adaptador no creó el formulario y el botón')
    }
    for (const callback of formulario.escuchas['submit'] ?? []) {
      callback({ preventDefault: preventDefaultSubmit })
    }
    for (const callback of boton.escuchas['click'] ?? []) {
      callback({ preventDefault: preventDefaultBoton })
    }

    expect(preventDefaultSubmit).toHaveBeenCalledTimes(1)
    expect(preventDefaultBoton).not.toHaveBeenCalled()
    expect(manejador).toHaveBeenCalledTimes(2)
    expect(manejador).toHaveBeenNthCalledWith(1, formulario)
    expect(manejador).toHaveBeenNthCalledWith(2, boton)
  })

  it('no previene el evento en los botones con acción click', () => {
    const { documento } = documentoFalso()
    const destino = new NodoFalso('main')
    const manejador = vi.fn()
    const preventDefault = vi.fn()

    crearAdaptadorNodos(documento).montar(
      destino,
      { etiqueta: 'button', texto: 'Ingresar', accion: 'ingresar', atributos: { type: 'submit' } },
      { ingresar: manejador },
    )
    const boton = destino.hijos[0]
    if (!(boton instanceof NodoFalso)) {
      throw new Error('El adaptador no creó un nodo falso')
    }
    for (const callback of boton.escuchas['click'] ?? []) {
      callback({ preventDefault })
    }

    expect(preventDefault).not.toHaveBeenCalled()
    expect(manejador).toHaveBeenCalledTimes(1)
  })

  it('ejecuta el manejador del submit aunque el evento no esté presente', () => {
    const { documento } = documentoFalso()
    const destino = new NodoFalso('main')
    const manejador = vi.fn()

    crearAdaptadorNodos(documento).montar(destino, { etiqueta: 'form', accion: 'ingresar' }, { ingresar: manejador })
    const formulario = destino.hijos[0]
    if (!(formulario instanceof NodoFalso)) {
      throw new Error('El adaptador no creó un nodo falso')
    }
    for (const callback of formulario.escuchas['submit'] ?? []) {
      callback()
    }

    expect(manejador).toHaveBeenCalledTimes(1)
    expect(manejador).toHaveBeenCalledWith(formulario)
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

  it('mueve el foco al nodo marcado después del montaje', async () => {
    const { documento, creados } = documentoFalso()
    const destino = new NodoFalso('main')

    crearAdaptadorNodos(documento).montar(destino, {
      etiqueta: 'h1',
      texto: 'Confirmá tu turno',
      enfocar: true,
    })
    await Promise.resolve()

    expect(creados[0]?.focus).toHaveBeenCalledTimes(1)
  })

  it('rechaza un destino que no sea un nodo', () => {
    const { documento } = documentoFalso()

    expect(() => crearAdaptadorNodos(documento).montar(null, { etiqueta: 'p', texto: 'Hola' })).toThrow(
      'debe ser un nodo',
    )
  })

  it('dispara la acción de una tecla con keydown y previene el default solo si hay manejador', () => {
    const { creados, documento } = documentoFalso()
    const cerrar = vi.fn()
    const destino = new NodoFalso('main')

    crearAdaptadorNodos(documento).montar(
      destino,
      { etiqueta: 'div', teclas: { Escape: 'cerrar' } },
      { cerrar },
    )

    const nodo = creados[0]
    const escucha = nodo?.escuchas['keydown']?.[0]
    const conManejador = { preventDefault: vi.fn(), key: 'Escape' }
    const otraTecla = { preventDefault: vi.fn(), key: 'Enter' }
    escucha?.(conManejador)
    escucha?.(otraTecla)

    expect(cerrar).toHaveBeenCalledTimes(1)
    expect(cerrar).toHaveBeenCalledWith(nodo)
    expect(conManejador.preventDefault).toHaveBeenCalled()
    expect(otraTecla.preventDefault).not.toHaveBeenCalled()
  })
})
