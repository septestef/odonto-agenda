import type { ClienteApi } from '../../shared/api-client'

export type ResultadoCancelacion = 'cancelado' | 'ya-empezo' | 'cambio' | 'error' | 'sesion-vencida'

const ERPOR: Readonly<Record<string, ResultadoCancelacion>> = {
  'RN-TU-07': 'ya-empezo',
  NO_ENCONTRADO: 'cambio',
  'RN-TU-01': 'cambio',
  NO_AUTENTICADO: 'sesion-vencida',
  CSRF_INVALIDO: 'sesion-vencida',
  ACCESO_DENEGADO: 'sesion-vencida',
  PROHIBIDO: 'sesion-vencida',
}

function codigoDe(error: unknown): string | undefined {
  if (typeof error !== 'object' || error === null) {
    return undefined
  }
  const codigo: unknown = (error as Record<string, unknown>)['codigo']
  return typeof codigo === 'string' ? codigo : undefined
}

/** Traduce una respuesta sin reintentar: solo el 204 habilita dar el turno por cancelado. */
export function interpretarCancelacion(respuesta: unknown): ResultadoCancelacion {
  if (respuesta === undefined) {
    return 'cancelado'
  }
  const codigo = codigoDe(respuesta)
  if (codigo === undefined) {
    return 'error'
  }
  return ERPOR[codigo] ?? 'error'
}

/** POST sin cuerpo: el CSRF lo reenvía el `api-client` desde la cookie. */
export async function cancelarTurno(cliente: ClienteApi, id: number): Promise<void> {
  await cliente.pedir('POST', `/api/turnos/${encodeURIComponent(String(id))}/cancelar`)
}
