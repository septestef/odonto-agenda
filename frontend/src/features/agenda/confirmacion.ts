import { fechaLocalArgentina } from '../../shared/fechas'
import type { Descriptor } from '../../shared/ui/componentes'
import { horaLocal, nombrePaciente, tituloFecha } from './formato'
import type { TurnoAgenda } from './tipos'

export interface OpcionesConfirmacion {
  turno: TurnoAgenda
  ahora: Date
  enBloqueo: boolean
}

function resumenDe(turno: TurnoAgenda): Descriptor {
  const fecha = fechaLocalArgentina(new Date(turno.inicio))
  const servicio = [turno.prestacion.nombre, turno.origen === 'ONLINE' ? 'reservado online' : 'cargado por el odontólogo']
    .filter((parte) => parte !== undefined)
    .join(' · ')
  return {
    etiqueta: 'div',
    clase: 'resumen',
    hijos: [
      { etiqueta: 'strong', texto: `${tituloFecha(fecha)}, ${horaLocal(turno.inicio)}` },
      { etiqueta: 'span', texto: servicio },
    ],
  }
}

/** Diálogo derivado de la copia aprobada en `mockups/cancelar-confirmacion.html`. */
export function descriptorConfirmacion(opciones: OpcionesConfirmacion): Descriptor {
  const { turno, enBloqueo } = opciones
  const texto = enBloqueo
    ? 'Le avisamos por email a la paciente. No se puede deshacer.'
    : 'Le avisamos por email a la paciente y el horario vuelve a quedar libre para reservas. No se puede deshacer.'

  return {
    etiqueta: 'div',
    clase: 'dialogo-confirmacion',
    teclas: { Escape: 'cerrar-confirmacion' },
    atributos: {
      role: 'alertdialog',
      'aria-modal': 'true',
      'aria-labelledby': 'c-titulo',
      'aria-describedby': 'c-texto',
      'data-cerrar-escape': 'cerrar-confirmacion',
    },
    hijos: [
      {
        etiqueta: 'h2',
        texto: `¿Cancelás el turno de ${nombrePaciente(turno.paciente)}?`,
        atributos: { id: 'c-titulo' },
      },
      resumenDe(turno),
      { etiqueta: 'p', texto, atributos: { id: 'c-texto' } },
      {
        etiqueta: 'div',
        clase: 'dialogo__acciones',
        hijos: [
          {
            etiqueta: 'button',
            texto: 'Sí, cancelar turno',
            clase: 'btn btn--danger-solid confirmacion__confirmar',
            atributos: { type: 'button', 'data-turno-id': String(turno.id) },
            accion: 'confirmar-cancelacion',
          },
          {
            etiqueta: 'button',
            texto: 'No, volver',
            clase: 'btn btn--secondary confirmacion__volver',
            atributos: { type: 'button', autofocus: '' },
            enfocar: true,
            accion: 'cerrar-confirmacion',
          },
        ],
      },
    ],
  }
}
