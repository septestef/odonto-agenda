import urlLogo from '@design-system/assets/logo.svg?url'
import { NOMBRE_MARCA } from '../../shared/brand'
import { interpretarErrorApi } from '../../shared/error-api'
import type { ErrorApi } from '../../shared/error-api'
import { esFechaValida } from '../../shared/fechas'
import {
  fechaDeInicio,
  formatearDiaCortoConFecha,
  formatearDiaMes,
  formatearFechaLarga,
  horaDeInicio,
  horaMasMinutos,
  nombreDia,
  numeroDia,
} from '../../shared/formato-fechas'
import {
  estaEnVentana,
  manejadoresNavegadorDias,
  moverSemana,
  navegadorDias,
  semanaDe,
} from '../../shared/navegador-dias'
import { selectorHorarios } from '../../shared/selector-horarios'
import type { ManejadoresUI, NodoUI } from '../../shared/ui/adaptador'
import type { Descriptor } from '../../shared/ui/componentes'
import type { ApiPublica } from '../reservar/api-publica'
import type { HorarioOpcion, PerfilReserva } from '../reservar/modelo'
import type { ApiGestion } from './api-gestion'
import { esTokenValido } from './api-gestion'
import { estadoInicialGestion, reaccionARechazo, reducir } from './estado'
import type { AccionGestion, EstadoGestion, TurnoGestion, VistaGestion } from './modelo'

export interface OpcionesControlTurno {
  token: string
  api: ApiGestion
  apiPublica: Pick<ApiPublica, 'perfil' | 'disponibilidad'>
  alCambiar?: () => void
}

export interface ControlTurno {
  estado(): EstadoGestion
  descriptor(): Descriptor
  manejadores(): ManejadoresUI
  iniciar(): Promise<void>
  abrirCancelacion(): void
  cerrarCancelacion(): void
  confirmarCancelacion(): Promise<void>
  abrirReprogramacion(): Promise<void>
  volverAlTurno(): void
  elegirFecha(fecha: string): Promise<void>
  cambiarSemana(direccion: number): void
  elegirHorario(horario: HorarioOpcion): void
  confirmarCambio(): Promise<void>
}

const ID_LEYENDA = 'ley-actual'

/* ---------- Textos ---------- */

function nombreOdontologo(turno: TurnoGestion): string {
  return `${turno.odontologo.nombre} ${turno.odontologo.apellido}`.trim()
}

function fechaTurno(turno: TurnoGestion): string {
  return formatearFechaLarga(fechaDeInicio(turno.inicio))
}

function rangoTurno(turno: TurnoGestion): string {
  return `${horaDeInicio(turno.inicio)} a ${horaDeInicio(turno.fin)}`
}

function minutosTurno(turno: TurnoGestion): number {
  const minutos = Math.round((new Date(turno.fin).getTime() - new Date(turno.inicio).getTime()) / 60_000)
  return Number.isFinite(minutos) && minutos > 0 ? minutos : 0
}

/** Duración vigente de la prestación (la que usa el sucesor) o, si no está, la guardada. */
function duracion(turno: TurnoGestion, perfil: PerfilReserva | undefined): number {
  return perfil?.prestaciones.find((item) => item.id === turno.prestacion.id)?.duracionMinutos ?? minutosTurno(turno)
}

function esPasado(turno: TurnoGestion): boolean {
  return turno.estado !== 'CANCELADO'
}

/* ---------- Piezas ---------- */

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
            hijos: [{ etiqueta: 'img', atributos: { src: urlLogo, alt: NOMBRE_MARCA, width: '135', height: '44' } }],
          },
        ],
      },
    ],
  }
}

type TipoAviso = 'error' | 'success' | 'info' | 'warning'

function aviso(tipo: TipoAviso, titulo: string, detalle?: string, extra: readonly Descriptor[] = []): Descriptor {
  return {
    etiqueta: 'div',
    clase: `alert alert--${tipo}`,
    atributos: tipo === 'error' || tipo === 'warning' ? { role: 'alert' } : { role: 'status', 'aria-live': 'polite' },
    hijos: [
      { etiqueta: 'strong', texto: titulo },
      ...(detalle === undefined ? [] : [{ etiqueta: 'span' as const, texto: detalle }]),
      ...extra,
    ],
  }
}

function enlaceReserva(turno: TurnoGestion, texto: string, clase: string): Descriptor {
  return {
    etiqueta: 'a',
    texto,
    clase,
    atributos: { href: `/reservar/${encodeURIComponent(turno.odontologo.slug)}` },
    accion: 'navegar',
  }
}

function avisoSinCambio(turno: TurnoGestion): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'alert alert--info',
    atributos: { role: 'status' },
    hijos: [
      {
        etiqueta: 'p',
        texto: 'Este turno no se puede cambiar desde el link. Si necesitás otro horario, cancelalo y ',
        hijos: [enlaceReserva(turno, 'reservá uno nuevo', 'link')],
      },
    ],
  }
}

function botonAccion(
  texto: string,
  accion: string,
  variante: string,
  opciones: { deshabilitado?: boolean; enviando?: boolean; enfocar?: boolean; clase?: string } = {},
): Descriptor {
  const deshabilitado = opciones.deshabilitado === true || opciones.enviando === true

  return {
    etiqueta: 'button',
    clase: ['btn', `btn--${variante}`, ...(opciones.clase === undefined ? [] : [opciones.clase])].join(' '),
    atributos: {
      type: 'button',
      ...(deshabilitado ? { disabled: '' } : {}),
      ...(opciones.enviando === true ? { 'aria-busy': 'true' } : {}),
    },
    accion,
    ...(opciones.enfocar === true ? { enfocar: true } : {}),
    ...(opciones.enviando === true
      ? { hijos: [{ etiqueta: 'span', clase: 'spinner', atributos: { 'aria-hidden': 'true' } }, { etiqueta: 'span', texto }] }
      : { texto }),
  }
}

function heroTurno(turno: TurnoGestion, inactivo: boolean): Descriptor {
  const cancelado = turno.estado === 'CANCELADO'
  const badge = !inactivo
    ? { clase: 'gestion-badge gestion-badge--reservado', texto: 'Reservado' }
    : cancelado
      ? { clase: 'gestion-badge gestion-badge--cancelado', texto: 'Cancelado' }
      : { clase: 'gestion-badge gestion-badge--pasado', texto: 'Pasado' }

  return {
    etiqueta: 'div',
    clase: ['turno-hero', ...(inactivo ? ['turno-hero--inactivo'] : []), ...(cancelado ? ['turno-hero--tachado'] : [])].join(' '),
    hijos: [
      { etiqueta: 'div', clase: 'turno-hero__badges', hijos: [{ etiqueta: 'span', ...badge }] },
      { etiqueta: 'p', texto: fechaTurno(turno), clase: 'turno-hero__fecha' },
      { etiqueta: 'p', texto: rangoTurno(turno), clase: 'turno-hero__hora' },
    ],
  }
}

function datosTurno(turno: TurnoGestion, conDireccion: boolean): Descriptor {
  const direccion = turno.odontologo.direccionConsultorio?.trim() ?? ''
  const filas: Descriptor[] = [
    { etiqueta: 'dt', texto: 'Prestación' },
    { etiqueta: 'dd', texto: turno.prestacion.nombre },
    { etiqueta: 'dt', texto: 'Profesional' },
    { etiqueta: 'dd', texto: nombreOdontologo(turno) },
  ]
  if (conDireccion && direccion !== '') {
    filas.push({ etiqueta: 'dt', texto: 'Dirección' }, { etiqueta: 'dd', texto: direccion })
  }
  return { etiqueta: 'dl', clase: 'gestion-datos', hijos: filas }
}

function ayuda(titulo: string, contenido: Descriptor, clase = 'gestion-ayuda'): Descriptor {
  return {
    etiqueta: 'section',
    clase,
    hijos: [{ etiqueta: 'h2', texto: titulo }, contenido],
  }
}

function lista(items: readonly string[]): Descriptor {
  return { etiqueta: 'ul', hijos: items.map((texto) => ({ etiqueta: 'li' as const, texto })) }
}

interface OpcionesPagina {
  saludo?: string
  titulo: string
  enfocarTitulo: boolean
  principal: readonly Descriptor[]
  lateral?: readonly Descriptor[]
  previo?: readonly Descriptor[]
  superpuesto?: readonly Descriptor[]
}

function pagina(opciones: OpcionesPagina): Descriptor {
  const tapado = opciones.superpuesto !== undefined && opciones.superpuesto.length > 0

  return {
    etiqueta: 'div',
    clase: 'reserva-publica gestion-turno',
    hijos: [
      appbar(),
      {
        etiqueta: 'div',
        clase: 'gestion-layout',
        ...(tapado ? { atributos: { 'aria-hidden': 'true' } } : {}),
        hijos: [
          {
            etiqueta: 'main',
            clase: 'gestion-main',
            hijos: [
              ...(opciones.previo ?? []),
              ...(opciones.saludo === undefined ? [] : [{ etiqueta: 'p' as const, texto: opciones.saludo, clase: 'gestion-saludo' }]),
              {
                etiqueta: 'h1',
                texto: opciones.titulo,
                clase: 'gestion-titulo',
                atributos: { tabindex: '-1' },
                ...(opciones.enfocarTitulo ? { enfocar: true } : {}),
              },
              ...opciones.principal,
            ],
          },
          ...(opciones.lateral === undefined || opciones.lateral.length === 0
            ? []
            : [{ etiqueta: 'aside' as const, clase: 'gestion-lateral', atributos: { 'aria-label': 'Información' }, hijos: opciones.lateral }]),
        ],
      },
      ...(opciones.superpuesto ?? []),
    ],
  }
}

function tarjeta(hijos: readonly Descriptor[], extra: Readonly<Record<string, string>> = {}): Descriptor {
  return { etiqueta: 'section', clase: 'gestion-tarjeta', atributos: extra, hijos }
}

/* ---------- Vistas ---------- */

function avisoResultado(estado: EstadoGestion): Descriptor[] {
  const resultado = estado.resultado
  if (resultado === undefined) {
    return []
  }
  if (resultado.tipo === 'cancelado') {
    return [aviso('success', 'Cancelaste tu turno', 'El horario quedó libre. Te mandamos un email con la cancelación.')]
  }
  if (resultado.tipo === 'reprogramado') {
    const fecha = fechaDeInicio(resultado.inicio)
    const cuando = esFechaValida(fecha)
      ? `al ${nombreDia(fecha)} ${formatearDiaMes(fecha)} a las ${horaDeInicio(resultado.inicio)}`
      : `a las ${horaDeInicio(resultado.inicio)}`
    return [
      aviso(
        'success',
        `Listo, cambiaste tu turno ${cuando}`,
        'Te mandamos un email con el link nuevo para gestionarlo. Este link queda solo para consultar el turno anterior.',
      ),
    ]
  }
  return [aviso('warning', 'No pudimos hacer el cambio', 'Este turno ya no se puede modificar. Te mostramos cómo quedó.')]
}

function cargando(estado: EstadoGestion, enfocar: boolean): Descriptor {
  return pagina({
    titulo: 'Tu turno',
    enfocarTitulo: enfocar,
    principal: [
      ...avisoResultado(estado),
      tarjeta(
        [
          { etiqueta: 'div', clase: 'skeleton gestion-esqueleto--alto' },
          { etiqueta: 'div', clase: 'skeleton skeleton--line' },
          { etiqueta: 'div', clase: 'skeleton skeleton--line' },
          { etiqueta: 'p', texto: 'Cargando tu turno.', clase: 'sr-only', atributos: { 'aria-live': 'polite' } },
        ],
        { 'aria-busy': 'true' },
      ),
    ],
  })
}

function errorLink(enfocar: boolean): Descriptor {
  return pagina({
    titulo: 'No encontramos este turno',
    enfocarTitulo: enfocar,
    principal: [
      tarjeta([
        {
          etiqueta: 'p',
          texto:
            'Revisá que el link esté completo, tal como llegó en el email. Si cambiaste el horario, usá el link del email más reciente.',
          clase: 'gestion-texto',
        },
      ]),
    ],
  })
}

function errorCarga(estado: EstadoGestion, enfocar: boolean): Descriptor {
  return pagina({
    titulo: 'Tu turno',
    enfocarTitulo: enfocar,
    principal: [
      ...avisoResultado(estado),
      tarjeta([
        aviso('error', 'No pudimos conectarnos', 'Probá de nuevo en unos segundos. Tu turno no cambió.'),
        botonAccion('Reintentar', 'reintentar-turno', 'secondary'),
      ]),
    ],
  })
}

function lateralActivo(): Descriptor[] {
  return [
    ayuda(
      'Antes de tu turno',
      lista([
        'Podés cancelar en cualquier momento antes de que empiece.',
        'Si cambiás el horario, el nuevo tiene que ser con al menos 24 h de anticipación.',
        'Cada cambio te llega por email.',
      ]),
    ),
    ayuda(
      'Este link es personal',
      { etiqueta: 'p', texto: 'Con este link se puede cancelar o cambiar tu turno. No lo compartas.' },
      'gestion-ayuda gestion-ayuda--aviso',
    ),
  ]
}

function dialogoCancelacion(estado: EstadoGestion, turno: TurnoGestion): Descriptor[] {
  return [
    { etiqueta: 'div', clase: 'gestion-velo', atributos: { 'aria-hidden': 'true' } },
    {
      etiqueta: 'div',
      clase: 'gestion-dialogo',
      atributos: {
        role: 'alertdialog',
        'aria-modal': 'true',
        'aria-labelledby': 'dialogo-titulo',
        'aria-describedby': 'dialogo-texto',
      },
      teclas: { Escape: 'cerrar-cancelacion' },
      hijos: [
        { etiqueta: 'h2', texto: '¿Cancelás tu turno?', atributos: { id: 'dialogo-titulo' } },
        {
          etiqueta: 'div',
          clase: 'gestion-resumen',
          hijos: [
            { etiqueta: 'strong', texto: `${fechaTurno(turno)}, ${horaDeInicio(turno.inicio)}` },
            { etiqueta: 'span', texto: `${turno.prestacion.nombre} · ${nombreOdontologo(turno)}` },
          ],
        },
        {
          etiqueta: 'p',
          texto: 'El horario queda libre para otra persona y no se puede deshacer. Te mandamos un email con la cancelación.',
          atributos: { id: 'dialogo-texto' },
        },
        ...(estado.aviso === 'sin-conexion-cancelar'
          ? [aviso('error', 'No pudimos conectarnos', 'Probá de nuevo en unos segundos. Tu turno no cambió.')]
          : []),
        {
          etiqueta: 'div',
          clase: 'gestion-dialogo__acciones',
          hijos: [
            botonAccion(estado.enviando ? 'Cancelando…' : 'Sí, cancelar turno', 'confirmar-cancelacion', 'danger-solid', {
              enviando: estado.enviando,
            }),
            botonAccion('No, volver', 'cerrar-cancelacion', 'secondary', {
              deshabilitado: estado.enviando,
              enfocar: !estado.enviando,
            }),
          ],
        },
      ],
    },
  ]
}

function activo(estado: EstadoGestion, turno: TurnoGestion, enfocar: boolean): Descriptor {
  const acciones: Descriptor[] = []
  if (turno.puedeReprogramar) {
    acciones.push(botonAccion('Cambiar horario', 'abrir-reprogramacion', 'secondary'))
  }
  if (turno.puedeCancelar) {
    acciones.push(botonAccion('Cancelar turno', 'abrir-cancelacion', 'danger', { enfocar: estado.enfocarCancelar }))
  }
  const confirmando = estado.vista === 'confirmar-cancelacion'

  return pagina({
    saludo: `Tu turno con ${nombreOdontologo(turno)}`,
    titulo: 'Gestioná tu turno',
    enfocarTitulo: enfocar && !confirmando,
    principal: [
      ...avisoResultado(estado),
      ...(!turno.puedeReprogramar && turno.puedeCancelar ? [avisoSinCambio(turno)] : []),
      tarjeta(
        [
          { etiqueta: 'h2', texto: 'Detalle del turno', clase: 'sr-only', atributos: { id: 'h-turno' } },
          heroTurno(turno, false),
          datosTurno(turno, true),
          {
            etiqueta: 'div',
            clase: acciones.length === 1 ? 'gestion-acciones gestion-acciones--una' : 'gestion-acciones',
            hijos: acciones,
          },
        ],
        { 'aria-labelledby': 'h-turno' },
      ),
    ],
    lateral: lateralActivo(),
    ...(confirmando ? { superpuesto: dialogoCancelacion(estado, turno) } : {}),
  })
}

function avisosReprogramacion(estado: EstadoGestion): Descriptor[] {
  switch (estado.aviso) {
    case 'horario-tomado':
      return [aviso('error', 'Ese horario acaba de ser tomado', 'Elegí otro; ya actualizamos los disponibles. Tu turno actual sigue igual.')]
    case 'horario-no-disponible':
      return [aviso('error', 'Ese horario ya no está disponible', 'Elegí otro de la lista. Tu turno actual sigue igual.')]
    case 'sin-conexion-reprogramar':
      return [aviso('error', 'No pudimos conectarnos', 'Probá de nuevo en unos segundos. Tu turno no cambió.')]
    case 'sin-conexion-horarios':
      return [
        aviso('error', 'No pudimos cargar los horarios', 'Revisá tu conexión y volvé a intentar.'),
        botonAccion('Reintentar', 'reintentar-horarios', 'secondary'),
      ]
    default:
      return []
  }
}

function comparacion(turno: TurnoGestion, horario: HorarioOpcion, minutos: number): Descriptor {
  const fechaNueva = fechaDeInicio(horario.inicio)
  const fechaActual = fechaDeInicio(turno.inicio)

  return {
    etiqueta: 'div',
    clase: 'gestion-cambio',
    atributos: { 'aria-live': 'polite' },
    hijos: [
      {
        etiqueta: 'dl',
        hijos: [
          {
            etiqueta: 'div',
            clase: 'gestion-cambio__fila gestion-cambio__fila--viejo',
            hijos: [
              { etiqueta: 'dt', texto: 'Antes' },
              { etiqueta: 'dd', texto: `${formatearDiaCortoConFecha(fechaActual)}, ${horaDeInicio(turno.inicio)}` },
            ],
          },
          {
            etiqueta: 'div',
            clase: 'gestion-cambio__fila',
            hijos: [
              { etiqueta: 'dt', texto: 'Nuevo' },
              {
                etiqueta: 'dd',
                texto: `${formatearDiaCortoConFecha(fechaNueva)}, ${horario.hora} a ${horaMasMinutos(horario.inicio, minutos)}`,
              },
            ],
          },
        ],
      },
    ],
  }
}

function reprogramando(estado: EstadoGestion, turno: TurnoGestion, enfocar: boolean): Descriptor {
  const perfil = estado.perfil
  const minutos = duracion(turno, perfil)
  const contenido: Descriptor[] = [
    { etiqueta: 'h2', texto: 'Nuevo día y horario', clase: 'sr-only', atributos: { id: 'h-selector' } },
    { etiqueta: 'p', texto: `${turno.prestacion.nombre} · ${minutos} min`, clase: 'step gestion-prestacion' },
    ...avisosReprogramacion(estado),
  ]

  if (estado.cargandoPerfil || perfil === undefined || estado.fecha === undefined || estado.inicioSemana === undefined) {
    if (estado.aviso !== 'sin-conexion-horarios') {
      contenido.push(
        { etiqueta: 'div', clase: 'skeleton skeleton--line' },
        { etiqueta: 'div', clase: 'skeleton gestion-esqueleto--alto' },
        { etiqueta: 'p', texto: 'Cargando los días disponibles.', clase: 'sr-only', atributos: { 'aria-live': 'polite' } },
      )
    }
  } else {
    const selector = selectorHorarios({
      horarios: estado.horarios,
      ...(estado.horario === undefined ? {} : { seleccionado: estado.horario }),
      onSeleccionar: () => {},
      vacio: 'No hay horarios libres este día. Probá con otro día.',
      cargando: estado.cargandoHorarios,
    })
    contenido.push(
      { etiqueta: 'p', texto: 'Día', clase: 'step' },
      ...navegadorDias({
        ventana: perfil,
        inicioSemana: estado.inicioSemana,
        seleccionada: estado.fecha,
        diaMarcado: fechaDeInicio(turno.inicio),
        idLeyenda: ID_LEYENDA,
      }),
      {
        etiqueta: 'p',
        clase: 'leyenda-dia',
        atributos: { id: ID_LEYENDA },
        hijos: [
          { etiqueta: 'span', clase: 'leyenda-dia__punto', atributos: { 'aria-hidden': 'true' } },
          { etiqueta: 'span', texto: 'Día de tu turno actual' },
        ],
      },
      { etiqueta: 'p', texto: `Horarios libres el ${nombreDia(estado.fecha)} ${numeroDia(estado.fecha)}`, clase: 'step' },
      selector.descriptor,
      ...(estado.horario === undefined ? [] : [comparacion(turno, estado.horario, minutos)]),
    )
  }

  contenido.push(
    {
      etiqueta: 'div',
      clase: 'gestion-acciones gestion-acciones--una',
      hijos: [
        botonAccion(estado.enviando ? 'Cambiando tu turno…' : 'Confirmar cambio', 'confirmar-cambio', 'primary', {
          enviando: estado.enviando,
          deshabilitado: estado.horario === undefined,
          clase: 'btn--block',
        }),
      ],
    },
    {
      etiqueta: 'p',
      texto: 'Tu turno actual se mantiene hasta que confirmes. El link para gestionar el turno nuevo te llega por email.',
      clase: 'gestion-nota',
    },
  )

  return pagina({
    titulo: 'Elegí un nuevo horario',
    enfocarTitulo: enfocar,
    previo: [botonAccion('← Volver a mi turno', 'volver-al-turno', 'ghost', { deshabilitado: estado.enviando, clase: 'gestion-volver' })],
    principal: [tarjeta(contenido, { 'aria-labelledby': 'h-selector' })],
    lateral: [
      ayuda(
        'Cómo funciona el cambio',
        lista([
          'Se cancela tu turno actual y se reserva el nuevo en un solo paso. Si algo falla, conservás el actual.',
          'Solo ves horarios con al menos 24 h de anticipación.',
          'Después del cambio, este link queda solo para consultar el turno anterior.',
        ]),
      ),
    ],
  })
}

function soloLectura(estado: EstadoGestion, turno: TurnoGestion, enfocar: boolean): Descriptor {
  const cancelado = turno.estado === 'CANCELADO'
  const reprogramado = estado.resultado?.tipo === 'reprogramado'
  const avisos = avisoResultado(estado)
  if (avisos.length === 0 && esPasado(turno)) {
    avisos.push(aviso('info', 'Este turno ya pasó. Solo podés consultarlo.'))
  }

  return pagina({
    saludo: `Tu turno con ${nombreOdontologo(turno)}`,
    titulo: 'Tu turno',
    enfocarTitulo: enfocar,
    principal: [
      ...avisos,
      tarjeta(
        [
          { etiqueta: 'h2', texto: cancelado ? 'Turno cancelado' : 'Turno pasado', clase: 'sr-only', atributos: { id: 'h-turno' } },
          heroTurno(turno, true),
          datosTurno(turno, !cancelado),
          ...(reprogramado
            ? []
            : [
                {
                  etiqueta: 'div' as const,
                  clase: 'gestion-acciones gestion-acciones--una',
                  hijos: [enlaceReserva(turno, 'Reservar otro turno', 'btn btn--secondary')],
                },
              ]),
        ],
        { 'aria-labelledby': 'h-turno' },
      ),
    ],
    lateral: [
      ayuda('¿Necesitás otro turno?', {
        etiqueta: 'p',
        texto: `Podés reservar uno nuevo con ${nombreOdontologo(turno)} desde su página de reservas.`,
      }),
    ],
  })
}

/* ---------- Control ---------- */

function errorDe(error: unknown): ErrorApi {
  return interpretarErrorApi(error)
}

export function crearControlTurno(opciones: OpcionesControlTurno): ControlTurno {
  const { token, api, apiPublica } = opciones
  let estado: EstadoGestion = estadoInicialGestion()
  let ultimaVista: VistaGestion | undefined
  let versionTurno = 0
  let versionDisponibilidad = 0

  function despachar(accion: AccionGestion): void {
    const nuevo = reducir(estado, accion)
    if (nuevo === estado) {
      return
    }
    estado = nuevo
    opciones.alCambiar?.()
  }

  async function releer(): Promise<void> {
    const version = ++versionTurno
    despachar({ tipo: 'cargar' })
    if (!esTokenValido(token)) {
      despachar({ tipo: 'turno-error', error: { codigo: 'NO_ENCONTRADO', mensaje: 'No encontramos lo que buscabas.' } })
      return
    }
    try {
      const turno = await api.consultar(token)
      if (version === versionTurno) {
        despachar({ tipo: 'turno-cargado', turno })
      }
    } catch (error: unknown) {
      if (version === versionTurno) {
        despachar({ tipo: 'turno-error', error: errorDe(error) })
      }
    }
  }

  function abrirCancelacion(): void {
    despachar({ tipo: 'abrir-cancelacion' })
  }

  function cerrarCancelacion(): void {
    despachar({ tipo: 'cerrar-cancelacion' })
  }

  async function confirmarCancelacion(): Promise<void> {
    if (estado.vista !== 'confirmar-cancelacion' || estado.enviando) {
      return
    }
    despachar({ tipo: 'enviar-cancelacion' })
    try {
      await api.cancelar(token)
      despachar({ tipo: 'cancelacion-ok' })
      await releer()
    } catch (error: unknown) {
      const apiError = errorDe(error)
      if (reaccionARechazo(apiError.codigo, 'cancelar') === 'accion-rechazada') {
        despachar({ tipo: 'accion-rechazada', error: apiError })
        await releer()
        return
      }
      despachar({ tipo: 'error-envio', error: apiError, accion: 'cancelar' })
    }
  }

  async function cargarDisponibilidad(fecha: string): Promise<void> {
    const turno = estado.turno
    if (turno === undefined) {
      return
    }
    const version = ++versionDisponibilidad
    despachar({ tipo: 'disponibilidad-inicio', fecha })
    try {
      const disponibilidad = await apiPublica.disponibilidad(turno.odontologo.slug, turno.prestacion.id, fecha)
      if (version === versionDisponibilidad) {
        despachar({ tipo: 'disponibilidad-cargada', fecha, horarios: disponibilidad.horarios })
      }
    } catch (error: unknown) {
      if (version === versionDisponibilidad) {
        despachar({ tipo: 'disponibilidad-error', error: errorDe(error) })
      }
    }
  }

  function fechaInicial(perfil: PerfilReserva, turno: TurnoGestion): string {
    const actual = fechaDeInicio(turno.inicio)
    return estaEnVentana(perfil, actual) ? actual : perfil.fechaMinima
  }

  async function abrirReprogramacion(): Promise<void> {
    despachar({ tipo: 'abrir-reprogramacion' })
    const turno = estado.turno
    if (estado.vista !== 'reprogramando' || turno === undefined) {
      return
    }
    let perfil = estado.perfil
    if (perfil === undefined) {
      try {
        perfil = await apiPublica.perfil(turno.odontologo.slug)
      } catch (error: unknown) {
        despachar({ tipo: 'perfil-error', error: errorDe(error) })
        return
      }
    }
    if (estado.vista !== 'reprogramando') {
      despachar({ tipo: 'perfil-cargado', perfil, fecha: perfil.fechaMinima, inicioSemana: perfil.fechaMinima })
      return
    }
    const fecha = fechaInicial(perfil, turno)
    despachar({ tipo: 'perfil-cargado', perfil, fecha, inicioSemana: semanaDe(perfil, fecha) })
    await cargarDisponibilidad(fecha)
  }

  function volverAlTurno(): void {
    versionDisponibilidad += 1
    despachar({ tipo: 'volver-al-turno' })
  }

  async function elegirFecha(fecha: string): Promise<void> {
    const perfil = estado.perfil
    if (estado.vista !== 'reprogramando' || estado.enviando || perfil === undefined) {
      return
    }
    if (!esFechaValida(fecha) || !estaEnVentana(perfil, fecha)) {
      return
    }
    const semana = semanaDe(perfil, fecha)
    if (semana !== estado.inicioSemana) {
      despachar({ tipo: 'cambiar-semana', inicioSemana: semana })
    }
    await cargarDisponibilidad(fecha)
  }

  function cambiarSemana(direccion: number): void {
    const perfil = estado.perfil
    if (perfil === undefined || estado.inicioSemana === undefined) {
      return
    }
    const destino = moverSemana(perfil, estado.inicioSemana, direccion)
    if (destino !== undefined) {
      despachar({ tipo: 'cambiar-semana', inicioSemana: destino })
    }
  }

  function elegirHorario(horario: HorarioOpcion): void {
    despachar({ tipo: 'elegir-horario', horario })
  }

  async function confirmarCambio(): Promise<void> {
    const horario = estado.horario
    if (estado.vista !== 'reprogramando' || estado.enviando || horario === undefined) {
      return
    }
    despachar({ tipo: 'enviar-reprogramacion' })
    try {
      await api.reprogramar(token, horario.inicio)
      despachar({ tipo: 'reprogramacion-ok', inicio: horario.inicio })
      await releer()
    } catch (error: unknown) {
      const apiError = errorDe(error)
      switch (reaccionARechazo(apiError.codigo, 'reprogramar')) {
        case 'horario-rechazado':
          despachar({ tipo: 'horario-rechazado', error: apiError })
          if (estado.fecha !== undefined) {
            await cargarDisponibilidad(estado.fecha)
          }
          return
        case 'no-reprogramable':
          despachar({ tipo: 'no-reprogramable', error: apiError })
          await releer()
          return
        case 'accion-rechazada':
          despachar({ tipo: 'accion-rechazada', error: apiError })
          await releer()
          return
        case 'error-envio':
          despachar({ tipo: 'error-envio', error: apiError, accion: 'reprogramar' })
      }
    }
  }

  function reintentarHorarios(): void {
    if (estado.perfil === undefined) {
      void abrirReprogramacionDeNuevo()
      return
    }
    if (estado.fecha !== undefined) {
      void cargarDisponibilidad(estado.fecha)
    }
  }

  async function abrirReprogramacionDeNuevo(): Promise<void> {
    if (estado.vista !== 'reprogramando') {
      return
    }
    despachar({ tipo: 'volver-al-turno' })
    await abrirReprogramacion()
  }

  function descriptor(): Descriptor {
    const vistaActual = estado.vista
    const enfocar = ultimaVista !== undefined && ultimaVista !== vistaActual
    ultimaVista = vistaActual
    const turno = estado.turno

    switch (vistaActual) {
      case 'cargando':
        return cargando(estado, enfocar)
      case 'error-link':
        return errorLink(enfocar)
      case 'error-carga':
        return errorCarga(estado, enfocar)
      case 'activo':
      case 'confirmar-cancelacion':
        return turno === undefined ? cargando(estado, enfocar) : activo(estado, turno, enfocar)
      case 'reprogramando':
        return turno === undefined ? cargando(estado, enfocar) : reprogramando(estado, turno, enfocar)
      case 'solo-lectura':
        return turno === undefined ? cargando(estado, enfocar) : soloLectura(estado, turno, enfocar)
    }
  }

  function manejadores(): ManejadoresUI {
    const resultado: Record<string, (nodo: NodoUI) => void> = {
      'reintentar-turno': () => {
        void releer()
      },
      'abrir-cancelacion': () => abrirCancelacion(),
      'cerrar-cancelacion': () => cerrarCancelacion(),
      'confirmar-cancelacion': () => {
        void confirmarCancelacion()
      },
      'abrir-reprogramacion': () => {
        void abrirReprogramacion()
      },
      'volver-al-turno': () => volverAlTurno(),
      'confirmar-cambio': () => {
        void confirmarCambio()
      },
      'reintentar-horarios': () => reintentarHorarios(),
    }

    const perfil = estado.perfil
    if (estado.vista === 'reprogramando' && perfil !== undefined && estado.inicioSemana !== undefined) {
      Object.assign(
        resultado,
        manejadoresNavegadorDias(perfil, estado.inicioSemana, {
          elegirFecha: (fecha) => {
            void elegirFecha(fecha)
          },
          cambiarSemana,
        }),
      )
      const selector = selectorHorarios({
        horarios: estado.horarios,
        onSeleccionar: (horario) => elegirHorario(horario),
        vacio: '',
      })
      Object.assign(resultado, selector.manejadores)
    }

    return resultado
  }

  return {
    estado: () => estado,
    descriptor,
    manejadores,
    iniciar: releer,
    abrirCancelacion,
    cerrarCancelacion,
    confirmarCancelacion,
    abrirReprogramacion,
    volverAlTurno,
    elegirFecha,
    cambiarSemana,
    elegirHorario,
    confirmarCambio,
  }
}
