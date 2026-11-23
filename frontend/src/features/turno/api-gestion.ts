import type { FetchLike } from '../../shared/api-client'
import type { ErrorApi } from '../../shared/error-api'
import { esRegistro, lanzar, pedir } from '../reservar/api-publica'
import type { EstadoTurno, TurnoGestion } from './modelo'

/** Cliente de `GET /api/public/turnos/{token}`, `cancelar` y `reprogramar` (C-09). */
export interface ApiGestion {
  consultar(token: string): Promise<TurnoGestion>
  cancelar(token: string): Promise<void>
  reprogramar(token: string, inicio: string): Promise<void>
}

export interface OpcionesApiGestion {
  fetch?: FetchLike
}

/** Mismo formato que valida C-09: 43 caracteres base64url sin padding. */
const FORMATO_TOKEN = /^[A-Za-z0-9_-]{43}$/

const NO_ENCONTRADO: ErrorApi = {
  codigo: 'NO_ENCONTRADO',
  mensaje: 'No encontramos lo que buscabas.',
}

const RESPUESTA_INVALIDA: ErrorApi = {
  codigo: 'RESPUESTA_INVALIDA',
  mensaje: 'No pudimos interpretar la respuesta del servidor.',
}

const ESTADOS: readonly EstadoTurno[] = ['RESERVADO', 'CANCELADO', 'ATENDIDO', 'AUSENTE']

export function esTokenValido(token: string): boolean {
  return FORMATO_TOKEN.test(token)
}

function esEstado(valor: unknown): valor is EstadoTurno {
  return typeof valor === 'string' && (ESTADOS as readonly string[]).includes(valor)
}

function esInstante(valor: unknown): valor is string {
  return typeof valor === 'string' && !Number.isNaN(new Date(valor).getTime())
}

function esTurno(valor: unknown): valor is TurnoGestion {
  if (!esRegistro(valor)) {
    return false
  }
  const prestacion = valor['prestacion']
  const odontologo = valor['odontologo']
  if (!esRegistro(prestacion) || !esRegistro(odontologo)) {
    return false
  }
  const direccion = odontologo['direccionConsultorio']

  return (
    esInstante(valor['inicio']) &&
    esInstante(valor['fin']) &&
    esEstado(valor['estado']) &&
    typeof prestacion['id'] === 'number' &&
    Number.isFinite(prestacion['id']) &&
    typeof prestacion['nombre'] === 'string' &&
    typeof odontologo['slug'] === 'string' &&
    typeof odontologo['nombre'] === 'string' &&
    typeof odontologo['apellido'] === 'string' &&
    (direccion === null || direccion === undefined || typeof direccion === 'string') &&
    typeof valor['puedeCancelar'] === 'boolean' &&
    typeof valor['puedeReprogramar'] === 'boolean'
  )
}

/** Copia solo los campos conocidos: lo que sobre en la respuesta no llega a la pantalla. */
function normalizar(turno: TurnoGestion): TurnoGestion {
  return {
    inicio: turno.inicio,
    fin: turno.fin,
    estado: turno.estado,
    prestacion: { id: turno.prestacion.id, nombre: turno.prestacion.nombre },
    odontologo: {
      slug: turno.odontologo.slug,
      nombre: turno.odontologo.nombre,
      apellido: turno.odontologo.apellido,
      direccionConsultorio:
        typeof turno.odontologo.direccionConsultorio === 'string' ? turno.odontologo.direccionConsultorio : null,
    },
    puedeCancelar: turno.puedeCancelar,
    puedeReprogramar: turno.puedeReprogramar,
  }
}

function ruta(token: string, accion?: 'cancelar' | 'reprogramar'): string {
  if (!esTokenValido(token)) {
    lanzar(NO_ENCONTRADO)
  }
  // El formato ya excluye `/`, `?` y `#`: el token no puede salir de su segmento.
  return accion === undefined ? `/api/public/turnos/${token}` : `/api/public/turnos/${token}/${accion}`
}

export function crearApiGestion(opciones: OpcionesApiGestion = {}): ApiGestion {
  const fetchReal: FetchLike = opciones.fetch ?? ((url, init) => globalThis.fetch(url, init))

  return {
    async consultar(token) {
      const contenido = await pedir(fetchReal, ruta(token), { credentials: 'same-origin' })
      if (!esTurno(contenido)) {
        lanzar(RESPUESTA_INVALIDA)
      }
      return normalizar(contenido)
    },

    async cancelar(token) {
      await pedir(fetchReal, ruta(token, 'cancelar'), {
        method: 'POST',
        credentials: 'same-origin',
      })
    },

    async reprogramar(token, inicio) {
      await pedir(fetchReal, ruta(token, 'reprogramar'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        // `inicio` viaja con el mismo texto que devolvió la disponibilidad.
        body: JSON.stringify({ inicio }),
      })
    },
  }
}
