import type { ClienteApi } from '../../shared/api-client'
import { fechaLocalArgentina, sumarDias } from '../../shared/fechas'
import { esFechaValida } from '../../shared/fechas'
import type {
  DisponibilidadPublica,
  PerfilManual,
  PrestacionReserva,
  ReservaConfirmada,
  SolicitudTurnoManual,
} from './modelo'

/** Horizonte de RN-TU-11, aplicado también al alta manual y a su disponibilidad. */
export const DIAS_HORIZONTE = 60

export interface ApiManual {
  perfil(): Promise<PerfilManual>
  disponibilidad(prestacionId: number, fecha: string): Promise<DisponibilidadPublica>
  crear(solicitud: SolicitudTurnoManual): Promise<ReservaConfirmada>
}

const RESPUESTA_INVALIDA = 'La respuesta del servidor no tiene el formato esperado.'

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function esPrestacion(valor: unknown): valor is PrestacionReserva {
  return (
    esRegistro(valor) &&
    typeof valor['id'] === 'number' &&
    Number.isInteger(valor['id']) &&
    typeof valor['nombre'] === 'string' &&
    typeof valor['duracionMinutos'] === 'number' &&
    Number.isFinite(valor['duracionMinutos'])
  )
}

function esHorario(valor: unknown): boolean {
  return esRegistro(valor) && typeof valor['inicio'] === 'string' && typeof valor['hora'] === 'string'
}

function esDisponibilidad(valor: unknown): valor is DisponibilidadPublica {
  if (!esRegistro(valor) || !Array.isArray(valor['horarios'])) {
    return false
  }
  if (!(valor['horarios'] as unknown[]).every(esHorario)) {
    return false
  }
  return (
    typeof valor['fecha'] === 'string' &&
    esFechaValida(valor['fecha']) &&
    typeof valor['prestacionId'] === 'number' &&
    typeof valor['duracionMinutos'] === 'number'
  )
}

function esReserva(valor: unknown): valor is ReservaConfirmada {
  if (!esRegistro(valor) || !esPrestacion(valor['prestacion']) || !esRegistro(valor['odontologo'])) {
    return false
  }
  const odontologo = valor['odontologo']
  const direccion = odontologo['direccionConsultorio']
  return (
    typeof valor['inicio'] === 'string' &&
    typeof valor['fin'] === 'string' &&
    typeof odontologo['nombre'] === 'string' &&
    typeof odontologo['apellido'] === 'string' &&
    (direccion === null || direccion === undefined || typeof direccion === 'string') &&
    typeof valor['email'] === 'string'
  )
}

function normalizarDisponibilidad(valor: DisponibilidadPublica): DisponibilidadPublica {
  return {
    fecha: valor.fecha,
    prestacionId: valor.prestacionId,
    duracionMinutos: valor.duracionMinutos,
    horarios: valor.horarios.map((horario) => ({ inicio: horario.inicio, hora: horario.hora })),
  }
}

function normalizarReserva(valor: ReservaConfirmada): ReservaConfirmada {
  const direccion = valor.odontologo.direccionConsultorio
  return {
    inicio: valor.inicio,
    fin: valor.fin,
    prestacion: {
      id: valor.prestacion.id,
      nombre: valor.prestacion.nombre,
      duracionMinutos: valor.prestacion.duracionMinutos,
    },
    odontologo: {
      nombre: valor.odontologo.nombre,
      apellido: valor.odontologo.apellido,
      direccionConsultorio: typeof direccion === 'string' ? direccion : null,
    },
    email: valor.email,
  }
}

/** Cliente autenticado: el `cliente` comparte sesión y CSRF con el resto de la SPA. */
export function crearApiManual(cliente: ClienteApi, ahora: () => Date = () => new Date()): ApiManual {
  return {
    async perfil() {
      const cuerpo: unknown = await cliente.pedir<unknown>('GET', '/api/prestaciones')
      if (!esRegistro(cuerpo) || !Array.isArray(cuerpo['prestaciones'])) {
        throw new Error(RESPUESTA_INVALIDA)
      }
      const prestaciones: PrestacionReserva[] = []
      for (const cruda of cuerpo['prestaciones'] as unknown[]) {
        if (!esPrestacion(cruda)) {
          throw new Error(RESPUESTA_INVALIDA)
        }
        if (esRegistro(cruda) && cruda['activa'] === true) {
          prestaciones.push({ id: cruda.id, nombre: cruda.nombre, duracionMinutos: cruda.duracionMinutos })
        }
      }

      const fechaMinima = fechaLocalArgentina(ahora())
      return {
        fechaMinima,
        fechaMaxima: sumarDias(fechaMinima, DIAS_HORIZONTE),
        prestaciones,
      }
    },

    async disponibilidad(prestacionId, fecha) {
      const parametros = new URLSearchParams({ prestacionId: String(prestacionId), fecha })
      const cuerpo: unknown = await cliente.pedir<unknown>('GET', `/api/disponibilidad?${parametros.toString()}`)
      if (!esDisponibilidad(cuerpo)) {
        throw new Error(RESPUESTA_INVALIDA)
      }
      return normalizarDisponibilidad(cuerpo)
    },

    async crear(solicitud) {
      const cuerpo: unknown = await cliente.pedir<unknown>('POST', '/api/turnos', solicitud)
      if (!esReserva(cuerpo)) {
        throw new Error(RESPUESTA_INVALIDA)
      }
      return normalizarReserva(cuerpo)
    },
  }
}
