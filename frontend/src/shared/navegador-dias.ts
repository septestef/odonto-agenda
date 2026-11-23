import { sumarDias } from './fechas'
import { formatearDiaCorto, formatearFechaLarga, formatearRangoSemana, numeroDia } from './formato-fechas'
import type { Descriptor } from './ui/componentes'

/**
 * Navegación de días compartida por la reserva pública (C-10) y la gestión del
 * turno (C-11): semanas de 7 días desde `fechaMinima`, "Ir a una fecha" y tira
 * de días. Funciones puras; la ventana la decide siempre el servidor.
 */

export interface VentanaFechas {
  fechaMinima: string
  fechaMaxima: string
}

export interface OpcionesNavegadorDias {
  ventana: VentanaFechas
  inicioSemana: string
  seleccionada?: string
  /** Día resaltado con un punto (en C-11, el día del turno actual). */
  diaMarcado?: string
  /** Id del texto que explica el día marcado. */
  idLeyenda?: string
}

export interface AccionesNavegadorDias {
  elegirFecha: (fecha: string) => void
  cambiarSemana: (direccion: number) => void
}

export function estaEnVentana(ventana: VentanaFechas, fecha: string): boolean {
  return fecha >= ventana.fechaMinima && fecha <= ventana.fechaMaxima
}

/** Inicio de la semana (contada desde `fechaMinima`) que contiene `fecha`. */
export function semanaDe(ventana: VentanaFechas, fecha: string): string {
  let inicio = ventana.fechaMinima
  while (sumarDias(inicio, 7) <= fecha && sumarDias(inicio, 7) <= ventana.fechaMaxima) {
    inicio = sumarDias(inicio, 7)
  }
  return inicio
}

/** Semana anterior o siguiente, o `undefined` si sale de la ventana. */
export function moverSemana(ventana: VentanaFechas, inicio: string, direccion: number): string | undefined {
  if (!Number.isInteger(direccion)) {
    return undefined
  }
  const destino = sumarDias(inicio, direccion * 7)
  if (destino < ventana.fechaMinima || destino > ventana.fechaMaxima) {
    return undefined
  }
  return destino
}

export function diasDeSemana(inicio: string): string[] {
  return [0, 1, 2, 3, 4, 5, 6].map((indice) => sumarDias(inicio, indice))
}

function botonSemana(direccion: 'anterior' | 'siguiente', deshabilitado: boolean): Descriptor {
  const texto = direccion === 'anterior' ? 'Semana anterior' : 'Semana siguiente'
  const flecha: Descriptor = {
    etiqueta: 'span',
    texto: direccion === 'anterior' ? '←' : '→',
    atributos: { 'aria-hidden': 'true' },
  }
  const etiqueta: Descriptor = { etiqueta: 'span', texto, clase: 'week-btn__text' }

  return {
    etiqueta: 'button',
    clase: 'week-btn',
    atributos: {
      type: 'button',
      'aria-label': texto,
      ...(deshabilitado ? { disabled: '' } : {}),
    },
    accion: `semana-${direccion}`,
    hijos: direccion === 'anterior' ? [flecha, etiqueta] : [etiqueta, flecha],
  }
}

function botonDia(fecha: string, opciones: OpcionesNavegadorDias): Descriptor {
  const fueraDeVentana = !estaEnVentana(opciones.ventana, fecha)
  const marcado = fecha === opciones.diaMarcado

  return {
    etiqueta: 'button',
    clase: marcado ? 'day day--actual' : 'day',
    atributos: {
      type: 'button',
      'aria-pressed': String(fecha === opciones.seleccionada),
      'aria-label': `${formatearFechaLarga(fecha)}${fueraDeVentana ? ', fuera de la ventana de reserva' : ''}`,
      ...(marcado && opciones.idLeyenda !== undefined ? { 'aria-describedby': opciones.idLeyenda } : {}),
      ...(fueraDeVentana ? { disabled: '' } : {}),
    },
    accion: `dia:${fecha}`,
    hijos: [
      { etiqueta: 'small', texto: formatearDiaCorto(fecha) },
      { etiqueta: 'strong', texto: numeroDia(fecha) },
    ],
  }
}

/** Controles de la navegación de días, listos para ir debajo del título del paso. */
export function navegadorDias(opciones: OpcionesNavegadorDias): Descriptor[] {
  const { ventana, inicioSemana } = opciones

  return [
    {
      etiqueta: 'div',
      clase: 'day-nav',
      hijos: [
        {
          etiqueta: 'div',
          clase: 'week-nav',
          atributos: { role: 'group', 'aria-label': 'Navegación por semana' },
          hijos: [
            botonSemana('anterior', inicioSemana <= ventana.fechaMinima),
            botonSemana('siguiente', sumarDias(inicioSemana, 7) > ventana.fechaMaxima),
          ],
        },
        {
          etiqueta: 'label',
          clase: 'date-jump',
          hijos: [
            { etiqueta: 'span', texto: 'Ir a una fecha' },
            {
              etiqueta: 'input',
              clase: 'input',
              atributos: {
                type: 'date',
                id: 'salto-fecha',
                min: ventana.fechaMinima,
                max: ventana.fechaMaxima,
                value: opciones.seleccionada ?? inicioSemana,
              },
              accion: 'salto-fecha',
            },
          ],
        },
      ],
    },
    {
      etiqueta: 'p',
      texto: formatearRangoSemana(inicioSemana),
      clase: 'days-range',
      atributos: { 'aria-live': 'polite' },
    },
    {
      etiqueta: 'div',
      clase: 'days-wrap',
      hijos: [
        {
          etiqueta: 'div',
          clase: 'days',
          atributos: { role: 'group', 'aria-label': 'Elegí el día' },
          hijos: diasDeSemana(inicioSemana).map((fecha) => botonDia(fecha, opciones)),
        },
      ],
    },
    {
      etiqueta: 'p',
      texto: 'Deslizá para ver más días →',
      clase: 'scroll-hint',
    },
  ]
}

/** Manejadores de semana, salto y días para `navegadorDias`. */
export function manejadoresNavegadorDias(
  ventana: VentanaFechas,
  inicioSemana: string,
  acciones: AccionesNavegadorDias,
): Record<string, (nodo: { value?: string }) => void> {
  const resultado: Record<string, (nodo: { value?: string }) => void> = {}

  for (const fecha of diasDeSemana(inicioSemana)) {
    if (estaEnVentana(ventana, fecha)) {
      resultado[`dia:${fecha}`] = () => acciones.elegirFecha(fecha)
    }
  }
  resultado['semana-anterior'] = () => acciones.cambiarSemana(-1)
  resultado['semana-siguiente'] = () => acciones.cambiarSemana(1)
  resultado['salto-fecha'] = (nodo) => acciones.elegirFecha(nodo.value ?? '')

  return resultado
}
