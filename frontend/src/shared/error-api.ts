export interface ErrorApi {
  codigo: string
  mensaje: string
  detalles?: unknown
}

const ERROR_GENERICO: ErrorApi = {
  codigo: 'ERROR_DESCONOCIDO',
  mensaje: 'Ocurrió un error inesperado. Probá de nuevo en unos minutos.',
}

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

/** Convierte el cuerpo de una respuesta de error de la API en un `ErrorApi` tipado. */
export function interpretarErrorApi(cuerpo: unknown): ErrorApi {
  if (!esObjeto(cuerpo)) {
    return { ...ERROR_GENERICO }
  }
  const { codigo, mensaje, detalles } = cuerpo
  if (typeof codigo !== 'string' || typeof mensaje !== 'string') {
    return { ...ERROR_GENERICO }
  }
  return detalles === undefined ? { codigo, mensaje } : { codigo, mensaje, detalles }
}
