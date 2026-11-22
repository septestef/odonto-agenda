import type { Descriptor } from '../../../shared/ui/componentes'
import type { ErroresCampo } from '../modelos'

export type TipoAlerta = 'error' | 'aviso' | 'exito' | 'info' | 'advertencia'

/** Atributos ARIA compartidos por los campos con error de todas las vistas. */
export function atributosValidacion(
  campo: string,
  errores: ErroresCampo,
  idError: string,
): Readonly<Record<string, string>> {
  return errores[campo] === undefined ? {} : { 'aria-invalid': 'true', 'aria-describedby': idError }
}

/** Etiqueta de error visible asociada a un campo, o `undefined` si no hay error. */
export function etiquetaError(
  campo: string,
  errores: ErroresCampo,
  idError: string,
  clase = 'error',
): Descriptor | undefined {
  const mensaje = errores[campo]
  return mensaje === undefined
    ? undefined
    : { etiqueta: 'span', clase, texto: mensaje, atributos: { id: idError } }
}

/** Alerta del design system: texto plano, sin HTML. */
export function alertaVisual(texto: string, tipo: TipoAlerta = 'error', detalle?: string): Descriptor {
  return {
    etiqueta: 'div',
    texto,
    clase: `alerta alerta--${tipo}`,
    atributos: { role: tipo === 'error' ? 'alert' : 'status', ...(tipo === 'error' ? {} : { 'aria-live': 'polite' }) },
    ...(detalle === undefined ? {} : { hijos: [{ etiqueta: 'span', clase: 'alerta__detalle', texto: detalle }] }),
  }
}

export interface OpcionesEntrada {
  readonly nombre: string
  readonly etiqueta: string
  readonly valor: string
  readonly error?: string
  readonly foco?: boolean
  readonly tipo?: 'text' | 'number' | 'date' | 'time'
}

/** Campo con etiqueta, valor y error asociado por `aria-describedby`. */
export function entrada(opciones: OpcionesEntrada): Descriptor {
  const id = `campo-${opciones.nombre.replace(/[^a-zA-Z0-9]/g, '-')}`
  const idError = `${id}-error`
  const errores: ErroresCampo = opciones.error === undefined ? {} : { [opciones.nombre]: opciones.error }
  const errorVisible = etiquetaError(opciones.nombre, errores, idError)

  return {
    etiqueta: 'div',
    clase: 'campo',
    hijos: [
      { etiqueta: 'label', texto: opciones.etiqueta, atributos: { for: id } },
      {
        etiqueta: 'input',
        atributos: {
          id,
          name: opciones.nombre,
          type: opciones.tipo ?? 'text',
          value: opciones.valor,
          'data-campo': opciones.nombre,
          ...atributosValidacion(opciones.nombre, errores, idError),
          ...(opciones.foco === true ? { autofocus: '' } : {}),
        },
      },
      ...(errorVisible === undefined ? [] : [errorVisible]),
    ],
  }
}

export interface EntradaConsultable {
  getAttribute(nombre: string): string | null
  readonly value: string
}

interface NodoConsultable {
  querySelectorAll(selector: string): ArrayLike<EntradaConsultable>
}

function esConsultable(nodo: unknown): nodo is NodoConsultable {
  if (typeof nodo !== 'object' || nodo === null) {
    return false
  }
  return typeof (nodo as Partial<NodoConsultable>).querySelectorAll === 'function'
}

interface NodoConAncestros {
  closest(selector: string): unknown
}

function tieneAncestros(nodo: unknown): nodo is NodoConAncestros {
  return typeof nodo === 'object' && nodo !== null && typeof (nodo as Partial<NodoConAncestros>).closest === 'function'
}

/**
 * Devuelve el formulario que contiene al nodo (o el nodo mismo si no se puede subir).
 * Sirve para leer lo que el usuario tipeó cuando la acción sale de un botón interno.
 */
export function formularioDe(nodo: unknown): unknown {
  if (!tieneAncestros(nodo)) {
    return nodo
  }
  return nodo.closest('form') ?? nodo
}

/** Lee los valores del formulario cuando el nodo real lo permite. */
export function valoresDeFormulario(nodo: unknown): ReadonlyMap<string, string> {
  const valores = new Map<string, string>()
  if (!esConsultable(nodo)) {
    return valores
  }
  const entradas = nodo.querySelectorAll('[data-campo]')
  for (let indice = 0; indice < entradas.length; indice += 1) {
    const entradaNodo = entradas[indice]
    if (entradaNodo === undefined) {
      continue
    }
    const nombre = entradaNodo.getAttribute('data-campo')
    if (nombre !== null) {
      valores.set(nombre, entradaNodo.value)
    }
  }
  return valores
}
