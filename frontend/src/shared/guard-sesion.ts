import { crearClienteApi, esRutaInterna } from './api-client'
import type { ClienteApi, FetchLike } from './api-client'
import { resolverRuta } from '../router'
import type { NombreRuta } from '../router'

export interface OdontologoDto {
  id: number
  nombre: string
  apellido: string
  email: string
  slug: string
}

export type EstadoSesion =
  | { tipo: 'validada'; odontologo: OdontologoDto }
  | { tipo: 'sin-sesion' }
  | { tipo: 'error-carga' }

export type DecisionGuard =
  | { tipo: 'mostrar'; ruta: string; odontologo?: OdontologoDto }
  | { tipo: 'redirigir'; ruta: string; para: string }
  | { tipo: 'error-carga'; ruta: string }

/** El guard se suscribe a la señal del api-client con esta función (inyectada en los tests). */
export type ObservadorSesionVencida = (callback: () => void) => () => void

export interface GuardSesion {
  decidir(ruta: string): Promise<DecisionGuard>
  descartarCache(): void
  fijarSesion(odontologo: OdontologoDto): void
  cerrarSesion(): Promise<void>
}

const NOMBRES_PUBLICOS: readonly NombreRuta[] = ['login', 'reservar', 'turno']

export function esRutaPublica(ruta: string): boolean {
  const resultado = resolverRuta(ruta)
  return resultado.tipo === 'ruta' && NOMBRES_PUBLICOS.includes(resultado.nombre)
}

function consultaDe(ruta: string): string {
  const indice = ruta.indexOf('?')
  return indice < 0 ? '' : ruta.slice(indice + 1)
}

/** Lee `redirect` de una URL de login y solo acepta rutas internas declaradas. */
export function destinoDesdeLogin(ruta: string): string {
  const destino = new URLSearchParams(consultaDe(ruta)).get('redirect')

  if (destino === null || !esRutaInterna(destino) || resolverRuta(destino).tipo !== 'ruta') {
    return '/agenda'
  }

  return destino
}

function necesitaVerificacion(estado: EstadoSesion): boolean {
  return estado.tipo === 'sin-sesion' || estado.tipo === 'error-carga'
}

export function decidirGuard(ruta: string, estado: EstadoSesion): DecisionGuard {
  if (esRutaPublica(ruta)) {
    return { tipo: 'mostrar', ruta }
  }
  if (estado.tipo === 'validada') {
    return { tipo: 'mostrar', ruta, odontologo: estado.odontologo }
  }
  if (estado.tipo === 'error-carga') {
    return { tipo: 'error-carga', ruta }
  }

  return { tipo: 'redirigir', ruta, para: `/login?redirect=${encodeURIComponent(ruta)}` }
}

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

export function esOdontologo(valor: unknown): valor is OdontologoDto {
  if (!esRegistro(valor)) {
    return false
  }

  return (
    typeof valor['id'] === 'number' &&
    typeof valor['nombre'] === 'string' &&
    typeof valor['apellido'] === 'string' &&
    typeof valor['email'] === 'string' &&
    typeof valor['slug'] === 'string'
  )
}

function esErrorNoAutenticado(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'codigo' in error && error.codigo === 'NO_AUTENTICADO'
}

/** Un 401 descarta la caché; cualquier otra falla deja un estado de error reintentable. */
export function estadoTrasFallo(error: unknown): EstadoSesion {
  return esErrorNoAutenticado(error) ? { tipo: 'sin-sesion' } : { tipo: 'error-carga' }
}

export function crearGuard(
  fetch: FetchLike,
  observador: ObservadorSesionVencida,
  clienteInyectado?: ClienteApi,
): GuardSesion {
  const cliente = clienteInyectado ?? crearClienteApi({ fetch })
  let estado: EstadoSesion = { tipo: 'sin-sesion' }

  function descartarCache(): void {
    estado = { tipo: 'sin-sesion' }
  }

  async function consultarSesion(): Promise<void> {
    try {
      const datos: unknown = await cliente.pedir<OdontologoDto>('GET', '/api/auth/me')
      estado = esOdontologo(datos) ? { tipo: 'validada', odontologo: datos } : { tipo: 'sin-sesion' }
    } catch (error: unknown) {
      estado = estadoTrasFallo(error)
    }
  }

  observador(descartarCache)

  return {
    async decidir(ruta) {
      if (!esRutaPublica(ruta) && necesitaVerificacion(estado)) {
        await consultarSesion()
      }

      return decidirGuard(ruta, estado)
    },

    descartarCache,

    fijarSesion(odontologo) {
      estado = { tipo: 'validada', odontologo }
    },

    async cerrarSesion() {
      await cliente.pedir('POST', '/api/auth/logout')
      descartarCache()
    },
  }
}
