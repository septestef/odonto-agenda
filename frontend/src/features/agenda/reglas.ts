import type { TurnoAgenda } from './tipos'

/** Única fuente de la decisión visual de cancelación; el servidor sigue siendo autoridad. */
export function puedeCancelar(turno: Pick<TurnoAgenda, 'estado' | 'inicio'>, ahora: Date): boolean {
  return turno.estado === 'RESERVADO' && Date.parse(turno.inicio) > ahora.getTime()
}
