/**
 * Las validaciones del paciente viven en `shared/formulario-paciente.ts` para que la reserva online
 * y el alta manual usen exactamente las mismas reglas y mensajes. Se re-exportan aquí para no
 * cambiar los imports existentes de C-10.
 */
export {
  erroresPorCampo,
  hayErrores,
  validarApellido,
  validarDatosPaciente,
  validarDni,
  validarEmail,
  validarNombre,
  validarTelefono,
} from '../../shared/formulario-paciente'
