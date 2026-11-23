import type { ErrorApi } from '../../shared/error-api'
import type { HorarioOpcion, PerfilReserva } from '../reservar/modelo'

/** Estados de un turno según el backend (`EstadoTurno`). */
export type EstadoTurno = 'RESERVADO' | 'CANCELADO' | 'ATENDIDO' | 'AUSENTE'

/** Detalle de `GET /api/public/turnos/{token}`: sin datos del paciente, ids ni token. */
export interface TurnoGestion {
  inicio: string
  fin: string
  estado: EstadoTurno
  prestacion: { id: number; nombre: string }
  odontologo: {
    slug: string
    nombre: string
    apellido: string
    direccionConsultorio: string | null
  }
  puedeCancelar: boolean
  puedeReprogramar: boolean
}

export type VistaGestion =
  | 'cargando'
  | 'error-link'
  | 'error-carga'
  | 'activo'
  | 'confirmar-cancelacion'
  | 'reprogramando'
  | 'solo-lectura'

/** Qué pasó en esta visita; define el aviso de la vista de solo lectura. */
export type ResultadoGestion =
  | { tipo: 'cancelado' }
  | { tipo: 'reprogramado'; inicio: string }
  | { tipo: 'rechazado' }

/** Aviso que se muestra sobre la vista actual. */
export type AvisoGestion =
  | 'horario-tomado'
  | 'horario-no-disponible'
  | 'no-reprogramable'
  | 'sin-conexion-cancelar'
  | 'sin-conexion-reprogramar'
  | 'sin-conexion-horarios'

export interface EstadoGestion {
  vista: VistaGestion
  turno?: TurnoGestion
  resultado?: ResultadoGestion
  aviso?: AvisoGestion
  /** Cancelación o reprogramación en curso: bloquea un segundo envío. */
  enviando: boolean
  /** Devuelve el foco a "Cancelar turno" al cerrar la confirmación. */
  enfocarCancelar: boolean
  perfil?: PerfilReserva
  cargandoPerfil: boolean
  fecha?: string
  inicioSemana?: string
  horarios: readonly HorarioOpcion[]
  cargandoHorarios: boolean
  horario?: HorarioOpcion
  error?: ErrorApi
}

export type AccionGestion =
  | { tipo: 'cargar' }
  | { tipo: 'turno-cargado'; turno: TurnoGestion }
  | { tipo: 'turno-error'; error: ErrorApi }
  | { tipo: 'abrir-cancelacion' }
  | { tipo: 'cerrar-cancelacion' }
  | { tipo: 'enviar-cancelacion' }
  | { tipo: 'cancelacion-ok' }
  | { tipo: 'abrir-reprogramacion' }
  | { tipo: 'volver-al-turno' }
  | { tipo: 'perfil-cargado'; perfil: PerfilReserva; fecha: string; inicioSemana: string }
  | { tipo: 'perfil-error'; error: ErrorApi }
  | { tipo: 'cambiar-semana'; inicioSemana: string }
  | { tipo: 'disponibilidad-inicio'; fecha: string }
  | { tipo: 'disponibilidad-cargada'; fecha: string; horarios: readonly HorarioOpcion[] }
  | { tipo: 'disponibilidad-error'; error: ErrorApi }
  | { tipo: 'elegir-horario'; horario: HorarioOpcion }
  | { tipo: 'enviar-reprogramacion' }
  | { tipo: 'reprogramacion-ok'; inicio: string }
  | { tipo: 'accion-rechazada'; error: ErrorApi }
  | { tipo: 'horario-rechazado'; error: ErrorApi }
  | { tipo: 'no-reprogramable'; error: ErrorApi }
  | { tipo: 'error-envio'; error: ErrorApi; accion: 'cancelar' | 'reprogramar' }
