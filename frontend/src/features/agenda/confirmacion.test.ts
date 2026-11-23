import { describe, expect, it } from 'vitest'
import type { Descriptor } from '../../shared/ui/componentes'
import { descriptorConfirmacion } from './confirmacion'
import { puedeCancelar } from './reglas'
import type { TurnoAgenda } from './tipos'

function turno(): TurnoAgenda {
  return {
    id: 3,
    inicio: '2026-11-25T10:30:00-03:00',
    fin: '2026-11-25T11:00:00-03:00',
    estado: 'RESERVADO',
    origen: 'ONLINE',
    paciente: { nombre: 'Ana', apellido: 'Pérez' },
    prestacion: { nombre: 'Consulta', duracionMinutos: 30 },
    canceladoPor: null,
    canceladoEn: null,
  }
}

function textos(descriptor: Descriptor): string[] {
  return [
    ...(descriptor.texto === undefined ? [] : [descriptor.texto]),
    ...(descriptor.hijos ?? []).flatMap(textos),
  ]
}

function deClase(descriptor: Descriptor, clase: string): Descriptor[] {
  const propios = (descriptor.clase ?? '').split(' ').includes(clase) ? [descriptor] : []
  return [...propios, ...(descriptor.hijos ?? []).flatMap((hijo) => deClase(hijo, clase))]
}

describe('diálogo de confirmación para cancelar', () => {
  const ahora = new Date('2026-11-25T09:00:00-03:00')

  it('muestra paciente, fecha local, prestación y origen con foco en “No, volver”', () => {
    const vista = descriptorConfirmacion({ turno: turno(), ahora, enBloqueo: false })
    const todo = textos(vista).join(' ')

    expect(vista.atributos?.['role']).toBe('alertdialog')
    expect(vista.atributos?.['aria-modal']).toBe('true')
    expect(vista.atributos?.['aria-labelledby']).toBe('c-titulo')
    expect(vista.atributos?.['aria-describedby']).toBe('c-texto')
    expect(vista.atributos?.['data-cerrar-escape']).toBe('cerrar-confirmacion')
    expect(todo).toContain('¿Cancelás el turno de Ana Pérez?')
    expect(todo).toContain('Miércoles 25 de noviembre, 10:30')
    expect(todo).toContain('Consulta · reservado online')

    const volver = deClase(vista, 'confirmacion__volver')[0]
    expect(volver?.texto).toBe('No, volver')
    expect(volver?.atributos?.['autofocus']).toBe('')
    expect(volver?.accion).toBe('cerrar-confirmacion')
    expect(deClase(vista, 'confirmacion__confirmar')[0]?.accion).toBe('confirmar-cancelacion')
  })

  it('advierte que el horario vuelve libre solo cuando no hay bloqueo', () => {
    const libre = textos(descriptorConfirmacion({ turno: turno(), ahora, enBloqueo: false })).join(' ')
    const bloqueado = textos(descriptorConfirmacion({ turno: turno(), ahora, enBloqueo: true })).join(' ')

    expect(libre).toContain('Le avisamos por email a la paciente y el horario vuelve a quedar libre para reservas. No se puede deshacer.')
    expect(bloqueado).toContain('Le avisamos por email a la paciente. No se puede deshacer.')
    expect(bloqueado).not.toContain('vuelve a quedar libre')
    expect(bloqueado).toContain('¿Cancelás el turno de Ana Pérez?')
  })
})

describe('visibilidad de Cancelar', () => {
  const ahora = new Date('2026-11-25T10:10:00-03:00')

  it('se ofrece solo para reservados que todavía no empezaron', () => {
    const base = turno()
    expect(puedeCancelar(base, ahora)).toBe(true)
    for (const estado of ['CANCELADO', 'ATENDIDO', 'AUSENTE'] as const) {
      expect(puedeCancelar({ ...base, estado }, ahora)).toBe(false)
    }
    expect(puedeCancelar({ ...base, inicio: '2026-11-25T10:10:00-03:00' }, ahora)).toBe(false)
    expect(puedeCancelar({ ...base, inicio: '2026-11-25T10:09:59-03:00' }, ahora)).toBe(false)
    expect(puedeCancelar({ ...base, inicio: '2026-11-25T10:10:01-03:00' }, ahora)).toBe(true)
  })
})
