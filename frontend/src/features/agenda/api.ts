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
