import type { AccionGestion, EstadoGestion, TurnoGestion } from './modelo'

/** Cómo reacciona la pantalla a un rechazo de cancelar o reprogramar (D5). */
export type ReaccionRechazo = 'accion-rechazada' | 'horario-rechazado' | 'no-reprogramable' | 'error-envio'

const ACCION_RECHAZADA = new Set(['NO_ENCONTRADO', 'RN-TU-07', 'RN-SE-02'])
const HORARIO_RECHAZADO = new Set([
  'RN-TU-01',
  'RN-TU-03',
  'RN-TU-11',
  'RN-AG-05',
  'VALIDACION',
  'SOLICITUD_INVALIDA',
])

export function reaccionARechazo(codigo: string, accion: 'cancelar' | 'reprogramar'): ReaccionRechazo {
  if (ACCION_RECHAZADA.has(codigo)) {
    return 'accion-rechazada'
  }
  if (accion === 'reprogramar' && codigo === 'RN-SE-04') {
    return 'no-reprogramable'
  }
  if (accion === 'reprogramar' && HORARIO_RECHAZADO.has(codigo)) {
    return 'horario-rechazado'
  }
  return 'error-envio'
}

export function estadoInicialGestion(): EstadoGestion {
  return {
    vista: 'cargando',
    enviando: false,
    enfocarCancelar: false,
    cargandoPerfil: false,
    horarios: [],
    cargandoHorarios: false,
  }
}

export function tieneAcciones(turno: TurnoGestion): boolean {
  return turno.puedeCancelar || turno.puedeReprogramar
}

type CampoOpcional = 'fecha' | 'inicioSemana' | 'horario' | 'error' | 'aviso' | 'resultado'

function sin(estado: EstadoGestion, ...campos: CampoOpcional[]): EstadoGestion {
  const copia: EstadoGestion = { ...estado }
  for (const campo of campos) {
    delete copia[campo]
  }
  return copia
}

/** Limpia todo lo de la reprogramación: día, semana, horarios y selección. */
function sinSeleccion(estado: EstadoGestion): EstadoGestion {
  return { ...sin(estado, 'fecha', 'inicioSemana', 'horario', 'error'), horarios: [], cargandoHorarios: false, cargandoPerfil: false }
}

function sinAviso(estado: EstadoGestion): EstadoGestion {
  return sin(estado, 'aviso', 'error')
}

function sinHorario(estado: EstadoGestion): EstadoGestion {
  return sin(estado, 'horario')
}

/** Un aviso de rechazo anterior no sigue visible al empezar otra acción. */
function sinRechazoPrevio(estado: EstadoGestion): EstadoGestion {
  return estado.resultado?.tipo === 'rechazado' ? sin(estado, 'resultado') : estado
}

export function reducir(estado: EstadoGestion, accion: AccionGestion): EstadoGestion {
  const base = { ...estado, enfocarCancelar: false }

  switch (accion.tipo) {
    case 'cargar':
      return { ...base, vista: 'cargando', enviando: false }

    case 'turno-cargado': {
      const { turno } = accion
      const despuesDeAccion = estado.resultado?.tipo === 'cancelado' || estado.resultado?.tipo === 'reprogramado'
      const vista = !despuesDeAccion && tieneAcciones(turno) ? 'activo' : 'solo-lectura'
      const limpio = sinSeleccion(sinAviso(base))
      return {
        ...limpio,
        vista,
        turno,
        enviando: false,
        ...(estado.aviso === 'no-reprogramable' ? { aviso: estado.aviso } : {}),
      }
    }

    case 'turno-error':
      return {
        ...base,
        vista: accion.error.codigo === 'NO_ENCONTRADO' && estado.resultado === undefined ? 'error-link' : 'error-carga',
        enviando: false,
        error: accion.error,
      }

    case 'abrir-cancelacion':
      if (estado.vista !== 'activo' || estado.turno?.puedeCancelar !== true) {
        return estado
      }
      return { ...sinRechazoPrevio(sinAviso(base)), vista: 'confirmar-cancelacion' }

    case 'cerrar-cancelacion':
      if (estado.vista !== 'confirmar-cancelacion' || estado.enviando) {
        return estado
      }
      return { ...sinAviso(base), vista: 'activo', enfocarCancelar: true }

    case 'enviar-cancelacion':
      if (estado.vista !== 'confirmar-cancelacion' || estado.enviando) {
        return estado
      }
      return { ...sinAviso(base), enviando: true }

    case 'cancelacion-ok':
      return { ...sinAviso(base), vista: 'cargando', enviando: false, resultado: { tipo: 'cancelado' } }

    case 'abrir-reprogramacion':
      if (estado.vista !== 'activo' || estado.turno?.puedeReprogramar !== true) {
        return estado
      }
      return {
        ...sinRechazoPrevio(sinSeleccion(sinAviso(base))),
        vista: 'reprogramando',
        cargandoPerfil: estado.perfil === undefined,
      }

    case 'volver-al-turno':
      if (estado.vista !== 'reprogramando' || estado.enviando) {
        return estado
      }
      return { ...sinSeleccion(sinAviso(base)), vista: 'activo' }

    case 'perfil-cargado':
      if (estado.vista !== 'reprogramando') {
        return { ...base, perfil: accion.perfil }
      }
      return {
        ...base,
        perfil: accion.perfil,
        cargandoPerfil: false,
        fecha: accion.fecha,
        inicioSemana: accion.inicioSemana,
      }

    case 'perfil-error':
      return { ...base, cargandoPerfil: false, aviso: 'sin-conexion-horarios', error: accion.error }

    case 'cambiar-semana':
      if (estado.vista !== 'reprogramando' || estado.enviando) {
        return estado
      }
      return { ...base, inicioSemana: accion.inicioSemana }

    case 'disponibilidad-inicio': {
      if (estado.vista !== 'reprogramando' || estado.enviando) {
        return estado
      }
      const mismoDia = accion.fecha === estado.fecha
      const conAviso =
        mismoDia && (estado.aviso === 'horario-tomado' || estado.aviso === 'horario-no-disponible')
          ? base
          : sinAviso(base)
      return {
        ...sinHorario(conAviso),
        fecha: accion.fecha,
        cargandoHorarios: true,
        horarios: mismoDia ? estado.horarios : [],
      }
    }

    case 'disponibilidad-cargada':
      if (accion.fecha !== estado.fecha) {
        return estado
      }
      return {
        ...(estado.aviso === 'sin-conexion-horarios' ? sinAviso(base) : base),
        horarios: accion.horarios,
        cargandoHorarios: false,
      }

    case 'disponibilidad-error':
      return { ...base, horarios: [], cargandoHorarios: false, aviso: 'sin-conexion-horarios', error: accion.error }

    case 'elegir-horario':
      if (estado.vista !== 'reprogramando' || estado.enviando) {
        return estado
      }
      return { ...sinAviso(base), horario: accion.horario }

    case 'enviar-reprogramacion':
      if (estado.vista !== 'reprogramando' || estado.enviando || estado.horario === undefined) {
        return estado
      }
      return { ...sinAviso(base), enviando: true }

    case 'reprogramacion-ok':
      return {
        ...sinAviso(base),
        vista: 'cargando',
        enviando: false,
        resultado: { tipo: 'reprogramado', inicio: accion.inicio },
      }

    case 'accion-rechazada':
      return {
        ...sinSeleccion(sinAviso(base)),
        vista: 'cargando',
        enviando: false,
        resultado: { tipo: 'rechazado' },
      }

    case 'horario-rechazado':
      return {
        ...sinHorario(base),
        enviando: false,
        aviso: accion.error.codigo === 'RN-TU-01' ? 'horario-tomado' : 'horario-no-disponible',
        error: accion.error,
      }

    case 'no-reprogramable':
      return {
        ...sinSeleccion(base),
        vista: 'cargando',
        enviando: false,
        aviso: 'no-reprogramable',
        error: accion.error,
      }

    case 'error-envio':
      return {
        ...base,
        enviando: false,
        aviso: accion.accion === 'cancelar' ? 'sin-conexion-cancelar' : 'sin-conexion-reprogramar',
        error: accion.error,
      }
  }
}
