import { boton } from '../../../shared/ui/componentes'
import type { Descriptor } from '../../../shared/ui/componentes'
import type { ManejadoresUI, NodoUI } from '../../../shared/ui/adaptador'
import type { ApiConfig } from '../api-config'
import { aplanarFranjas, agruparFranjas, textoFranja } from '../franjas'
import { MENSAJE_SOLAPE, MENSAJE_SOLAPE_FRANJA, validarFranjas } from '../validadores'
import { traducirError } from '../errores'
import type { DiaSemana, ErroresCampo, Franja } from '../modelos'
import { alertaVisual, atributosValidacion, etiquetaError, valoresDeFormulario } from './compartido'

export type EstadoHorarios =
  | { readonly tipo: 'cargando'; readonly dias: readonly DiaSemana[] }
  | { readonly tipo: 'listo'; readonly dias: readonly DiaSemana[]; readonly aviso?: string }
  | { readonly tipo: 'guardando'; readonly dias: readonly DiaSemana[] }
  | {
      readonly tipo: 'error'
      readonly dias: readonly DiaSemana[]
      readonly alerta: string
      readonly errores: ErroresCampo
      readonly foco?: string
      readonly reintentar: boolean
    }

export interface ControlHorarios {
  estado(): EstadoHorarios
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
  cargar(): Promise<void>
  guardar(dias?: readonly DiaSemana[]): Promise<void>
  agregarFranja(diaSemana: number): void
  quitarFranja(diaSemana: number, indice: number): void
  descartarCambios(): void
}

export interface OpcionesVistaHorarios {
  readonly api: ApiConfig
  readonly alCambiar?: () => void
}

interface DiaEditable {
  diaSemana: number
  nombre: string
  franjas: Franja[]
}

function clonarDias(dias: readonly DiaSemana[]): DiaEditable[] {
  return dias.map((dia) => ({ ...dia, franjas: dia.franjas.map((franja) => ({ ...franja })) }))
}

function atributoDe(nodo: NodoUI, nombre: string): string | undefined {
  const consultable = nodo as Partial<{ getAttribute(nombre: string): string | null }>
  return consultable.getAttribute?.(nombre) ?? undefined
}

function diasDesdeFormulario(nodo: NodoUI, actuales: readonly DiaSemana[]): readonly DiaSemana[] {
  const valores = valoresDeFormulario(nodo)
  if (valores.size === 0) {
    return actuales
  }
  const resultado = clonarDias(actuales)
  let indice = 0
  for (const dia of resultado) {
    dia.franjas = dia.franjas.map((franja) => {
      const inicio = valores.get(`franjas[${indice}].inicio`) ?? franja.inicio
      const fin = valores.get(`franjas[${indice}].fin`) ?? franja.fin
      indice += 1
      return { ...franja, inicio, fin }
    })
  }
  return resultado
}

function mensajeDeValidacion(errores: ErroresCampo): string {
  const mensajes = Object.values(errores)
  if (mensajes.includes(MENSAJE_SOLAPE_FRANJA)) {
    return MENSAJE_SOLAPE
  }
  return mensajes[0] ?? 'Revisá las franjas cargadas.'
}

export function crearVistaHorarios(opciones: OpcionesVistaHorarios): ControlHorarios {
  let estado: EstadoHorarios = { tipo: 'cargando', dias: [] }
  let diasOriginales: readonly DiaSemana[] = []

  function cambiar(nuevo: EstadoHorarios): void {
    estado = nuevo
    opciones.alCambiar?.()
  }

  function entradasDeFranja(franja: Franja, indice: number, errores: ErroresCampo, foco?: boolean): Descriptor[] {
    const prefijo = `franjas-${indice}`
    const error = errores[`franjas[${indice}]`]
    const erroresCampo: ErroresCampo =
      error === undefined
        ? {}
        : { [`franjas[${indice}].inicio`]: error, [`franjas[${indice}].fin`]: error }
    return (['inicio', 'fin'] as const).map((parte) => {
      const campo = `franjas[${indice}].${parte}`
      return {
        etiqueta: 'input' as const,
        atributos: {
          id: `${prefijo}-${parte}`,
          type: 'text',
          name: campo,
          'data-campo': campo,
          value: franja[parte],
          'aria-label': `${parte === 'inicio' ? 'Inicio' : 'Fin'} de la franja ${indice + 1}`,
          ...atributosValidacion(campo, erroresCampo, `${prefijo}-error`),
          ...(foco === true && parte === 'inicio' ? { autofocus: '' } : {}),
        },
      }
    })
  }

  function renderFranja(
    franja: Franja,
    diaSemana: number,
    indice: number,
    errores: ErroresCampo,
    foco?: string,
  ): Descriptor {
    const error = errores[`franjas[${indice}]`]
    const conError = error !== undefined
    const errorVisible = etiquetaError(`franjas[${indice}]`, errores, `franjas-${indice}-error`, 'franja__error')
    return {
      etiqueta: 'div',
      clase: conError ? 'franja franja--error' : 'franja',
      atributos: { 'data-franja': String(indice), ...(conError ? { 'aria-invalid': 'true' } : {}) },
      hijos: [
        { etiqueta: 'span', clase: 'franja__hora', texto: textoFranja(franja) },
        ...entradasDeFranja(franja, indice, errores, foco === `franjas[${indice}]`),
        ...(errorVisible === undefined ? [] : [errorVisible]),
        {
          etiqueta: 'button' as const,
          texto: 'Quitar franja',
          clase: 'btn btn--fantasma btn--sm',
          atributos: {
            type: 'button',
            'data-accion': 'quitar-franja',
            'data-dia': String(diaSemana),
            'data-indice': String(indice),
          },
          accion: 'quitar-franja',
        },
      ],
    }
  }

  function renderDia(dia: DiaSemana, indiceGlobal: number, errores: ErroresCampo, foco?: string): Descriptor {
    const franjas = dia.franjas.map((franja, indiceLocal) =>
      renderFranja(franja, dia.diaSemana, indiceGlobal + indiceLocal, errores, foco),
    )
    return {
      etiqueta: 'section' as const,
      clase: 'dia',
      atributos: { 'data-dia': String(dia.diaSemana) },
      hijos: [
        { etiqueta: 'h3' as const, texto: dia.nombre },
        ...(franjas.length === 0
          ? [{ etiqueta: 'p' as const, clase: 'dia__vacio', texto: 'Sin atención' }]
          : franjas),
        {
          etiqueta: 'button' as const,
          texto: 'Agregar franja',
          clase: 'btn btn--secundaria btn--sm',
          atributos: { type: 'button', 'data-accion': 'agregar-franja', 'data-dia': String(dia.diaSemana) },
          accion: 'agregar-franja',
        },
      ],
    }
  }

  function descriptor(): Descriptor {
    const errores = estado.tipo === 'error' ? estado.errores : {}
    const foco = estado.tipo === 'error' ? estado.foco : undefined
    const aviso = estado.tipo === 'listo' ? estado.aviso : undefined
    const alertaGeneral = estado.tipo === 'error' ? estado.alerta : undefined
    let indice = 0
    const dias = estado.dias.map((dia) => {
      const renderizado = renderDia(dia, indice, errores, foco)
      indice += dia.franjas.length
      return renderizado
    })
    const enviando = estado.tipo === 'guardando'

    return {
      etiqueta: 'div',
      clase: 'seccion-config horarios',
      hijos: [
        { etiqueta: 'h2', texto: 'Horario semanal', atributos: { id: 'h-horarios' } },
        {
          etiqueta: 'p',
          clase: 'desc',
          texto: 'Definí las franjas en las que atendés. Todo se muestra en hora de Argentina.',
        },
        ...(aviso === undefined
          ? []
          : [alertaVisual('Cambios guardados', 'exito', 'El horario de atención quedó actualizado.')]),
        ...(alertaGeneral === undefined ? [] : [alertaVisual(alertaGeneral, 'error')]),
        {
          etiqueta: 'form',
          clase: 'horarios__form',
          accion: 'guardar-horario',
          atributos: { 'aria-busy': enviando ? 'true' : 'false' },
          hijos: [
            ...dias,
            {
              etiqueta: 'div',
              clase: 'acciones',
              hijos: [
                boton({
                  texto: 'Guardar horario',
                  textoEnviando: 'Guardando…',
                  enviando,
                  accion: 'guardar-horario',
                }),
                boton({
                  texto: 'Descartar cambios',
                  variante: 'fantasma',
                  tipo: 'button',
                  accion: 'descartar-horarios',
                }),
              ],
            },
          ],
        },
        ...(estado.tipo === 'cargando' ? [alertaVisual('Cargando horarios…', 'info')] : []),
        ...(estado.tipo === 'error' && estado.reintentar
          ? [boton({ texto: 'Reintentar', accion: 'reintentar-horarios', variante: 'secundaria', tipo: 'button' })]
          : []),
      ],
    }
  }

  const vista: ControlHorarios = {
    estado: () => estado,
    descriptor,

    manejadores() {
      return {
        async 'guardar-horario'(nodo: NodoUI) {
          await vista.guardar(diasDesdeFormulario(nodo, estado.dias))
        },
        'agregar-franja'(nodo: NodoUI) {
          const dia = Number(atributoDe(nodo, 'data-dia'))
          if (Number.isInteger(dia)) {
            vista.agregarFranja(dia)
          }
        },
        'quitar-franja'(nodo: NodoUI) {
          const dia = Number(atributoDe(nodo, 'data-dia'))
          const indice = Number(atributoDe(nodo, 'data-indice'))
          if (Number.isInteger(dia) && Number.isInteger(indice)) {
            vista.quitarFranja(dia, indice)
          }
        },
        'descartar-horarios'() {
          vista.descartarCambios()
        },
        async 'reintentar-horarios'() {
          await vista.cargar()
        },
      }
    },

    async cargar() {
      cambiar({ tipo: 'cargando', dias: [] })
      try {
        const horario = await opciones.api.horarios()
        const dias = agruparFranjas(horario.franjas)
        diasOriginales = clonarDias(dias)
        cambiar({ tipo: 'listo', dias })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({ tipo: 'error', dias: [], alerta: traducido.alerta, errores: {}, reintentar: traducido.reintentar })
      }
    },

    async guardar(dias = estado.dias) {
      const borrador = clonarDias(dias)
      const franjas = aplanarFranjas(borrador)
      const validacion = validarFranjas(franjas)
      if (!validacion.valido) {
        cambiar({
          tipo: 'error',
          dias: borrador,
          alerta: mensajeDeValidacion(validacion.errores),
          errores: validacion.errores,
          ...(validacion.primero === undefined ? {} : { foco: validacion.primero }),
          reintentar: false,
        })
        return
      }

      cambiar({ tipo: 'guardando', dias: borrador })
      try {
        const respuesta = await opciones.api.guardarHorario(franjas)
        const diasGuardados = agruparFranjas(respuesta.franjas)
        diasOriginales = clonarDias(diasGuardados)
        cambiar({ tipo: 'listo', dias: diasGuardados, aviso: 'Cambios guardados' })
      } catch (error: unknown) {
        const traducido = traducirError(error)
        cambiar({
          tipo: 'error',
          dias: borrador,
          alerta: traducido.alerta,
          errores: traducido.erroresCampo,
          ...(traducido.primero === undefined ? {} : { foco: traducido.primero }),
          reintentar: traducido.reintentar,
        })
      }
    },

    agregarFranja(diaSemana) {
      const dias = clonarDias(estado.dias)
      const dia = dias.find((item) => item.diaSemana === diaSemana)
      if (dia === undefined) {
        return
      }
      dia.franjas = [...dia.franjas, { diaSemana, inicio: '09:00', fin: '13:00' }]
      cambiar({ tipo: 'listo', dias })
    },

    quitarFranja(diaSemana, indice) {
      const dias = clonarDias(estado.dias)
      const dia = dias.find((item) => item.diaSemana === diaSemana)
      if (dia === undefined || indice < 0 || indice >= dia.franjas.length) {
        return
      }
      dia.franjas = dia.franjas.filter((_, posicion) => posicion !== indice)
      cambiar({ tipo: 'listo', dias })
    },

    descartarCambios() {
      cambiar({ tipo: 'listo', dias: clonarDias(diasOriginales) })
    },
  }

  return vista
}
