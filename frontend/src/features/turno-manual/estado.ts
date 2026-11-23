import { DATOS_PACIENTE_VACIOS } from '../../shared/formulario-paciente'
import type { AccionManual, EstadoManual, PerfilManual } from './modelo'

export function estadoInicialManual(): EstadoManual {
  return {
    paso: 'cargando-perfil',
    horarios: [],
    cargandoHorarios: false,
    datos: { ...DATOS_PACIENTE_VACIOS },
    errores: {},
  }
}

function estadoEligiendoPrestacion(perfil: PerfilManual): EstadoManual {
  return {
    ...estadoInicialManual(),
    paso: 'eligiendo-prestacion',
    perfil,
    inicioSemana: perfil.fechaMinima,
  }
}

function pasoDeHorarios(estado: EstadoManual): EstadoManual['paso'] {
  return estado.horarios.length === 0 ? 'sin-horarios' : 'eligiendo-horario'
}

/** Máquina de estados pura del alta manual. Las acciones inválidas no cambian nada. */
export function reducir(estado: EstadoManual, accion: AccionManual): EstadoManual {
  switch (accion.tipo) {
    case 'iniciar':
      return estadoInicialManual()

    case 'perfil-cargado':
      return estadoEligiendoPrestacion(accion.perfil)

    case 'perfil-error':
      return { ...estado, paso: 'error-perfil', error: accion.error }

    case 'elegir-prestacion': {
      const prestacion = estado.perfil?.prestaciones.find((item) => item.id === accion.prestacionId)
      if (prestacion === undefined) {
        return estado
      }
      return {
        ...estado,
        paso: 'eligiendo-horario',
        prestacionId: prestacion.id,
        duracionMinutos: prestacion.duracionMinutos,
        fecha: accion.fecha,
        inicioSemana: estado.inicioSemana ?? accion.fecha,
        horarios: [],
        cargandoHorarios: true,
        horario: undefined,
        errores: {},
        error: undefined,
      }
    }

    case 'cambiar-semana': {
      const perfil = estado.perfil
      if (perfil === undefined) {
        return estado
      }
      if (accion.inicioSemana < perfil.fechaMinima || accion.inicioSemana > perfil.fechaMaxima) {
        return estado
      }
      return {
        ...estado,
        inicioSemana: accion.inicioSemana,
        fecha: undefined,
        duracionMinutos: undefined,
        horarios: [],
        cargandoHorarios: false,
        horario: undefined,
        error: undefined,
      }
    }

    case 'disponibilidad-inicio':
      if (estado.prestacionId === undefined) {
        return estado
      }
      return {
        ...estado,
        paso: 'eligiendo-horario',
        fecha: accion.fecha,
        inicioSemana: estado.inicioSemana ?? accion.fecha,
        horarios: [],
        cargandoHorarios: true,
        horario: undefined,
        error: accion.conservarError === true ? estado.error : undefined,
      }

    case 'disponibilidad-cargada': {
      if (estado.prestacionId === undefined) {
        return estado
      }
      const horarios = accion.disponibilidad.horarios
      return {
        ...estado,
        paso: horarios.length === 0 ? 'sin-horarios' : 'eligiendo-horario',
        fecha: accion.disponibilidad.fecha,
        duracionMinutos: accion.disponibilidad.duracionMinutos,
        horarios,
        cargandoHorarios: false,
        horario: undefined,
        error: accion.conservarError === true ? estado.error : undefined,
      }
    }

    case 'disponibilidad-error':
      return {
        ...estado,
        paso: 'eligiendo-horario',
        horarios: [],
        cargandoHorarios: false,
        horario: undefined,
        error: accion.error,
      }

    case 'elegir-horario': {
      const existe = estado.horarios.some((horario) => horario.inicio === accion.horario.inicio)
      if (!existe) {
        return estado
      }
      return { ...estado, paso: 'eligiendo-horario', horario: accion.horario, error: undefined }
    }

    case 'ir-a-datos':
      if (estado.horario === undefined || estado.prestacionId === undefined || estado.fecha === undefined) {
        return estado
      }
      return { ...estado, paso: 'completando-datos', errores: {}, error: undefined }

    case 'volver-a-horario':
      if (estado.perfil === undefined) {
        return estado
      }
      return {
        ...estado,
        paso: pasoDeHorarios(estado),
        datos: accion.datos ?? estado.datos,
        errores: {},
        error: undefined,
      }

    case 'volver-a-datos':
      if (estado.paso !== 'error-envio') {
        return estado
      }
      return { ...estado, paso: 'completando-datos', errores: {}, error: undefined }

    case 'validacion-local':
      if (estado.paso !== 'completando-datos') {
        return estado
      }
      return { ...estado, datos: accion.datos, errores: accion.errores }

    case 'validacion-servidor':
      return {
        ...estado,
        paso: 'completando-datos',
        datos: accion.datos,
        errores: accion.errores,
        error: accion.error,
      }

    case 'confirmar':
      if (estado.paso !== 'completando-datos') {
        return estado
      }
      return { ...estado, paso: 'enviando', datos: accion.datos, errores: {}, error: undefined }

    case 'exito':
      if (estado.paso !== 'enviando') {
        return estado
      }
      return { ...estado, paso: 'exito', reserva: accion.reserva, error: undefined }

    case 'conflicto':
      if (estado.paso !== 'enviando') {
        return estado
      }
      return {
        ...estado,
        paso: 'conflicto',
        horario: undefined,
        cargandoHorarios: true,
        error: accion.error,
      }

    case 'error-envio':
      if (estado.paso !== 'enviando') {
        return estado
      }
      return { ...estado, paso: 'error-envio', error: accion.error }

    case 'reintentar-envio':
      if (estado.paso !== 'error-envio') {
        return estado
      }
      return { ...estado, paso: 'enviando', error: undefined }

    case 'reiniciar':
      if (estado.perfil === undefined) {
        return estado
      }
      return estadoEligiendoPrestacion(estado.perfil)
  }
}

/** Códigos que obligan a volver a elegir horario sin descartar los datos del paciente. */
const CODIGOS_CONFLICTO: ReadonlySet<string> = new Set([
  'RN-TU-01',
  'RN-TU-11',
  'RN-TU-12',
  'RN-AG-05',
])

export function esConflictoManual(codigo: string): boolean {
  return CODIGOS_CONFLICTO.has(codigo)
}
