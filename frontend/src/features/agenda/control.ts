import type { ClienteApi } from '../../shared/api-client'
import { fechaLocalArgentina, sumarDias } from '../../shared/fechas'
import type { ManejadoresUI, NodoUI } from '../../shared/ui/adaptador'
import { toast } from '../../shared/ui/componentes'
import type { Descriptor } from '../../shared/ui/componentes'
import { consultarAgenda, consultarBloqueos, consultarHorarios, consultarWhatsApp } from './api'
import { cancelarTurno, interpretarCancelacion } from './cancelar'
import { descriptorConfirmacion } from './confirmacion'
import { contenedorDetalle } from './detalle'
import { descriptorAviso, descriptorEstado, seleccionarEstado } from './estados'
import { diaSemanaDe, tituloFecha, tituloRango } from './formato'
import { crearControlNavegacion, rangoDeVista } from './navegacion'
import type { RangoAgenda } from './navegacion'
import { descriptorDia } from './vista-dia'
import { descriptorSemana } from './vista-semana'
import type { BloqueoAgenda, FranjaHorario, TurnoAgenda } from './tipos'
import { disponibilidadWhatsApp } from './whatsapp-detalle'
import type { EstadoWhatsApp } from './whatsapp-detalle'
import { cerrarPestanaPendiente, navegarPestanaWhatsApp, reservarPestanaWhatsApp } from './whatsapp-popup'

export interface OpcionesControlAgenda {
  cliente: ClienteApi
  ancho: number
  ahora: Date | (() => Date)
  alCambiar?: (rango: RangoAgenda) => void
}

type Aviso = 'ya-empezo' | 'cambio' | 'error'

interface DatosAgenda {
  rango: RangoAgenda
  franjas: readonly FranjaHorario[]
  turnos: readonly TurnoAgenda[]
  bloqueos: readonly BloqueoAgenda[]
  cargando: boolean
  error: unknown
}

export interface ControlAgenda {
  descriptor(): Descriptor
  manejadores: ManejadoresUI
  cargar(): Promise<void>
  desmontar(): void
  cancelarWhatsAppAlOcultar(persistido: boolean): void
}

interface DisparadorAgenda {
  id: number
  etiqueta: 'article' | 'button'
  accion: 'ver-turno' | 'cancelar-turno'
}

interface NodoConAtributos extends NodoUI {
  getAttribute(nombre: string): string | null
}

function atributoDe(nodo: NodoUI, nombre: string): string | undefined {
  const candidato: Partial<NodoConAtributos> = nodo
  return typeof candidato.getAttribute === 'function'
    ? candidato.getAttribute(nombre) ?? undefined
    : undefined
}

function idDe(nodo: NodoUI): number | undefined {
  const texto = atributoDe(nodo, 'data-turno-id')
  if (texto === undefined) {
    return undefined
  }
  const id = Number(texto)
  return Number.isInteger(id) && id > 0 ? id : undefined
}

/**
 * Extremo exclusivo para el título semanal: el día visible coincide con las
 * columnas que dibuja `vista-semana` (días con franjas). Mientras cargan las
 * franjas, o si ninguna cae en el rango, se conserva el rango completo.
 */
function finDelTituloSemana(
  rango: RangoAgenda,
  franjas: readonly FranjaHorario[],
  cargando: boolean,
): string {
  if (cargando || franjas.length === 0) {
    return rango.hasta
  }
  const diasAtendidos = new Set(franjas.map((franja) => franja.diaSemana))
  let ultimoDia: string | undefined
  for (let fecha = rango.desde; fecha < rango.hasta; fecha = sumarDias(fecha, 1)) {
    if (diasAtendidos.has(diaSemanaDe(fecha))) {
      ultimoDia = fecha
    }
  }
  return ultimoDia === undefined ? rango.hasta : sumarDias(ultimoDia, 1)
}

export function crearControlAgenda(opciones: OpcionesControlAgenda): ControlAgenda {
  const instante = (): Date => (typeof opciones.ahora === 'function' ? opciones.ahora() : opciones.ahora)
  let seleccionadoId: number | undefined
  let confirmacionId: number | undefined
  let aviso: Aviso | undefined
  let mensajeToast: string | undefined
  let generacion = 0
  let desmontado = false
  let whatsapp: EstadoWhatsApp | undefined
  let preparacion: { pestana: Window | undefined } | undefined
  let focoDetalle: 'cerrar' | 'whatsapp' | 'cancelar' | undefined
  let disparador: DisparadorAgenda | undefined
  let retorno: DisparadorAgenda | undefined
  let cancelacionDesdeDetalle = false
  let datos: DatosAgenda = {
    rango: rangoDeVista('dia', fechaLocalArgentina(instante())),
    franjas: [],
    turnos: [],
    bloqueos: [],
    cargando: true,
    error: undefined,
  }

  const notificar = (rango: RangoAgenda): void => {
    if (!desmontado) opciones.alCambiar?.(rango)
  }

  function descartarWhatsApp(): void {
    const anterior = preparacion
    preparacion = undefined
    whatsapp = undefined
    cerrarPestanaPendiente(anterior?.pestana)
  }

  async function recargar(rango: RangoAgenda): Promise<void> {
    if (desmontado) return
    descartarWhatsApp()
    const actual = ++generacion
    datos = { ...datos, rango, cargando: true, error: undefined }
    notificar(rango)
    try {
      const [turnos, franjas, bloqueos] = await Promise.all([
        consultarAgenda(opciones.cliente, rango.desde, rango.hasta),
        consultarHorarios(opciones.cliente),
        consultarBloqueos(opciones.cliente, rango.desde, rango.hasta),
      ])
      if (actual !== generacion) {
        return
      }
      datos = { rango, turnos, franjas, bloqueos, cargando: false, error: undefined }
    } catch (error: unknown) {
      if (actual !== generacion) {
        return
      }
      datos = { ...datos, rango, cargando: false, error }
    }
    notificar(rango)
  }

  const navegacion = crearControlNavegacion({
    ancho: opciones.ancho,
    ahora: instante,
    alCambiar: (rango) => {
      seleccionadoId = undefined
      confirmacionId = undefined
      retorno = undefined
      void recargar(rango)
    },
  })
  datos = { ...datos, rango: rangoDeVista(navegacion.estado().vista, navegacion.estado().fecha) }

  function bloqueoDe(id: number): BloqueoAgenda | undefined {
    return datos.bloqueos.find((bloqueo) => bloqueo.turnosAfectados.some((turno) => turno.id === id))
  }

  function turnoDe(id: number | undefined): TurnoAgenda | undefined {
    return id === undefined ? undefined : datos.turnos.find((turno) => turno.id === id)
  }

  async function prepararWhatsApp(nodo: NodoUI): Promise<void> {
    const id = idDe(nodo)
    const turno = turnoDe(id)
    if (desmontado || turno === undefined || id !== seleccionadoId || preparacion !== undefined ||
      confirmacionId !== undefined || disponibilidadWhatsApp(turno, instante()) !== 'disponible' ||
      (whatsapp?.tipo === 'error' && whatsapp.falla !== 'error')) return

    const actual = { pestana: reservarPestanaWhatsApp() }
    preparacion = actual
    whatsapp = { tipo: 'preparando' }
    focoDetalle = 'whatsapp'
    notificar(datos.rango)
    const resultado = await consultarWhatsApp(opciones.cliente, turno.id)
    if (preparacion !== actual) return

    preparacion = undefined
    if (resultado.tipo !== 'lista') {
      cerrarPestanaPendiente(actual.pestana)
      whatsapp = { tipo: 'error', falla: resultado.tipo }
    } else if (navegarPestanaWhatsApp(actual.pestana, resultado.url)) {
      whatsapp = undefined
    } else {
      cerrarPestanaPendiente(actual.pestana)
      whatsapp = { tipo: 'apertura-bloqueada', url: resultado.url }
    }
    focoDetalle = 'whatsapp'
    notificar(datos.rango)
  }

  function devolverFoco(nodo: Descriptor, destino: DisparadorAgenda): Descriptor {
    if (nodo.etiqueta === destino.etiqueta && nodo.accion === destino.accion &&
      nodo.atributos?.['data-turno-id'] === String(destino.id)) return { ...nodo, enfocar: true }
    return nodo.hijos === undefined ? nodo : { ...nodo, hijos: nodo.hijos.map((hijo) => devolverFoco(hijo, destino)) }
  }

  function cabecera(fecha: string): Descriptor {
    const hoy = fechaLocalArgentina(instante())
    const vista = navegacion.estado().vista
    const dia = vista === 'dia'
    const rango = rangoDeVista(vista, fecha)
    const titulo = dia
      ? tituloFecha(fecha)
      : tituloRango(rango.desde, finDelTituloSemana(rango, datos.franjas, datos.cargando))
    const botonVista = (vista: 'dia' | 'semana', texto: string): Descriptor => ({
      etiqueta: 'button',
      texto,
      atributos: {
        type: 'button',
        'data-vista': vista,
        'aria-pressed': navegacion.estado().vista === vista ? 'true' : 'false',
      },
      accion: 'cambiar-vista',
    })

    return {
      etiqueta: 'div',
      clase: 'agenda-head',
      hijos: [
        {
          etiqueta: 'div',
          clase: 'agenda-head__titulo',
          hijos: [
            { etiqueta: 'h1', texto: titulo },
            {
              etiqueta: 'p',
              texto: fecha === hoy ? 'Hoy · horarios en hora de Argentina' : 'Horarios en hora de Argentina',
            },
          ],
        },
        {
          etiqueta: 'div',
          clase: 'seg',
          atributos: { role: 'group', 'aria-label': 'Vista' },
          hijos: [botonVista('dia', 'Día'), botonVista('semana', 'Semana')],
        },
        {
          etiqueta: 'div',
          clase: 'navfecha',
          hijos: [
            {
              etiqueta: 'button',
              texto: '‹',
              clase: 'btn btn--secondary btn--icono',
              atributos: { type: 'button', 'aria-label': dia ? 'Día anterior' : 'Semana anterior' },
              accion: 'anterior',
            },
            {
              etiqueta: 'button',
              texto: 'Hoy',
              clase: 'btn btn--secondary btn--sm',
              atributos: {
                type: 'button',
                ...(navegacion.puedeIrHoy() ? {} : { disabled: '' }),
              },
              accion: 'hoy',
            },
            {
              etiqueta: 'button',
              texto: '›',
              clase: 'btn btn--secondary btn--icono',
              atributos: { type: 'button', 'aria-label': dia ? 'Día siguiente' : 'Semana siguiente' },
              accion: 'siguiente',
            },
          ],
        },
      ],
    }
  }

  function descriptor(): Descriptor {
    const estado = navegacion.estado()
    const ahora = instante()
    const estadoVisual = seleccionarEstado({
      fecha: estado.fecha,
      vista: estado.vista,
      franjas: datos.franjas,
      turnos: datos.turnos,
      cargando: datos.cargando,
      error: datos.error,
    })
    const contenido: Descriptor[] = []
    const pieza = descriptorEstado(estadoVisual, {
      fecha: estado.fecha,
      vista: estado.vista,
      franjas: datos.franjas,
      turnos: datos.turnos,
      bloqueos: datos.bloqueos,
    })
    if (pieza !== undefined) {
      contenido.push(pieza)
    } else if (estadoVisual === 'con-agenda') {
      const comunes = {
        fecha: estado.fecha,
        ahora,
        franjas: datos.franjas,
        turnos: datos.turnos,
        bloqueos: datos.bloqueos,
        seleccionadoId,
      }
      const vista = estado.vista === 'dia' ? descriptorDia(comunes) : descriptorSemana(comunes)
      contenido.push(retorno === undefined ? vista : devolverFoco(vista, retorno))
    }

    const turno = turnoDe(seleccionadoId)
    if (turno !== undefined) {
      contenido.push(contenedorDetalle({
        turno,
        ahora,
        bloqueo: bloqueoDe(turno.id),
        vista: estado.vista,
        ancho: opciones.ancho,
        whatsapp,
        foco: focoDetalle,
      }))
    }

    if (confirmacionId !== undefined) {
      const confirmado = turnoDe(confirmacionId)
      if (confirmado !== undefined) {
        contenido.push(descriptorConfirmacion({
          turno: confirmado,
          ahora,
          enBloqueo: bloqueoDe(confirmado.id) !== undefined,
        }))
      }
    }

    if (aviso !== undefined) {
      contenido.push(descriptorAviso(aviso))
    }
    if (mensajeToast !== undefined) {
      contenido.push(toast({ mensaje: mensajeToast }))
    }

    retorno = undefined
    focoDetalle = undefined
    return { etiqueta: 'div', clase: 'agenda', hijos: [cabecera(estado.fecha), ...contenido] }
  }

  const manejadores: ManejadoresUI = {
    anterior: () => navegacion.anterior(),
    siguiente: () => navegacion.siguiente(),
    hoy: () => navegacion.hoy(),
    'cambiar-vista': (nodo) => {
      const vista = atributoDe(nodo, 'data-vista')
      if (vista === 'dia' || vista === 'semana') {
        navegacion.cambiarVista(vista)
      }
    },
    'ver-turno': (nodo) => {
      const id = idDe(nodo)
      if (!desmontado && confirmacionId === undefined && id !== undefined && id !== seleccionadoId && turnoDe(id) !== undefined) {
        descartarWhatsApp()
        const etiqueta = atributoDe(nodo, 'class')?.split(' ').includes('turno') === true ? 'article' : 'button'
        disparador = { id, etiqueta, accion: 'ver-turno' }
        seleccionadoId = id
        focoDetalle = 'cerrar'
        notificar(datos.rango)
      }
    },
    'cerrar-detalle': () => {
      descartarWhatsApp()
      retorno = disparador
      disparador = undefined
      seleccionadoId = undefined
      confirmacionId = undefined
      notificar(datos.rango)
    },
    'preparar-whatsapp': prepararWhatsApp,
    'actualizar-agenda-whatsapp': () => {
      if (whatsapp?.tipo === 'error' && whatsapp.falla === 'cambio') {
        seleccionadoId = undefined
        disparador = undefined
        void recargar(datos.rango)
      }
    },
    'cancelar-turno': (nodo) => {
      const id = idDe(nodo)
      if (id !== undefined && turnoDe(id) !== undefined) {
        descartarWhatsApp()
        confirmacionId = id
        cancelacionDesdeDetalle = atributoDe(nodo, 'data-origen-cancelacion') === 'detalle'
        notificar(datos.rango)
      }
    },
    'cerrar-confirmacion': () => {
      const id = confirmacionId
      confirmacionId = undefined
      if (id !== undefined) {
        if (cancelacionDesdeDetalle) focoDetalle = 'cancelar'
        else retorno = { id, etiqueta: 'button', accion: 'cancelar-turno' }
      }
      notificar(datos.rango)
    },
    'confirmar-cancelacion': async () => {
      if (confirmacionId === undefined) {
        return
      }
      const id = confirmacionId
      confirmacionId = undefined
      try {
        await cancelarTurno(opciones.cliente, id)
        aviso = undefined
        mensajeToast = 'Turno cancelado'
      } catch (error: unknown) {
        const resultado = interpretarCancelacion(error)
        if (resultado === 'sesion-vencida') {
          notificar(datos.rango)
          return
        }
        aviso = resultado === 'cancelado'
          ? undefined
          : resultado === 'ya-empezo'
            ? 'ya-empezo'
            : resultado === 'cambio'
              ? 'cambio'
              : 'error'
        mensajeToast = resultado === 'cancelado' ? 'Turno cancelado' : undefined
      }
      seleccionadoId = undefined
      await recargar(datos.rango)
    },
    'reintentar-agenda': () => {
      void recargar(datos.rango)
    },
  }

  return {
    descriptor,
    manejadores,
    desmontar: () => {
      desmontado = true
      generacion += 1
      descartarWhatsApp()
    },
    cancelarWhatsAppAlOcultar: (persistido) => {
      if (whatsapp === undefined) return
      descartarWhatsApp()
      // Si vuelve desde bfcache, la misma vista debe quedar lista para otro gesto.
      if (persistido) notificar(datos.rango)
    },
    cargar: async () => {
      const { vista, fecha } = navegacion.estado()
      await recargar(rangoDeVista(vista, fecha))
    },
  }
}
