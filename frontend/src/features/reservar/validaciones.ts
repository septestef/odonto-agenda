import type { CampoPaciente, DatosPaciente, ErroresPaciente } from './modelo'

const DNI = /^[0-9]{7,8}$/
const TELEFONO = /^\+[1-9][0-9]{7,14}$/
const EMAIL = /^(?=[^\s]{6,255}$)[^@\s]{1,64}@(?!.*\.\.)[^@\s.][^@\s]*\.[^@\s.]+$/

const CAMPOS: readonly CampoPaciente[] = ['dni', 'nombre', 'apellido', 'telefono', 'email']

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

  for (const campo of CAMPOS) {
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
  return (CAMPOS as readonly string[]).includes(valor)
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
