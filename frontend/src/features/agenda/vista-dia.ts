import type { Descriptor } from '../../shared/ui/componentes'
import {
  diaSemanaDe,
  etiquetaEstado,
  etiquetaOrigen,
  horaAhora,
  horaDesdeMinutos,
  horaLocal,
  minutosDe,
  nombrePaciente,
  periodoDe,
  rangoLocal,
} from './formato'
import { puedeCancelar } from './reglas'
import type { BloqueoAgenda, FranjaHorario, TurnoAgenda } from './tipos'

export interface OpcionesVistaDia {
  fecha: string
  ahora: Date
  franjas: readonly FranjaHorario[]
  turnos: readonly TurnoAgenda[]
  bloqueos: readonly BloqueoAgenda[]
  seleccionadoId?: number
}

interface ItemFranja {
  minutos: number
  fin: number
  descriptor: Descriptor
}

function descriptorLibre(inicio: number, fin: number): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'libre',
    texto: `${horaDesdeMinutos(inicio)}–${horaDesdeMinutos(fin)} · libre`,
  }
}

function descriptorAfectado(
  afectado: BloqueoAgenda['turnosAfectados'][number],
  ahora: Date,
): Descriptor {
  const nombre = [afectado.pacienteNombre, afectado.pacienteApellido]
    .filter((parte) => parte !== undefined)
    .join(' ')
  const detalles = [nombre, afectado.prestacionNombre].filter((parte) => parte !== '').join(' · ')
  const cancelable = puedeCancelar(afectado, ahora)
  const detallesNodos: Descriptor[] = detalles === ''
    ? []
    : [{ etiqueta: 'span', texto: ` · ${detalles}` }]
  const acciones: Descriptor[] = cancelable
    ? [
        {
          etiqueta: 'button',
          texto: 'Cancelar turno',
          clase: 'btn btn--danger btn--sm',
          atributos: { type: 'button', 'data-turno-id': String(afectado.id) },
          accion: 'cancelar-turno',
        },
      ]
    : []

  return {
    etiqueta: 'div',
    clase: 'afectado',
    atributos: { 'data-turno-id': String(afectado.id) },
    hijos: [
      {
        etiqueta: 'span',
        hijos: [
          { etiqueta: 'b', texto: rangoLocal(afectado.inicio, afectado.fin) },
          ...detallesNodos,
        ],
      },
      ...acciones,
    ],
  }
}

function descriptorBloqueo(
  bloqueo: BloqueoAgenda,
  ahora: Date,
): Descriptor {
  const cantidad = bloqueo.turnosAfectados.length
  const motivo: Descriptor[] = bloqueo.motivo === null
    ? []
    : [{ etiqueta: 'span', clase: 'bloqueo__titulo', texto: bloqueo.motivo }]
  const afectados: Descriptor[] = cantidad === 0
    ? []
    : [
        {
          etiqueta: 'div',
          clase: 'bloqueo__afectados',
          hijos: [
            {
              etiqueta: 'p',
              clase: 'bloqueo__aviso',
              texto: `Hay ${cantidad} turno${cantidad === 1 ? '' : 's'} dentro del bloqueo. El bloqueo no lo cancela: decidí vos.`,
            },
            ...bloqueo.turnosAfectados.map((afectado) => descriptorAfectado(afectado, ahora)),
          ],
        },
      ]

  return {
    etiqueta: 'div',
    clase: 'bloqueo',
    atributos: { 'data-bloqueo-id': String(bloqueo.id) },
    hijos: [
      {
        etiqueta: 'div',
        clase: 'bloqueo__fila',
        hijos: [
          { etiqueta: 'span', clase: 'turno__hora', texto: rangoLocal(bloqueo.inicio, bloqueo.fin) },
          { etiqueta: 'span', clase: 'badge badge--bloqueo', texto: 'Bloqueo' },
          ...motivo,
        ],
      },
      ...afectados,
    ],
  }
}

function claseTurno(turno: TurnoAgenda, paso: boolean, seleccionado: boolean): string {
  return [
    'turno',
    `turno--${turno.estado.toLowerCase()}`,
    ...(paso ? ['turno--pasado'] : []),
    ...(seleccionado ? ['turno--seleccionado'] : []),
  ].join(' ')
}

function metaTurno(turno: TurnoAgenda, ahora: Date): string {
  const partes: string[] = []
  if (turno.prestacion.nombre !== undefined) {
    partes.push(turno.prestacion.nombre)
  }
  if (turno.estado === 'CANCELADO' && turno.canceladoPor !== null) {
    partes.push(turno.canceladoPor === 'PACIENTE' ? 'canceló el paciente' : 'canceló el odontólogo')
  }
  const paso = Date.parse(turno.inicio) <= ahora.getTime()
  const enCurso = Date.parse(turno.fin) > ahora.getTime()
  if (turno.estado === 'RESERVADO' && paso && enCurso) {
    partes.push('en curso')
  }
  return partes.join(' · ')
}

function descriptorTurno(
  turno: TurnoAgenda,
  ahora: Date,
  seleccionado: boolean,
): Descriptor {
  const paso = Date.parse(turno.inicio) <= ahora.getTime()
  const cancelable = puedeCancelar(turno, ahora)
  const acciones: Descriptor[] = cancelable
    ? [
        {
          etiqueta: 'div',
          clase: 'turno__acciones',
          hijos: [
            {
              etiqueta: 'button',
              texto: 'Ver',
              clase: 'btn btn--secondary btn--sm',
              atributos: { type: 'button', 'data-turno-id': String(turno.id) },
              accion: 'ver-turno',
            },
            {
              etiqueta: 'button',
              texto: 'Cancelar',
              clase: 'btn btn--danger btn--sm',
              atributos: { type: 'button', 'data-turno-id': String(turno.id) },
              accion: 'cancelar-turno',
            },
          ],
        },
      ]
    : []

  return {
    etiqueta: 'article',
    clase: claseTurno(turno, paso, seleccionado),
    atributos: {
      tabindex: '0',
      'data-turno-id': String(turno.id),
      ...(seleccionado ? { 'aria-current': 'true' } : {}),
    },
    accion: 'ver-turno',
    hijos: [
      {
        etiqueta: 'div',
        clase: 'turno__fila',
        hijos: [
          { etiqueta: 'span', clase: 'turno__hora', texto: `${horaLocal(turno.inicio)}–${horaLocal(turno.fin)}` },
          { etiqueta: 'span', clase: `badge badge--${turno.estado.toLowerCase()}`, texto: etiquetaEstado(turno.estado) },
          { etiqueta: 'span', clase: `badge badge--${turno.origen.toLowerCase()}`, texto: etiquetaOrigen(turno.origen) },
        ],
      },
      { etiqueta: 'div', clase: 'turno__quien', texto: nombrePaciente(turno.paciente) },
      { etiqueta: 'div', clase: 'turno__meta', texto: metaTurno(turno, ahora) },
      ...acciones,
    ],
  }
}

function descriptorAhora(hora: string): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'ahora',
    texto: `Ahora · ${hora}`,
    atributos: { role: 'note' },
  }
}

function itemsDeFranja(opciones: OpcionesVistaDia, franja: FranjaHorario): ItemFranja[] {
  const inicio = minutosDe(franja.inicio)
  const fin = minutosDe(franja.fin)
  const intervalos: ItemFranja[] = []

  for (const turno of opciones.turnos) {
    const inicioTurno = minutosDe(horaLocal(turno.inicio))
    const finTurno = minutosDe(horaLocal(turno.fin))
    if (inicioTurno < fin && finTurno > inicio) {
      intervalos.push({
        minutos: inicioTurno,
        fin: finTurno,
        descriptor: descriptorTurno(turno, opciones.ahora, turno.id === opciones.seleccionadoId),
      })
    }
  }

  for (const bloqueo of opciones.bloqueos) {
    const inicioBloqueo = minutosDe(horaLocal(bloqueo.inicio))
    const finBloqueo = minutosDe(horaLocal(bloqueo.fin))
    if (inicioBloqueo < fin && finBloqueo > inicio) {
      intervalos.push({
        minutos: inicioBloqueo,
        fin: finBloqueo,
        descriptor: descriptorBloqueo(bloqueo, opciones.ahora),
      })
    }
  }

  intervalos.sort((a, b) => a.minutos - b.minutos)
  const items: ItemFranja[] = []
  let cursor = inicio

  for (const intervalo of intervalos) {
    if (intervalo.minutos > cursor) {
      items.push({ minutos: cursor, fin: intervalo.minutos, descriptor: descriptorLibre(cursor, intervalo.minutos) })
    }
    items.push(intervalo)
    cursor = Math.max(cursor, intervalo.fin)
  }

  if (cursor < fin) {
    items.push({ minutos: cursor, fin, descriptor: descriptorLibre(cursor, fin) })
  }

  const horaVisual = horaAhora(opciones.ahora, opciones.fecha)
  const minutoVisual = horaVisual === undefined ? undefined : minutosDe(horaVisual)
  if (horaVisual !== undefined && minutoVisual !== undefined && minutoVisual > inicio && minutoVisual < fin) {
    items.push({ minutos: minutoVisual, fin: minutoVisual, descriptor: descriptorAhora(horaVisual) })
  }

  return items.sort((a, b) => a.minutos - b.minutos)
}

export function descriptorDia(opciones: OpcionesVistaDia): Descriptor {
  const dia = diaSemanaDe(opciones.fecha)
  const franjas = [...opciones.franjas]
    .filter((franja) => franja.diaSemana === dia)
    .sort((a, b) => a.inicio.localeCompare(b.inicio))
  const hijos: Descriptor[] = []

  franjas.forEach((franja, indice) => {
    if (indice > 0) {
      const anterior = franjas[indice - 1]
      if (anterior !== undefined) {
        hijos.push({
          etiqueta: 'p',
          clase: 'fuera-horario',
          texto: `${anterior.fin} – ${franja.inicio} · fuera del horario de atención`,
        })
      }
    }

    hijos.push({
      etiqueta: 'section',
      clase: 'franja',
      atributos: { 'aria-labelledby': `franja-dia-${indice}` },
      hijos: [
        {
          etiqueta: 'h2',
          clase: 'franja__titulo',
          atributos: { id: `franja-dia-${indice}` },
          hijos: [
            { etiqueta: 'span', texto: periodoDe(franja.inicio) },
            { etiqueta: 'span', texto: `${franja.inicio} – ${franja.fin}` },
          ],
        },
        ...itemsDeFranja(opciones, franja).map((item) => item.descriptor),
      ],
    })
  })

  return { etiqueta: 'div', clase: 'dia', hijos }
}
