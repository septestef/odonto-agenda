import type { DatosPaciente, ErroresPaciente, SolicitudReserva } from './modelo'
import { hayErrores, validarDatosPaciente } from './validaciones'

export type ResultadoSolicitud =
  | { ok: true; solicitud: SolicitudReserva }
  | { ok: false; errores: ErroresPaciente }

/**
 * Arma el cuerpo del POST. El `inicio` se conserva tal como llegó de la API,
 * con su offset, para no reinterpretar la zona horaria.
 */
export function armarSolicitud(
  prestacionId: number,
  inicio: string,
  datos: DatosPaciente,
): ResultadoSolicitud {
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
