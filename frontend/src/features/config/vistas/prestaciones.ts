import { boton } from '../../../shared/ui/componentes'
import type { Descriptor } from '../../../shared/ui/componentes'
import type { ManejadoresUI } from '../../../shared/ui/adaptador'
import type { ApiConfig, DatosPrestacion } from '../api-config'
import { MENSAJE_DURACION, validarDuracion } from '../validadores'
import { traducirError } from '../errores'
import type { ErroresCampo, Prestacion } from '../modelos'
import { alertaVisual, entrada } from './compartido'

export type EstadoPrestaciones =
  | { readonly tipo: 'cargando'; readonly prestaciones: readonly Prestacion[] }
  | { readonly tipo: 'listo'; readonly prestaciones: readonly Prestacion[]; readonly aviso?: string }
  | { readonly tipo: 'guardando'; readonly prestaciones: readonly Prestacion[] }
  | { readonly tipo: 'confirmando-baja'; readonly prestaciones: readonly Prestacion[]; readonly id: number }
  | {
      readonly tipo: 'error'
      readonly prestaciones: readonly Prestacion[]
      readonly alerta: string
      readonly errores: ErroresCampo
      readonly detalle?: string
      readonly foco?: string
      readonly reintentar: boolean
    }

export interface ControlPrestaciones {
  estado(): EstadoPrestaciones
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
  cargar(): Promise<void>
  guardar(datos: DatosPrestacion, id?: number): Promise<void>
  pedirBaja(id: number): void
  cancelarBaja(): void
  confirmarBaja(): Promise<void>
  editar(prestacion: Prestacion): void
}

export interface OpcionesVistaPrestaciones {
  readonly api: ApiConfig
  readonly alCambiar?: () => void
}

const AVISO_DURACION = 'Cambiar la duración no modifica los turnos ya reservados.'
const DETALLE_DURACION = 'Los turnos existentes conservan la duración con la que se reservaron.'

function copiar(listado: readonly Prestacion[]): Prestacion[] {
  return listado.map((prestacion) => ({ ...prestacion }))
}

export function crearVistaPrestaciones(opciones: OpcionesVistaPrestaciones): ControlPrestaciones {
  let estado: EstadoPrestaciones = { tipo: 'cargando', prestaciones: [] }
  let editando: number | undefined

  function cambiar(nuevo: EstadoPrestaciones): void {
    estado = nuevo
    opciones.alCambiar?.()
  }

  function renderFila(prestacion: Prestacion): Descriptor {
    const baja = !prestacion.activa
    return {
      etiqueta: 'div',
      clase: baja ? 'fila fila--baja' : 'fila',
      hijos: [
        { etiqueta: 'span', clase: 'fila__titulo', texto: prestacion.nombre },
        {
          etiqueta: 'span',
          clase: 'fila__meta',
          texto: `${prestacion.duracionMinutos} min · ${prestacion.activa ? 'Activa' : 'Dada de baja'}`,
        },
        {
          etiqueta: 'div',
          clase: 'fila__acciones',
          hijos: baja
            ? []
            : [
                {
                  etiqueta: 'button',
                  texto: 'Editar',
                  clase: 'btn btn--secundaria btn--sm',
                  atributos: { type: 'button', 'data-accion': 'editar-prestacion', 'data-id': String(prestacion.id) },
                  accion: 'editar-prestacion',
                },
                {
                  etiqueta: 'button',
                  texto: 'Dar de baja',
                  clase: 'btn btn--danger btn--sm',
                  atributos: { type: 'button', 'data-accion': 'pedir-baja', 'data-id': String(prestacion.id) },
                  accion: 'pedir-baja',
                },
              ],
        },
      ],
    }
  }

  function renderConfirmacion(): Descriptor | undefined {
    if (estado.tipo !== 'confirmando-baja') {
      return undefined
    }
    const id = estado.id
    const prestacion = estado.prestaciones.find((item) => item.id === id)
    if (prestacion === undefined) {
      return undefined
    }
    return {
      etiqueta: 'dialog',
      clase: 'modal',
      atributos: { open: '', 'aria-labelledby': 'titulo-modal-baja' },
      hijos: [
        { etiqueta: 'h2', texto: 'Dar de baja', atributos: { id: 'titulo-modal-baja' } },
        { etiqueta: 'p', texto: `¿Querés dar de baja "${prestacion.nombre}"?` },
        {
          etiqueta: 'div',
          clase: 'modal__acciones',
          hijos: [
            boton({ texto: 'Volver', accion: 'cancelar-baja', variante: 'secundaria', tipo: 'button' }),
            boton({ texto: 'Dar de baja', accion: 'confirmar-baja', tipo: 'button' }),
          ],
        },
      ],
    }
  }

  function descriptor(): Descriptor {
    const errores = estado.tipo === 'error' ? estado.errores : {}
    const foco = estado.tipo === 'error' ? estado.foco : undefined
    const aviso = estado.tipo === 'listo' ? estado.aviso : undefined
    const alertaGeneral = estado.tipo === 'error' ? estado.alerta : undefined
    const detalleAlerta = estado.tipo === 'error' ? estado.detalle : undefined
    const enviando = estado.tipo === 'guardando'
    const editada = estado.prestaciones.find((item) => item.id === editando)
    const nombre = editada?.nombre ?? ''
    const duracion = editada === undefined ? '' : String(editada.duracionMinutos)

    return {
      etiqueta: 'div',
      clase: 'seccion-config prestaciones',
      hijos: [
        { etiqueta: 'h2', texto: editada === undefined ? 'Nueva prestación' : 'Editar prestación', atributos: { id: 'h-form-prestacion' } },
        {
          etiqueta: 'p',
          clase: 'desc',
          texto: 'Cargá el nombre y la duración. La duración va en múltiplos de 15 minutos.',
        },
        ...(alertaGeneral === undefined ? [] : [alertaVisual(alertaGeneral, 'error', detalleAlerta)]),
        {
          etiqueta: 'form',
          clase: 'prestaciones__form',
          accion: 'guardar-prestacion',
          atributos: { 'aria-busy': enviando ? 'true' : 'false' },
          hijos: [
            {
              etiqueta: 'div',
              clase: 'campo',
              hijos: [
                entrada({ nombre: 'nombre', etiqueta: 'Nombre', valor: nombre, error: errores['nombre'], foco: foco === 'nombre' }),
                { etiqueta: 'span', clase: 'hint', texto: 'Así lo va a ver el paciente al reservar.' },
              ],
            },
            entrada({
              nombre: 'duracionMinutos',
              etiqueta: 'Duración (minutos)',
              valor: duracion,
              tipo: 'number',
              error: errores['duracionMinutos'],
              foco: foco === 'duracionMinutos',
            }),
            {
              etiqueta: 'div',
              clase: 'acciones',
              hijos: [
                boton({
                  texto: 'Guardar prestación',
                  textoEnviando: 'Guardando…',
                  enviando,
                  accion: 'guardar-prestacion',
                }),
                boton({ texto: 'Cancelar', accion: 'cancelar-edicion', variante: 'fantasma', tipo: 'button' }),
              ],
            },
          ],
        },
        { etiqueta: 'h2', texto: 'Tus prestaciones', atributos: { id: 'h-lista-prestaciones' } },
        {
          etiqueta: 'p',
          clase: 'desc',
          texto: 'Las dadas de baja siguen en la lista para que veas los turnos que ya tenés.',
        },
        alertaVisual(AVISO_DURACION, 'info', aviso === undefined ? undefined : DETALLE_DURACION),
        {
          etiqueta: 'div',
          clase: 'lista',
          hijos: estado.prestaciones.map(renderFila),
        },
        ...(estado.tipo === 'cargando' ? [alertaVisual('Cargando prestaciones…', 'info')] : []),
        ...(estado.tipo === 'error' && estado.reintentar
          ? [boton({ texto: 'Reintentar', accion: 'reintentar-prestaciones', variante: 'secundaria', tipo: 'button' })]
          : []),
        ...(renderConfirmacion() === undefined ? [] : [renderConfirmacion() ?? { etiqueta: 'div' }]),
      ],
    }
  }

  const vista: ControlPrestaciones = {
    estado: () => estado,
    descriptor,

    manejadores() {
      return {
        'guardar-prestacion': async () => {
          const campos = new Map<string, string>()
          await vista.guardar({
            nombre: campos.get('nombre') ?? '',
            duracionMinutos: Number(campos.get('duracionMinutos') ?? ''),
          })
        },
        'cancelar-edicion'() {
          editando = undefined
          cambiar({ tipo: 'listo', prestaciones: estado.prestaciones })
        },
        'editar-prestacion': (nodo) => {
          const consultable = nodo as Partial<{ getAttribute(nombre: string): string | null }>
          const id = Number(consultable.getAttribute?.('data-id'))
          const prestacion = estado.prestaciones.find((item) => item.id === id)
          if (prestacion !== undefined) {
            vista.editar(prestacion)
          }
        },
        'pedir-baja': (nodo) => {
          const consultable = nodo as Partial<{ getAttribute(nombre: string): string | null }>
          const id = Number(consultable.getAttribute?.('data-id'))
          if (Number.isInteger(id)) {
            vista.pedirBaja(id)
          }
        },
        'cancelar-baja'() {
          vista.cancelarBaja()
        },
        async 'confirmar-baja'() {
          await vista.confirmarBaja()
        },
        async 'reintentar-prestaciones'() {
          await vista.cargar()
        },
      }
    },

    async cargar() {
      cambiar({ tipo: 'cargando', prestaciones: [] })
      try {
        const prestaciones = await opciones.api.prestaciones()
        cambiar({ tipo: 'listo', prestaciones })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({
          tipo: 'error',
          prestaciones: [],
          alerta: traducido.alerta,
          errores: {},
          reintentar: traducido.reintentar,
        })
      }
    },

    async guardar(datos, id) {
      const validacion = validarDuracion(datos.duracionMinutos)
      if (!validacion.valido) {
        cambiar({
          tipo: 'error',
          prestaciones: estado.prestaciones,
          alerta: MENSAJE_DURACION,
          errores: validacion.errores,
          detalle: 'Probá con 15, 30, 45 o 60 minutos.',
          ...(validacion.primero === undefined ? {} : { foco: validacion.primero }),
          reintentar: false,
        })
        return
      }
      if (datos.nombre.trim() === '') {
        cambiar({
          tipo: 'error',
          prestaciones: estado.prestaciones,
          alerta: 'Ingresá el nombre de la prestación.',
          errores: { nombre: 'Ingresá el nombre de la prestación.' },
          foco: 'nombre',
          reintentar: false,
        })
        return
      }

      cambiar({ tipo: 'guardando', prestaciones: estado.prestaciones })
      try {
        const guardada = id === undefined
          ? await opciones.api.crearPrestacion(datos)
          : await opciones.api.editarPrestacion(id, datos)
        editando = undefined
        const prestaciones = estado.prestaciones.some((item) => item.id === guardada.id)
          ? estado.prestaciones.map((item) => (item.id === guardada.id ? guardada : item))
          : [...estado.prestaciones, guardada]
        cambiar({
          tipo: 'listo',
          prestaciones,
          ...(id === undefined ? {} : { aviso: AVISO_DURACION }),
        })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({
          tipo: 'error',
          prestaciones: estado.prestaciones,
          alerta: traducido.alerta,
          errores: traducido.erroresCampo,
          ...(traducido.primero === undefined ? {} : { foco: traducido.primero }),
          reintentar: traducido.reintentar,
        })
      }
    },

    pedirBaja(id) {
      if (estado.prestaciones.some((item) => item.id === id && item.activa)) {
        cambiar({ tipo: 'confirmando-baja', prestaciones: estado.prestaciones, id })
      }
    },

    cancelarBaja() {
      cambiar({ tipo: 'listo', prestaciones: estado.prestaciones })
    },

    async confirmarBaja() {
      if (estado.tipo !== 'confirmando-baja') {
        return
      }
      const id = estado.id
      try {
        await opciones.api.bajaPrestacion(id)
        const prestaciones = estado.prestaciones.map((item) =>
          item.id === id ? { ...item, activa: false } : item,
        )
        cambiar({ tipo: 'listo', prestaciones })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({
          tipo: 'error',
          prestaciones: estado.prestaciones,
          alerta: traducido.alerta,
          errores: traducido.erroresCampo,
          ...(traducido.primero === undefined ? {} : { foco: traducido.primero }),
          reintentar: traducido.reintentar,
        })
      }
    },

    editar(prestacion) {
      editando = prestacion.id
      cambiar({ tipo: 'listo', prestaciones: copiar(estado.prestaciones) })
    },
  }

  return vista
}
