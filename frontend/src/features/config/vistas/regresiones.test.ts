import { describe, expect, it } from 'vitest'
import { crearAdaptadorNodos } from '../../../shared/ui/adaptador'
import type { DocumentoUI, EventoUI, ManejadoresUI, NodoUI } from '../../../shared/ui/adaptador'
import type { Descriptor } from '../../../shared/ui/componentes'
import type { ApiConfig, DatosBloqueo, DatosPrestacion } from '../api-config'
import type { Bloqueo, Franja, Prestacion } from '../modelos'
import { crearVistaBloqueos } from './bloqueos'
import { crearVistaHorarios } from './horarios'
import { crearVistaPrestaciones } from './prestaciones'

/**
 * DOM falso mínimo que se comporta como el navegador en lo que importa acá:
 * un clic en un botón `submit` corre sus escuchas de click y después el `submit` del formulario.
 */
class Nodo implements NodoUI {
  textContent: string | null = null
  readonly atributos = new Map<string, string>()
  readonly hijos: Nodo[] = []
  readonly escuchas: Record<string, ((evento?: EventoUI) => void)[]> = {}
  padre: Nodo | undefined
  private valorActual: string | undefined

  readonly etiqueta: string

  constructor(etiqueta: string) {
    this.etiqueta = etiqueta
  }

  get value(): string {
    return this.valorActual ?? this.atributos.get('value') ?? ''
  }

  set value(nuevo: string) {
    this.valorActual = nuevo
  }

  append(...nodos: NodoUI[]): void {
    for (const nodo of nodos) {
      if (nodo instanceof Nodo) {
        nodo.padre = this
        this.hijos.push(nodo)
      }
    }
  }

  setAttribute(nombre: string, valor: string): void {
    this.atributos.set(nombre, valor)
  }

  getAttribute(nombre: string): string | null {
    return this.atributos.get(nombre) ?? null
  }

  addEventListener(tipo: string, callback: (evento?: EventoUI) => void): void {
    ;(this.escuchas[tipo] ??= []).push(callback)
  }

  closest(selector: string): Nodo | null {
    if (this.etiqueta === selector) {
      return this
    }
    return this.padre?.closest(selector) ?? null
  }

  descendientes(): Nodo[] {
    return this.hijos.flatMap((hijo) => [hijo, ...hijo.descendientes()])
  }

  querySelectorAll(selector: string): Nodo[] {
    if (selector !== '[data-campo]') {
      throw new Error(`Selector no soportado en el DOM falso: ${selector}`)
    }
    return this.descendientes().filter((nodo) => nodo.atributos.has('data-campo'))
  }
}

const documento: DocumentoUI = { createElement: (etiqueta) => new Nodo(etiqueta) }

function clic(nodo: Nodo): void {
  for (const escucha of nodo.escuchas['click'] ?? []) {
    escucha({ preventDefault: () => undefined })
  }
  const esSubmit = nodo.etiqueta === 'button' && (nodo.getAttribute('type') ?? 'submit') === 'submit'
  const formulario = esSubmit ? nodo.closest('form') : null
  if (formulario !== null) {
    for (const escucha of formulario.escuchas['submit'] ?? []) {
      escucha({ preventDefault: () => undefined })
    }
  }
}

interface Vista {
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
}

/** Monta la vista y la vuelve a montar en cada cambio, como hace `main.ts`. */
function montable(): { raiz: Nodo; alCambiar: () => void; montar: (vista: Vista) => void } {
  const raiz = new Nodo('main')
  const adaptador = crearAdaptadorNodos(documento)
  let actual: Vista | undefined
  const montar = (vista: Vista): void => {
    actual = vista
    raiz.hijos.length = 0
    adaptador.montar(raiz, vista.descriptor(), vista.manejadores())
  }
  return {
    raiz,
    montar,
    alCambiar: () => {
      if (actual !== undefined) {
        montar(actual)
      }
    },
  }
}

function campo(raiz: Nodo, nombre: string): Nodo {
  const encontrado = raiz.descendientes().find((nodo) => nodo.getAttribute('data-campo') === nombre)
  if (encontrado === undefined) {
    throw new Error(`No se encontró el campo ${nombre}`)
  }
  return encontrado
}

function botonConTexto(raiz: Nodo, texto: string, posicion = 0): Nodo {
  const botones = raiz.descendientes().filter((nodo) => nodo.etiqueta === 'button' && nodo.textContent === texto)
  const boton = botones[posicion]
  if (boton === undefined) {
    throw new Error(`No se encontró el botón "${texto}" #${posicion}`)
  }
  return boton
}

async function esperar(): Promise<void> {
  await new Promise((resolver) => setTimeout(resolver, 0))
}

interface Pedido {
  readonly metodo: string
  readonly url: string
  readonly cuerpo?: unknown
}

function apiFalsa(inicial: {
  franjas?: readonly Franja[]
  prestaciones?: readonly Prestacion[]
  bloqueos?: readonly Bloqueo[]
  pedidos: Pedido[]
}): ApiConfig {
  const pedidos = inicial.pedidos
  return {
    async horarios() {
      return { franjas: inicial.franjas ?? [] }
    },
    async guardarHorario(franjas) {
      pedidos.push({ metodo: 'PUT', url: '/api/horarios', cuerpo: franjas })
      return { franjas }
    },
    async prestaciones() {
      return inicial.prestaciones ?? []
    },
    async crearPrestacion(datos: DatosPrestacion) {
      pedidos.push({ metodo: 'POST', url: '/api/prestaciones', cuerpo: datos })
      return { id: 99, ...datos, activa: true }
    },
    async editarPrestacion(id: number, datos: DatosPrestacion) {
      pedidos.push({ metodo: 'PUT', url: `/api/prestaciones/${id}`, cuerpo: datos })
      return { id, ...datos, activa: true }
    },
    async bajaPrestacion(id: number) {
      pedidos.push({ metodo: 'DELETE', url: `/api/prestaciones/${id}` })
      await esperar()
    },
    async bloqueos() {
      return inicial.bloqueos ?? []
    },
    async crearBloqueo(datos: DatosBloqueo) {
      pedidos.push({ metodo: 'POST', url: '/api/bloqueos', cuerpo: datos })
      return { id: 50, inicio: datos.inicio, fin: datos.fin, turnosAfectados: [] }
    },
    async bajaBloqueo(id: number) {
      pedidos.push({ metodo: 'DELETE', url: `/api/bloqueos/${id}` })
      await esperar()
    },
  }
}

describe('prestaciones desde el formulario real', () => {
  it('crea con lo que tipeó el usuario y manda un solo POST', async () => {
    const pedidos: Pedido[] = []
    const dom = montable()
    const vista = crearVistaPrestaciones({ api: apiFalsa({ pedidos }), alCambiar: dom.alCambiar })
    await vista.cargar()
    dom.montar(vista)

    campo(dom.raiz, 'nombre').value = 'Limpieza'
    campo(dom.raiz, 'duracionMinutos').value = '30'
    clic(botonConTexto(dom.raiz, 'Guardar prestación'))
    await esperar()

    expect(pedidos).toEqual([
      { metodo: 'POST', url: '/api/prestaciones', cuerpo: { nombre: 'Limpieza', duracionMinutos: 30 } },
    ])
    expect(vista.estado().tipo).toBe('listo')
  })

  it('al editar manda PUT sobre la prestación elegida, no un alta nueva', async () => {
    const pedidos: Pedido[] = []
    const dom = montable()
    const vista = crearVistaPrestaciones({
      api: apiFalsa({ pedidos, prestaciones: [{ id: 7, nombre: 'Consulta', duracionMinutos: 30, activa: true }] }),
      alCambiar: dom.alCambiar,
    })
    await vista.cargar()
    dom.montar(vista)

    clic(botonConTexto(dom.raiz, 'Editar'))
    expect(campo(dom.raiz, 'nombre').value).toBe('Consulta')
    campo(dom.raiz, 'duracionMinutos').value = '45'
    clic(botonConTexto(dom.raiz, 'Guardar prestación'))
    await esperar()

    expect(pedidos).toEqual([
      { metodo: 'PUT', url: '/api/prestaciones/7', cuerpo: { nombre: 'Consulta', duracionMinutos: 45 } },
    ])
  })

  it('un doble clic en "Dar de baja" manda un solo DELETE', async () => {
    const pedidos: Pedido[] = []
    const vista = crearVistaPrestaciones({
      api: apiFalsa({ pedidos, prestaciones: [{ id: 7, nombre: 'Consulta', duracionMinutos: 30, activa: true }] }),
    })
    await vista.cargar()
    vista.pedirBaja(7)

    await Promise.all([vista.confirmarBaja(), vista.confirmarBaja()])

    expect(pedidos.filter((pedido) => pedido.metodo === 'DELETE')).toHaveLength(1)
  })
})

describe('bloqueos desde el formulario real', () => {
  const reloj = (): Date => new Date('2026-12-01T15:00:00Z')

  it('propone la fecha de hoy en Argentina', async () => {
    const dom = montable()
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos: [] }), alCambiar: dom.alCambiar, reloj })
    await vista.cargar()
    dom.montar(vista)

    expect(campo(dom.raiz, 'fechaInicio').value).toBe('2026-12-01')
    expect(campo(dom.raiz, 'fechaFin').value).toBe('2026-12-01')
  })

  it('crea un solo bloqueo con el rango que cargó el usuario', async () => {
    const pedidos: Pedido[] = []
    const dom = montable()
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos }), alCambiar: dom.alCambiar, reloj })
    await vista.cargar()
    dom.montar(vista)

    campo(dom.raiz, 'fechaInicio').value = '2026-12-03'
    campo(dom.raiz, 'horaInicio').value = '15:00'
    campo(dom.raiz, 'fechaFin').value = '2026-12-03'
    campo(dom.raiz, 'horaFin').value = '17:00'
    clic(botonConTexto(dom.raiz, 'Agregar bloqueo'))
    await esperar()

    expect(pedidos).toEqual([
      {
        metodo: 'POST',
        url: '/api/bloqueos',
        cuerpo: { inicio: '2026-12-03T15:00:00-03:00', fin: '2026-12-03T17:00:00-03:00' },
      },
    ])
  })

  it('tras un error de validación conserva lo tipeado en vez de volver a los valores por defecto', async () => {
    const pedidos: Pedido[] = []
    const dom = montable()
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos }), alCambiar: dom.alCambiar, reloj })
    await vista.cargar()
    dom.montar(vista)

    campo(dom.raiz, 'fechaInicio').value = '2026-12-03'
    campo(dom.raiz, 'horaInicio').value = '18:00'
    campo(dom.raiz, 'fechaFin').value = '2026-12-03'
    campo(dom.raiz, 'horaFin').value = '17:00'
    clic(botonConTexto(dom.raiz, 'Agregar bloqueo'))
    await esperar()

    expect(pedidos).toEqual([])
    expect(campo(dom.raiz, 'fechaInicio').value).toBe('2026-12-03')
    expect(campo(dom.raiz, 'horaInicio').value).toBe('18:00')
    expect(campo(dom.raiz, 'horaFin').getAttribute('aria-invalid')).toBe('true')
  })

  it('marca y enfoca el inicio cuando falta la fecha de inicio', async () => {
    const dom = montable()
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos: [] }), alCambiar: dom.alCambiar, reloj })
    await vista.cargar()
    dom.montar(vista)

    campo(dom.raiz, 'fechaInicio').value = ''
    clic(botonConTexto(dom.raiz, 'Agregar bloqueo'))
    await esperar()

    const horaInicio = campo(dom.raiz, 'horaInicio')
    expect(horaInicio.getAttribute('aria-invalid')).toBe('true')
    expect(horaInicio.getAttribute('autofocus')).toBe('')
    const textos = dom.raiz.descendientes().map((nodo) => nodo.textContent)
    expect(textos).toContain('Completá la fecha y la hora de inicio.')
  })

  it('muestra las dos fechas cuando el bloqueo abarca varios días', async () => {
    const dom = montable()
    const vista = crearVistaBloqueos({
      api: apiFalsa({
        pedidos: [],
        bloqueos: [
          { id: 1, inicio: '2026-11-27T10:00:00-03:00', fin: '2026-12-05T12:00:00-03:00', turnosAfectados: [] },
        ],
      }),
      alCambiar: dom.alCambiar,
      reloj,
    })
    await vista.cargar()
    dom.montar(vista)

    const textos = dom.raiz.descendientes().map((nodo) => nodo.textContent)
    expect(textos).toContain('27/11/2026 10:00 – 05/12/2026 12:00')
  })

  it('un doble clic en "Quitar bloqueo" manda un solo DELETE', async () => {
    const pedidos: Pedido[] = []
    const vista = crearVistaBloqueos({
      api: apiFalsa({
        pedidos,
        bloqueos: [{ id: 1, inicio: '2026-11-27T10:00:00-03:00', fin: '2026-11-27T12:00:00-03:00', turnosAfectados: [] }],
      }),
      reloj,
    })
    await vista.cargar()

    await Promise.all([vista.baja(1), vista.baja(1)])

    expect(pedidos).toEqual([{ metodo: 'DELETE', url: '/api/bloqueos/1' }])
  })
})

describe('horarios desde el formulario real', () => {
  const franja = (diaSemana: number, inicio: string, fin: string): Franja => ({ diaSemana, inicio, fin })

  it('guarda con un solo PUT y con lo que tipeó el usuario', async () => {
    const pedidos: Pedido[] = []
    const dom = montable()
    const vista = crearVistaHorarios({
      api: apiFalsa({ pedidos, franjas: [franja(1, '09:00', '13:00')] }),
      alCambiar: dom.alCambiar,
    })
    await vista.cargar()
    dom.montar(vista)

    campo(dom.raiz, 'franjas[0].inicio').value = '08:00'
    clic(botonConTexto(dom.raiz, 'Guardar horario'))
    await esperar()

    expect(pedidos).toEqual([{ metodo: 'PUT', url: '/api/horarios', cuerpo: [franja(1, '08:00', '13:00')] }])
  })

  it('agregar una franja no pisa lo que el usuario venía editando', async () => {
    const dom = montable()
    const vista = crearVistaHorarios({
      api: apiFalsa({ pedidos: [], franjas: [franja(1, '09:00', '13:00')] }),
      alCambiar: dom.alCambiar,
    })
    await vista.cargar()
    dom.montar(vista)

    campo(dom.raiz, 'franjas[0].inicio').value = '08:00'
    clic(botonConTexto(dom.raiz, 'Agregar franja', 1))

    expect(campo(dom.raiz, 'franjas[0].inicio').value).toBe('08:00')
    expect(vista.estado().dias.find((dia) => dia.diaSemana === 2)?.franjas).toHaveLength(1)
  })

  it('"Quitar franja" del martes quita esa franja aunque el lunes tenga otras', async () => {
    const dom = montable()
    const vista = crearVistaHorarios({
      api: apiFalsa({
        pedidos: [],
        franjas: [franja(1, '09:00', '13:00'), franja(1, '14:00', '18:00'), franja(2, '10:00', '12:00')],
      }),
      alCambiar: dom.alCambiar,
    })
    await vista.cargar()
    dom.montar(vista)

    clic(botonConTexto(dom.raiz, 'Quitar franja', 2))

    const dias = vista.estado().dias
    expect(dias.find((dia) => dia.diaSemana === 1)?.franjas).toHaveLength(2)
    expect(dias.find((dia) => dia.diaSemana === 2)?.franjas).toEqual([])
  })

  it('marca como inválida la franja que tiene el error aunque se haya cargado fuera de orden', async () => {
    const dom = montable()
    const vista = crearVistaHorarios({
      api: apiFalsa({ pedidos: [], franjas: [franja(1, '14:00', '18:00')] }),
      alCambiar: dom.alCambiar,
    })
    await vista.cargar()
    dom.montar(vista)

    clic(botonConTexto(dom.raiz, 'Agregar franja', 0))
    campo(dom.raiz, 'franjas[1].inicio').value = '13:00'
    campo(dom.raiz, 'franjas[1].fin').value = '12:00'
    clic(botonConTexto(dom.raiz, 'Guardar horario'))
    await esperar()

    const invalidos = dom.raiz
      .descendientes()
      .filter((nodo) => nodo.etiqueta === 'input' && nodo.getAttribute('aria-invalid') === 'true')
      .map((nodo) => nodo.value)
    expect(invalidos).toEqual(['13:00', '12:00'])
  })
})
