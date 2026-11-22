import { esRutaInterna } from './shared/api-client'

export type NombreRuta = 'login' | 'agenda' | 'config' | 'reservar' | 'turno'

export type ResultadoRuta =
  | { tipo: 'ruta'; nombre: NombreRuta; parametros: Readonly<Record<string, string>> }
  | { tipo: 'no-encontrado'; ruta: string }

interface PatronRuta {
  nombre: NombreRuta
  segmentos: readonly string[]
}

const PATRONES: readonly PatronRuta[] = [
  { nombre: 'login', segmentos: ['login'] },
  { nombre: 'agenda', segmentos: ['agenda'] },
  { nombre: 'config', segmentos: ['config'] },
  { nombre: 'reservar', segmentos: ['reservar', '{slug}'] },
  { nombre: 'turno', segmentos: ['turno', '{token}'] },
]

function segmentosDe(ruta: string): string[] {
  const sinExtras = ruta.split(/[?#]/, 1)[0] ?? ''
  return sinExtras.split('/').filter((segmento) => segmento.length > 0)
}

function parametrosDe(patron: PatronRuta, segmentos: readonly string[]): Record<string, string> | undefined {
  if (patron.segmentos.length !== segmentos.length) {
    return undefined
  }

  const parametros: Record<string, string> = {}
  for (let indice = 0; indice < patron.segmentos.length; indice += 1) {
    const esperado = patron.segmentos[indice]
    const actual = segmentos[indice]
    if (esperado === undefined || actual === undefined) {
      return undefined
    }
    if (esperado.startsWith('{') && esperado.endsWith('}')) {
      parametros[esperado.slice(1, -1)] = actual
    } else if (esperado !== actual) {
      return undefined
    }
  }

  return parametros
}

/** Resuelve una ruta interna contra la tabla declarada, sin tocar el navegador. */
export function resolverRuta(ruta: string): ResultadoRuta {
  const segmentos = segmentosDe(ruta)

  for (const patron of PATRONES) {
    const parametros = parametrosDe(patron, segmentos)
    if (parametros !== undefined) {
      return { tipo: 'ruta', nombre: patron.nombre, parametros }
    }
  }

  return { tipo: 'no-encontrado', ruta }
}

export interface HistorialNavegacion {
  rutaActual(): string
  pushState(url: string): void
  replaceState(url: string): void
  suscribirPopstate(callback: () => void): () => void
}

export interface Router {
  rutaActual(): string
  ir(ruta: string): void
  reemplazar(ruta: string): void
  suscribir(callback: (vista: ResultadoRuta) => void): () => void
  iniciar(): () => void
}

export function crearRouter(historial: HistorialNavegacion): Router {
  const suscriptores = new Set<(vista: ResultadoRuta) => void>()

  function notificar(ruta: string): void {
    const vista = resolverRuta(ruta)
    for (const suscriptor of suscriptores) {
      suscriptor(vista)
    }
  }

  function verificar(ruta: string): void {
    if (!esRutaInterna(ruta)) {
      throw new Error('Las rutas de navegación deben ser relativas al origen')
    }
  }

  function notificarRutaActual(): void {
    notificar(historial.rutaActual())
  }

  return {
    rutaActual: () => historial.rutaActual(),

    ir(ruta) {
      verificar(ruta)
      historial.pushState(ruta)
      notificar(ruta)
    },

    reemplazar(ruta) {
      verificar(ruta)
      historial.replaceState(ruta)
      notificar(ruta)
    },

    suscribir(callback) {
      suscriptores.add(callback)

      return () => {
        suscriptores.delete(callback)
      }
    },

    iniciar() {
      const quitar = historial.suscribirPopstate(notificarRutaActual)
      notificarRutaActual()

      return quitar
    },
  }
}

