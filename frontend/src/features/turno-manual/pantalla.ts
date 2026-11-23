import type { ErrorApi } from '../../shared/error-api'
import { interpretarErrorApi } from '../../shared/error-api'
import { esFechaValida, sumarDias } from '../../shared/fechas'
import {
  fechaDeInicio,
  formatearFechaLarga,
  horaDeInicio,
  horaMasMinutos,
} from '../../shared/formato-fechas'
import { camposPaciente, leerDatosDe } from '../../shared/formulario-paciente'
import {
  manejadoresNavegadorDias,
  moverSemana,
  navegadorDias,
  semanaDe,
} from '../../shared/navegador-dias'
import { selectorHorarios } from '../../shared/selector-horarios'
import type { ManejadoresUI, NodoUI } from '../../shared/ui/adaptador'
import { alerta, boton } from '../../shared/ui/componentes'
import type { Descriptor } from '../../shared/ui/componentes'
import type { ApiManual } from './api'
import { esConflictoManual, reducir, estadoInicialManual } from './estado'
import { armarSolicitudManual } from './solicitud'
import { erroresPorCampo, hayErrores, validarDatosPaciente } from './validaciones'
import type {
  AccionManual,
  DatosPaciente,
  EstadoManual,
  HorarioOpcion,
  PrestacionReserva,
  ReservaConfirmada,
  SolicitudTurnoManual,
} from './modelo'

export interface OpcionesControlManual {
  api: ApiManual
  nombreOdontologo?: string
  alCambiar?: () => void
  alCerrar?: () => void
  alExito?: (confirmacion: ReservaConfirmada) => void
}

export interface ControlManual {
  estado(): EstadoManual
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
  iniciar(): Promise<void>
  elegirPrestacion(prestacionId: number): Promise<void>
  elegirFecha(fecha: string): Promise<void>
  cambiarSemana(direccion: number): void
  elegirHorario(horario: HorarioOpcion): void
  irADatos(): void
  confirmar(datos: DatosPaciente): Promise<void>
  cerrar(): void
}

function formatearPrestacion(prestacion: PrestacionReserva): string {
  return `${prestacion.nombre} · ${prestacion.duracionMinutos} min`
}

function errorDe(error: unknown): ErrorApi {
  return interpretarErrorApi(error)
}

function nodosDe(descriptor: Descriptor | undefined): Descriptor[] {
  if (descriptor === undefined) {
    return []
  }
  return [descriptor, ...(descriptor.hijos ?? []).flatMap(nodosDe)]
}

function progreso(paso: number): Descriptor {
  const pasos = ['Prestación', 'Día', 'Horario', 'Datos']
  return {
    etiqueta: 'div',
    clase: 'progreso',
    atributos: { 'aria-label': 'Pasos del formulario' },
    hijos: pasos.map((texto, indice) => ({
      etiqueta: 'span' as const,
      texto: `${indice + 1} ${texto}`,
      ...(indice + 1 === paso ? { atributos: { 'aria-current': 'step' } } : {}),
    })),
  }
}

function cabecera(titulo: string, detalle: string | undefined): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'modal__cab',
    hijos: [
      {
        etiqueta: 'div',
        hijos: [
          { etiqueta: 'h2', texto: titulo, atributos: { id: 'titulo-formulario-manual' } },
          ...(detalle === undefined ? [] : [{ etiqueta: 'p' as const, texto: detalle }]),
        ],
      },
      {
        etiqueta: 'button',
        texto: '✕',
        clase: 'btn btn--fantasma btn--icono',
        atributos: { type: 'button', 'aria-label': 'Cerrar el formulario' },
        accion: 'cerrar-manual',
      },
    ],
  }
}

function envolver(titulo: string, detalle: string | undefined, hijos: readonly Descriptor[]): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'overlay-manual',
    hijos: [
      {
        etiqueta: 'dialog',
        clase: 'modal modal--manual',
        atributos: { open: '', 'aria-labelledby': 'titulo-formulario-manual' },
        hijos: [cabecera(titulo, detalle), ...hijos],
      },
    ],
  }
}

function resumen(estado: EstadoManual): Descriptor | undefined {
  const perfil = estado.perfil
  if (perfil === undefined || estado.fecha === undefined) {
    return undefined
  }
  const prestacion = perfil.prestaciones.find((item) => item.id === estado.prestacionId)
  if (prestacion === undefined) {
    return undefined
  }
  const inicio = estado.horario?.inicio
  const horario =
    inicio === undefined
      ? undefined
      : `${horaDeInicio(inicio)} – ${horaMasMinutos(inicio, estado.duracionMinutos ?? 0)}`

  return {
    etiqueta: 'div',
    clase: 'summary',
    atributos: { 'aria-label': 'Resumen del turno elegido' },
    hijos: [
      {
        etiqueta: 'dl',
        hijos: [
          { etiqueta: 'dt', texto: 'Prestación' },
          { etiqueta: 'dd', texto: formatearPrestacion(prestacion) },
          { etiqueta: 'dt', texto: 'Día' },
          { etiqueta: 'dd', texto: formatearFechaLarga(estado.fecha) },
          ...(horario === undefined
            ? []
            : ([{ etiqueta: 'dt' as const, texto: 'Horario' }, { etiqueta: 'dd' as const, texto: horario }] as Descriptor[])),
        ],
      },
    ],
  }
}

function alertaDeRechazo(error: ErrorApi): Descriptor {
  switch (error.codigo) {
    case 'RN-TU-01':
      return alerta({
        texto: 'Ese horario acaba de ser tomado, elegí otro',
        detalle: 'Refrescamos la lista de horarios libres de este día.',
        tipo: 'error',
      })
    case 'RN-TU-12':
      return alerta({
        texto: 'Ese horario ya pasó',
        detalle: 'Elegí un horario futuro dentro de tu agenda.',
        tipo: 'aviso',
      })
    case 'RN-TU-11':
      return alerta({
        texto: 'Solo se pueden cargar turnos hasta 60 días hacia adelante',
        detalle: 'Elegí una fecha más cercana.',
        tipo: 'aviso',
      })
    case 'RN-AG-05':
      return alerta({
        texto: 'Ese horario no está disponible en la agenda',
        detalle: 'Puede que esté fuera de una franja o cruce un bloqueo. Elegí otro.',
        tipo: 'aviso',
      })
    default:
      return alerta({ texto: 'No pudimos guardar el turno', detalle: error.mensaje, tipo: 'error' })
  }
}

function datosSeguros(estado: EstadoManual): boolean {
  return estado.datos.dni.trim() !== '' || estado.datos.email.trim() !== ''
}

function avisosDeConflicto(estado: EstadoManual): Descriptor[] {
  const error = estado.error
  if (error === undefined) {
    return []
  }
  const avisos: Descriptor[] = [alertaDeRechazo(error)]
  if (datosSeguros(estado)) {
    avisos.push(
      alerta({
        texto: 'Los datos siguen cargados',
        detalle: `DNI ${estado.datos.dni.trim()} · ${estado.datos.nombre.trim()} ${estado.datos.apellido.trim()} · ${estado.datos.email.trim()}. No hace falta volver a escribirlos.`,
        tipo: 'aviso',
      }),
    )
  }
  return avisos
}

function avisosDeDisponibilidad(estado: EstadoManual): Descriptor[] {
  const error = estado.error
  if (error === undefined) {
    return []
  }
  return [
    alerta({
      texto: 'No pudimos cargar los horarios',
      detalle: error.mensaje,
      tipo: 'error',
    }),
    boton({
      texto: 'Reintentar',
      accion: 'reintentar-disponibilidad',
      variante: 'secundaria',
      tipo: 'button',
      clase: 'btn--block',
    }),
  ]
}

function prestacionesDescriptor(estado: EstadoManual): Descriptor | undefined {
  const perfil = estado.perfil
  if (perfil === undefined) {
    return undefined
  }
  if (perfil.prestaciones.length === 0) {
    return {
      etiqueta: 'div',
      clase: 'empty',
      atributos: { role: 'status' },
      hijos: [
        { etiqueta: 'h3', texto: 'No hay prestaciones activas' },
        { etiqueta: 'p', texto: 'Habilitá una prestación en Configuración para cargar turnos.' },
      ],
    }
  }
  return {
    etiqueta: 'fieldset',
    clase: 'options',
    hijos: [
      { etiqueta: 'legend', texto: '1 · Prestación', clase: 'step' },
      ...perfil.prestaciones.map((prestacion): Descriptor => ({
        etiqueta: 'button',
        clase: 'option',
        atributos: {
          type: 'button',
          'aria-pressed': String(prestacion.id === estado.prestacionId),
        },
        accion: `prestacion:${prestacion.id}`,
        hijos: [
          { etiqueta: 'span', texto: prestacion.nombre, clase: 'option__title' },
          { etiqueta: 'span', texto: `${prestacion.duracionMinutos} min`, clase: 'option__meta' },
        ],
      })),
    ],
  }
}

function diasDescriptor(estado: EstadoManual): Descriptor | undefined {
  const perfil = estado.perfil
  if (perfil === undefined) {
    return undefined
  }
  return {
    etiqueta: 'div',
    hijos: [
      { etiqueta: 'p', texto: '2 · Día', clase: 'step' },
      ...navegadorDias({
        ventana: perfil,
        inicioSemana: estado.inicioSemana ?? perfil.fechaMinima,
        ...(estado.fecha === undefined ? {} : { seleccionada: estado.fecha }),
      }),
    ],
  }
}

function horariosDescriptor(estado: EstadoManual): Descriptor[] {
  const hijos: Descriptor[] = [{ etiqueta: 'p', texto: '3 · Horario', clase: 'step' }]
  if (estado.prestacionId === undefined) {
    hijos.push({
      etiqueta: 'p',
      texto: 'Elegí una prestación para ver los horarios libres.',
      clase: 'small muted',
    })
    return hijos
  }

  const selector = selectorHorarios({
    horarios: estado.horarios,
    seleccionado: estado.horario,
    onSeleccionar: () => {},
    vacio: 'No hay horarios libres este día',
    cargando: estado.cargandoHorarios,
  })
  hijos.push(selector.descriptor)

  if (!estado.cargandoHorarios && estado.horarios.length === 0) {
    hijos.push({
      etiqueta: 'p',
      texto: 'Elegí otra fecha o volvé a la agenda.',
      clase: 'small muted center',
    })
    hijos.push(
      boton({
        texto: 'Ver la semana',
        accion: 'semana-siguiente',
        variante: 'secundaria',
        tipo: 'button',
        clase: 'btn--block',
      }),
    )
  }
  return hijos
}

function seleccionDescriptor(estado: EstadoManual): Descriptor {
  const esConflicto = estado.error !== undefined && esConflictoManual(estado.error.codigo)
  const avisos = esConflicto
    ? avisosDeConflicto(estado)
    : estado.error !== undefined
      ? avisosDeDisponibilidad(estado)
      : []
  const resumenSeleccion = resumen(estado)
  const prestaciones = prestacionesDescriptor(estado)
  const dias = diasDescriptor(estado)

  return envolver('Cargar un turno', detalle(estado), [
    progreso(estado.prestacionId === undefined ? 1 : estado.fecha === undefined ? 2 : 3),
    ...avisos,
    ...(prestaciones === undefined ? [] : [prestaciones]),
    ...(dias === undefined ? [] : [dias]),
    ...horariosDescriptor(estado),
    ...(resumenSeleccion === undefined ? [] : [resumenSeleccion]),
    {
      etiqueta: 'div',
      clase: 'divider',
    },
    {
      etiqueta: 'div',
      clase: 'btn-row',
      hijos: [
        boton({
          texto: 'Continuar con mis datos',
          accion: 'ir-datos',
          deshabilitado: estado.horario === undefined,
          tipo: 'button',
          clase: 'btn--block',
        }),
      ],
    },
  ])
}

function detalle(estado: EstadoManual): string | undefined {
  const estadoFecha = estado.fecha
  return estadoFecha === undefined ? undefined : `Agenda del ${formatearFechaLarga(estadoFecha)}`
}

function formularioDescriptor(estado: EstadoManual, nombreOdontologo: string | undefined): Descriptor {
  const enviando = estado.paso === 'enviando'
  const errorServidor =
    estado.error?.codigo === 'VALIDACION'
      ? alerta({ texto: 'Revisá estos datos', detalle: estado.error.mensaje, tipo: 'error' })
      : undefined
  const resumenSeleccion = resumen(estado)
  const campos = camposPaciente({
    datos: estado.datos,
    errores: estado.errores,
    enviando,
    hintEmail: 'El paciente recibe ahí el link para gestionar el turno.',
  })

  return envolver('Cargar un turno', nombreOdontologo ?? detalle(estado), [
    progreso(4),
    ...(resumenSeleccion === undefined ? [] : [resumenSeleccion]),
    alerta({
      texto: 'El link para consultar, cancelar o reprogramar llega por email al paciente.',
      tipo: 'aviso',
    }),
    {
      etiqueta: 'form',
      accion: 'confirmar',
      atributos: { novalidate: '', 'aria-busy': enviando ? 'true' : 'false' },
      hijos: [
        ...(errorServidor === undefined ? [] : [errorServidor]),
        ...campos,
        ...(hayErrores(estado.errores)
          ? [
              {
                etiqueta: 'p' as const,
                texto: 'Corregí los datos marcados para poder confirmar.',
                clase: 'small muted',
                atributos: { role: 'status' },
              },
            ]
          : []),
        { etiqueta: 'div', clase: 'divider' },
        {
          etiqueta: 'div',
          clase: 'btn-row',
          hijos: [
            boton({
              texto: 'Volver al horario',
              accion: 'volver-horario',
              variante: 'secundaria',
              tipo: 'button',
            }),
            boton({
              texto: 'Confirmar turno',
              textoEnviando: 'Confirmando…',
              enviando,
              deshabilitado: enviando,
              accion: 'confirmar',
            }),
          ],
        },
      ],
    },
  ])
}

function errorEnvioDescriptor(estado: EstadoManual): Descriptor {
  return envolver('No pudimos guardar el turno', detalle(estado), [
    alerta({
      texto: 'No pudimos guardar el turno',
      detalle: estado.error?.mensaje ?? 'Revisá tu conexión o volvé a ingresar. Tus datos siguen cargados.',
      tipo: 'error',
    }),
    boton({ texto: 'Reintentar', accion: 'reintentar-envio', tipo: 'button', clase: 'btn--block' }),
    boton({
      texto: 'Volver a mis datos',
      accion: 'volver-a-datos',
      variante: 'secundaria',
      tipo: 'button',
      clase: 'btn--block',
    }),
  ])
}

function exitoDescriptor(estado: EstadoManual): Descriptor {
  const reserva = estado.reserva
  const fecha = reserva === undefined ? '' : formatearFechaLarga(fechaDeInicio(reserva.inicio))
  const hora = reserva === undefined ? '' : horaDeInicio(reserva.inicio)
  return envolver('Turno cargado', detalle(estado), [
    alerta({
      texto: 'Turno cargado',
      detalle: `Le enviamos el link de gestión a ${reserva?.email ?? ''}.`,
      tipo: 'aviso',
    }),
    {
      etiqueta: 'p',
      texto: `${fecha} a las ${hora}.`,
      clase: 'small muted center',
    },
  ])
}

export function crearControlManual(opciones: OpcionesControlManual): ControlManual {
  const api = opciones.api
  let estado: EstadoManual = estadoInicialManual()
  let versionDisponibilidad = 0

  function cambiar(nuevo: EstadoManual): void {
    estado = nuevo
    opciones.alCambiar?.()
  }

  function despachar(accion: AccionManual): void {
    cambiar(reducir(estado, accion))
  }

  async function cargarDisponibilidad(fecha: string, conservarError = false): Promise<void> {
    const prestacionId = estado.prestacionId
    if (prestacionId === undefined) {
      return
    }
    const version = ++versionDisponibilidad
    despachar({ tipo: 'disponibilidad-inicio', fecha, conservarError })
    try {
      const disponibilidad = await api.disponibilidad(prestacionId, fecha)
      if (version !== versionDisponibilidad) {
        return
      }
      despachar({ tipo: 'disponibilidad-cargada', disponibilidad, conservarError })
    } catch (error: unknown) {
      if (version !== versionDisponibilidad) {
        return
      }
      despachar({ tipo: 'disponibilidad-error', error: errorDe(error) })
    }
  }

  async function iniciar(): Promise<void> {
    if (estado.paso !== 'cargando-perfil') {
      despachar({ tipo: 'iniciar' })
    }
    try {
      const perfil = await api.perfil()
      despachar({ tipo: 'perfil-cargado', perfil })
    } catch (error: unknown) {
      despachar({ tipo: 'perfil-error', error: errorDe(error) })
    }
  }

  async function elegirPrestacion(prestacionId: number): Promise<void> {
    const perfil = estado.perfil
    if (perfil === undefined) {
      return
    }
    const prestacion = perfil.prestaciones.find((item) => item.id === prestacionId)
    if (prestacion === undefined) {
      return
    }
    const fecha = perfil.fechaMinima
    despachar({ tipo: 'elegir-prestacion', prestacionId, fecha })
    await cargarDisponibilidad(fecha)
  }

  async function elegirFecha(fecha: string): Promise<void> {
    const perfil = estado.perfil
    if (perfil === undefined || !esFechaValida(fecha)) {
      return
    }
    if (fecha < perfil.fechaMinima || fecha > perfil.fechaMaxima) {
      return
    }
    const pagina = semanaDe(perfil, fecha)
    if (estado.inicioSemana !== pagina) {
      despachar({ tipo: 'cambiar-semana', inicioSemana: pagina })
    }
    await cargarDisponibilidad(fecha)
  }

  function cambiarSemana(direccion: number): void {
    const perfil = estado.perfil
    if (perfil === undefined || !Number.isInteger(direccion)) {
      return
    }
    const destino = moverSemana(perfil, estado.inicioSemana ?? perfil.fechaMinima, direccion)
    if (destino !== undefined) {
      despachar({ tipo: 'cambiar-semana', inicioSemana: destino })
    }
  }

  function elegirHorario(horario: HorarioOpcion): void {
    despachar({ tipo: 'elegir-horario', horario })
  }

  function irADatos(): void {
    despachar({ tipo: 'ir-a-datos' })
  }

  function volverAHorario(datos?: DatosPaciente): void {
    despachar({ tipo: 'volver-a-horario', datos })
  }

  function cerrar(): void {
    opciones.alCerrar?.()
  }

  async function enviarSolicitud(datos: DatosPaciente, solicitud: SolicitudTurnoManual): Promise<void> {
    try {
      const reserva = await api.crear(solicitud)
      despachar({ tipo: 'exito', reserva })
      opciones.alExito?.(reserva)
    } catch (error: unknown) {
      const apiError = errorDe(error)

      if (apiError.codigo === 'VALIDACION') {
        despachar({
          tipo: 'validacion-servidor',
          datos,
          errores: erroresPorCampo(apiError.detalles),
          error: apiError,
        })
        return
      }

      if (esConflictoManual(apiError.codigo)) {
        despachar({ tipo: 'conflicto', error: apiError })
        if (estado.fecha !== undefined) {
          await cargarDisponibilidad(estado.fecha, true)
        }
        return
      }

      despachar({ tipo: 'error-envio', error: apiError })
    }
  }

  async function confirmar(datos: DatosPaciente): Promise<void> {
    if (estado.paso !== 'completando-datos') {
      return
    }
    const errores = validarDatosPaciente(datos)
    if (hayErrores(errores)) {
      despachar({ tipo: 'validacion-local', datos, errores })
      return
    }

    const prestacionId = estado.prestacionId
    const inicio = estado.horario?.inicio
    if (prestacionId === undefined || inicio === undefined) {
      return
    }

    const resultado = armarSolicitudManual(prestacionId, inicio, datos)
    if (!resultado.ok) {
      despachar({ tipo: 'validacion-local', datos, errores: resultado.errores })
      return
    }

    despachar({ tipo: 'confirmar', datos })
    await enviarSolicitud(datos, resultado.solicitud)
  }

  async function reintentarEnvio(): Promise<void> {
    if (estado.paso !== 'error-envio') {
      return
    }
    const prestacionId = estado.prestacionId
    const inicio = estado.horario?.inicio
    if (prestacionId === undefined || inicio === undefined) {
      return
    }
    const resultado = armarSolicitudManual(prestacionId, inicio, estado.datos)
    if (!resultado.ok) {
      despachar({ tipo: 'validacion-local', datos: estado.datos, errores: resultado.errores })
      return
    }
    despachar({ tipo: 'reintentar-envio' })
    await enviarSolicitud(estado.datos, resultado.solicitud)
  }

  function descriptor(): Descriptor {
    switch (estado.paso) {
      case 'cargando-perfil':
        return envolver('Cargar un turno', undefined, [
          progreso(1),
          {
            etiqueta: 'div',
            clase: 'skeleton skeleton--title',
            atributos: { 'aria-hidden': 'true' },
          },
          {
            etiqueta: 'p',
            texto: 'Cargando tus prestaciones.',
            clase: 'sr-only',
            atributos: { 'aria-live': 'polite' },
          },
        ])
      case 'error-perfil':
        return envolver('No pudimos abrir el formulario', undefined, [
          alerta({
            texto: 'No pudimos cargar tus prestaciones',
            detalle: estado.error?.mensaje ?? 'Revisá tu conexión y volvé a intentar.',
            tipo: 'error',
          }),
          boton({ texto: 'Reintentar', accion: 'reintentar-perfil', tipo: 'button', clase: 'btn--block' }),
        ])
      case 'eligiendo-prestacion':
      case 'eligiendo-horario':
      case 'sin-horarios':
      case 'conflicto':
        return seleccionDescriptor(estado)
      case 'completando-datos':
      case 'enviando':
        return formularioDescriptor(estado, opciones.nombreOdontologo)
      case 'error-envio':
        return errorEnvioDescriptor(estado)
      case 'exito':
        return exitoDescriptor(estado)
    }
  }

  function manejadores(): ManejadoresUI {
    const resultado: Record<string, (nodo: NodoUI) => void> = {
      'cerrar-manual': () => cerrar(),
      'ir-datos': () => irADatos(),
      'volver-horario': (nodo) => volverAHorario(leerDatosDe(nodo) ?? estado.datos),
      'volver-a-datos': () => despachar({ tipo: 'volver-a-datos' }),
      confirmar: (nodo) => {
        void confirmar(leerDatosDe(nodo) ?? estado.datos)
      },
      'reintentar-envio': () => {
        void reintentarEnvio()
      },
      'reintentar-perfil': () => {
        void iniciar()
      },
      'reintentar-disponibilidad': () => {
        if (estado.fecha !== undefined) {
          void cargarDisponibilidad(estado.fecha)
        }
      },
    }

    const perfil = estado.perfil
    if (perfil !== undefined) {
      for (const prestacion of perfil.prestaciones) {
        resultado[`prestacion:${prestacion.id}`] = () => {
          void elegirPrestacion(prestacion.id)
        }
      }
      Object.assign(
        resultado,
        manejadoresNavegadorDias(perfil, estado.inicioSemana ?? perfil.fechaMinima, {
          elegirFecha: (fecha) => {
            void elegirFecha(fecha)
          },
          cambiarSemana,
        }),
      )
    }
    resultado['semana-anterior'] ??= () => cambiarSemana(-1)
    resultado['semana-siguiente'] ??= () => cambiarSemana(1)
    resultado['salto-fecha'] ??= (nodo) => {
      void elegirFecha(nodo.value ?? '')
    }

    const selector = selectorHorarios({
      horarios: estado.horarios,
      seleccionado: estado.horario,
      onSeleccionar: (horario) => elegirHorario(horario),
      vacio: '',
    })
    Object.assign(resultado, selector.manejadores)

    return resultado
  }

  return {
    estado: () => estado,
    descriptor,
    manejadores,
    iniciar,
    elegirPrestacion,
    elegirFecha,
    cambiarSemana,
    elegirHorario,
    irADatos,
    confirmar,
    cerrar,
  }
}

/** Expuesto para tests: texto plano de un descriptor (sin interpretar HTML). */
export function textosDeManual(descriptor: Descriptor | undefined): string[] {
  return nodosDe(descriptor)
    .map((nodo) => nodo.texto)
    .filter((texto): texto is string => texto !== undefined)
}

/** Próximo día con horarios, usado por el botón “Ver la semana” del estado vacío. */
export function siguienteDiaConHorarios(estado: EstadoManual): string | undefined {
  const perfil = estado.perfil
  const desde = estado.fecha ?? perfil?.fechaMinima
  if (perfil === undefined || desde === undefined) {
    return undefined
  }
  const proximo = sumarDias(desde, 1)
  return proximo <= perfil.fechaMaxima ? proximo : undefined
}
