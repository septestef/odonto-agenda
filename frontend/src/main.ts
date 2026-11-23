import './estilos/app.css'
import { crearRouter, resolverRuta } from './router'
import type { HistorialNavegacion, ResultadoRuta } from './router'
import { crearClienteApi } from './shared/api-client'
import type { FetchLike, LectorCookie } from './shared/api-client'
import { crearGuard } from './shared/guard-sesion'
import type { AdaptadorNodos, ManejadoresUI, NodoUI } from './shared/ui/adaptador'
import { alerta, boton } from './shared/ui/componentes'
import type { Descriptor } from './shared/ui/componentes'
import { layout } from './shared/ui/layout'
import { crearApiConfig } from './features/config/api-config'
import { crearConfig } from './features/config/config'
import type { ControlConfig } from './features/config/config'
import { crearControlLogin } from './features/auth/login'
import { crearControlAgenda } from './features/agenda/control'
import type { ControlAgenda } from './features/agenda/control'
import type { OdontologoDto } from './shared/guard-sesion'
import { crearApiPublica } from './features/reservar/api-publica'
import { crearControlReserva } from './features/reservar/pantalla-reserva'
import { crearApiGestion } from './features/turno/api-gestion'
import { crearControlTurno } from './features/turno/pantalla-turno'
import { vistaMarcador } from './vistas/marcador'

export interface OpcionesArranque {
  historial: HistorialNavegacion
  fetch: FetchLike
  leerCookie?: LectorCookie
  suscribirPagehide?: (callback: (persistido: boolean) => void) => () => void
}

export type DetenerArranque = () => void

function descriptorError(): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'error-carga',
    hijos: [
      alerta({ texto: 'No pudimos cargar tu sesión.' }),
      boton({ texto: 'Reintentar', accion: 'reintentar', variante: 'secundaria', tipo: 'button' }),
    ],
  }
}

interface ControlVista {
  descriptor: Descriptor
  manejadores: ManejadoresUI
  alMontar?: () => Promise<void>
  iniciar?: () => void
  desmontar?: () => void
  cancelarWhatsAppAlOcultar?: (persistido: boolean) => void
}

interface NodoConsultable extends NodoUI {
  querySelector(selector: string): { value: string } | null
  getAttribute(nombre: string): string | null
}

/** En el navegador se usa el ancho real; en los tests de descriptores hay un default. */
function anchoDelNavegador(): number {
  const global: { innerWidth?: unknown } = globalThis as { innerWidth?: unknown }
  return typeof global.innerWidth === 'number' ? global.innerWidth : 1024
}

function esConsultable(nodo: NodoUI): nodo is NodoConsultable {
  const candidato: Record<string, unknown> = nodo as unknown as Record<string, unknown>
  return typeof candidato['querySelector'] === 'function' && typeof candidato['getAttribute'] === 'function'
}

function valorCampo(nodo: NodoUI, nombre: string): string {
  if (!esConsultable(nodo)) {
    return ''
  }
  return nodo.querySelector(`[data-campo="${nombre}"]`)?.value ?? ''
}

/** El manejador de `ingresar` recibe el form en `submit` y el botón en `click`. */
function formularioDe(nodo: NodoUI): NodoUI {
  const conCercania = nodo as { closest?: (selector: string) => NodoUI | null }
  return conCercania.closest?.('form') ?? nodo
}

function hrefDe(nodo: NodoUI): string | undefined {
  if (!esConsultable(nodo)) {
    return undefined
  }
  return nodo.getAttribute('href') ?? undefined
}

export function arrancar(
  destino: unknown,
  adaptador: AdaptadorNodos,
  opciones: OpcionesArranque,
): DetenerArranque {
  const router = crearRouter(opciones.historial)
  const cliente = crearClienteApi({ fetch: opciones.fetch, leerCookie: opciones.leerCookie })
  const guard = crearGuard(opciones.fetch, cliente.suscribirSesionVencida, cliente)
  let cola: Promise<void> = Promise.resolve()
  let controlConfig: ControlConfig | undefined
  let desmontarVista: (() => void) | undefined
  let cancelarWhatsAppAlOcultar: ControlVista['cancelarWhatsAppAlOcultar']
  let detenido = false

  function crearLogin(ruta: string): ControlVista {
    const manejadores: ManejadoresUI = {
      async ingresar(nodo: NodoUI) {
        const formulario = formularioDe(nodo)
        await control.enviar(valorCampo(formulario, 'email'), valorCampo(formulario, 'password'))
      },
    }

    const control = crearControlLogin({
      cliente,
      guard,
      ruta,
      navegar: router.ir,
      alIngresar: () => {},
      // Diseño elegido: suscripción a cambios de estado del control. En cada
      // transición volvemos a montar el descriptor con los mismos manejadores;
      // eso incluye "Ingresando…" antes del await y el fin del bloqueo.
      alCambiar: () => {
        if (router.rutaActual() !== ruta) {
          return
        }
        adaptador.montar(destino, control.descriptor(), { ...manejadores, ...manejadoresComunes() })
      },
    })

    return { descriptor: control.descriptor(), manejadores }
  }

  function crearAgenda(ruta: string, odontologo: OdontologoDto): ControlVista {
    const referencia: { actual?: ControlAgenda } = {}

    const montar = (): void => {
      const control = referencia.actual
      if (control === undefined || router.rutaActual() !== ruta) {
        return
      }
      adaptador.montar(destino, layout({ odontologo, ruta, contenido: control.descriptor() }), {
        ...control.manejadores,
        ...manejadoresComunes(),
      })
    }

    const instancia = crearControlAgenda({
      cliente,
      ancho: anchoDelNavegador(),
      ahora: () => new Date(),
      alCambiar: montar,
      nombreOdontologo: `${odontologo.nombre} ${odontologo.apellido}`.trim(),
    })
    referencia.actual = instancia

    return {
      descriptor: layout({ odontologo, ruta, contenido: instancia.descriptor() }),
      manejadores: { ...instancia.manejadores, ...manejadoresComunes() },
      alMontar: () => instancia.cargar(),
      desmontar: instancia.desmontar,
      cancelarWhatsAppAlOcultar: instancia.cancelarWhatsAppAlOcultar,
    }
  }

  function manejadoresComunes(): ManejadoresUI {
    return {
      async 'cerrar-sesion'() {
        await guard.cerrarSesion()
        router.ir('/login')
      },
      navegar(nodo: NodoUI) {
        const href = hrefDe(nodo)
        if (href !== undefined) {
          router.ir(href)
        }
      },
      reintentar() {
        guard.descartarCache()
        programarRenderizado()
      },
    }
  }

  async function resolverVista(vista: ResultadoRuta, ruta: string): Promise<ControlVista | undefined> {
    const decision = await guard.decidir(ruta)

    if (decision.tipo === 'redirigir') {
      router.reemplazar(decision.para)
      return undefined
    }
    if (decision.tipo === 'error-carga') {
      return { descriptor: descriptorError(), manejadores: {} }
    }
    if (vista.tipo === 'ruta' && vista.nombre === 'login') {
      return crearLogin(ruta)
    }
    if (vista.tipo === 'ruta' && vista.nombre === 'reservar') {
      const slug = vista.parametros['slug']
      if (slug === undefined || slug === '') {
        return { descriptor: vistaMarcador(vista), manejadores: {} }
      }

      const api = crearApiPublica({ fetch: opciones.fetch })
      const control = crearControlReserva({
        slug,
        api,
        alCambiar: () => {
          if (router.rutaActual() !== ruta) {
            return
          }
          adaptador.montar(destino, control.descriptor(), {
            ...control.manejadores(),
            ...manejadoresComunes(),
          })
        },
      })

      return {
        descriptor: control.descriptor(),
        manejadores: control.manejadores(),
        iniciar: () => {
          void control.iniciar()
        },
      }
    }
    if (vista.tipo === 'ruta' && vista.nombre === 'turno') {
      // Ruta pública: el token solo viaja a /api/public/turnos/ y no se guarda.
      const control = crearControlTurno({
        token: vista.parametros['token'] ?? '',
        api: crearApiGestion({ fetch: opciones.fetch }),
        apiPublica: crearApiPublica({ fetch: opciones.fetch }),
        alCambiar: () => {
          if (router.rutaActual() !== ruta) {
            return
          }
          adaptador.montar(destino, control.descriptor(), {
            ...control.manejadores(),
            ...manejadoresComunes(),
          })
        },
      })

      return {
        descriptor: control.descriptor(),
        manejadores: control.manejadores(),
        iniciar: () => {
          void control.iniciar()
        },
      }
    }
    if (vista.tipo === 'ruta' && vista.nombre === 'agenda' && decision.odontologo !== undefined) {
      return crearAgenda(ruta, decision.odontologo)
    }
    if (decision.odontologo === undefined) {
      return undefined
    }

    if (vista.tipo === 'ruta' && vista.nombre === 'config') {
      if (controlConfig === undefined) {
        controlConfig = crearConfig({
          api: crearApiConfig(cliente),
          alCambiar: () => {
            if (router.rutaActual() === '/config') {
              programarRenderizado()
            }
          },
        })
        void controlConfig.cargar()
      }
      return {
        descriptor: layout({
          odontologo: decision.odontologo,
          ruta,
          contenido: controlConfig.descriptor(),
        }),
        manejadores: controlConfig.manejadores(),
      }
    }

    return {
      descriptor: layout({
        odontologo: decision.odontologo,
        ruta,
        contenido: vistaMarcador(vista),
      }),
      manejadores: {},
    }
  }

  function programarRenderizado(): void {
    const ruta = router.rutaActual()
    desmontarVista?.()
    desmontarVista = undefined
    cancelarWhatsAppAlOcultar = undefined
    cola = cola
      .then(async () => {
        if (detenido || router.rutaActual() !== ruta) {
          return
        }
        if (ruta !== '/config') {
          controlConfig = undefined
        }
        const vista = resolverRuta(ruta)
        const control = await resolverVista(vista, ruta)
        if (detenido || router.rutaActual() !== ruta || control === undefined) {
          control?.desmontar?.()
          return
        }
        desmontarVista?.()
        desmontarVista = control.desmontar
        cancelarWhatsAppAlOcultar = control.cancelarWhatsAppAlOcultar
        adaptador.montar(destino, control.descriptor, { ...control.manejadores, ...manejadoresComunes() })
        control.iniciar?.()
        if (control.alMontar !== undefined) {
          await control.alMontar()
        }
      })
      // El guard ya traduce las fallas de red a un estado visible; evitamos
      // que una promesa huérfana interrumpa la cadena de renderizados.
      .catch(() => {})
  }

  const quitarSuscripcion = router.suscribir(programarRenderizado)
  const quitarPopstate = router.iniciar()
  const quitarPagehide = opciones.suscribirPagehide?.((persistido) => cancelarWhatsAppAlOcultar?.(persistido))

  return () => {
    detenido = true
    desmontarVista?.()
    desmontarVista = undefined
    cancelarWhatsAppAlOcultar = undefined
    quitarSuscripcion()
    quitarPopstate()
    quitarPagehide?.()
  }
}
