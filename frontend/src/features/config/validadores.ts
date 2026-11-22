import { esFechaValida } from '../../shared/fechas'
import type { ErroresCampo, Franja, MomentoLocal, ResultadoValidacion } from './modelos'

export const MENSAJE_RANGO_FRANJA = 'La hora de inicio debe ser anterior a la de fin.'
export const MENSAJE_SOLAPE = 'Las franjas del mismo día no pueden solaparse'
export const MENSAJE_SOLAPE_FRANJA = 'Se superpone con la otra franja.'
export const MENSAJE_DURACION = 'La duración tiene que ser un múltiplo de 15 minutos.'
export const MENSAJE_FIN_BLOQUEO = 'La hora de fin debe ser posterior a la de inicio.'
export const MENSAJE_INICIO_INCOMPLETO = 'Completá la fecha y la hora de inicio.'
export const MENSAJE_FIN_INCOMPLETO = 'Completá la fecha y la hora de fin.'
export const MENSAJE_DIA ='Elegí un día entre el lunes y el domingo.'
export const MENSAJE_HORA = 'Usá el formato HH:mm.'

const FORMATO_HORA = /^([01]\d|2[0-3]):[0-5]\d$/

function resultado(errores: ErroresCampo, codigo?: string): ResultadoValidacion {
  const primero = Object.keys(errores)[0]
  return { valido: Object.keys(errores).length === 0, errores, ...(codigo === undefined ? {} : { codigo }), ...(primero === undefined ? {} : { primero }) }
}

function validarHora(valor: string): boolean {
  return FORMATO_HORA.test(valor)
}

function campoFranja(indice: number): string {
  return `franjas[${indice}]`
}

function mensajeDeFranja(franja: Franja): string | undefined {
  if (!Number.isInteger(franja.diaSemana) || franja.diaSemana < 1 || franja.diaSemana > 7) {
    return MENSAJE_DIA
  }
  if (!validarHora(franja.inicio) || !validarHora(franja.fin)) {
    return MENSAJE_HORA
  }
  if (franja.inicio >= franja.fin) {
    return MENSAJE_RANGO_FRANJA
  }
  return undefined
}

function seSolapan(uno: Franja, otro: Franja): boolean {
  return uno.diaSemana === otro.diaSemana && uno.inicio < otro.fin && otro.inicio < uno.fin
}

function indicesSolapados(franjas: readonly Franja[], validas: readonly number[]): ReadonlySet<number> {
  const marcados = new Set<number>()
  for (const i of validas) {
    for (const j of validas) {
      if (i >= j) {
        continue
      }
      const uno = franjas[i]
      const otro = franjas[j]
      if (uno !== undefined && otro !== undefined && seSolapan(uno, otro)) {
        marcados.add(i)
        marcados.add(j)
      }
    }
  }
  return marcados
}

/** Valida el horario semanal completo: rango de cada franja y solapes dentro del día. */
export function validarFranjas(franjas: readonly Franja[]): ResultadoValidacion {
  const errores: Record<string, string> = {}
  const validas: number[] = []

  franjas.forEach((franja, indice) => {
    const mensaje = mensajeDeFranja(franja)
    if (mensaje === undefined) {
      validas.push(indice)
      return
    }
    errores[campoFranja(indice)] = mensaje
  })

  for (const indice of indicesSolapados(franjas, validas)) {
    errores[campoFranja(indice)] = MENSAJE_SOLAPE_FRANJA
  }

  return resultado(errores, Object.keys(errores).length === 0 ? undefined : 'RN-AG-01')
}

export function validarDuracion(duracion: number): ResultadoValidacion {
  const errores: Record<string, string> = {}
  if (!Number.isInteger(duracion) || duracion <= 0 || duracion % 15 !== 0) {
    errores['duracionMinutos'] = MENSAJE_DURACION
  }
  return resultado(errores, Object.keys(errores).length === 0 ? undefined : 'RN-AG-03')
}

function claveDe(momento: MomentoLocal): string {
  return `${momento.fecha}T${momento.hora}`
}

export function validarRangoBloqueo(inicio: MomentoLocal, fin: MomentoLocal): ResultadoValidacion {
  const errores: Record<string, string> = {}
  if (!esFechaValida(inicio.fecha) || !validarHora(inicio.hora)) {
    errores['inicio'] = MENSAJE_INICIO_INCOMPLETO
  }
  if (!esFechaValida(fin.fecha) || !validarHora(fin.hora)) {
    errores['fin'] = MENSAJE_FIN_INCOMPLETO
  }
  if (Object.keys(errores).length === 0 && claveDe(fin) <= claveDe(inicio)) {
    errores['fin'] = MENSAJE_FIN_BLOQUEO
  }
  return resultado(errores)
}
