import { campoEnDetalles } from './contratos'
import type { ErroresCampo } from './modelos'

export interface ErrorVisible {
  readonly alerta: string
  readonly erroresCampo: ErroresCampo
  readonly primero?: string
  readonly reintentar: boolean
}

const CAMPOS_RECONOCIDOS = /^(franjas\[\d+\]|nombre|duracionMinutos|fin|inicio)$/

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function codigoDe(error: unknown): string | undefined {
  if (!esRegistro(error) || typeof error['codigo'] !== 'string') {
    return undefined
  }
  return error['codigo']
}

function mensajeDe(error: unknown): string | undefined {
  if (!esRegistro(error) || typeof error['mensaje'] !== 'string') {
    return undefined
  }
  return error['mensaje']
}

function detallesDe(error: unknown): unknown {
  return esRegistro(error) ? error['detalles'] : undefined
}

/** Convierte un `ErrorApi` en texto plano y errores por campo, sin interpretar HTML. */
export function traducirError(error: unknown): ErrorVisible {
  const codigo = codigoDe(error)
  const mensaje = mensajeDe(error) ?? 'Ocurrió un error inesperado. Probá de nuevo en unos minutos.'
  const erroresCampo: Record<string, string> = {}

  if (codigo === 'NO_ENCONTRADO') {
    return { alerta: 'No encontramos ese recurso.', erroresCampo, reintentar: false }
  }

  if (codigo === 'NO_AUTENTICADO' || codigo === 'ACCESO_DENEGADO') {
    return { alerta: 'Tu sesión venció. Ingresá de nuevo.', erroresCampo, reintentar: false }
  }

  for (const detalle of campoEnDetalles(detallesDe(error))) {
    if (CAMPOS_RECONOCIDOS.test(detalle.campo)) {
      erroresCampo[detalle.campo] = detalle.mensaje
    }
  }

  const primero = Object.keys(erroresCampo)[0]

  return {
    alerta: mensaje,
    erroresCampo,
    ...(primero === undefined ? {} : { primero }),
    reintentar: codigo === 'SIN_CONEXION' || codigo === 'ERROR_INTERNO',
  }
}
