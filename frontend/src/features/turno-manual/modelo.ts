import type { ErrorApi } from '../../shared/error-api'
import type { DatosPaciente, ErroresPaciente } from '../../shared/formulario-paciente'
import type {
  DisponibilidadPublica,
  HorarioOpcion,
  PrestacionReserva,
  ReservaConfirmada,
} from '../reservar/modelo'

export type { DatosPaciente, ErroresPaciente }
export type { DisponibilidadPublica, HorarioOpcion, PrestacionReserva, ReservaConfirmada }

/** Datos que el formulario necesita del odontólogo autenticado. */
export interface PerfilManual {
  fechaMinima: string
  fechaMaxima: string
  prestaciones: readonly PrestacionReserva[]
}

/** Cuerpo de `POST /api/turnos`; el odontólogo sale siempre de la sesión. */
export interface SolicitudTurnoManual {
  prestacionId: number
  inicio: string
  dni: string
  nombre: string
  apellido: string
  telefono: string
  email: string
}

export type PasoManual =
  | 'cargando-perfil'
  | 'error-perfil'
  | 'eligiendo-prestacion'
  | 'eligiendo-horario'
  | 'sin-horarios'
  | 'completando-datos'
  | 'enviando'
  | 'conflicto'
  | 'error-envio'
  | 'exito'

export interface EstadoManual {
  paso: PasoManual
  perfil?: PerfilManual
  prestacionId?: number
  fecha?: string
  inicioSemana?: string
  duracionMinutos?: number
  horarios: readonly HorarioOpcion[]
  cargandoHorarios: boolean
  horario?: HorarioOpcion
  datos: DatosPaciente
  errores: ErroresPaciente
  reserva?: ReservaConfirmada
  error?: ErrorApi
}

export type AccionManual =
  | { tipo: 'iniciar' }
  | { tipo: 'perfil-cargado'; perfil: PerfilManual }
  | { tipo: 'perfil-error'; error: ErrorApi }
  | { tipo: 'elegir-prestacion'; prestacionId: number; fecha: string }
  | { tipo: 'cambiar-semana'; inicioSemana: string }
  | { tipo: 'disponibilidad-inicio'; fecha: string; conservarError?: boolean }
  | { tipo: 'disponibilidad-cargada'; disponibilidad: DisponibilidadPublica; conservarError?: boolean }
  | { tipo: 'disponibilidad-error'; error: ErrorApi }
  | { tipo: 'elegir-horario'; horario: HorarioOpcion }
  | { tipo: 'ir-a-datos' }
  | { tipo: 'volver-a-horario'; datos?: DatosPaciente }
  | { tipo: 'volver-a-datos' }
  | { tipo: 'validacion-local'; datos: DatosPaciente; errores: ErroresPaciente }
  | { tipo: 'validacion-servidor'; datos: DatosPaciente; errores: ErroresPaciente; error: ErrorApi }
  | { tipo: 'confirmar'; datos: DatosPaciente }
  | { tipo: 'exito'; reserva: ReservaConfirmada }
  | { tipo: 'conflicto'; error: ErrorApi }
  | { tipo: 'error-envio'; error: ErrorApi }
  | { tipo: 'reintentar-envio' }
  | { tipo: 'reiniciar' }
