import type { NodoUI } from './ui/adaptador'
import type { Descriptor } from './ui/componentes'

/**
 * Formulario de paciente compartido por la reserva online (C-10) y el alta manual (C-15).
 * Los datos se renderizan siempre como texto: `Descriptor.texto` llega a `textContent` en el adaptador.
 */

export interface DatosPaciente {
  dni: string
  nombre: string
  apellido: string
  telefono: string
  email: string
}

export type CampoPaciente = keyof DatosPaciente

export type ErroresPaciente = Partial<Record<CampoPaciente, string>>

export const DATOS_PACIENTE_VACIOS: DatosPaciente = {
  dni: '',
  nombre: '',
  apellido: '',
  telefono: '',
  email: '',
}

export const ORDEN_CAMPOS_PACIENTE: readonly CampoPaciente[] = ['dni', 'telefono', 'nombre', 'apellido', 'email']

const DNI = /^[0-9]{7,8}$/
const TELEFONO = /^\+[1-9][0-9]{7,14}$/
const EMAIL = /^(?=[^\s]{6,255}$)[^@\s]{1,64}@(?!.*\.\.)[^@\s.][^@\s]*\.[^@\s.]+$/

export function validarDni(valor: string): string | undefined {
  return DNI.test(valor.trim()) ? undefined : 'Ingresá tu DNI sin puntos, 7 u 8 dígitos.'
}

export function validarTelefono(valor: string): string | undefined {
  return TELEFONO.test(valor.trim())
    ? undefined
    : 'Ingresá el teléfono con código de país, por ejemplo +5493511234567.'
}

export function validarEmail(valor: string): string | undefined {
  return EMAIL.test(valor.trim()) ? undefined : 'Ingresá un email válido.'
}

export function validarNombre(valor: string): string | undefined {
  const texto = valor.trim()
  if (texto === '') {
    return 'Ingresá tu nombre.'
  }
  return texto.length > 100 ? 'El nombre es demasiado largo.' : undefined
}

export function validarApellido(valor: string): string | undefined {
  const texto = valor.trim()
  if (texto === '') {
    return 'Ingresá tu apellido.'
  }
  return texto.length > 100 ? 'El apellido es demasiado largo.' : undefined
}

export function validarDatosPaciente(datos: DatosPaciente): ErroresPaciente {
  const errores: ErroresPaciente = {}
  const porCampo: Readonly<Record<CampoPaciente, string | undefined>> = {
    dni: validarDni(datos.dni),
    nombre: validarNombre(datos.nombre),
    apellido: validarApellido(datos.apellido),
    telefono: validarTelefono(datos.telefono),
    email: validarEmail(datos.email),
  }

  for (const campo of ORDEN_CAMPOS_PACIENTE) {
    const mensaje = porCampo[campo]
    if (mensaje !== undefined) {
      errores[campo] = mensaje
    }
  }

  return errores
}

export function hayErrores(errores: ErroresPaciente): boolean {
  return Object.keys(errores).length > 0
}

function esCampoPaciente(valor: string): valor is CampoPaciente {
  return (ORDEN_CAMPOS_PACIENTE as readonly string[]).includes(valor)
}

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function mensajeDeDetalle(detalle: unknown): string | undefined {
  if (typeof detalle === 'string') {
    return detalle
  }
  if (esRegistro(detalle) && typeof detalle['mensaje'] === 'string') {
    return detalle['mensaje']
  }
  return undefined
}

/** Traduce los `detalles` de un 400 VALIDACION a errores por campo del formulario. */
export function erroresPorCampo(detalles: unknown): ErroresPaciente {
  const errores: ErroresPaciente = {}

  if (Array.isArray(detalles)) {
    for (const detalle of detalles) {
      if (!esRegistro(detalle) || typeof detalle['campo'] !== 'string') {
        continue
      }
      const campo = detalle['campo']
      const mensaje = mensajeDeDetalle(detalle)
      if (esCampoPaciente(campo) && mensaje !== undefined) {
        errores[campo] = mensaje
      }
    }
    return errores
  }

  if (esRegistro(detalles)) {
    for (const [campo, detalle] of Object.entries(detalles)) {
      const mensaje = mensajeDeDetalle(detalle)
      if (esCampoPaciente(campo) && mensaje !== undefined) {
        errores[campo] = mensaje
      }
    }
  }

  return errores
}

interface NodoFormulario extends NodoUI {
  querySelector(selector: string): { value: string } | null
  closest?(selector: string): NodoUI | null
}

function esNodoFormulario(nodo: NodoUI): nodo is NodoFormulario {
  const candidato = nodo as unknown as Record<string, unknown>
  return typeof candidato['querySelector'] === 'function'
}

export function formularioDe(nodo: NodoUI): NodoUI {
  const conCercania = nodo as NodoUI & { closest?: (selector: string) => NodoUI | null }
  return conCercania.closest?.('form') ?? nodo
}

export function valorCampo(nodo: NodoUI, nombre: CampoPaciente): string {
  if (!esNodoFormulario(nodo)) {
    return ''
  }
  return nodo.querySelector(`[data-campo="${nombre}"]`)?.value ?? ''
}

/** Lee los cinco campos del paciente desde el formulario que contiene al nodo. */
export function leerDatosDe(nodo: NodoUI): DatosPaciente | undefined {
  const formulario = formularioDe(nodo)
  if (!esNodoFormulario(formulario)) {
    return undefined
  }

  return {
    dni: valorCampo(formulario, 'dni'),
    nombre: valorCampo(formulario, 'nombre'),
    apellido: valorCampo(formulario, 'apellido'),
    telefono: valorCampo(formulario, 'telefono'),
    email: valorCampo(formulario, 'email'),
  }
}

export interface OpcionesCampoPaciente {
  etiqueta: string
  nombre: CampoPaciente
  tipo: 'text' | 'tel' | 'email'
  valor: string
  autocompletado?: string
  inputmode?: string
  hint?: string
  error?: string
  deshabilitado?: boolean
  enfocar?: boolean
}

/** Campo accesible: etiqueta asociada, pista y error con `role="alert"` cuando corresponde. */
export function campoPaciente(opciones: OpcionesCampoPaciente): Descriptor {
  const id = `campo-${opciones.nombre}`
  const idHint = `${id}-hint`
  const idError = `${id}-error`
  const descritos = [
    ...(opciones.hint === undefined ? [] : [idHint]),
    ...(opciones.error === undefined ? [] : [idError]),
  ]

  return {
    etiqueta: 'div',
    clase: 'field',
    hijos: [
      { etiqueta: 'label', texto: opciones.etiqueta, atributos: { for: id } },
      {
        etiqueta: 'input',
        clase: 'input',
        enfocar: opciones.enfocar,
        atributos: {
          id,
          name: opciones.nombre,
          type: opciones.tipo,
          'data-campo': opciones.nombre,
          value: opciones.valor,
          ...(opciones.autocompletado === undefined ? {} : { autocomplete: opciones.autocompletado }),
          ...(opciones.inputmode === undefined ? {} : { inputmode: opciones.inputmode }),
          ...(descritos.length === 0 ? {} : { 'aria-describedby': descritos.join(' ') }),
          ...(opciones.error === undefined ? {} : { 'aria-invalid': 'true' }),
          ...(opciones.deshabilitado === true ? { disabled: '' } : {}),
        },
      },
      ...(opciones.hint === undefined
        ? []
        : [{ etiqueta: 'span' as const, texto: opciones.hint, clase: 'hint', atributos: { id: idHint } }]),
      ...(opciones.error === undefined
        ? []
        : [
            {
              etiqueta: 'span' as const,
              texto: opciones.error,
              clase: 'error',
              atributos: { id: idError, role: 'alert' },
            },
          ]),
    ],
  }
}

export interface OpcionesCamposPaciente {
  datos: DatosPaciente
  errores: ErroresPaciente
  enviando?: boolean
  hintDni?: string
  hintEmail?: string
  enfocarPrimerError?: boolean
}

/** Los cinco campos en el mismo orden y disposición que la reserva pública. */
export function camposPaciente(opciones: OpcionesCamposPaciente): Descriptor[] {
  const errores = opciones.errores
  const enviando = opciones.enviando === true
  const primerError = ORDEN_CAMPOS_PACIENTE.find((campo) => errores[campo] !== undefined)
  const enfocar = (campo: CampoPaciente): boolean | undefined =>
    opciones.enfocarPrimerError === false ? undefined : primerError === campo

  return [
    {
      etiqueta: 'div',
      clase: 'field-row',
      hijos: [
        campoPaciente({
          etiqueta: 'DNI',
          nombre: 'dni',
          tipo: 'text',
          valor: opciones.datos.dni,
          inputmode: 'numeric',
          autocompletado: 'off',
          hint: opciones.hintDni ?? 'Sin puntos.',
          error: errores.dni,
          deshabilitado: enviando,
          enfocar: enfocar('dni'),
        }),
        campoPaciente({
          etiqueta: 'Teléfono',
          nombre: 'telefono',
          tipo: 'tel',
          valor: opciones.datos.telefono,
          inputmode: 'tel',
          autocompletado: 'tel',
          hint: 'Con código de país, por ejemplo +5493511234567.',
          error: errores.telefono,
          deshabilitado: enviando,
          enfocar: enfocar('telefono'),
        }),
      ],
    },
    {
      etiqueta: 'div',
      clase: 'field-row',
      hijos: [
        campoPaciente({
          etiqueta: 'Nombre',
          nombre: 'nombre',
          tipo: 'text',
          valor: opciones.datos.nombre,
          autocompletado: 'given-name',
          error: errores.nombre,
          deshabilitado: enviando,
          enfocar: enfocar('nombre'),
        }),
        campoPaciente({
          etiqueta: 'Apellido',
          nombre: 'apellido',
          tipo: 'text',
          valor: opciones.datos.apellido,
          autocompletado: 'family-name',
          error: errores.apellido,
          deshabilitado: enviando,
          enfocar: enfocar('apellido'),
        }),
      ],
    },
    campoPaciente({
      etiqueta: 'Email',
      nombre: 'email',
      tipo: 'email',
      valor: opciones.datos.email,
      inputmode: 'email',
      autocompletado: 'email',
      hint: opciones.hintEmail ?? 'Te mandamos ahí el link para gestionar tu turno.',
      error: errores.email,
      deshabilitado: enviando,
      enfocar: enfocar('email'),
    }),
  ]
}
