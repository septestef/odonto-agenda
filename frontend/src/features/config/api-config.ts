import type { ClienteApi } from '../../shared/api-client'
import { leerBloqueo, leerBloqueos, leerHorario, leerPrestacion, leerPrestaciones } from './contratos'
import type { Bloqueo, Franja, Horario, Prestacion } from './modelos'

export interface DatosPrestacion {
  readonly nombre: string
  readonly duracionMinutos: number
}

export interface DatosBloqueo {
  readonly inicio: string
  readonly fin: string
  readonly motivo?: string
}

export interface ApiConfig {
  horarios(): Promise<Horario>
  guardarHorario(franjas: readonly Franja[]): Promise<Horario>
  prestaciones(): Promise<readonly Prestacion[]>
  crearPrestacion(datos: DatosPrestacion): Promise<Prestacion>
  editarPrestacion(id: number, datos: DatosPrestacion): Promise<Prestacion>
  bajaPrestacion(id: number): Promise<void>
  bloqueos(): Promise<readonly Bloqueo[]>
  crearBloqueo(datos: DatosBloqueo): Promise<Bloqueo>
  bajaBloqueo(id: number): Promise<void>
}

/** Único punto que conoce las rutas de configuración. El dueño lo resuelve la sesión. */
export function crearApiConfig(cliente: ClienteApi): ApiConfig {
  return {
    async horarios() {
      return leerHorario(await cliente.pedir<unknown>('GET', '/api/horarios'))
    },
    async guardarHorario(franjas) {
      return leerHorario(await cliente.pedir<unknown>('PUT', '/api/horarios', { franjas }))
    },
    async prestaciones() {
      return leerPrestaciones(await cliente.pedir<unknown>('GET', '/api/prestaciones'))
    },
    async crearPrestacion(datos) {
      return leerPrestacion(await cliente.pedir<unknown>('POST', '/api/prestaciones', datos))
    },
    async editarPrestacion(id, datos) {
      return leerPrestacion(await cliente.pedir<unknown>('PUT', `/api/prestaciones/${id}`, datos))
    },
    async bajaPrestacion(id) {
      await cliente.pedir<unknown>('DELETE', `/api/prestaciones/${id}`)
    },
    async bloqueos() {
      return leerBloqueos(await cliente.pedir<unknown>('GET', '/api/bloqueos'))
    },
    async crearBloqueo(datos) {
      const cuerpo = {
        inicio: datos.inicio,
        fin: datos.fin,
        ...(datos.motivo === undefined || datos.motivo.trim() === '' ? {} : { motivo: datos.motivo }),
      }
      return leerBloqueo(await cliente.pedir<unknown>('POST', '/api/bloqueos', cuerpo))
    },
    async bajaBloqueo(id) {
      await cliente.pedir<unknown>('DELETE', `/api/bloqueos/${id}`)
    },
  }
}
