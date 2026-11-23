import { hayErrores, validarDatosPaciente } from '../../shared/formulario-paciente'
import type { DatosPaciente, ErroresPaciente, SolicitudTurnoManual } from './modelo'

export type ResultadoSolicitudManual =
  | { ok: true; solicitud: SolicitudTurnoManual }
  | { ok: false; errores: ErroresPaciente }

/**
 * Arma el cuerpo del POST manual. El `inicio` se conserva tal como lo devolvió
 * la disponibilidad autenticada, con su offset, para no reinterpretar la zona.
 */
export function armarSolicitudManual(
  prestacionId: number,
  inicio: string,
  datos: DatosPaciente,
): ResultadoSolicitudManual {
  if (!Number.isInteger(prestacionId) || prestacionId <= 0 || inicio.trim() === '') {
    throw new Error('La selección del turno está incompleta')
  }

  const errores = validarDatosPaciente(datos)
  if (hayErrores(errores)) {
    return { ok: false, errores }
  }

  return {
    ok: true,
    solicitud: {
      prestacionId,
      inicio,
      dni: datos.dni.trim(),
      nombre: datos.nombre.trim(),
      apellido: datos.apellido.trim(),
      telefono: datos.telefono.trim(),
      email: datos.email.trim(),
    },
  }
}
