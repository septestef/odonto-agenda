import { crearClienteApi } from './api-client'
import type { FetchLike } from './api-client'
import { resolverRuta } from '../router'
import type { NombreRuta } from '../router'

export interface OdontologoDto {
  id: number
  nombre: string
  apellido: string
  email: string
  slug: string
}

export type EstadoSesion = { tipo: 'validada'; odontologo: OdontologoDto } | { tipo: 'sin-sesion' }

export type DecisionGuard =
  | { tipo: 'mostrar'; ruta: string; odontologo?: OdontologoDto }
  | { tipo: 'redirigir'; ruta: string; para: string }

/** El guard se suscribe a la señal del api-client con esta función (inyectada en los tests). */
export type ObservadorSesionVencida = (callback: () => void) => () => void

export interface GuardSesion {
  decidir(ruta: string): Promise<DecisionGuard>
  descartarCache(): void
  cerrarSesion(): Promise<void>
}

const NOMBRES_PUBLICOS: readonly NombreRuta[] = ['login', 'reservar', 'turno']

export function esRutaPublica(ruta: string): boolean {
  const resultado = resolverRuta(ruta)
  return resultado.tipo === 'ruta' && NOMBRES_PUBLICOS.includes(resultado.nombre)
}

export function decidirGuard(ruta: string, estado: EstadoSesion): DecisionGuard {
  if (esRutaPublica(ruta)) {
    return { tipo: 'mostrar', ruta }
  }
  if (estado.tipo === 'validada') {
    return { tipo: 'mostrar', ruta, odontologo: estado.odontologo }
  }

  return { tipo: 'redirigir', ruta, para: `/login?redirect=${encodeURIComponent(ruta)}` }
}

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function esOdontologo(valor: unknown): valor is OdontologoDto {
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

export function crearGuard(fetch: FetchLike, observador: ObservadorSesionVencida): GuardSesion {
  const cliente = crearClienteApi({ fetch })
  let estado: EstadoSesion = { tipo: 'sin-sesion' }

  function descartarCache(): void {
    estado = { tipo: 'sin-sesion' }
  }

  async function consultarSesion(): Promise<void> {
    try {
      const datos: unknown = await cliente.pedir<OdontologoDto>('GET', '/api/auth/me')
      estado = esOdontologo(datos) ? { tipo: 'validada', odontologo: datos } : { tipo: 'sin-sesion' }
    } catch (error: unknown) {
      if (!esErrorNoAutenticado(error)) {
        throw error
      }
      descartarCache()
    }
  }

  observador(descartarCache)

  return {
    async decidir(ruta) {
      if (!esRutaPublica(ruta) && estado.tipo === 'sin-sesion') {
        await consultarSesion()
      }

      return decidirGuard(ruta, estado)
    },

    descartarCache,

    async cerrarSesion() {
      await cliente.pedir('POST', '/api/auth/logout')
      descartarCache()
    },
  }
}
