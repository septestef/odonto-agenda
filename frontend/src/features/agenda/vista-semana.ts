import { fechaLocalArgentina, sumarDias } from '../../shared/fechas'
import type { Descriptor } from '../../shared/ui/componentes'
import {
  diaCortoDe,
  diaLargoDe,
  diaSemanaDe,
  esMismoDia,
  fechaCorta,
  horaAhora,
 horaDesdeMinutos,
  horaLocal,
  minutosDe,
} from './formato'
import { rangoDeVista } from './navegacion'
import type { BloqueoAgenda, FranjaHorario, TurnoAgenda } from './tipos'

export interface OpcionesVistaSemana {
  fecha: string
  ahora: Date
  franjas: readonly FranjaHorario[]
  turnos: readonly TurnoAgenda[]
  bloqueos: readonly BloqueoAgenda[]
  seleccionadoId?: number
}

interface Columna {
  fecha: string
  franjas: readonly FranjaHorario[]
  esHoy: boolean
}

function columnasDe(opciones: OpcionesVistaSemana): Columna[] {
  const { desde } = rangoDeVista('semana', opciones.fecha)
  const hoy = fechaLocalArgentina(opciones.ahora)

  return Array.from({ length: 7 }, (_, indice) => {
    const fecha = sumarDias(desde, indice)
    const dia = diaSemanaDe(fecha)
    return {
      fecha,
      franjas: opciones.franjas.filter((franja) => franja.diaSemana === dia),
      esHoy: fecha === hoy,
    }
  }).filter((columna) => columna.franjas.length > 0)
}

function limites(columnas: readonly Columna[]): { primera: number; ultima: number } {
  let primera = Number.POSITIVE_INFINITY
  let ultima = Number.NEGATIVE_INFINITY
  for (const columna of columnas) {
    for (const franja of columna.franjas) {
      primera = Math.min(primera, minutosDe(franja.inicio))
      ultima = Math.max(ultima, minutosDe(franja.fin))
    }
  }
  return { primera, ultima }
}

function estiloRango(inicio: number, fin: number, primera: number): string {
  const top = inicio - primera
  const altura = Math.max(0, fin - inicio)
  return `top:calc(var(--min) * ${top});height:calc(var(--min) * ${altura})`
}

function cerradosDe(columna: Columna, primera: number): Descriptor[] {
  const franjas = [...columna.franjas].sort((a, b) => a.inicio.localeCompare(b.inicio))
  const cerrados: Descriptor[] = []
  for (let indice = 1; indice < franjas.length; indice += 1) {
    const anterior = franjas[indice - 1]
    const siguiente = franjas[indice]
    if (anterior === undefined || siguiente === undefined) {
      continue
    }
    const inicio = minutosDe(anterior.fin)
    const fin = minutosDe(siguiente.inicio)
    if (fin > inicio) {
      cerrados.push({
        etiqueta: 'div',
        clase: 'cerrado',
        texto: 'Fuera de horario',
        atributos: { style: estiloRango(inicio, fin, primera) },
      })
    }
  }
  return cerrados
}

function horasDe(primera: number, ultima: number): Descriptor[] {
  const horas: Descriptor[] = []
  for (let minuto = primera; minuto < ultima; minuto += 60) {
    horas.push({
      etiqueta: 'span',
      texto: horaDesdeMinutos(minuto),
      atributos: { style: `top:calc(var(--min) * ${minuto - primera})` },
    })
  }
  return horas
}

function cabeceraDe(columna: Columna): Descriptor {
  return {
    etiqueta: 'div',
    clase: ['semana__cab', ...(columna.esHoy ? ['semana__cab--hoy'] : [])].join(' '),
    atributos: {
      'data-fecha': columna.fecha,
      ...(columna.esHoy
        ? { style: 'scroll-start-target: auto; scroll-snap-align: start' }
        : {}),
    },
    hijos: [
      { etiqueta: 'span', texto: diaCortoDe(columna.fecha) },
      { etiqueta: 'small', texto: `${fechaCorta(columna.fecha).slice(0, 2)}${columna.esHoy ? ' · hoy' : ''}` },
    ],
  }
}

function eventosDe(
  columna: Columna,
  opciones: OpcionesVistaSemana,
  primera: number,
  afectados: ReadonlySet<number>,
): Descriptor[] {
  return opciones.turnos
    .filter((turno) => esMismoDia(turno.inicio, columna.fecha))
    .map((turno) => {
      const inicio = minutosDe(horaLocal(turno.inicio))
      const fin = minutosDe(horaLocal(turno.fin))
      const clases = [
        'ev',
        turno.estado === 'CANCELADO' ? 'ev--cancelado' : 'ev--reservado',
        ...(afectados.has(turno.id) ? ['ev--afectado'] : []),
        ...(turno.id === opciones.seleccionadoId ? ['ev--sel'] : []),
      ]
      const apellido: Descriptor[] = turno.paciente.apellido === undefined
        ? []
        : [{ etiqueta: 'span', texto: turno.paciente.apellido }]
      const prestacion: Descriptor[] = turno.prestacion.nombre === undefined
        ? []
        : [{ etiqueta: 'span', texto: turno.prestacion.nombre }]
      const detalles: Descriptor[] = [
        { etiqueta: 'b', texto: horaLocal(turno.inicio) },
        ...apellido,
        ...prestacion,
      ]

      return {
        etiqueta: 'button',
        clase: clases.join(' '),
        atributos: {
          type: 'button',
          'data-turno-id': String(turno.id),
          ...(turno.id === opciones.seleccionadoId ? { 'aria-current': 'true' } : {}),
          style: `${estiloRango(inicio, fin, primera)};margin-top:1px`,
        },
        accion: 'ver-turno',
        hijos: detalles,
      }
    })
}

function bloquesDe(
  columna: Columna,
  opciones: OpcionesVistaSemana,
  primera: number,
): Descriptor[] {
  return opciones.bloqueos
    .filter((bloqueo) => esMismoDia(bloqueo.inicio, columna.fecha))
    .map((bloqueo) => ({
      etiqueta: 'div',
      clase: 'bloq',
      atributos: {
        'data-bloqueo-id': String(bloqueo.id),
        style: estiloRango(minutosDe(horaLocal(bloqueo.inicio)), minutosDe(horaLocal(bloqueo.fin)), primera),
      },
      texto: bloqueo.motivo === null ? 'Bloqueo' : `Bloqueo · ${bloqueo.motivo}`,
    }))
}

function resumenDe(opciones: OpcionesVistaSemana): Descriptor[] {
  const afectados = opciones.bloqueos.flatMap((bloqueo) => bloqueo.turnosAfectados)
  if (afectados.length === 0) {
    return []
  }

  const detalles = afectados
    .map((afectado, indice) => {
      const fecha = fechaLocalArgentina(new Date(afectado.inicio))
      const dia = diaLargoDe(fecha)
      return `${indice === 0 ? dia : dia.toLowerCase()} ${fechaCorta(fecha).slice(0, 2)} a las ${horaLocal(afectado.inicio)}`
    })
    .join(' y ')

  return [
    {
      etiqueta: 'div',
      clase: 'alert alert--warning',
      atributos: { role: 'status' },
      hijos: [
        {
          etiqueta: 'strong',
          texto: `Hay ${afectados.length} turno${afectados.length === 1 ? '' : 's'} dentro de bloqueos esta semana`,
        },
        { etiqueta: 'div', texto: `${detalles}. Tocalos para revisarlos o cancelarlos.` },
      ],
    },
  ]
}

function leyenda(): Descriptor {
  const entradas: Readonly<Record<string, string>> = {
    Reservado: 'background:var(--color-primary-100)',
    Cancelado: 'background:var(--color-surface-alt)',
    Bloqueo: 'background:var(--bloqueo-bg)',
    'Fuera de horario': 'background:var(--color-bg)',
    'Turno dentro de un bloqueo': 'box-shadow:inset 0 0 0 2px var(--color-warning-600)',
  }
  return {
    etiqueta: 'div',
    clase: 'leyenda',
    hijos: Object.entries(entradas).map(([texto, estilo]) => ({
      etiqueta: 'span',
      hijos: [
        { etiqueta: 'i', atributos: { style: estilo } },
        { etiqueta: 'span', texto },
      ],
    })),
  }
}

function columnaDe(
  columna: Columna,
  opciones: OpcionesVistaSemana,
  primera: number,
  horaVisual: string | undefined,
  afectados: ReadonlySet<number>,
): Descriptor {
  const hijos: Descriptor[] = [
    ...cerradosDe(columna, primera),
    ...bloquesDe(columna, opciones, primera),
    ...eventosDe(columna, opciones, primera, afectados),
  ]
  if (columna.esHoy && horaVisual !== undefined) {
    const minuto = minutosDe(horaVisual)
    if (minuto > primera && minuto < ultimaDe(columna)) {
      hijos.push({
        etiqueta: 'div',
        clase: 'linea-ahora',
        atributos: {
          style: `top:calc(var(--min) * ${minuto - primera})`,
          'aria-label': `Ahora ${horaVisual}`,
        },
      })
    }
  }

  return {
    etiqueta: 'div',
    clase: ['semana__col', ...(columna.esHoy ? ['semana__col--hoy'] : [])].join(' '),
    atributos: { 'data-fecha': columna.fecha },
    hijos,
  }
}

function ultimaDe(columna: Columna): number {
  return Math.max(...columna.franjas.map((franja) => minutosDe(franja.fin)))
}

export function descriptorSemana(opciones: OpcionesVistaSemana): Descriptor {
  const columnas = columnasDe(opciones)
  const { primera, ultima } = limites(columnas)
  const hoy = fechaLocalArgentina(opciones.ahora)
  const horaVisual = horaAhora(opciones.ahora, hoy)
  const afectados = new Set(
    opciones.bloqueos.flatMap((bloqueo) => bloqueo.turnosAfectados.map((turno) => turno.id)),
  )
  const grid: Descriptor[] = [
    { etiqueta: 'div', clase: 'semana__cab' },
    ...columnas.map(cabeceraDe),
    {
      etiqueta: 'div',
      clase: 'semana__horas',
      atributos: { 'aria-hidden': 'true' },
      hijos: horasDe(primera, ultima),
    },
    ...columnas.map((columna) => columnaDe(columna, opciones, primera, horaVisual, afectados)),
  ]

  return {
    etiqueta: 'div',
    clase: 'semana-contenedor',
    hijos: [
      ...resumenDe(opciones),
      {
        etiqueta: 'div',
        clase: 'semana',
        atributos: { tabindex: '0', 'aria-label': 'Agenda semanal, desplazable' },
        hijos: [{ etiqueta: 'div', clase: 'semana__grid', hijos: grid }],
      },
      leyenda(),
    ],
  }
}
