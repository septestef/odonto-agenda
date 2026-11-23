/** Tipos propios de la agenda del odontólogo, derivados de `unknown` sin `any`. */

export type EstadoTurno = 'RESERVADO' | 'CANCELADO' | 'ATENDIDO' | 'AUSENTE'
export type OrigenTurno = 'ONLINE' | 'MANUAL'
export type CanceladoPor = 'PACIENTE' | 'ODONTOLOGO'

export interface PacienteAgenda {
  id?: number
  dni?: string
  nombre?: string
  apellido?: string
  telefono?: string
  email?: string
}

export interface PrestacionAgenda {
  id?: number
  nombre?: string
  duracionMinutos?: number
}

export interface TurnoAgenda {
  id: number
  inicio: string
  fin: string
  estado: EstadoTurno
  origen: OrigenTurno
  paciente: PacienteAgenda
  prestacion: PrestacionAgenda
  canceladoPor: CanceladoPor | null
  canceladoEn: string | null
}

export interface FranjaHorario {
  diaSemana: number
  inicio: string
  fin: string
}

export interface TurnoAfectado {
  id: number
  inicio: string
  fin: string
  estado: EstadoTurno
  pacienteNombre?: string
  pacienteApellido?: string
  prestacionNombre?: string
}

export interface BloqueoAgenda {
  id: number
  inicio: string
  fin: string
  motivo: string | null
  turnosAfectados: TurnoAfectado[]
}

const ESTADOS: ReadonlySet<string> = new Set(['RESERVADO', 'CANCELADO', 'ATENDIDO', 'AUSENTE'])
const ORIGENES: ReadonlySet<string> = new Set(['ONLINE', 'MANUAL'])
const CANCELADORES: ReadonlySet<string> = new Set(['PACIENTE', 'ODONTOLOGO'])

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function esEnteroPositivo(valor: unknown): valor is number {
  return typeof valor === 'number' && Number.isInteger(valor) && valor > 0
}

function textoOpcional(valor: unknown): string | undefined {
  return typeof valor === 'string' && valor !== '' ? valor : undefined
}

function estadoDe(valor: unknown): EstadoTurno | undefined {
  return typeof valor === 'string' && ESTADOS.has(valor) ? (valor as EstadoTurno) : undefined
}

export function respuestaInvalida(): never {
  throw new Error('La agenda devolvió datos que no pudimos interpretar.')
}

function pacienteDe(valor: unknown): PacienteAgenda {
  if (!esRegistro(valor)) {
    return {}
  }
  const paciente: PacienteAgenda = {}
  const id = valor['id']
  if (esEnteroPositivo(id)) {
    paciente.id = id
  }
  for (const clave of ['dni', 'nombre', 'apellido', 'telefono', 'email'] as const) {
    const texto = textoOpcional(valor[clave])
    if (texto !== undefined) {
      paciente[clave] = texto
    }
  }
  return paciente
}

function prestacionDe(valor: unknown): PrestacionAgenda {
  if (!esRegistro(valor)) {
    return {}
  }
  const prestacion: PrestacionAgenda = {}
  const id = valor['id']
  const duracion = valor['duracionMinutos']
  const nombre = textoOpcional(valor['nombre'])
  if (esEnteroPositivo(id)) {
    prestacion.id = id
  }
  if (esEnteroPositivo(duracion)) {
    prestacion.duracionMinutos = duracion
  }
  if (nombre !== undefined) {
    prestacion.nombre = nombre
  }
  return prestacion
}

function turnoDe(valor: unknown): TurnoAgenda {
  if (!esRegistro(valor)) {
    return respuestaInvalida()
  }
  const id = valor['id']
  const inicio = valor['inicio']
  const fin = valor['fin']
  const estado = estadoDe(valor['estado'])
  const origen = valor['origen']
  if (
    !esEnteroPositivo(id) ||
    typeof inicio !== 'string' ||
    typeof fin !== 'string' ||
    Number.isNaN(Date.parse(inicio)) ||
    Number.isNaN(Date.parse(fin)) ||
    estado === undefined ||
    typeof origen !== 'string' ||
    !ORIGENES.has(origen)
  ) {
    return respuestaInvalida()
  }
  const canceladoPor = valor['canceladoPor']
  const canceladoEn = valor['canceladoEn']

  return {
    id,
    inicio,
    fin,
    estado,
    origen: origen as OrigenTurno,
    paciente: pacienteDe(valor['paciente']),
    prestacion: prestacionDe(valor['prestacion']),
    canceladoPor: typeof canceladoPor === 'string' && CANCELADORES.has(canceladoPor)
      ? (canceladoPor as CanceladoPor)
      : null,
    canceladoEn: typeof canceladoEn === 'string' && canceladoEn !== '' ? canceladoEn : null,
  }
}

/** Valida el array de turnos de C-12 y conserva solo los campos del contrato P2. */
export function leerTurnos(cuerpo: unknown): TurnoAgenda[] {
  if (!Array.isArray(cuerpo)) {
    return respuestaInvalida()
  }
  return cuerpo.map(turnoDe)
}

function horaValida(valor: unknown): boolean {
  return typeof valor === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(valor)
}

function franjaDe(valor: unknown): FranjaHorario {
  if (!esRegistro(valor)) {
    return respuestaInvalida()
  }
  const dia = valor['diaSemana']
  const inicio = valor['inicio']
  const fin = valor['fin']
  if (
    typeof dia !== 'number' ||
    !Number.isInteger(dia) ||
    dia < 1 ||
    dia > 7 ||
    !horaValida(inicio) ||
    !horaValida(fin)
  ) {
    return respuestaInvalida()
  }
  return { diaSemana: dia, inicio: inicio as string, fin: fin as string }
}

function afectadoDe(valor: unknown): TurnoAfectado {
  if (!esRegistro(valor)) {
    return respuestaInvalida()
  }
  const id = valor['id']
  const inicio = valor['inicio']
  const fin = valor['fin']
  const estado = estadoDe(valor['estado'])
  if (
    !esEnteroPositivo(id) ||
    typeof inicio !== 'string' ||
    typeof fin !== 'string' ||
    estado === undefined
  ) {
    return respuestaInvalida()
  }
  const afectado: TurnoAfectado = { id, inicio, fin, estado }
  for (const clave of ['pacienteNombre', 'pacienteApellido', 'prestacionNombre'] as const) {
    const texto = textoOpcional(valor[clave])
    if (texto !== undefined) {
      afectado[clave] = texto
    }
  }
  return afectado
}

function bloqueoDe(valor: unknown): BloqueoAgenda {
  if (!esRegistro(valor)) {
    return respuestaInvalida()
  }
  const id = valor['id']
  const inicio = valor['inicio']
  const fin = valor['fin']
  const afectados = valor['turnosAfectados']
  if (
    !esEnteroPositivo(id) ||
    typeof inicio !== 'string' ||
    typeof fin !== 'string' ||
    !Array.isArray(afectados)
  ) {
    return respuestaInvalida()
  }
  return {
    id,
    inicio,
    fin,
    motivo: textoOpcional(valor['motivo']) ?? null,
    turnosAfectados: afectados.map(afectadoDe),
  }
}

/** Valida `{franjas:[...]}` de `GET /api/horarios`. */
export function leerFranjas(cuerpo: unknown): FranjaHorario[] {
  if (!esRegistro(cuerpo) || !Array.isArray(cuerpo['franjas'])) {
    return respuestaInvalida()
  }
  return (cuerpo['franjas'] as unknown[]).map(franjaDe)
}

/** Valida `{bloqueos:[...]}` de `GET /api/bloqueos`, incluidos sus afectados. */
export function leerBloqueos(cuerpo: unknown): BloqueoAgenda[] {
  if (!esRegistro(cuerpo) || !Array.isArray(cuerpo['bloqueos'])) {
    return respuestaInvalida()
  }
  return (cuerpo['bloqueos'] as unknown[]).map(bloqueoDe)
}
