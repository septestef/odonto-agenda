import { esFechaValida, fechaLocalArgentina, horaArgentina, sumarDias } from './fechas'

/** Formatos de fecha y hora para las pantallas públicas (reserva y gestión del turno). */

const ZONA_ARGENTINA = 'America/Argentina/Buenos_Aires'

function fechaInstante(fecha: string): Date {
  const partes = fecha.split('-').map((parte) => Number(parte))
  const anio = partes[0] ?? 1970
  const mes = partes[1] ?? 1
  const dia = partes[2] ?? 1

  return new Date(Date.UTC(anio, mes - 1, dia, 12))
}

function partesFecha(fecha: string, opciones: Intl.DateTimeFormatOptions): Intl.DateTimeFormatPart[] {
  return new Intl.DateTimeFormat('es-AR', { timeZone: ZONA_ARGENTINA, ...opciones }).formatToParts(
    fechaInstante(fecha),
  )
}

function parte(partes: Intl.DateTimeFormatPart[], tipo: Intl.DateTimeFormatPartTypes): string {
  const valor = partes.find((item) => item.type === tipo)?.value
  if (valor === undefined) {
    throw new Error(`No se pudo formatear ${tipo}`)
  }
  return valor
}

function capitalizar(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

export function formatearFechaLarga(fecha: string): string {
  const partes = partesFecha(fecha, { weekday: 'long', day: 'numeric', month: 'long' })

  return `${capitalizar(parte(partes, 'weekday'))} ${parte(partes, 'day')} de ${parte(partes, 'month')}`
}

export function formatearDiaCorto(fecha: string): string {
  return parte(partesFecha(fecha, { weekday: 'short' }), 'weekday')
}

export function numeroDia(fecha: string): string {
  return parte(partesFecha(fecha, { day: 'numeric' }), 'day')
}

/** `DD/MM` de una fecha `YYYY-MM-DD`, sin depender de la zona del navegador. */
export function formatearDiaMes(fecha: string): string {
  const partes = partesFecha(fecha, { day: '2-digit', month: '2-digit' })

  return `${parte(partes, 'day')}/${parte(partes, 'month')}`
}

export function formatearRangoSemana(inicio: string): string {
  const fin = sumarDias(inicio, 6)
  const partesInicio = partesFecha(inicio, { day: 'numeric', month: 'long' })
  const partesFin = partesFecha(fin, { day: 'numeric', month: 'long' })
  const diaInicio = parte(partesInicio, 'day')
  const diaFin = parte(partesFin, 'day')
  const mesInicio = parte(partesInicio, 'month')
  const mesFin = parte(partesFin, 'month')

  if (mesInicio === mesFin) {
    return `Semana del ${diaInicio} al ${diaFin} de ${mesInicio}`
  }

  return `Semana del ${diaInicio} de ${mesInicio} al ${diaFin} de ${mesFin}`
}

export function horaDeInicio(inicio: string): string {
  const instante = new Date(inicio)
  if (Number.isNaN(instante.getTime())) {
    return inicio.slice(11, 16)
  }
  return horaArgentina(instante)
}

export function fechaDeInicio(inicio: string): string {
  const instante = new Date(inicio)
  if (Number.isNaN(instante.getTime())) {
    const fecha = inicio.slice(0, 10)
    return esFechaValida(fecha) ? fecha : ''
  }
  return fechaLocalArgentina(instante)
}

/** Nombre del día en minúscula ("jueves"), para frases como "el jueves 26". */
export function nombreDia(fecha: string): string {
  return parte(partesFecha(fecha, { weekday: 'long' }), 'weekday')
}

/** "Viernes 27/11", para comparar horarios en poco espacio. */
export function formatearDiaCortoConFecha(fecha: string): string {
  return `${capitalizar(nombreDia(fecha))} ${formatearDiaMes(fecha)}`
}

/** Suma minutos a un instante ISO y devuelve la hora local `HH:mm`. */
export function horaMasMinutos(inicio: string, minutos: number): string {
  const instante = new Date(inicio)
  if (Number.isNaN(instante.getTime())) {
    return ''
  }
  return horaArgentina(new Date(instante.getTime() + minutos * 60_000))
}
