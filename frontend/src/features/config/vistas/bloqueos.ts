import { boton } from '../../../shared/ui/componentes'
import type { Descriptor } from '../../../shared/ui/componentes'
import type { ManejadoresUI, NodoUI } from '../../../shared/ui/adaptador'
import type { ApiConfig } from '../api-config'
import { fechaLocalArgentina } from '../../../shared/fechas'
import { instanteConOffset, rangoBloqueoVisible, rangoVisible } from '../instantes'
import { MENSAJE_FIN_BLOQUEO, validarRangoBloqueo } from '../validadores'
import { traducirError } from '../errores'
import type { Bloqueo, ErroresCampo, MomentoLocal, TurnoAfectado } from '../modelos'
import { alertaVisual, entrada, valoresDeFormulario } from './compartido'

export type EstadoBloqueos =
  | { readonly tipo: 'cargando'; readonly bloqueos: readonly Bloqueo[] }
  | { readonly tipo: 'listo'; readonly bloqueos: readonly Bloqueo[]; readonly aviso?: string; readonly notaAccion?: string }
  | { readonly tipo: 'guardando'; readonly bloqueos: readonly Bloqueo[] }
  | {
      readonly tipo: 'error'
      readonly bloqueos: readonly Bloqueo[]
      readonly alerta: string
      readonly detalle?: string
      readonly errores: ErroresCampo
      readonly foco?: string
      readonly reintentar: boolean
    }

export interface DatosBloqueoFormulario {
  readonly fechaInicio: string
  readonly horaInicio: string
  readonly fechaFin: string
  readonly horaFin: string
  readonly motivo?: string
}

export interface ControlBloqueos {
  estado(): EstadoBloqueos
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
  cargar(): Promise<void>
  crear(datos: DatosBloqueoFormulario): Promise<void>
  baja(id: number): Promise<void>
  cancelarTurno(): void
}

export interface OpcionesVistaBloqueos {
  readonly api: ApiConfig
  readonly alCambiar?: () => void
  /** Reloj inyectable para la fecha por defecto del formulario (tests). */
  readonly reloj?: () => Date
}

const AVISO_NO_CANCELA = 'Crear un bloqueo no cancela tus turnos.'
const DETALLE_RANGO_BLOQUEO = 'Corregí el rango antes de agregar el bloqueo.'
const MENSAJE_ACCION_DIFERIDA = 'Esta acción se habilita con la gestión de turnos del odontólogo.'
const SIN_TURNOS = 'Sin turnos afectados'

function estadoVisible(estado: string): string {
  return estado.charAt(0) + estado.slice(1).toLowerCase()
}

function resumenTurnos(cantidad: number): { titulo: string; detalle: string } {
  return cantidad === 1
    ? { titulo: 'Hay 1 turno dentro del bloqueo', detalle: 'Revisalo y decidí si lo cancelás.' }
    : { titulo: `Hay ${cantidad} turnos dentro del bloqueo`, detalle: 'Revisalos y decidí si los cancelás.' }
}

/** Render de un turno afectado: comparte estructura con escritorio y móvil. */
export function renderTurno(turno: TurnoAfectado): Descriptor {
  return {
    etiqueta: 'article',
    clase: 'turno',
    hijos: [
      {
        etiqueta: 'div',
        clase: 'turno__top',
        hijos: [
          { etiqueta: 'span', clase: 'turno__hora', texto: rangoVisible(turno.inicio, turno.fin) },
          { etiqueta: 'span', clase: 'badge badge--reservado', texto: estadoVisible(turno.estado) },
        ],
      },
      {
        etiqueta: 'div',
        clase: 'turno__quien',
        texto: `${turno.pacienteNombre} ${turno.pacienteApellido} · ${turno.prestacionNombre}`,
      },
      { etiqueta: 'div', clase: 'turno__meta', texto: 'Turno online' },
      {
        etiqueta: 'div',
        clase: 'turno__acciones',
        hijos: [
          {
            etiqueta: 'button',
            texto: 'Cancelá el turno',
            clase: 'btn btn--danger btn--sm',
            atributos: {
              type: 'button',
              'data-accion': 'cancelar-turno',
              'data-id': String(turno.id),
            },
            accion: 'cancelar-turno',
          },
        ],
      },
    ],
  }
}

function renderBloqueo(bloqueo: Bloqueo, ocupado: boolean): Descriptor {
  const turnos = bloqueo.turnosAfectados
  return {
    etiqueta: 'article',
    clase: 'bloqueo',
    hijos: [
      {
        etiqueta: 'div',
        clase: 'bloqueo__top',
        hijos: [
          {
            etiqueta: 'span',
            clase: 'bloqueo__rango',
            texto: rangoBloqueoVisible(bloqueo.inicio, bloqueo.fin),
          },
          {
            etiqueta: 'button',
            texto: 'Quitar bloqueo',
            clase: 'btn btn--danger btn--sm',
            atributos: {
              type: 'button',
              'data-accion': 'baja-bloqueo',
              'data-id': String(bloqueo.id),
              ...(ocupado ? { disabled: '' } : {}),
            },
            accion: 'baja-bloqueo',
          },
        ],
      },
      ...(bloqueo.motivo === undefined || bloqueo.motivo === ''
        ? []
        : [{ etiqueta: 'p' as const, clase: 'bloqueo__motivo', texto: bloqueo.motivo }]),
      ...(turnos.length === 0
        ? [{ etiqueta: 'p' as const, clase: 'bloqueo__vacio', texto: SIN_TURNOS }]
        : [{ etiqueta: 'div' as const, clase: 'turnos', hijos: turnos.map(renderTurno) }]),
    ],
  }
}

function atributoDe(nodo: NodoUI, nombre: string): string | undefined {
  const consultable = nodo as Partial<{ getAttribute(nombre: string): string | null }>
  return consultable.getAttribute?.(nombre) ?? undefined
}

function datosDelFormulario(nodo: NodoUI, actuales: DatosBloqueoFormulario): DatosBloqueoFormulario {
  const valores = valoresDeFormulario(nodo)
  return {
    fechaInicio: valores.get('fechaInicio') ?? actuales.fechaInicio,
    horaInicio: valores.get('horaInicio') ?? actuales.horaInicio,
    fechaFin: valores.get('fechaFin') ?? actuales.fechaFin,
    horaFin: valores.get('horaFin') ?? actuales.horaFin,
    motivo: valores.get('motivo') ?? actuales.motivo,
  }
}

export function crearVistaBloqueos(opciones: OpcionesVistaBloqueos): ControlBloqueos {
  let estado: EstadoBloqueos = { tipo: 'cargando', bloqueos: [] }
  const hoy = fechaLocalArgentina((opciones.reloj ?? (() => new Date()))())
  // Lo último que envió el usuario: se re-dibuja así tras un error en vez de volver a los valores por defecto.
  let borrador: DatosBloqueoFormulario = {
    fechaInicio: hoy,
    horaInicio: '10:00',
    fechaFin: hoy,
    horaFin: '12:00',
    motivo: '',
  }

  function cambiar(nuevo: EstadoBloqueos): void {
    estado = nuevo
    opciones.alCambiar?.()
  }

  function descriptor(): Descriptor {
    const errores = estado.tipo === 'error' ? estado.errores : {}
    const foco = estado.tipo === 'error' ? estado.foco : undefined
    const aviso = estado.tipo === 'listo' ? estado.aviso : undefined
    const notaAccion = estado.tipo === 'listo' ? estado.notaAccion : undefined
    const alertaGeneral = estado.tipo === 'error' ? estado.alerta : undefined
    const detalleAlerta = estado.tipo === 'error' ? estado.detalle : undefined
    const enviando = estado.tipo === 'guardando'
    const totalTurnos = estado.bloqueos.reduce((total, bloqueo) => total + bloqueo.turnosAfectados.length, 0)
    const resumen = resumenTurnos(totalTurnos)

    return {
      etiqueta: 'div',
      clase: 'seccion-config bloqueos',
      hijos: [
        ...(totalTurnos > 0
          ? [alertaVisual(resumen.titulo, 'advertencia', resumen.detalle)]
          : []),
        alertaVisual(AVISO_NO_CANCELA, 'info'),
        ...(aviso === undefined ? [] : [alertaVisual(aviso, 'exito')]),
        ...(notaAccion === undefined ? [] : [alertaVisual(notaAccion, 'info')]),
        ...(alertaGeneral === undefined ? [] : [alertaVisual(alertaGeneral, 'error', detalleAlerta)]),
        { etiqueta: 'h2', texto: 'Nuevo bloqueo', atributos: { id: 'h-form-bloqueo' } },
        {
          etiqueta: 'p',
          clase: 'desc',
          texto: 'Marcá un rango para no recibir turnos. Todo se interpreta en hora de Argentina.',
        },
        {
          etiqueta: 'form',
          clase: 'bloqueos__form',
          accion: 'crear-bloqueo',
          atributos: { 'aria-busy': enviando ? 'true' : 'false' },
          hijos: [
            {
              etiqueta: 'div',
              clase: 'field-row',
              hijos: [
                entrada({ nombre: 'fechaInicio', etiqueta: 'Fecha de inicio', valor: borrador.fechaInicio, tipo: 'date' }),
                entrada({
                  nombre: 'horaInicio',
                  etiqueta: 'Hora de inicio',
                  valor: borrador.horaInicio,
                  tipo: 'time',
                  error: errores['inicio'],
                  foco: foco === 'inicio',
                }),
                entrada({ nombre: 'fechaFin', etiqueta: 'Fecha de fin', valor: borrador.fechaFin, tipo: 'date' }),
                entrada({
                  nombre: 'horaFin',
                  etiqueta: 'Hora de fin',
                  valor: borrador.horaFin,
                  tipo: 'time',
                  error: errores['fin'],
                  foco: foco === 'fin',
                }),
              ],
            },
            {
              etiqueta: 'div',
              clase: 'campo',
              hijos: [
                entrada({
                  nombre: 'motivo',
                  etiqueta: 'Motivo (opcional)',
                  valor: borrador.motivo ?? '',
                }),
                { etiqueta: 'span', clase: 'hint', texto: 'Hasta 255 caracteres.' },
              ],
            },
            {
              etiqueta: 'div',
              clase: 'acciones',
              hijos: [
                boton({ texto: 'Agregar bloqueo', textoEnviando: 'Agregando…', enviando }),
                boton({ texto: 'Cancelar', accion: 'cancelar-bloqueo', variante: 'fantasma', tipo: 'button' }),
              ],
            },
          ],
        },
        { etiqueta: 'h2', texto: 'Tus bloqueos', atributos: { id: 'h-lista-bloqueos' } },
        {
          etiqueta: 'p',
          clase: 'desc',
          texto: 'Si hay turnos adentro, te los mostramos para que decidas qué hacer.',
        },
        {
          etiqueta: 'div',
          clase: 'lista',
          hijos: estado.bloqueos.map((bloqueo) => renderBloqueo(bloqueo, enviando)),
        },
        ...(estado.tipo === 'cargando' ? [alertaVisual('Cargando bloqueos…', 'info')] : []),
        ...(estado.tipo === 'error' && estado.reintentar
          ? [boton({ texto: 'Reintentar', accion: 'reintentar-bloqueos', variante: 'secundaria', tipo: 'button' })]
          : []),
      ],
    }
  }

  const vista: ControlBloqueos = {
    estado: () => estado,
    descriptor,

    manejadores() {
      return {
        async 'crear-bloqueo'(nodo: NodoUI) {
          await vista.crear(datosDelFormulario(nodo, borrador))
        },
        'cancelar-bloqueo'() {
          cambiar({ tipo: 'listo', bloqueos: estado.bloqueos })
        },
        async 'baja-bloqueo'(nodo: NodoUI) {
          const id = Number(atributoDe(nodo, 'data-id'))
          if (Number.isInteger(id)) {
            await vista.baja(id)
          }
        },
        'cancelar-turno'() {
          vista.cancelarTurno()
        },
        async 'reintentar-bloqueos'() {
          await vista.cargar()
        },
      }
    },

    async cargar() {
      cambiar({ tipo: 'cargando', bloqueos: estado.bloqueos })
      try {
        const bloqueos = await opciones.api.bloqueos()
        cambiar({ tipo: 'listo', bloqueos })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({
          tipo: 'error',
          bloqueos: estado.bloqueos,
          alerta: traducido.alerta,
          errores: {},
          reintentar: traducido.reintentar,
        })
      }
    },

    async crear(datos) {
      borrador = { ...datos }
      const inicio: MomentoLocal = { fecha: datos.fechaInicio, hora: datos.horaInicio }
      const fin: MomentoLocal = { fecha: datos.fechaFin, hora: datos.horaFin }
      const validacion = validarRangoBloqueo(inicio, fin)
      if (!validacion.valido) {
        cambiar({
          tipo: 'error',
          bloqueos: estado.bloqueos,
          alerta: (validacion.primero === undefined ? undefined : validacion.errores[validacion.primero]) ?? MENSAJE_FIN_BLOQUEO,
          detalle: DETALLE_RANGO_BLOQUEO,
          errores: validacion.errores,
          ...(validacion.primero === undefined ? {} : { foco: validacion.primero }),
          reintentar: false,
        })
        return
      }

      cambiar({ tipo: 'guardando', bloqueos: estado.bloqueos })
      try {
        const creado = await opciones.api.crearBloqueo({
          inicio: instanteConOffset(datos.fechaInicio, datos.horaInicio),
          fin: instanteConOffset(datos.fechaFin, datos.horaFin),
          ...(datos.motivo === undefined || datos.motivo.trim() === '' ? {} : { motivo: datos.motivo }),
        })
        cambiar({ tipo: 'listo', bloqueos: [...estado.bloqueos, creado], aviso: 'Bloqueo agregado' })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({
          tipo: 'error',
          bloqueos: estado.bloqueos,
          alerta: traducido.alerta,
          errores: traducido.erroresCampo,
          ...(traducido.primero === undefined ? {} : { foco: traducido.primero }),
          reintentar: traducido.reintentar,
        })
      }
    },

    async baja(id) {
      if (estado.tipo === 'guardando') {
        return
      }
      const anteriores = estado.bloqueos
      cambiar({ tipo: 'guardando', bloqueos: anteriores })
      try {
        await opciones.api.bajaBloqueo(id)
        cambiar({ tipo: 'listo', bloqueos: anteriores.filter((bloqueo) => bloqueo.id !== id) })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({
          tipo: 'error',
          bloqueos: anteriores,
          alerta: traducido.alerta,
          errores: {},
          reintentar: traducido.reintentar,
        })
      }
    },

    cancelarTurno() {
      cambiar({
        tipo: 'listo',
        bloqueos: estado.bloqueos,
        notaAccion: MENSAJE_ACCION_DIFERIDA,
      })
    },
  }

  return vista
}
