import { destinoDesdeLogin, esOdontologo } from '../../shared/guard-sesion'
import type { GuardSesion, OdontologoDto } from '../../shared/guard-sesion'
import type { ClienteApi } from '../../shared/api-client'
import { alerta, boton, campo } from '../../shared/ui/componentes'
import type { Descriptor } from '../../shared/ui/componentes'

export type EstadoLogin =
  | { tipo: 'limpio' }
  | { tipo: 'enviando' }
  | { tipo: 'error'; mensaje: string }
  | { tipo: 'bloqueado'; mensaje: string; hasta: number | null }

export interface OpcionesLogin {
  cliente: ClienteApi
  ruta?: string
  guard?: GuardSesion
  navegar?: (ruta: string) => void
  alIngresar: (destino: string, odontologo: OdontologoDto) => void
  /** Avisa a la vista que el estado cambió para que vuelva a montar el descriptor. */
  alCambiar?: () => void
  reloj?: () => number
}

export interface ControlLogin {
  estado(): EstadoLogin
  descriptor(ahora?: number): Descriptor
  enviar(email: string, contraseña: string): Promise<void>
  cerrarSesion(): Promise<void>
}

const MENSAJE_CREDENCIALES = 'El email o la contraseña no son correctos.'
const MENSAJE_FALLBACK = 'No pudimos iniciar sesión. Probá de nuevo.'

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function codigoDe(error: unknown): string | undefined {
  if (!esRegistro(error) || typeof error['codigo'] !== 'string') {
    return undefined
  }
  return error['codigo']
}

function mensajeDe(error: unknown): string | undefined {
  if (!esRegistro(error) || typeof error['mensaje'] !== 'string') {
    return undefined
  }
  return error['mensaje']
}

export function minutosDeBloqueo(segundos: unknown): number | undefined {
  if (typeof segundos !== 'number' || !Number.isFinite(segundos) || segundos <= 0) {
    return undefined
  }
  return Math.ceil(segundos / 60)
}

function estadoDesdeError(error: unknown, ahora: number): EstadoLogin {
  const codigo = codigoDe(error)
  if (codigo === 'RN-AU-01') {
    return { tipo: 'error', mensaje: MENSAJE_CREDENCIALES }
  }
  if (codigo === 'RN-AU-03') {
    const detalles = esRegistro(error) && esRegistro(error['detalles']) ? error['detalles'] : undefined
    const valor = detalles?.['reintentarEnSegundos']
    const segundos = typeof valor === 'number' && Number.isFinite(valor) && valor > 0 ? valor : undefined
    const minutos = minutosDeBloqueo(segundos)
    const mensaje =
      minutos === undefined
        ? 'Demasiados intentos. Probá de nuevo más tarde.'
        : `Demasiados intentos. Probá de nuevo en ${minutos} minutos.`
    const hasta = segundos === undefined ? null : ahora + segundos * 1000

    return { tipo: 'bloqueado', mensaje, hasta }
  }
  return { tipo: 'error', mensaje: mensajeDe(error) ?? MENSAJE_FALLBACK }
}

export function estaBloqueado(hasta: number | null, ahora: number): boolean {
  return hasta === null || ahora < hasta
}

const DETALLE_CREDENCIALES = 'Revisá los datos y probá de nuevo.'
const DETALLE_BLOQUEO = 'El acceso se habilita solo; no hace falta que hagas nada.'

function detallePara(mensaje: string | undefined, estado: EstadoLogin): string | undefined {
  if (mensaje === undefined) {
    return undefined
  }
  if (mensaje === MENSAJE_CREDENCIALES) {
    return DETALLE_CREDENCIALES
  }
  return estado.tipo === 'bloqueado' ? DETALLE_BLOQUEO : undefined
}

interface MensajesVisibles {
  enviando: boolean
  bloqueado: boolean
  errorEmail?: string
  errorPassword?: string
  avisoGeneral?: string
  detalleAviso?: string
}

function mensajesVisibles(estado: EstadoLogin, ahora: number): MensajesVisibles {
  const enviando = estado.tipo === 'enviando'
  const bloqueado = estado.tipo === 'bloqueado' && estaBloqueado(estado.hasta, ahora)
  const mensaje =
    estado.tipo === 'error' || (estado.tipo === 'bloqueado' && bloqueado) ? estado.mensaje : undefined
  const errorEmail = mensaje === 'Ingresá tu email.' ? mensaje : undefined
  const errorPassword = mensaje === 'Ingresá tu contraseña.' ? mensaje : undefined
  const avisoGeneral = mensaje !== undefined && errorEmail === undefined && errorPassword === undefined ? mensaje : undefined
  const detalleAviso = detallePara(avisoGeneral, estado)

  return { enviando, bloqueado, errorEmail, errorPassword, avisoGeneral, detalleAviso }
}

function registrarIngreso(opciones: OpcionesLogin, ruta: string, odontologo: OdontologoDto): void {
  const destino = destinoDesdeLogin(ruta)
  opciones.guard?.fijarSesion(odontologo)
  opciones.navegar?.(destino)
  opciones.alIngresar(destino, odontologo)
}

async function cerrarSesionRemota(opciones: OpcionesLogin): Promise<void> {
  if (opciones.guard === undefined) {
    await opciones.cliente.pedir('POST', '/api/auth/logout')
    return
  }
  await opciones.guard.cerrarSesion()
}

function programarFinDeBloqueo(
  estado: EstadoLogin,
  reloj: () => number,
  alVencer: () => void,
): ReturnType<typeof setTimeout> | undefined {
  if (estado.tipo !== 'bloqueado' || estado.hasta === null) {
    return undefined
  }

  return setTimeout(alVencer, Math.max(0, estado.hasta - reloj()))
}

export function crearControlLogin(opciones: OpcionesLogin): ControlLogin {
  const ruta = opciones.ruta ?? '/login'
  const reloj = opciones.reloj ?? (() => Date.now())
  let estado: EstadoLogin = { tipo: 'limpio' }
  let temporizador: ReturnType<typeof setTimeout> | undefined

  // Diseño elegido: una sola función de mutación notifica cada transición.
  // Así la vista vuelve a montar incluso el estado "enviando", que ocurre
  // antes de que resuelva el pedido, y también el fin del bloqueo por timer.
  function cambiar(nuevo: EstadoLogin): void {
    if (temporizador !== undefined) {
      clearTimeout(temporizador)
      temporizador = undefined
    }
    estado = nuevo
    temporizador = programarFinDeBloqueo(nuevo, reloj, () => {
      temporizador = undefined
      opciones.alCambiar?.()
    })
    opciones.alCambiar?.()
  }

  return {
    estado: () => estado,

    descriptor(ahora = reloj()) {
      const { enviando, bloqueado, errorEmail, errorPassword, avisoGeneral, detalleAviso } = mensajesVisibles(
        estado,
        ahora,
      )

      return {
        etiqueta: 'div',
        clase: 'login',
        hijos: [
          { etiqueta: 'h1', texto: 'Ingresá a tu agenda' },
          { etiqueta: 'p', texto: 'Usá el email y la contraseña de tu cuenta.' },
          {
            etiqueta: 'form',
            accion: 'ingresar',
            atributos: { 'aria-busy': enviando ? 'true' : 'false' },
            hijos: [
              ...(avisoGeneral === undefined
                ? []
                : [alerta({ texto: avisoGeneral, detalle: detalleAviso, tipo: 'error' })]),
              campo({
                etiqueta: 'Email',
                nombre: 'email',
                tipo: 'email',
                autocompletado: 'username',
                deshabilitado: enviando,
                error: errorEmail,
              }),
              campo({
                etiqueta: 'Contraseña',
                nombre: 'password',
                tipo: 'password',
                autocompletado: 'current-password',
                deshabilitado: enviando,
                error: errorPassword,
              }),
              boton({
                texto: 'Ingresar',
                textoEnviando: 'Ingresando…',
                enviando,
                deshabilitado: bloqueado,
                accion: 'ingresar',
              }),
            ],
          },
        ],
      }
    },

    async enviar(email, contraseña) {
      if (estado.tipo === 'enviando') {
        return
      }
      if (estado.tipo === 'bloqueado' && estaBloqueado(estado.hasta, reloj())) {
        return
      }
      if (email.trim() === '') {
        cambiar({ tipo: 'error', mensaje: 'Ingresá tu email.' })
        return
      }
      if (contraseña === '') {
        cambiar({ tipo: 'error', mensaje: 'Ingresá tu contraseña.' })
        return
      }

      cambiar({ tipo: 'enviando' })
      try {
        const datos = await opciones.cliente.pedir<OdontologoDto>('POST', '/api/auth/login', {
          email,
          password: contraseña,
        })
        cambiar({ tipo: 'limpio' })
        if (esOdontologo(datos)) {
          registrarIngreso(opciones, ruta, datos)
        }
      } catch (error: unknown) {
        cambiar(estadoDesdeError(error, reloj()))
      }
    },

    async cerrarSesion() {
      await cerrarSesionRemota(opciones)
      cambiar({ tipo: 'limpio' })
      opciones.navegar?.('/login')
    },
  }
}
