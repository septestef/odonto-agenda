import type { Bloqueo, Franja, Horario, Prestacion, TurnoAfectado } from './modelos'

function esRegistro(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function texto(valor: unknown): string | undefined {
  return typeof valor === 'string' ? valor : undefined
}

function entero(valor: unknown): number | undefined {
  return typeof valor === 'number' && Number.isInteger(valor) ? valor : undefined
}

function booleano(valor: unknown): boolean | undefined {
  return typeof valor === 'boolean' ? valor : undefined
}

function fallar(ruta: string): never {
  throw new Error(`La respuesta de ${ruta} no tiene el formato esperado.`)
}

export function leerFranja(valor: unknown): Franja {
  if (!esRegistro(valor)) {
    return fallar('/api/horarios')
  }
  const diaSemana = entero(valor['diaSemana'])
  const inicio = texto(valor['inicio'])
  const fin = texto(valor['fin'])
  if (diaSemana === undefined || inicio === undefined || fin === undefined) {
    return fallar('/api/horarios')
  }
  return { diaSemana, inicio, fin }
}

export function leerHorario(datos: unknown): Horario {
  if (!esRegistro(datos) || !Array.isArray(datos['franjas'])) {
    return fallar('/api/horarios')
  }
  return { franjas: datos['franjas'].map(leerFranja) }
}

export function leerPrestacion(valor: unknown): Prestacion {
  if (!esRegistro(valor)) {
    return fallar('/api/prestaciones')
  }
  const id = entero(valor['id'])
  const nombre = texto(valor['nombre'])
  const duracionMinutos = entero(valor['duracionMinutos'])
  const activa = booleano(valor['activa'])
  if (id === undefined || nombre === undefined || duracionMinutos === undefined || activa === undefined) {
    return fallar('/api/prestaciones')
  }
  return { id, nombre, duracionMinutos, activa }
}

export function leerPrestaciones(datos: unknown): readonly Prestacion[] {
  if (!esRegistro(datos) || !Array.isArray(datos['prestaciones'])) {
    return fallar('/api/prestaciones')
  }
  return datos['prestaciones'].map(leerPrestacion)
}

export function leerTurnoAfectado(valor: unknown): TurnoAfectado {
  if (!esRegistro(valor)) {
    return fallar('/api/bloqueos')
  }
  const id = entero(valor['id'])
  const inicio = texto(valor['inicio'])
  const fin = texto(valor['fin'])
  const estado = texto(valor['estado'])
  const pacienteNombre = texto(valor['pacienteNombre'])
  const pacienteApellido = texto(valor['pacienteApellido'])
  const prestacionNombre = texto(valor['prestacionNombre'])
  if (
    id === undefined ||
    inicio === undefined ||
    fin === undefined ||
    estado === undefined ||
    pacienteNombre === undefined ||
    pacienteApellido === undefined ||
    prestacionNombre === undefined
  ) {
    return fallar('/api/bloqueos')
  }
  return { id, inicio, fin, estado, pacienteNombre, pacienteApellido, prestacionNombre }
}

export function leerBloqueo(valor: unknown): Bloqueo {
  if (!esRegistro(valor)) {
    return fallar('/api/bloqueos')
  }
  const id = entero(valor['id'])
  const inicio = texto(valor['inicio'])
  const fin = texto(valor['fin'])
  const motivo = texto(valor['motivo'])
  const crudos = valor['turnosAfectados']
  if (id === undefined || inicio === undefined || fin === undefined || !Array.isArray(crudos)) {
    return fallar('/api/bloqueos')
  }
  return {
    id,
    inicio,
    fin,
    ...(motivo === undefined ? {} : { motivo }),
    turnosAfectados: crudos.map(leerTurnoAfectado),
  }
}

export function leerBloqueos(datos: unknown): readonly Bloqueo[] {
  if (!esRegistro(datos) || !Array.isArray(datos['bloqueos'])) {
    return fallar('/api/bloqueos')
  }
  return datos['bloqueos'].map(leerBloqueo)
}

export interface DetalleCampo {
  readonly campo: string
  readonly mensaje: string
}

/** Centraliza la búsqueda de `campo`/`mensaje` en el `detalles` del backend. */
export function campoEnDetalles(detalles: unknown): readonly DetalleCampo[] {
  const posibles: readonly unknown[] = Array.isArray(detalles)
    ? detalles
    : esRegistro(detalles)
      ? [detalles]
      : []
  const encontrados: DetalleCampo[] = []

  for (const posible of posibles) {
    if (!esRegistro(posible)) {
      continue
    }
    const campo = texto(posible['campo'])
    const mensaje = texto(posible['mensaje'])
    if (campo !== undefined && campo !== '' && mensaje !== undefined) {
      encontrados.push({ campo, mensaje })
    }
  }

  return encontrados
}
