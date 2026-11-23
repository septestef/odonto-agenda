import type { Franja } from './modelos'

/** Formato único de rango horario visible: `inicio–fin`. */
export function rangoTexto(inicio: string, fin: string): string {
  return `${inicio}–${fin}`
}

export function textoFranja(franja: Franja): string {
  return rangoTexto(franja.inicio, franja.fin)
}
