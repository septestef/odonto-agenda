import { interpretarErrorApi } from './error-api'
import type { ErrorApi } from './error-api'

export type ErrorApiCliente = Error & ErrorApi

const SIN_CONEXION: ErrorApi = {
  codigo: 'SIN_CONEXION',
  mensaje: 'Revisá tu conexión e intentá de nuevo.',
}

export type MetodoHttp = 'GET' | 'HEAD' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type FetchLike = (url: string, init?: RequestInit) => Promise<Response>

export type LectorCookie = (nombre: string) => string | undefined

/** Lee una cookie de una cadena `document.cookie` sin depender del DOM. */
export function leerCookieDeCadena(cadena: string, nombre: string): string | undefined {
  for (const parte of cadena.split(';')) {
    const cookie = parte.trim()
    const separador = cookie.indexOf('=')
    if (separador < 0 || cookie.slice(0, separador) !== nombre) {
      continue
    }
    const valor = cookie.slice(separador + 1)
    return valor === '' ? undefined : valor
  }

  return undefined
}

export interface OpcionesClienteApi {
  fetch?: FetchLike
  leerCookie?: LectorCookie
  notificarSesionVencida?: () => void
}

export interface ClienteApi {
  pedir<T>(metodo: MetodoHttp, ruta: string, cuerpo?: unknown): Promise<T | undefined>
  suscribirSesionVencida(callback: () => void): () => void
}

const METODOS_ESCRITURA: ReadonlySet<string> = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const RUTAS_SIN_CSRF: ReadonlySet<string> = new Set(['/api/auth/login'])

/** Indica si una ruta es interna al origen; la usan el cliente y la validación de `redirect`. */
export function esRutaInterna(ruta: string): boolean {
  return ruta.startsWith('/') && !ruta.startsWith('//')
}

function debeLlevarCsrf(metodo: MetodoHttp, ruta: string): boolean {
  return METODOS_ESCRITURA.has(metodo) && !RUTAS_SIN_CSRF.has(ruta)
}

function esContenedor(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function esDetalleVacio(detalles: unknown): boolean {
  return (
    detalles === null ||
    (Array.isArray(detalles) && detalles.length === 0) ||
    (esContenedor(detalles) && Object.keys(detalles).length === 0)
  )
}

function normalizarDetalles(error: ErrorApi): ErrorApi {
  if (!('detalles' in error) || !esDetalleVacio(error.detalles)) {
    return error
  }

  return { codigo: error.codigo, mensaje: error.mensaje }
}

function lanzarErrorApi(cuerpo: unknown): never {
  const interpretado = normalizarDetalles(interpretarErrorApi(cuerpo))

  throw Object.assign(new Error(interpretado.mensaje), interpretado)
}

function esSesionVencida(respuesta: Response, contenido: unknown): boolean {
  return respuesta.status === 401 && esContenedor(contenido) && contenido['codigo'] === 'NO_AUTENTICADO'
}

function esRespuesta(valor: unknown): valor is Response {
  return (
    typeof valor === 'object' &&
    valor !== null &&
    'ok' in valor &&
    typeof valor.ok === 'boolean' &&
    'status' in valor &&
    typeof valor.status === 'number'
  )
}

async function ejecutarFetch(fetchReal: FetchLike, ruta: string, init: RequestInit): Promise<Response> {
  let respuesta: unknown
  try {
    respuesta = await fetchReal(ruta, init)
  } catch {
    lanzarErrorApi(SIN_CONEXION)
  }

  if (!esRespuesta(respuesta)) {
    lanzarErrorApi(SIN_CONEXION)
  }

  return respuesta
}

function armarCabeceras(metodo: MetodoHttp, ruta: string, cuerpo: unknown, leerCookie: LectorCookie): Headers {
  const cabeceras = new Headers()
  if (debeLlevarCsrf(metodo, ruta)) {
    const token = leerCookie('XSRF-TOKEN')
    if (token !== undefined) {
      cabeceras.set('X-XSRF-TOKEN', token)
    }
  }
  if (cuerpo !== undefined) {
    cabeceras.set('Content-Type', 'application/json')
  }

  return cabeceras
}

export function crearClienteApi(opciones: OpcionesClienteApi = {}): ClienteApi {
  const fetchReal: FetchLike = opciones.fetch ?? ((url, init) => globalThis.fetch(url, init))
  const leerCookie = opciones.leerCookie ?? (() => undefined)
  const oyentes = new Set<() => void>()

  if (opciones.notificarSesionVencida !== undefined) {
    oyentes.add(opciones.notificarSesionVencida)
  }

  function publicarSesionVencida(): void {
    for (const oyente of oyentes) {
      oyente()
    }
  }

  return {
    async pedir<T>(metodo: MetodoHttp, ruta: string, cuerpo?: unknown): Promise<T | undefined> {
      if (!esRutaInterna(ruta)) {
        throw new Error('Las rutas de la API deben ser relativas al origen')
      }

      const respuesta = await ejecutarFetch(fetchReal, ruta, {
        method: metodo,
        headers: armarCabeceras(metodo, ruta, cuerpo, leerCookie),
        credentials: 'same-origin',
        ...(cuerpo === undefined ? {} : { body: JSON.stringify(cuerpo) }),
      })

      if (respuesta.status === 204) {
        return undefined
      }

      const contenido: unknown = await respuesta.json().catch(() => undefined)
      if (!respuesta.ok) {
        if (esSesionVencida(respuesta, contenido)) {
          publicarSesionVencida()
        }

        lanzarErrorApi(contenido)
      }

      return contenido as T
    },

    suscribirSesionVencida(callback: () => void): () => void {
      oyentes.add(callback)

      return () => {
        oyentes.delete(callback)
      }
    },
  }
}
