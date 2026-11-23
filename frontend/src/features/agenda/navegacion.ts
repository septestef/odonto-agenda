import { fechaLocalArgentina, sumarDias } from '../../shared/fechas'

export type Vista = 'dia' | 'semana'

export interface EstadoNavegacion {
  vista: Vista
  fecha: string
}

export interface RangoAgenda {
  desde: string
  hasta: string
}

export const UMBRAL_VISTA = 720

export function vistaInicial(ancho: number): Vista {
  return ancho < UMBRAL_VISTA ? 'dia' : 'semana'
}

export function estadoInicial(ancho: number, ahora: Date): EstadoNavegacion {
  return { vista: vistaInicial(ancho), fecha: fechaLocalArgentina(ahora) }
}

/** Rango semiabierto de C-12: un día o la semana que arranca en lunes. */
export function rangoDeVista(vista: Vista, fecha: string): RangoAgenda {
  if (vista === 'dia') {
    return { desde: fecha, hasta: sumarDias(fecha, 1) }
  }
  const diaSemana = new Date(`${fecha}T00:00:00Z`).getUTCDay()
  const lunes = sumarDias(fecha, -(diaSemana === 0 ? 6 : diaSemana - 1))
  return { desde: lunes, hasta: sumarDias(lunes, 7) }
}

export interface OpcionesControlNavegacion {
  ancho: number
  ahora: Date | (() => Date)
  /** Avisa a la vista que debe volver a consultar el rango visible. */
  alCambiar?: (rango: RangoAgenda) => void
}

export interface ControlNavegacion {
  estado(): EstadoNavegacion
  siguiente(): void
  anterior(): void
  hoy(): void
  cambiarVista(vista: Vista): void
  puedeIrHoy(): boolean
}

/** Estado en memoria de la agenda: nada se guarda entre recargas. */
export function crearControlNavegacion(opciones: OpcionesControlNavegacion): ControlNavegacion {
  const instanteActual = (): Date =>
    typeof opciones.ahora === 'function' ? opciones.ahora() : opciones.ahora
  const fechaDeHoy = (): string => fechaLocalArgentina(instanteActual())
  let estado: EstadoNavegacion = estadoInicial(opciones.ancho, instanteActual())

  const notificar = (): void => {
    opciones.alCambiar?.(rangoDeVista(estado.vista, estado.fecha))
  }

  const mover = (pasos: number): void => {
    const dias = estado.vista === 'dia' ? pasos : pasos * 7
    estado = { ...estado, fecha: sumarDias(estado.fecha, dias) }
    notificar()
  }

  return {
    estado: () => estado,
    siguiente: () => mover(1),
    anterior: () => mover(-1),
    hoy: () => {
      const fechaHoy = fechaDeHoy()
      if (estado.fecha === fechaHoy) {
        return
      }
      estado = { ...estado, fecha: fechaHoy }
      notificar()
    },
    cambiarVista: (vista: Vista) => {
      if (estado.vista === vista) {
        return
      }
      estado = { ...estado, vista }
      notificar()
    },
    puedeIrHoy: () => estado.fecha !== fechaDeHoy(),
  }
}
