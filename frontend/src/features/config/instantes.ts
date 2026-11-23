import { esFechaValida, fechaLocalArgentina, horaArgentina } from '../../shared/fechas'
import { rangoTexto } from './formato'
import type { MomentoLocal } from './modelos'

const ZONA_ARGENTINA = 'America/Argentina/Buenos_Aires'
const FORMATO_HORA = /^([01]\d|2[0-3]):[0-5]\d$/

interface Partes {
  readonly anio: number
  readonly mes: number
  readonly dia: number
  readonly hora: number
  readonly minuto: number
  readonly segundo: number
}

function partesEnZona(instante: Date): Partes {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: ZONA_ARGENTINA,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(instante)
  const valor = (tipo: Intl.DateTimeFormatPartTypes): number => {
    const texto = partes.find((parte) => parte.type === tipo)?.value
    if (texto === undefined) {
      throw new Error('No se pudo resolver la zona de Argentina')
    }
    return Number(texto)
  }

  return {
    anio: valor('year'),
    mes: valor('month'),
    dia: valor('day'),
    hora: valor('hour'),
    minuto: valor('minute'),
    segundo: valor('second'),
  }
}

function offsetMinutos(instante: Date): number {
  const partes = partesEnZona(instante)
  const comoUTC = Date.UTC(partes.anio, partes.mes - 1, partes.dia, partes.hora, partes.minuto, partes.segundo)

  return Math.round((comoUTC - instante.getTime()) / 60_000)
}

function textoOffset(minutos: number): string {
  const signo = minutos < 0 ? '-' : '+'
  const absoluto = Math.abs(minutos)
  const horas = String(Math.floor(absoluto / 60)).padStart(2, '0')
  const minutosRestantes = String(absoluto % 60).padStart(2, '0')

  return `${signo}${horas}:${minutosRestantes}`
}

/** Compone un instante ISO con el offset vigente en Argentina para esa fecha. */
export function instanteConOffset(fecha: string, hora: string): string {
  if (!esFechaValida(fecha) || !FORMATO_HORA.test(hora)) {
    throw new Error('No se puede armar un instante con una fecha o hora inválidas')
  }

  const [anio, mes, dia] = fecha.split('-').map(Number)
  const [horas, minutos] = hora.split(':').map(Number)
  if (anio === undefined || mes === undefined || dia === undefined || horas === undefined || minutos === undefined) {
    throw new Error('No se puede armar un instante con una fecha o hora inválidas')
  }

  const comoUTC = Date.UTC(anio, mes - 1, dia, horas, minutos)
  const offsetInicial = offsetMinutos(new Date(comoUTC))
  const instante = new Date(comoUTC - offsetInicial * 60_000)
  const offset = offsetMinutos(instante)

  return `${fechaLocalArgentina(instante)}T${horaArgentina(instante)}:00${textoOffset(offset)}`
}

function instanteDe(iso: string): Date {
  const instante = new Date(iso)
  if (Number.isNaN(instante.getTime())) {
    throw new Error('No se puede mostrar un instante inválido')
  }
  return instante
}

export function desglosarInstante(iso: string): MomentoLocal {
  const instante = instanteDe(iso)
  return { fecha: fechaLocalArgentina(instante), hora: horaArgentina(instante) }
}

export function fechaVisible(iso: string): string {
  const [anio, mes, dia] = fechaLocalArgentina(instanteDe(iso)).split('-')
  return `${dia}/${mes}/${anio}`
}

export function rangoVisible(inicio: string, fin: string): string {
  return rangoTexto(horaArgentina(instanteDe(inicio)), horaArgentina(instanteDe(fin)))
}

/**
 * Rango de un bloqueo: `27/11/2026 · 10:00–12:00` si empieza y termina el mismo día
 * (en hora de Argentina) y `27/11/2026 10:00 – 29/11/2026 12:00` si abarca varios días.
 */
export function rangoBloqueoVisible(inicio: string, fin: string): string {
  const desde = desglosarInstante(inicio)
  const hasta = desglosarInstante(fin)
  if (desde.fecha === hasta.fecha) {
    return `${fechaVisible(inicio)} · ${rangoTexto(desde.hora, hasta.hora)}`
  }
  return `${fechaVisible(inicio)} ${desde.hora} – ${fechaVisible(fin)} ${hasta.hora}`
}
