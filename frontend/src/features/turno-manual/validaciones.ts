import { MENSAJES_PACIENTE_MANUAL, validarDatosPaciente as validarDatosCompartidos } from '../../shared/formulario-paciente'
import type { DatosPaciente, ErroresPaciente } from './modelo'

/**
 * Reutiliza los validadores de la reserva online con los textos del alta manual:
 * acá el odontólogo carga los datos del paciente, no los suyos (C-15).
 */
export {
  erroresPorCampo,
  hayErrores,
  validarApellido,
  validarDni,
  validarEmail,
  validarNombre,
  validarTelefono,
} from '../../shared/formulario-paciente'

export function validarDatosPaciente(datos: DatosPaciente): ErroresPaciente {
  return validarDatosCompartidos(datos, MENSAJES_PACIENTE_MANUAL)
}
