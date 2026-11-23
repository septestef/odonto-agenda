import type { FetchLike } from '../../shared/api-client'
import { interpretarErrorApi } from '../../shared/error-api'
import type { ErrorApi } from '../../shared/error-api'
import { esFechaValida } from '../../shared/fechas'
import type {
  DisponibilidadPublica,
  HorarioOpcion,
  OdontologoPublico,
  PerfilReserva,
  PrestacionReserva,
  ReservaConfirmada,
  SolicitudReserva,
} from './modelo'

export interface ApiPublica {
  perfil(slug: string): Promise<PerfilReserva>
  disponibilidad(slug: string, prestacionId: number, fecha: string): Promise<DisponibilidadPublica>
  reservar(slug: string, solicitud: SolicitudReserva): Promise<ReservaConfirmada>
}

export interface OpcionesApiPublica {
  fetch?: FetchLike
}

const SIN_CONEXION: ErrorApi = {
  codigo: 'SIN_CONEXION',
  mensaje: 'Revisá tu conexión e intentá de nuevo.',
}

const RESPUESTA_INVALIDA: ErrorApi = {
  codigo: 'RESPUESTA_INVALIDA',
  mensaje: 'No pudimos interpretar la respuesta del servidor.',
}

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function esRespuesta(valor: unknown): valor is Response {
  return (
    typeof valor === 'object' &&
    valor !== null &&
    'ok' in valor &&
    typeof valor.ok === 'boolean' &&
    'status' in valor &&
    typeof valor.status === 'number'
  )
}

function lanzar(error: ErrorApi): never {
  throw Object.assign(new Error(error.mensaje), error)
}

async function pedir(fetchReal: FetchLike, ruta: string, init?: RequestInit): Promise<unknown> {
  let respuesta: unknown
  try {
    respuesta = await fetchReal(ruta, init)
  } catch {
    lanzar(SIN_CONEXION)
  }

  if (!esRespuesta(respuesta)) {
    lanzar(SIN_CONEXION)
  }

  const contenido: unknown = await respuesta.json().catch(() => undefined)
  if (!respuesta.ok) {
    lanzar(interpretarErrorApi(contenido))
  }

  return contenido
}

function esPrestacion(valor: unknown): valor is PrestacionReserva {
  return (
    esRegistro(valor) &&
    typeof valor['id'] === 'number' &&
    Number.isFinite(valor['id']) &&
    typeof valor['nombre'] === 'string' &&
    typeof valor['duracionMinutos'] === 'number' &&
    Number.isFinite(valor['duracionMinutos'])
  )
}

function esHorario(valor: unknown): valor is HorarioOpcion {
  return (
    esRegistro(valor) && typeof valor['inicio'] === 'string' && typeof valor['hora'] === 'string'
  )
}

function esPerfil(valor: unknown): valor is PerfilReserva {
  if (!esRegistro(valor)) {
    return false
  }
  const direccion = valor['direccionConsultorio']
  const prestaciones = valor['prestaciones']
  if (!Array.isArray(prestaciones)) {
    return false
  }
  for (const prestacion of prestaciones as unknown[]) {
    if (!esPrestacion(prestacion)) {
      return false
    }
  }

  return (
    typeof valor['slug'] === 'string' &&
    typeof valor['nombre'] === 'string' &&
    typeof valor['apellido'] === 'string' &&
    (direccion === null || direccion === undefined || typeof direccion === 'string') &&
    typeof valor['zonaHoraria'] === 'string' &&
    typeof valor['fechaMinima'] === 'string' &&
    esFechaValida(valor['fechaMinima']) &&
    typeof valor['fechaMaxima'] === 'string' &&
    esFechaValida(valor['fechaMaxima'])
  )
}

function esDisponibilidad(valor: unknown): valor is DisponibilidadPublica {
  if (!esRegistro(valor)) {
    return false
  }
  const horarios = valor['horarios']
  if (!Array.isArray(horarios)) {
    return false
  }
  for (const horario of horarios as unknown[]) {
    if (!esHorario(horario)) {
      return false
    }
  }

  return (
    typeof valor['fecha'] === 'string' &&
    esFechaValida(valor['fecha']) &&
    typeof valor['prestacionId'] === 'number' &&
    typeof valor['duracionMinutos'] === 'number'
  )
}

function esOdontologo(valor: unknown): valor is OdontologoPublico {
  if (!esRegistro(valor)) {
    return false
  }
  const direccion = valor['direccionConsultorio']

  return (
    typeof valor['nombre'] === 'string' &&
    typeof valor['apellido'] === 'string' &&
    (direccion === null || direccion === undefined || typeof direccion === 'string')
  )
}

function esReserva(valor: unknown): valor is ReservaConfirmada {
  return (
    esRegistro(valor) &&
    typeof valor['inicio'] === 'string' &&
    typeof valor['fin'] === 'string' &&
    esPrestacion(valor['prestacion']) &&
    esOdontologo(valor['odontologo']) &&
    typeof valor['email'] === 'string'
  )
}

function normalizarPrestacion(prestacion: PrestacionReserva): PrestacionReserva {
  return {
    id: prestacion.id,
    nombre: prestacion.nombre,
    duracionMinutos: prestacion.duracionMinutos,
  }
}

function normalizarPerfil(perfil: PerfilReserva): PerfilReserva {
  return {
    slug: perfil.slug,
    nombre: perfil.nombre,
    apellido: perfil.apellido,
    direccionConsultorio:
      typeof perfil.direccionConsultorio === 'string' ? perfil.direccionConsultorio : null,
    zonaHoraria: perfil.zonaHoraria,
    fechaMinima: perfil.fechaMinima,
    fechaMaxima: perfil.fechaMaxima,
    prestaciones: perfil.prestaciones.map(normalizarPrestacion),
  }
}

function normalizarDisponibilidad(disponibilidad: DisponibilidadPublica): DisponibilidadPublica {
  return {
    fecha: disponibilidad.fecha,
    prestacionId: disponibilidad.prestacionId,
    duracionMinutos: disponibilidad.duracionMinutos,
    horarios: disponibilidad.horarios.map((horario) => ({ inicio: horario.inicio, hora: horario.hora })),
  }
}

function normalizarReserva(reserva: ReservaConfirmada): ReservaConfirmada {
  return {
    inicio: reserva.inicio,
    fin: reserva.fin,
    prestacion: normalizarPrestacion(reserva.prestacion),
    odontologo: {
      nombre: reserva.odontologo.nombre,
      apellido: reserva.odontologo.apellido,
      direccionConsultorio:
        typeof reserva.odontologo.direccionConsultorio === 'string'
          ? reserva.odontologo.direccionConsultorio
          : null,
    },
    email: reserva.email,
  }
}

export function crearApiPublica(opciones: OpcionesApiPublica = {}): ApiPublica {
  const fetchReal: FetchLike = opciones.fetch ?? ((url, init) => globalThis.fetch(url, init))

  return {
    async perfil(slug) {
      const contenido = await pedir(fetchReal, `/api/public/${encodeURIComponent(slug)}`)
      if (!esPerfil(contenido)) {
        lanzar(RESPUESTA_INVALIDA)
      }
      return normalizarPerfil(contenido)
    },

    async disponibilidad(slug, prestacionId, fecha) {
      const parametros = new URLSearchParams({ prestacionId: String(prestacionId), fecha })
      const contenido = await pedir(
        fetchReal,
        `/api/public/${encodeURIComponent(slug)}/disponibilidad?${parametros.toString()}`,
      )
      if (!esDisponibilidad(contenido)) {
        lanzar(RESPUESTA_INVALIDA)
      }
      return normalizarDisponibilidad(contenido)
    },

    async reservar(slug, solicitud) {
      const contenido = await pedir(fetchReal, `/api/public/${encodeURIComponent(slug)}/turnos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(solicitud),
      })
      if (!esReserva(contenido)) {
        lanzar(RESPUESTA_INVALIDA)
      }
      return normalizarReserva(contenido)
    },
  }
}
