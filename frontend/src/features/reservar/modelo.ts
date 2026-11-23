import type { ErrorApi } from '../../shared/error-api'
import type { DatosPaciente, ErroresPaciente } from '../../shared/formulario-paciente'

export type { CampoPaciente, DatosPaciente, ErroresPaciente } from '../../shared/formulario-paciente'
export { DATOS_PACIENTE_VACIOS } from '../../shared/formulario-paciente'

export interface PrestacionReserva {
  id: number
  nombre: string
  duracionMinutos: number
}

export interface PerfilReserva {
  slug: string
  nombre: string
  apellido: string
  direccionConsultorio: string | null
  zonaHoraria: string
  fechaMinima: string
  fechaMaxima: string
  prestaciones: readonly PrestacionReserva[]
}

export interface HorarioOpcion {
  inicio: string
  hora: string
}

export interface DisponibilidadPublica {
  fecha: string
  prestacionId: number
  duracionMinutos: number
  horarios: readonly HorarioOpcion[]
}

export interface SolicitudReserva {
  prestacionId: number
  inicio: string
  dni: string
  nombre: string
  apellido: string
  telefono: string
  email: string
}

export interface OdontologoPublico {
  nombre: string
  apellido: string
  direccionConsultorio: string | null
}

export interface ReservaConfirmada {
  inicio: string
  fin: string
  prestacion: PrestacionReserva
  odontologo: OdontologoPublico
  email: string
}

export type PasoReserva =
  | 'cargando-perfil'
  | 'error-perfil'
  | 'eligiendo-prestacion'
  | 'eligiendo-horario'
  | 'sin-horarios'
  | 'completando-datos'
  | 'enviando'
  | 'exito'
  | 'conflicto'
  | 'error-envio'

export interface EstadoReserva {
  paso: PasoReserva
  perfil?: PerfilReserva
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

export type AccionReserva =
  | { tipo: 'iniciar' }
  | { tipo: 'perfil-cargado'; perfil: PerfilReserva }
  | { tipo: 'perfil-error'; error: ErrorApi }
  | { tipo: 'elegir-prestacion'; prestacionId: number; fecha: string }
  | { tipo: 'cambiar-semana'; inicioSemana: string }
  | { tipo: 'disponibilidad-inicio'; fecha: string; conservarError?: boolean }
  | { tipo: 'buscar-inicio' }
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
