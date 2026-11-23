/**
 * Las validaciones del paciente son compartidas con la reserva online: un mismo
 * DNI, teléfono o email se juzga igual en ambos flujos. Se re-exportan para que
 * el módulo de turno manual no dependa de la ruta de `features/reservar`.
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
