import { textoFranja } from './formato'
import type { DiaSemana, Franja } from './modelos'

export { textoFranja }

export const DIAS_SEMANA: readonly { readonly diaSemana: number; readonly nombre: string }[] = [
  { diaSemana: 1, nombre: 'Lunes' },
  { diaSemana: 2, nombre: 'Martes' },
  { diaSemana: 3, nombre: 'Miércoles' },
  { diaSemana: 4, nombre: 'Jueves' },
  { diaSemana: 5, nombre: 'Viernes' },
  { diaSemana: 6, nombre: 'Sábado' },
  { diaSemana: 7, nombre: 'Domingo' },
]

export function ordenarFranjas(franjas: readonly Franja[]): readonly Franja[] {
  return [...franjas].sort(
    (uno, otro) =>
      uno.diaSemana - otro.diaSemana ||
      uno.inicio.localeCompare(otro.inicio) ||
      uno.fin.localeCompare(otro.fin),
  )
}

export function agruparFranjas(franjas: readonly Franja[]): readonly DiaSemana[] {
  const ordenadas = ordenarFranjas(franjas)

  return DIAS_SEMANA.map((dia) => ({
    diaSemana: dia.diaSemana,
    nombre: dia.nombre,
    franjas: ordenadas.filter((franja) => franja.diaSemana === dia.diaSemana),
  }))
}

export function aplanarFranjas(dias: readonly DiaSemana[]): readonly Franja[] {
  return ordenarFranjas(dias.flatMap((dia) => dia.franjas))
}
