import urlLogo from '@design-system/assets/logo.svg?url'
import { NOMBRE_MARCA } from '../../shared/brand'
import { interpretarErrorApi } from '../../shared/error-api'
import type { ErrorApi } from '../../shared/error-api'
import { esFechaValida, sumarDias } from '../../shared/fechas'
import { fechaDeInicio, formatearFechaLarga, horaDeInicio } from '../../shared/formato-fechas'
import { manejadoresNavegadorDias, moverSemana, navegadorDias, semanaDe } from '../../shared/navegador-dias'
import { selectorHorarios } from '../../shared/selector-horarios'
import type { ManejadoresUI, NodoUI } from '../../shared/ui/adaptador'
import { alerta, boton } from '../../shared/ui/componentes'
import type { Descriptor } from '../../shared/ui/componentes'
import type { ApiPublica } from './api-publica'
import { esConflictoDeReserva, estadoInicialReserva, reducir } from './estado'
import type {
  CampoPaciente,
  DatosPaciente,
  EstadoReserva,
  PerfilReserva,
  PrestacionReserva,
  ReservaConfirmada,
  SolicitudReserva,
} from './modelo'
import { armarSolicitud } from './solicitud'
import { erroresPorCampo, hayErrores, validarDatosPaciente } from './validaciones'

export {
  fechaDeInicio,
  formatearDiaCorto,
  formatearFechaLarga,
  formatearRangoSemana,
  horaDeInicio,
  numeroDia,
} from '../../shared/formato-fechas'

export interface OpcionesControlReserva {
  slug: string
  api: ApiPublica
  alCambiar?: () => void
}

export interface ControlReserva {
  estado(): EstadoReserva
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
  iniciar(): Promise<void>
  elegirPrestacion(prestacionId: number): Promise<void>
  elegirFecha(fecha: string): Promise<void>
  cambiarSemana(direccion: number): void
  elegirHorario(horario: EstadoReserva['horario']): void
  irADatos(): void
  volverAHorario(datos?: DatosPaciente): void
  confirmar(datos: DatosPaciente): Promise<void>
  reintentarEnvio(): Promise<void>
  reiniciar(): void
}

export function formatearPrestacion(prestacion: PrestacionReserva): string {
  return `${prestacion.nombre} · ${prestacion.duracionMinutos} min`
}

function iniciales(nombre: string, apellido: string): string {
  const primeraNombre = nombre.trim().charAt(0)
  const primeraApellido = apellido.trim().charAt(0)

  return `${primeraNombre}${primeraApellido}`.toUpperCase()
}

interface NodoFormulario extends NodoUI {
  querySelector(selector: string): { value: string } | null
  closest?(selector: string): NodoUI | null
}

function esNodoFormulario(nodo: NodoUI): nodo is NodoFormulario {
  const candidato = nodo as unknown as Record<string, unknown>
  return typeof candidato['querySelector'] === 'function'
}

function formularioDe(nodo: NodoUI): NodoUI {
  const conCercania = nodo as NodoUI & { closest?: (selector: string) => NodoUI | null }
  return conCercania.closest?.('form') ?? nodo
}

function valorCampo(nodo: NodoUI, nombre: CampoPaciente): string {
  if (!esNodoFormulario(nodo)) {
    return ''
  }
  return nodo.querySelector(`[data-campo="${nombre}"]`)?.value ?? ''
}

function leerDatosDe(nodo: NodoUI): DatosPaciente | undefined {
  const formulario = formularioDe(nodo)
  if (!esNodoFormulario(formulario)) {
    return undefined
  }

  return {
    dni: valorCampo(formulario, 'dni'),
    nombre: valorCampo(formulario, 'nombre'),
    apellido: valorCampo(formulario, 'apellido'),
    telefono: valorCampo(formulario, 'telefono'),
    email: valorCampo(formulario, 'email'),
  }
}

function appbar(): Descriptor {
  return {
    etiqueta: 'header',
    clase: 'appbar',
    hijos: [
      {
        etiqueta: 'div',
        clase: 'appbar__inner',
        hijos: [
          {
            etiqueta: 'div',
            clase: 'appbar__brand',
            hijos: [
              {
                etiqueta: 'img',
                atributos: { src: urlLogo, alt: NOMBRE_MARCA, width: '135', height: '44' },
              },
            ],
          },
          { etiqueta: 'div', clase: 'appbar__spacer' },
          { etiqueta: 'span', texto: 'Reserva online', clase: 'appbar__hint' },
        ],
      },
    ],
  }
}

function pagina(
  titulo: string | undefined,
  subtitulo: string | undefined,
  hijos: readonly Descriptor[],
  enfocarTitulo = false,
): Descriptor {
  const encabezado: Descriptor[] =
    titulo === undefined
      ? []
      : [
          {
            etiqueta: 'div',
            clase: 'page-head',
            hijos: [
              { etiqueta: 'h1', texto: titulo, ...(enfocarTitulo ? { enfocar: true } : {}) },
              ...(subtitulo === undefined ? [] : [{ etiqueta: 'p' as const, texto: subtitulo }]),
            ],
          },
        ]

  return {
    etiqueta: 'div',
    clase: 'reserva-publica',
    hijos: [
      appbar(),
      {
        etiqueta: 'main',
        clase: 'flow',
        hijos: [...encabezado, ...hijos],
      },
    ],
  }
}

function tarjeta(hijos: readonly Descriptor[]): Descriptor {
  return { etiqueta: 'div', clase: 'card stack-6', hijos }
}

function paso(numero: number, texto: string): Descriptor {
  return { etiqueta: 'p', texto: `Paso ${numero} de 4: ${texto}`, clase: 'step' }
}

function cargandoPerfil(): Descriptor {
  return pagina('Reservá tu turno', 'Estamos cargando los datos del consultorio.', [
    tarjeta([
      { etiqueta: 'div', clase: 'skeleton skeleton--title' },
      { etiqueta: 'div', clase: 'skeleton skeleton--option' },
      { etiqueta: 'div', clase: 'skeleton skeleton--option' },
      {
        etiqueta: 'p',
        texto: 'Cargando el consultorio.',
        clase: 'sr-only',
        atributos: { 'aria-live': 'polite' },
      },
    ]),
  ])
}

function errorPerfil(): Descriptor {
  return pagina('Reservá tu turno', 'No pudimos abrir este link.', [
    tarjeta([
      alerta({
        texto: 'Este link no es válido',
        detalle:
          'Puede haber cambiado o el consultorio ya no está disponible. Pedile un link nuevo a tu odontólogo.',
        tipo: 'error',
      }),
      boton({
        texto: 'Reintentar',
        accion: 'reintentar-perfil',
        variante: 'secundaria',
        tipo: 'button',
        clase: 'btn--block',
      }),
    ]),
  ], true)
}

function perfilDescriptor(perfil: PerfilReserva): Descriptor {
  const nombre = `${perfil.nombre} ${perfil.apellido}`.trim()
  const direccion = perfil.direccionConsultorio?.trim() ?? ''

  return {
    etiqueta: 'div',
    clase: 'profile',
    hijos: [
      {
        etiqueta: 'div',
        clase: 'profile__avatar',
        texto: iniciales(perfil.nombre, perfil.apellido),
        atributos: { 'aria-hidden': 'true' },
      },
      {
        etiqueta: 'div',
        hijos: [
          { etiqueta: 'div', texto: nombre, clase: 'profile__name' },
          ...(direccion === '' ? [] : [{ etiqueta: 'div' as const, texto: direccion, clase: 'profile__meta' }]),
        ],
      },
    ],
  }
}

function prestacionesDescriptor(estado: EstadoReserva): Descriptor | undefined {
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
        { etiqueta: 'h3', texto: 'Este consultorio no tiene prestaciones disponibles' },
        { etiqueta: 'p', texto: 'Pedile a tu odontólogo que habilite una prestación para reservar.' },
      ],
    }
  }

  return {
    etiqueta: 'fieldset',
    clase: 'options',
    hijos: [
      { etiqueta: 'legend', texto: 'Paso 1 de 4: Prestación', clase: 'step' },
      ...perfil.prestaciones.map((prestacion): Descriptor => ({
        etiqueta: 'button' as const,
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

function diasDescriptor(estado: EstadoReserva): Descriptor | undefined {
  const perfil = estado.perfil
  if (perfil === undefined) {
    return undefined
  }

  return {
    etiqueta: 'div',
    hijos: [
      paso(2, 'Día'),
      ...navegadorDias({
        ventana: perfil,
        inicioSemana: estado.inicioSemana ?? perfil.fechaMinima,
        ...(estado.fecha === undefined ? {} : { seleccionada: estado.fecha }),
      }),
    ],
  }
}

function horariosDescriptor(estado: EstadoReserva): Descriptor[] {
  const hijos: Descriptor[] = [paso(3, 'Horario')]

  if (estado.prestacionId === undefined) {
    hijos.push({
      etiqueta: 'p',
      texto: 'Elegí una prestación para ver los horarios disponibles.',
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
      texto: 'Probá con otro día o con otra prestación más corta.',
      clase: 'small muted center',
    })
    hijos.push(
      boton({
        texto: 'Ver el próximo día disponible',
        accion: 'proximo-dia',
        variante: 'secundaria',
        tipo: 'button',
        clase: 'btn--block',
      }),
    )
  }

  return hijos
}

function resumenSeleccion(estado: EstadoReserva): Descriptor | undefined {
  const perfil = estado.perfil
  if (perfil === undefined || estado.fecha === undefined) {
    return undefined
  }
  const prestacion = perfil.prestaciones.find((item) => item.id === estado.prestacionId)
  if (prestacion === undefined) {
    return undefined
  }

  const fecha = `${formatearFechaLarga(estado.fecha)}${estado.horario === undefined ? '' : `, ${estado.horario.hora}`}`
  const direccion = perfil.direccionConsultorio?.trim()

  return {
    etiqueta: 'div',
    clase: 'summary',
    atributos: { 'aria-label': 'Resumen de la selección' },
    hijos: [
      {
        etiqueta: 'dl',
        hijos: [
          { etiqueta: 'dt', texto: 'Prestación' },
          { etiqueta: 'dd', texto: formatearPrestacion(prestacion) },
          { etiqueta: 'dt', texto: 'Fecha' },
          { etiqueta: 'dd', texto: fecha },
          ...(direccion === undefined || direccion === ''
            ? []
            : [
                { etiqueta: 'dt' as const, texto: 'Lugar' },
                { etiqueta: 'dd' as const, texto: direccion },
              ]),
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
        detalle: 'Ya actualizamos los horarios disponibles.',
        tipo: 'error',
      })
    case 'RN-PA-03':
      return alerta({
        texto: 'Ya tenés 2 turnos futuros con este odontólogo',
        detalle: 'Para reservar otro, cancelá uno desde el link del email de confirmación.',
        tipo: 'aviso',
      })
    case 'RN-TU-03':
      return alerta({
        texto: 'Los turnos se reservan con al menos 24 h de anticipación',
        detalle: 'Elegí una fecha más adelante.',
        tipo: 'aviso',
      })
    case 'RN-TU-11':
      return alerta({
        texto: 'Solo podés reservar hasta 60 días hacia adelante',
        detalle: 'Elegí una fecha más cercana.',
        tipo: 'aviso',
      })
    case 'RN-AG-05':
      return alerta({
        texto: 'Ese horario dejó de estar disponible',
        detalle: 'Puede que el odontólogo haya cambiado su agenda. Elegí otro horario.',
        tipo: 'aviso',
      })
    default:
      return alerta({ texto: 'No pudimos cargar los horarios', detalle: error.mensaje, tipo: 'error' })
  }
}

function avisosDeEstado(estado: EstadoReserva): Descriptor[] {
  const error = estado.error
  if (error === undefined) {
    return []
  }

  if (error.codigo === 'RN-PA-03') {
    return [alertaDeRechazo(error)]
  }

  if (esConflictoDeReserva(error.codigo)) {
    const avisos: Descriptor[] = [alertaDeRechazo(error)]
    const dni = estado.datos.dni.trim()
    const email = estado.datos.email.trim()
    if (dni !== '' || email !== '') {
      avisos.push(
        alerta({
          texto: 'Tus datos siguen cargados',
          detalle: `${dni === '' ? '' : `DNI ${dni} · `}${email}. No hace falta que los vuelvas a escribir.`,
          tipo: 'aviso',
        }),
      )
    }
    return avisos
  }

  return [
    alerta({
      texto: 'No pudimos cargar los horarios',
      detalle: 'Revisá tu conexión y volvé a intentar.',
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

function contenidoSeleccion(estado: EstadoReserva): Descriptor[] {
  const hijos: Descriptor[] = []

  if (estado.perfil !== undefined) {
    hijos.push(perfilDescriptor(estado.perfil))
  }

  const prestaciones = prestacionesDescriptor(estado)
  if (prestaciones !== undefined) {
    hijos.push(prestaciones)
  }

  const dias = diasDescriptor(estado)
  if (dias !== undefined) {
    hijos.push(dias)
  }

  hijos.push(...horariosDescriptor(estado))

  const resumen = resumenSeleccion(estado)
  if (resumen !== undefined) {
    hijos.push(resumen)
  }

  hijos.push({
    etiqueta: 'div',
    hijos: [
      boton({
        texto: 'Continuar con mis datos',
        accion: 'ir-datos',
        deshabilitado: estado.horario === undefined,
        tipo: 'button',
        clase: 'btn--block',
      }),
      {
        etiqueta: 'p',
        texto: 'Los turnos se reservan con al menos 24 h de anticipación.',
        clase: 'small muted center',
      },
    ],
  })

  return hijos
}

interface OpcionesCampoReserva {
  etiqueta: string
  nombre: CampoPaciente
  tipo: 'text' | 'tel' | 'email'
  valor: string
  autocompletado?: string
  inputmode?: string
  hint?: string
  error?: string
  deshabilitado?: boolean
  enfocar?: boolean
}

function campoReserva(opciones: OpcionesCampoReserva): Descriptor {
  const id = `campo-${opciones.nombre}`
  const idHint = `${id}-hint`
  const idError = `${id}-error`
  const descritos = [
    ...(opciones.hint === undefined ? [] : [idHint]),
    ...(opciones.error === undefined ? [] : [idError]),
  ]

  return {
    etiqueta: 'div',
    clase: 'field',
    hijos: [
      { etiqueta: 'label', texto: opciones.etiqueta, atributos: { for: id } },
      {
        etiqueta: 'input',
        clase: 'input',
        enfocar: opciones.enfocar,
        atributos: {
          id,
          name: opciones.nombre,
          type: opciones.tipo,
          'data-campo': opciones.nombre,
          value: opciones.valor,
          ...(opciones.autocompletado === undefined ? {} : { autocomplete: opciones.autocompletado }),
          ...(opciones.inputmode === undefined ? {} : { inputmode: opciones.inputmode }),
          ...(descritos.length === 0 ? {} : { 'aria-describedby': descritos.join(' ') }),
          ...(opciones.error === undefined ? {} : { 'aria-invalid': 'true' }),
          ...(opciones.deshabilitado === true ? { disabled: '' } : {}),
        },
      },
      ...(opciones.hint === undefined
        ? []
        : [{ etiqueta: 'span' as const, texto: opciones.hint, clase: 'hint', atributos: { id: idHint } }]),
      ...(opciones.error === undefined
        ? []
        : [
            {
              etiqueta: 'span' as const,
              texto: opciones.error,
              clase: 'error',
              atributos: { id: idError, role: 'alert' },
            },
          ]),
    ],
  }
}

function formularioDescriptor(estado: EstadoReserva): Descriptor {
  const enviando = estado.paso === 'enviando'
  const errores = estado.errores
  const ordenCampos: readonly CampoPaciente[] = ['dni', 'telefono', 'nombre', 'apellido', 'email']
  const primerError = ordenCampos.find((campo) => errores[campo] !== undefined)
  const resumen = resumenSeleccion(estado)
  const errorServidor =
    estado.error?.codigo === 'VALIDACION'
      ? alerta({ texto: 'Revisá estos datos', detalle: estado.error.mensaje, tipo: 'error' })
      : undefined

  const campos: Descriptor[] = [
    {
      etiqueta: 'div',
      clase: 'field-row',
      hijos: [
        campoReserva({
          etiqueta: 'DNI',
          nombre: 'dni',
          tipo: 'text',
          valor: estado.datos.dni,
          inputmode: 'numeric',
          autocompletado: 'off',
          hint: 'Sin puntos.',
          error: errores.dni,
          deshabilitado: enviando,
          enfocar: primerError === 'dni',
        }),
        campoReserva({
          etiqueta: 'Teléfono',
          nombre: 'telefono',
          tipo: 'tel',
          valor: estado.datos.telefono,
          inputmode: 'tel',
          autocompletado: 'tel',
          hint: 'Con código de país, por ejemplo +5493511234567.',
          error: errores.telefono,
          deshabilitado: enviando,
          enfocar: primerError === 'telefono',
        }),
      ],
    },
    {
      etiqueta: 'div',
      clase: 'field-row',
      hijos: [
        campoReserva({
          etiqueta: 'Nombre',
          nombre: 'nombre',
          tipo: 'text',
          valor: estado.datos.nombre,
          autocompletado: 'given-name',
          error: errores.nombre,
          deshabilitado: enviando,
          enfocar: primerError === 'nombre',
        }),
        campoReserva({
          etiqueta: 'Apellido',
          nombre: 'apellido',
          tipo: 'text',
          valor: estado.datos.apellido,
          autocompletado: 'family-name',
          error: errores.apellido,
          deshabilitado: enviando,
          enfocar: primerError === 'apellido',
        }),
      ],
    },
    campoReserva({
      etiqueta: 'Email',
      nombre: 'email',
      tipo: 'email',
      valor: estado.datos.email,
      inputmode: 'email',
      autocompletado: 'email',
      hint: 'Te mandamos ahí el link para gestionar tu turno.',
      error: errores.email,
      deshabilitado: enviando,
      enfocar: primerError === 'email',
    }),
  ]

  return pagina('Confirmá tu turno', 'Revisá tus datos. Te vamos a enviar el link de gestión al email que ingreses.', [
    tarjeta([
      paso(4, 'Tus datos'),
      ...(resumen === undefined ? [] : [resumen]),
      alerta({
        texto: 'El link para cancelar o reprogramar tu turno llega solo por email.',
        tipo: 'aviso',
      }),
      {
        etiqueta: 'form',
        accion: 'confirmar',
        atributos: { novalidate: '', 'aria-busy': enviando ? 'true' : 'false' },
        hijos: [
          ...(errorServidor === undefined ? [] : [errorServidor]),
          ...campos,
          ...(hayErrores(errores)
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
                texto: 'Volver',
                accion: 'volver-horario',
                variante: 'secundaria',
                tipo: 'button',
              }),
              boton({
                texto: 'Confirmar reserva',
                textoEnviando: 'Confirmando…',
                enviando,
                deshabilitado: enviando,
                accion: 'confirmar',
              }),
            ],
          },
        ],
      },
    ]),
  ], true)
}

function resumenReserva(reserva: ReservaConfirmada): Descriptor {
  const direccion = reserva.odontologo.direccionConsultorio?.trim()

  return {
    etiqueta: 'div',
    clase: 'summary',
    atributos: { 'aria-label': 'Detalle del turno reservado' },
    hijos: [
      {
        etiqueta: 'dl',
        hijos: [
          { etiqueta: 'dt', texto: 'Prestación' },
          { etiqueta: 'dd', texto: formatearPrestacion(reserva.prestacion) },
          { etiqueta: 'dt', texto: 'Fecha' },
          { etiqueta: 'dd', texto: formatearFechaLarga(fechaDeInicio(reserva.inicio)) },
          { etiqueta: 'dt', texto: 'Hora' },
          { etiqueta: 'dd', texto: horaDeInicio(reserva.inicio) },
          ...(direccion === undefined || direccion === ''
            ? []
            : [
                { etiqueta: 'dt' as const, texto: 'Lugar' },
                { etiqueta: 'dd' as const, texto: direccion },
              ]),
          { etiqueta: 'dt' as const, texto: 'Email' },
          { etiqueta: 'dd' as const, texto: reserva.email },
        ],
      },
    ],
  }
}

function exitoDescriptor(estado: EstadoReserva): Descriptor {
  const reserva = estado.reserva
  if (reserva === undefined) {
    return errorPerfil()
  }

  const fecha = formatearFechaLarga(fechaDeInicio(reserva.inicio))
  const hora = horaDeInicio(reserva.inicio)

  return pagina(undefined, undefined, [
    {
      etiqueta: 'div',
      clase: 'card',
      hijos: [
        {
          etiqueta: 'div',
          clase: 'success-hero',
          hijos: [
            {
              etiqueta: 'div',
              clase: 'success-hero__icon',
              texto: '✓',
              atributos: { 'aria-hidden': 'true' },
            },
            { etiqueta: 'h1', texto: '¡Turno reservado!', enfocar: true },
            { etiqueta: 'p', texto: `Te esperamos el ${fecha.toLowerCase()} a las ${hora}.` },
          ],
        },
        alerta({
          texto: 'Te enviamos un email',
          detalle: `Revisá ${reserva.email}. Ahí está el link para cancelar o reprogramar tu turno.`,
          tipo: 'aviso',
        }),
        resumenReserva(reserva),
        alerta({
          texto: 'El link es personal. Si no lo ves en unos minutos, revisá la carpeta de spam.',
          tipo: 'aviso',
        }),
        boton({
          texto: 'Reservar otro turno',
          accion: 'reiniciar',
          tipo: 'button',
          clase: 'btn--block',
        }),
        {
          etiqueta: 'p',
          texto: '¿Necesitás cambiar algo? Usá el link del email.',
          clase: 'small muted center',
        },
      ],
    },
  ])
}

function errorEnvioDescriptor(estado: EstadoReserva): Descriptor {
  const resumen = resumenSeleccion(estado)

  return pagina('No pudimos confirmar tu turno', 'Tus datos siguen cargados. Podés reintentar.', [
    tarjeta([
      ...(resumen === undefined ? [] : [resumen]),
      alerta({
        texto: 'No pudimos confirmar tu turno',
        detalle: estado.error?.mensaje ?? 'Revisá tu conexión y volvé a intentar. Tus datos siguen cargados.',
        tipo: 'error',
      }),
      boton({
        texto: 'Reintentar',
        accion: 'reintentar-envio',
        tipo: 'button',
        clase: 'btn--block',
      }),
      boton({
        texto: 'Volver a mis datos',
        accion: 'volver-a-datos',
        variante: 'secundaria',
        tipo: 'button',
        clase: 'btn--block',
      }),
    ]),
  ], true)
}

function conflictoDescriptor(estado: EstadoReserva): Descriptor {
  const avisos = avisosDeEstado(estado)

  if (estado.error?.codigo === 'RN-PA-03') {
    return pagina('No pudimos reservar tu turno', undefined, [
      tarjeta([
        ...avisos,
        boton({
          texto: 'Entendido',
          accion: 'entendido',
          variante: 'secundaria',
          tipo: 'button',
          clase: 'btn--block',
        }),
      ]),
    ], true)
  }

  return pagina('Elegí otro horario', 'Tu turno no se reservó. Elegí otro horario disponible.', [
    tarjeta([...avisos, ...contenidoSeleccion(estado)]),
  ], true)
}

function seleccionDescriptor(estado: EstadoReserva): Descriptor {
  return pagina(
    'Reservá tu turno',
    'Elegí la prestación, el día y el horario. No necesitás crear una cuenta.',
    [tarjeta([...avisosDeEstado(estado), ...contenidoSeleccion(estado)])],
  )
}

function errorDe(error: unknown): ErrorApi {
  return interpretarErrorApi(error)
}

export function crearControlReserva(opciones: OpcionesControlReserva): ControlReserva {
  const slug = opciones.slug
  const api = opciones.api
  let estado: EstadoReserva = estadoInicialReserva()
  let versionDisponibilidad = 0

  function cambiar(nuevo: EstadoReserva): void {
    estado = nuevo
    opciones.alCambiar?.()
  }

  async function cargarDisponibilidad(fecha: string, conservarError = false): Promise<void> {
    const prestacionId = estado.prestacionId
    if (prestacionId === undefined) {
      return
    }
    const version = ++versionDisponibilidad
    cambiar(reducir(estado, { tipo: 'disponibilidad-inicio', fecha, conservarError }))
    try {
      const disponibilidad = await api.disponibilidad(slug, prestacionId, fecha)
      if (version !== versionDisponibilidad) {
        return
      }
      cambiar(reducir(estado, { tipo: 'disponibilidad-cargada', disponibilidad, conservarError }))
    } catch (error: unknown) {
      if (version !== versionDisponibilidad) {
        return
      }
      cambiar(reducir(estado, { tipo: 'disponibilidad-error', error: errorDe(error) }))
    }
  }

  async function iniciar(): Promise<void> {
    if (estado.paso !== 'cargando-perfil') {
      cambiar(reducir(estado, { tipo: 'iniciar' }))
    }
    try {
      const perfil = await api.perfil(slug)
      cambiar(reducir(estado, { tipo: 'perfil-cargado', perfil }))
    } catch (error: unknown) {
      cambiar(reducir(estado, { tipo: 'perfil-error', error: errorDe(error) }))
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
    cambiar(reducir(estado, { tipo: 'elegir-prestacion', prestacionId, fecha }))
    await cargarDisponibilidad(fecha)
  }

  async function elegirFecha(fecha: string): Promise<void> {
    const perfil = estado.perfil
    if (perfil === undefined) {
      return
    }
    if (!esFechaValida(fecha) || fecha < perfil.fechaMinima || fecha > perfil.fechaMaxima) {
      return
    }
    const pagina = semanaDe(perfil, fecha)
    if (estado.inicioSemana !== pagina) {
      cambiar(reducir(estado, { tipo: 'cambiar-semana', inicioSemana: pagina }))
    }
    await cargarDisponibilidad(fecha)
  }

  function cambiarSemana(direccion: number): void {
    const perfil = estado.perfil
    if (perfil === undefined || !Number.isInteger(direccion)) {
      return
    }
    const destino = moverSemana(perfil, estado.inicioSemana ?? perfil.fechaMinima, direccion)
    if (destino === undefined) {
      return
    }
    cambiar(reducir(estado, { tipo: 'cambiar-semana', inicioSemana: destino }))
  }

  function elegirHorario(horario: EstadoReserva['horario']): void {
    if (horario === undefined) {
      return
    }
    cambiar(reducir(estado, { tipo: 'elegir-horario', horario }))
  }

  function irADatos(): void {
    cambiar(reducir(estado, { tipo: 'ir-a-datos' }))
  }

  function volverAHorario(datos?: DatosPaciente): void {
    cambiar(reducir(estado, { tipo: 'volver-a-horario', datos }))
  }

  function reiniciar(): void {
    cambiar(reducir(estado, { tipo: 'reiniciar' }))
  }

  async function enviarSolicitud(datos: DatosPaciente, solicitud: SolicitudReserva): Promise<void> {
    try {
      const reserva = await api.reservar(slug, solicitud)
      cambiar(reducir(estado, { tipo: 'exito', reserva }))
    } catch (error: unknown) {
      const apiError = errorDe(error)

      if (apiError.codigo === 'VALIDACION') {
        cambiar(
          reducir(estado, {
            tipo: 'validacion-servidor',
            datos,
            errores: erroresPorCampo(apiError.detalles),
            error: apiError,
          }),
        )
        return
      }

      if (apiError.codigo === 'NO_ENCONTRADO') {
        await iniciar()
        return
      }

      if (esConflictoDeReserva(apiError.codigo)) {
        cambiar(reducir(estado, { tipo: 'conflicto', error: apiError }))
        if (
          (apiError.codigo === 'RN-TU-01' || apiError.codigo === 'RN-AG-05') &&
          estado.fecha !== undefined
        ) {
          await cargarDisponibilidad(estado.fecha, true)
        }
        return
      }

      cambiar(reducir(estado, { tipo: 'error-envio', error: apiError }))
    }
  }

  async function confirmar(datos: DatosPaciente): Promise<void> {
    if (estado.paso !== 'completando-datos') {
      return
    }

    const errores = validarDatosPaciente(datos)
    if (hayErrores(errores)) {
      cambiar(reducir(estado, { tipo: 'validacion-local', datos, errores }))
      return
    }

    const prestacionId = estado.prestacionId
    const inicio = estado.horario?.inicio
    if (prestacionId === undefined || inicio === undefined) {
      return
    }

    const resultado = armarSolicitud(prestacionId, inicio, datos)
    if (!resultado.ok) {
      cambiar(reducir(estado, { tipo: 'validacion-local', datos, errores: resultado.errores }))
      return
    }

    cambiar(reducir(estado, { tipo: 'confirmar', datos }))
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

    const resultado = armarSolicitud(prestacionId, inicio, estado.datos)
    if (!resultado.ok) {
      cambiar(reducir(estado, { tipo: 'validacion-local', datos: estado.datos, errores: resultado.errores }))
      return
    }

    cambiar(reducir(estado, { tipo: 'reintentar-envio' }))
    await enviarSolicitud(estado.datos, resultado.solicitud)
  }

  async function buscarProximoDia(): Promise<void> {
    const perfil = estado.perfil
    const prestacionId = estado.prestacionId
    const desde = estado.fecha ?? perfil?.fechaMinima
    if (perfil === undefined || prestacionId === undefined || desde === undefined) {
      return
    }

    const version = ++versionDisponibilidad
    cambiar(reducir(estado, { tipo: 'buscar-inicio' }))

    for (let fecha = sumarDias(desde, 1); fecha <= perfil.fechaMaxima; fecha = sumarDias(fecha, 1)) {
      try {
        const disponibilidad = await api.disponibilidad(slug, prestacionId, fecha)
        if (version !== versionDisponibilidad) {
          return
        }
        if (disponibilidad.horarios.length > 0) {
          const pagina = semanaDe(perfil, fecha)
          if (estado.inicioSemana !== pagina) {
            cambiar(reducir(estado, { tipo: 'cambiar-semana', inicioSemana: pagina }))
          }
          cambiar(reducir(estado, { tipo: 'disponibilidad-cargada', disponibilidad }))
          return
        }
      } catch (error: unknown) {
        if (version !== versionDisponibilidad) {
          return
        }
        cambiar(reducir(estado, { tipo: 'disponibilidad-error', error: errorDe(error) }))
        return
      }
    }

    if (version !== versionDisponibilidad) {
      return
    }
    cambiar(
      reducir(estado, {
        tipo: 'disponibilidad-cargada',
        disponibilidad: {
          fecha: perfil.fechaMaxima,
          prestacionId,
          duracionMinutos: estado.duracionMinutos ?? 0,
          horarios: [],
        },
      }),
    )
  }

  function descriptor(): Descriptor {
    switch (estado.paso) {
      case 'cargando-perfil':
        return cargandoPerfil()
      case 'error-perfil':
        return errorPerfil()
      case 'eligiendo-prestacion':
      case 'eligiendo-horario':
      case 'sin-horarios':
        return seleccionDescriptor(estado)
      case 'completando-datos':
      case 'enviando':
        return formularioDescriptor(estado)
      case 'exito':
        return exitoDescriptor(estado)
      case 'conflicto':
        return conflictoDescriptor(estado)
      case 'error-envio':
        return errorEnvioDescriptor(estado)
    }
  }

  function manejadores(): ManejadoresUI {
    const resultado: Record<string, (nodo: NodoUI) => void> = {}
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
    resultado['proximo-dia'] = () => {
      void buscarProximoDia()
    }
    resultado['ir-datos'] = () => irADatos()
    resultado['volver-horario'] = (nodo) => volverAHorario(leerDatosDe(nodo))
    resultado['volver-a-datos'] = () => {
      cambiar(reducir(estado, { tipo: 'volver-a-datos' }))
    }
    resultado['confirmar'] = (nodo) => {
      void confirmar(leerDatosDe(nodo) ?? estado.datos)
    }
    resultado['reintentar-envio'] = () => {
      void reintentarEnvio()
    }
    resultado['reintentar-perfil'] = () => {
      void iniciar()
    }
    resultado['reintentar-disponibilidad'] = () => {
      if (estado.fecha !== undefined) {
        void cargarDisponibilidad(estado.fecha)
      }
    }
    resultado['entendido'] = () => volverAHorario(estado.datos)
    resultado['reiniciar'] = () => reiniciar()

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
    volverAHorario,
    confirmar,
    reintentarEnvio,
    reiniciar,
  }
}
