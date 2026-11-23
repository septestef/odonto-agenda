import { sumarDias } from '../../shared/fechas'
import type { Descriptor } from '../../shared/ui/componentes'
import { diaLargoDe, diaSemanaDe } from './formato'
import { rangoDeVista } from './navegacion'
import type { VistaAgenda } from './detalle'
import type { BloqueoAgenda, FranjaHorario, TurnoAgenda } from './tipos'

export type EstadoAgenda =
  | 'cargando'
  | 'sin-horarios'
  | 'sin-franjas'
  | 'sin-turnos'
  | 'con-agenda'
  | 'error'

export interface OpcionesEstado {
  fecha: string
  vista: VistaAgenda
  franjas: readonly FranjaHorario[]
  turnos?: readonly TurnoAgenda[]
  bloqueos?: readonly BloqueoAgenda[]
}

export interface OpcionesSeleccion extends OpcionesEstado {
  cargando: boolean
  error: unknown
}

/** El error se decide antes de mirar los datos: nunca se dibuja una agenda parcial. */
export function seleccionarEstado(opciones: OpcionesSeleccion): EstadoAgenda {
  if (opciones.cargando) {
    return 'cargando'
  }
  if (opciones.error !== undefined && opciones.error !== null) {
    return 'error'
  }
  if (opciones.franjas.length === 0) {
    return 'sin-horarios'
  }
  const diasVisibles = new Set<number>()
  if (opciones.vista === 'semana') {
    const { desde, hasta } = rangoDeVista('semana', opciones.fecha)
    for (let fecha = desde; fecha < hasta; fecha = sumarDias(fecha, 1)) {
      diasVisibles.add(diaSemanaDe(fecha))
    }
  } else {
    diasVisibles.add(diaSemanaDe(opciones.fecha))
  }
  if (!opciones.franjas.some((franja) => diasVisibles.has(franja.diaSemana))) {
    return 'sin-franjas'
  }
  if ((opciones.turnos ?? []).length === 0) {
    return 'sin-turnos'
  }
  return 'con-agenda'
}

function esqueletos(): Descriptor[] {
  return Array.from({ length: 3 }, () => ({ etiqueta: 'div', clase: 'esqueleto' }))
}

function descriptorVacio(hijos: Descriptor[]): Descriptor {
  return { etiqueta: 'div', clase: 'empty', hijos }
}

function horariosTexto(franjas: readonly FranjaHorario[]): string {
  const horarios = [...new Set(franjas.map((franja) => `${franja.inicio} a ${franja.fin}`))]
  if (horarios.length === 2) {
    return horarios.join(' y de ')
  }
  return horarios.join(', ')
}

function descriptorError(): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'alert alert--error',
    atributos: { role: 'alert' },
    hijos: [
      { etiqueta: 'strong', texto: 'No pudimos cargar la agenda' },
      { etiqueta: 'div', texto: 'Revisá la conexión y probá de nuevo.' },
      {
        etiqueta: 'button',
        texto: 'Reintentar',
        clase: 'btn btn--secondary reintentar-agenda',
        atributos: { type: 'button' },
        accion: 'reintentar-agenda',
      },
    ],
  }
}

export function descriptorAviso(tipo: 'ya-empezo' | 'cambio' | 'error'): Descriptor {
  if (tipo === 'error') {
    return descriptorError()
  }
  if (tipo === 'ya-empezo') {
    return {
      etiqueta: 'div',
      clase: 'alert alert--warning',
      atributos: { role: 'alert' },
      hijos: [
        { etiqueta: 'strong', texto: 'No se pudo cancelar' },
        { etiqueta: 'div', texto: 'Este turno ya empezó. Actualizamos la agenda.' },
      ],
    }
  }
  return {
    etiqueta: 'div',
    clase: 'alert alert--info',
    atributos: { role: 'alert' },
    hijos: [
      { etiqueta: 'strong', texto: 'Este turno cambió' },
      { etiqueta: 'div', texto: 'Puede que el paciente lo haya cancelado o cambiado de horario. Actualizamos la agenda.' },
    ],
  }
}

export function descriptorEstado(estado: EstadoAgenda, opciones: OpcionesEstado): Descriptor | undefined {
  switch (estado) {
    case 'cargando':
      return {
        etiqueta: 'div',
        clase: 'franja',
        atributos: { 'aria-busy': 'true', 'aria-label': 'Cargando la agenda' },
        hijos: esqueletos(),
      }
    case 'sin-horarios':
      return descriptorVacio([
        { etiqueta: 'h3', texto: 'Todavía no cargaste tus horarios' },
        { etiqueta: 'p', texto: 'Definí los días y las franjas en las que atendés para empezar a recibir turnos.' },
        {
          etiqueta: 'a',
          texto: 'Ir a Configuración',
          clase: 'btn btn--secondary',
          atributos: { href: '/config' },
        },
      ])
    case 'sin-franjas':
      return descriptorVacio([
        { etiqueta: 'h3', texto: `El ${diaLargoDe(opciones.fecha).toLowerCase()} no atendés` },
        { etiqueta: 'p', texto: 'No hay franjas cargadas para este día.' },
      ])
    case 'sin-turnos':
      return descriptorVacio([
        {
          etiqueta: 'h3',
          texto: opciones.vista === 'dia' ? 'No tenés turnos este día' : 'No tenés turnos esta semana',
        },
        {
          etiqueta: 'p',
          texto: `Atendés de ${horariosTexto(opciones.franjas)}. Los turnos que reserven tus pacientes aparecen acá.`,
        },
      ])
    case 'error':
      return descriptorError()
    case 'con-agenda':
      return undefined
  }
}
