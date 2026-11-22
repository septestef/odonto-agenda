/** Tipos del dominio de configuración. Sin comportamiento: solo contratos inmutables. */

export interface Franja {
  readonly diaSemana: number
  readonly inicio: string
  readonly fin: string
}

export interface DiaSemana {
  readonly diaSemana: number
  readonly nombre: string
  readonly franjas: readonly Franja[]
}

export interface Horario {
  readonly franjas: readonly Franja[]
}

export interface Prestacion {
  readonly id: number
  readonly nombre: string
  readonly duracionMinutos: number
  readonly activa: boolean
}

export interface TurnoAfectado {
  readonly id: number
  readonly inicio: string
  readonly fin: string
  readonly estado: string
  readonly pacienteNombre: string
  readonly pacienteApellido: string
  readonly prestacionNombre: string
}

export interface Bloqueo {
  readonly id: number
  readonly inicio: string
  readonly fin: string
  readonly motivo?: string
  readonly turnosAfectados: readonly TurnoAfectado[]
}

export type ErroresCampo = Readonly<Record<string, string>>

export interface ResultadoValidacion {
  readonly valido: boolean
  readonly errores: ErroresCampo
  readonly codigo?: string
  readonly primero?: string
}

export interface MomentoLocal {
  readonly fecha: string
  readonly hora: string
}
