import type { ClienteApi } from '../../shared/api-client'
import { fechaLocalArgentina, horaArgentina } from '../../shared/fechas'
import { leerBloqueos, leerFranjas, leerTurnos } from './tipos'
import type { BloqueoAgenda, FranjaHorario, TurnoAgenda } from './tipos'

/** Arma la consulta de C-12 con fecha local y rango semiabierto `[desde, hasta)`. */
export function urlAgenda(desde: string, hasta: string): string {
  return `/api/turnos?desde=${desde}&hasta=${hasta}`
}

function compararTurnos(a: TurnoAgenda, b: TurnoAgenda): number {
  const inicioA = Date.parse(a.inicio)
  const inicioB = Date.parse(b.inicio)
  if (inicioA !== inicioB) {
    return inicioA - inicioB
  }
  return a.id - b.id
}

export async function consultarAgenda(cliente: ClienteApi, desde: string, hasta: string): Promise<TurnoAgenda[]> {
  const cuerpo: unknown = await cliente.pedir('GET', urlAgenda(desde, hasta))
  return leerTurnos(cuerpo).sort(compararTurnos)
}

export const WHATSAPP_SIN_TELEFONO = 'El paciente no tiene un teléfono internacional válido.'
export const WHATSAPP_SIN_DIRECCION = 'Falta la dirección del consultorio.'

export type FallaWhatsApp = 'sesion-vencida' | 'sin-acceso' | 'cambio' | 'telefono' | 'direccion' | 'error'
export type ResultadoWhatsApp = { tipo: 'lista'; url: string } | { tipo: FallaWhatsApp }

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function urlWhatsAppValida(cuerpo: unknown): string | undefined {
  if (!esRegistro(cuerpo) || Object.keys(cuerpo).length !== 1 || typeof cuerpo.url !== 'string') {
    return undefined
  }
  // La variable URI del backend solo deja caracteres no reservados y escapes UTF-8.
  const coincidencia = /^https:\/\/wa\.me\/[1-9][0-9]{7,14}\?text=((?:[A-Za-z0-9._~-]|%[0-9A-Fa-f]{2})+)$/.exec(cuerpo.url)
  const texto = coincidencia?.[1]
  if (texto === undefined || coincidencia?.[0] !== cuerpo.url) {
    return undefined
  }
  try {
    return decodeURIComponent(texto).trim() === '' ? undefined : cuerpo.url
  } catch {
    return undefined
  }
}

function fallaWhatsApp(error: unknown): FallaWhatsApp {
  if (!esRegistro(error)) return 'error'
  switch (error.codigo) {
    case 'NO_AUTENTICADO': return 'sesion-vencida'
    case 'ACCESO_DENEGADO':
    case 'PROHIBIDO': return 'sin-acceso'
    case 'NO_ENCONTRADO':
    case 'RN-NO-06': return 'cambio'
    case 'RN-NO-07':
      if (error.mensaje === WHATSAPP_SIN_TELEFONO) return 'telefono'
      if (error.mensaje === WHATSAPP_SIN_DIRECCION) return 'direccion'
  }
  return 'error'
}

export async function consultarWhatsApp(cliente: ClienteApi, id: number): Promise<ResultadoWhatsApp> {
  try {
    const cuerpo: unknown = await cliente.pedir('GET', `/api/turnos/${encodeURIComponent(String(id))}/whatsapp`)
    const url = urlWhatsAppValida(cuerpo)
    return url === undefined ? { tipo: 'error' } : { tipo: 'lista', url }
  } catch (error: unknown) {
    return { tipo: fallaWhatsApp(error) }
  }
}

export async function consultarHorarios(cliente: ClienteApi): Promise<FranjaHorario[]> {
  const cuerpo: unknown = await cliente.pedir('GET', '/api/horarios')
  return leerFranjas(cuerpo).sort((a, b) => a.diaSemana - b.diaSemana || a.inicio.localeCompare(b.inicio))
}

/** Clave `YYYY-MM-DDTHH:mm` en hora de Argentina, comparable léxicamente. */
export function claveLocal(instante: string): string {
  const fecha = new Date(instante)
  return `${fechaLocalArgentina(fecha)}T${horaArgentina(fecha)}`
}

/** Intersección semiabierta con el rango visible, en hora de Argentina. */
export function bloqueosEnRango(bloqueos: readonly BloqueoAgenda[], desde: string, hasta: string): BloqueoAgenda[] {
  const inicioRango = `${desde}T00:00`
  const finRango = `${hasta}T00:00`
  return bloqueos
    .filter((bloqueo) => claveLocal(bloqueo.inicio) < finRango && claveLocal(bloqueo.fin) > inicioRango)
    .sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio))
}

export async function consultarBloqueos(
  cliente: ClienteApi,
  desde: string,
  hasta: string,
): Promise<BloqueoAgenda[]> {
  const cuerpo: unknown = await cliente.pedir('GET', '/api/bloqueos')
  return bloqueosEnRango(leerBloqueos(cuerpo), desde, hasta)
}
