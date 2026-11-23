import { fechaLocalArgentina, horaArgentina, restarDias } from '../../shared/fechas'

/** Formatea instantes del DTO siempre en hora de Argentina. */
export function horaLocal(instante: string): string {
  return horaArgentina(new Date(instante))
}

export function rangoLocal(inicio: string, fin: string): string {
  return `${horaLocal(inicio)}–${horaLocal(fin)}`
}

export function minutosDe(hora: string): number {
  return Number(hora.slice(0, 2)) * 60 + Number(hora.slice(3, 5))
}

export function horaDesdeMinutos(minutos: number): string {
  const horas = String(Math.floor(minutos / 60)).padStart(2, '0')
  const resto = String(minutos % 60).padStart(2, '0')
  return `${horas}:${resto}`
}

export function diaSemanaDe(fecha: string): number {
  const dia = new Date(`${fecha}T00:00:00Z`).getUTCDay()
  return dia === 0 ? 7 : dia
}

export function esMismoDia(instante: string, fecha: string): boolean {
  return fechaLocalArgentina(new Date(instante)) === fecha
}

/** Hora visual del día, o `undefined` si el reloj está en otra fecha local. */
export function horaAhora(ahora: Date, fecha: string): string | undefined {
  return fechaLocalArgentina(ahora) === fecha ? horaArgentina(ahora) : undefined
}

export function fechaCorta(fecha: string): string {
  return `${fecha.slice(8, 10)}/${fecha.slice(5, 7)}`
}

const DIAS_LARGOS = [
  'domingo',
  'lunes',
  'martes',
  'miércoles',
  'jueves',
  'viernes',
  'sábado',
] as const

const DIAS_CORTOS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'] as const

const MESES_LARGOS = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
] as const

function capitalizar(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

export function diaCortoDe(fecha: string): string {
  return DIAS_CORTOS[new Date(`${fecha}T12:00:00Z`).getUTCDay()] ?? ''
}

export function diaLargoDe(fecha: string): string {
  return capitalizar(DIAS_LARGOS[new Date(`${fecha}T12:00:00Z`).getUTCDay()] ?? '')
}

export function tituloFecha(fecha: string): string {
  const instante = new Date(`${fecha}T12:00:00Z`)
  const dia = DIAS_LARGOS[instante.getUTCDay()] ?? ''
  const mes = MESES_LARGOS[instante.getUTCMonth()] ?? ''
  return `${capitalizar(dia)} ${instante.getUTCDate()} de ${mes}`
}

/**
 * Título de un rango semiabierto `[desde, hasta)`: `hasta` no se muestra, así que
 * el último día visible es `hasta - 1`, igual que en `rangoDeVista`.
 * Usa el mismo estilo que `tituloFecha` (día sin cero, mes en minúsculas).
 * Si los extremos caen en años distintos se incluye el año en ambos para no
 * perder contexto: «28 de diciembre de 2026 al 1 de enero de 2027».
 */
export function tituloRango(desde: string, hasta: string): string {
  const inicio = new Date(`${desde}T12:00:00Z`)
  const fin = new Date(`${restarDias(hasta, 1)}T12:00:00Z`)
  const diaInicio = inicio.getUTCDate()
  const diaFin = fin.getUTCDate()
  const mesInicio = MESES_LARGOS[inicio.getUTCMonth()] ?? ''
  const mesFin = MESES_LARGOS[fin.getUTCMonth()] ?? ''
  const anioInicio = inicio.getUTCFullYear()
  const anioFin = fin.getUTCFullYear()

  if (anioInicio !== anioFin) {
    return `${diaInicio} de ${mesInicio} de ${anioInicio} al ${diaFin} de ${mesFin} de ${anioFin}`
  }
  if (inicio.getUTCMonth() !== fin.getUTCMonth()) {
    return `${diaInicio} de ${mesInicio} al ${diaFin} de ${mesFin}`
  }
  return `${diaInicio} al ${diaFin} de ${mesFin}`
}

export function periodoDe(hora: string): string {
  const minutos = minutosDe(hora)
  if (minutos < 12 * 60) {
    return 'Mañana'
  }
  if (minutos < 18 * 60) {
    return 'Tarde'
  }
  return 'Noche'
}

export function etiquetaEstado(estado: string): string {
  switch (estado) {
    case 'RESERVADO':
      return 'Reservado'
    case 'CANCELADO':
      return 'Cancelado'
    case 'ATENDIDO':
      return 'Atendido'
    case 'AUSENTE':
      return 'Ausente'
    default:
      return 'Reservado'
  }
}

export function etiquetaOrigen(origen: string): string {
  return origen === 'ONLINE' ? 'Online' : 'Manual'
}

export function nombrePaciente(paciente: { nombre?: string; apellido?: string }): string {
  return [paciente.nombre, paciente.apellido].filter((parte) => parte !== undefined).join(' ')
}
