import { fechaLocalArgentina } from '../../shared/fechas'
import type { Descriptor, Etiqueta } from '../../shared/ui/componentes'
import {
  diaLargoDe,
  etiquetaEstado,
  etiquetaOrigen,
  fechaCorta,
  horaLocal,
  nombrePaciente,
} from './formato'
import { puedeCancelar } from './reglas'
import type { BloqueoAgenda, TurnoAgenda } from './tipos'

export type VistaAgenda = 'dia' | 'semana'

export interface OpcionesDetalle {
  turno: TurnoAgenda
  ahora: Date
  bloqueo?: BloqueoAgenda | undefined
}

export interface OpcionesContenedorDetalle extends OpcionesDetalle {
  vista: VistaAgenda
  ancho: number
}

function fila(etiqueta: string, valor: Descriptor): Descriptor[] {
  return [
    {
      etiqueta: 'div',
      clase: 'dato',
      hijos: [
        { etiqueta: 'dt', texto: etiqueta },
        valor,
      ],
    },
  ]
}

function texto(textoValor: string): Descriptor {
  return { etiqueta: 'dd', texto: textoValor }
}

function avisoDeBloqueo(bloqueo: BloqueoAgenda | undefined): Descriptor[] {
  if (bloqueo === undefined) {
    return []
  }
  const motivo = bloqueo.motivo ?? 'sin motivo'
  return [
    {
      etiqueta: 'div',
      clase: 'alert alert--warning',
      atributos: { role: 'status' },
      hijos: [
        { etiqueta: 'strong', texto: 'Está dentro de un bloqueo' },
        {
          etiqueta: 'div',
          texto: `"${motivo}", ${horaLocal(bloqueo.inicio)} a ${horaLocal(bloqueo.fin)}. El turno sigue reservado hasta que lo canceles.`,
        },
      ],
    },
  ]
}

function datosDe(turno: TurnoAgenda): Descriptor[] {
  const filas: Descriptor[] = []
  const { nombre, duracionMinutos } = turno.prestacion
  if (nombre !== undefined || duracionMinutos !== undefined) {
    const prestacion = [nombre, duracionMinutos === undefined ? undefined : `(${duracionMinutos} min)`]
      .filter((parte) => parte !== undefined)
      .join(' ')
    filas.push(...fila('Prestación', texto(prestacion)))
  }
  if (turno.paciente.dni !== undefined) {
    filas.push(...fila('DNI', texto(turno.paciente.dni)))
  }
  if (turno.paciente.telefono !== undefined) {
    filas.push(...fila('Teléfono', {
      etiqueta: 'dd',
      hijos: [{
        etiqueta: 'a',
        texto: turno.paciente.telefono,
        atributos: { href: `tel:${turno.paciente.telefono.replace(/\s/g, '')}` },
      }],
    }))
  }
  if (turno.paciente.email !== undefined) {
    filas.push(...fila('Email', texto(turno.paciente.email)))
  }
  filas.push(...fila('Origen', texto(
    turno.origen === 'ONLINE' ? 'Lo reservó el paciente online' : 'Lo cargó el odontólogo',
  )))
  if (turno.canceladoPor !== null) {
    filas.push(...fila('Cancelado por', texto(
      turno.canceladoPor === 'PACIENTE' ? 'Lo canceló el paciente' : 'Lo canceló el odontólogo',
    )))
  }
  if (turno.canceladoEn !== null) {
    const fecha = fechaLocalArgentina(new Date(turno.canceladoEn))
    filas.push(...fila('Cancelado el', texto(
      `${diaLargoDe(fecha)} ${fechaCorta(fecha)} a las ${horaLocal(turno.canceladoEn)}`,
    )))
  }
  return filas
}

function panelDeAcciones(acciones: readonly Descriptor[]): Descriptor[] {
  return acciones.length === 0
    ? []
    : [{ etiqueta: 'div', clase: 'panel__acciones', hijos: acciones }]
}

export function descriptorDetalle(opciones: OpcionesDetalle): Descriptor {
  const { turno, ahora } = opciones
  const fecha = fechaLocalArgentina(new Date(turno.inicio))
  const acciones: Descriptor[] = puedeCancelar(turno, ahora)
    ? [{
        etiqueta: 'button',
        texto: 'Cancelar turno',
        clase: 'btn btn--danger detalle__cancelar',
        atributos: { type: 'button', 'data-turno-id': String(turno.id) },
        accion: 'cancelar-turno',
      }]
    : []

  return {
    etiqueta: 'div',
    clase: 'detalle__contenido',
    hijos: [
      {
        etiqueta: 'header',
        clase: 'panel__cab',
        hijos: [
          {
            etiqueta: 'div',
            hijos: [
              {
                etiqueta: 'h2',
                texto: nombrePaciente(turno.paciente),
                atributos: { id: 'detalle-titulo' },
              },
              {
                etiqueta: 'p',
                texto: `${diaLargoDe(fecha)} ${fechaCorta(fecha)} · ${horaLocal(turno.inicio)} a ${horaLocal(turno.fin)}`,
              },
            ],
          },
          {
            etiqueta: 'button',
            texto: '✕',
            clase: 'btn btn--ghost btn--icono detalle__cerrar',
            atributos: { type: 'button', 'aria-label': 'Cerrar el detalle', autofocus: '' },
            accion: 'cerrar-detalle',
          },
        ],
      },
      {
        etiqueta: 'div',
        clase: 'panel__badges',
        hijos: [
          {
            etiqueta: 'span',
            clase: `badge badge--${turno.estado.toLowerCase()}`,
            texto: etiquetaEstado(turno.estado),
          },
          {
            etiqueta: 'span',
            clase: `badge badge--${turno.origen.toLowerCase()}`,
            texto: etiquetaOrigen(turno.origen),
          },
        ],
      },
      ...avisoDeBloqueo(opciones.bloqueo),
      { etiqueta: 'dl', clase: 'datos', hijos: datosDe(turno) },
      ...panelDeAcciones(acciones),
    ],
  }
}

function contenedorDe(vista: VistaAgenda, ancho: number): string {
  if (ancho < 720) {
    return 'detalle--hoja'
  }
  return vista === 'dia' && ancho >= 1100 ? 'detalle--panel' : 'detalle--cajon'
}

export function contenedorDetalle(opciones: OpcionesContenedorDetalle): Descriptor {
  const etiqueta: Etiqueta = 'aside'
  return {
    etiqueta,
    clase: `detalle ${contenedorDe(opciones.vista, opciones.ancho)}`,
    atributos: {
      role: 'dialog',
      'aria-modal': 'true',
      'aria-labelledby': 'detalle-titulo',
    },
    hijos: [descriptorDetalle(opciones)],
  }
}
