import { describe, expect, it } from 'vitest'
import type { Descriptor } from '../../shared/ui/componentes'
import { descriptorAviso, descriptorEstado, seleccionarEstado } from './estados'
import type { BloqueoAgenda, FranjaHorario, TurnoAgenda } from './tipos'

function textos(descriptor: Descriptor): string[] {
  return [
    ...(descriptor.texto === undefined ? [] : [descriptor.texto]),
    ...Object.values(descriptor.atributos ?? {}),
    ...(descriptor.hijos ?? []).flatMap(textos),
  ]
}

function deClase(descriptor: Descriptor, clase: string): Descriptor[] {
  const propios = (descriptor.clase ?? '').split(' ').includes(clase) ? [descriptor] : []
  return [...propios, ...(descriptor.hijos ?? []).flatMap((hijo) => deClase(hijo, clase))]
}

const franjas: FranjaHorario[] = [
  { diaSemana: 3, inicio: '09:00', fin: '13:00' },
  { diaSemana: 3, inicio: '16:00', fin: '19:00' },
]

describe('selección del estado de la agenda', () => {
  it('prioriza carga y error por sobre datos parciales', () => {
    const base = { fecha: '2026-11-25', vista: 'dia' as const, franjas, turnos: [] as TurnoAgenda[] }
    expect(seleccionarEstado({ ...base, cargando: true, error: undefined })).toBe('cargando')
    expect(seleccionarEstado({ ...base, cargando: false, error: Object.assign(new Error('falló'), { codigo: 'ERROR_INTERNO' }) })).toBe('error')
  })

  it('en semana mira las franjas de toda la semana, no solo el día de la fecha', () => {
    const base = {
      cargando: false,
      error: undefined,
      fecha: '2026-11-26',
      vista: 'semana' as const,
      turnos: [] as TurnoAgenda[],
    }
    expect(seleccionarEstado({ ...base, franjas: [{ diaSemana: 3, inicio: '09:00', fin: '13:00' }] })).toBe('sin-turnos')
    expect(seleccionarEstado({ ...base, franjas: [] })).toBe('sin-horarios')
    expect(seleccionarEstado({ ...base, franjas: [{ diaSemana: 6, inicio: '09:00', fin: '13:00' }] })).toBe('sin-turnos')
  })

  it('distingue sin horarios, día sin franjas, sin turnos y agenda con datos', () => {
    const base = { cargando: false, error: undefined, fecha: '2026-11-25', vista: 'dia' as const, turnos: [] as TurnoAgenda[] }
    expect(seleccionarEstado({ ...base, franjas: [] })).toBe('sin-horarios')
    expect(seleccionarEstado({ ...base, franjas: [{ diaSemana: 4, inicio: '09:00', fin: '13:00' }] })).toBe('sin-franjas')
    expect(seleccionarEstado({ ...base, franjas })).toBe('sin-turnos')
    const turno: TurnoAgenda = {
      id: 1,
      inicio: '2026-11-25T09:00:00-03:00',
      fin: '2026-11-25T09:30:00-03:00',
      estado: 'RESERVADO',
      origen: 'ONLINE',
      paciente: {},
      prestacion: {},
      canceladoPor: null,
      canceladoEn: null,
    }
    expect(seleccionarEstado({ ...base, franjas, turnos: [turno] })).toBe('con-agenda')
  })
})

describe('copia de los estados vacíos y de carga', () => {
  it('muestra esqueletos accesibles mientras carga', () => {
    const vista = descriptorEstado('cargando', { fecha: '2026-11-25', vista: 'dia', franjas })
    expect(vista?.atributos?.['aria-busy']).toBe('true')
    expect(vista?.atributos?.['aria-label']).toBe('Cargando la agenda')
    expect(deClase(vista ?? { etiqueta: 'div' }, 'esqueleto')).toHaveLength(3)
  })

  it('invita a cargar horarios con enlace a /config', () => {
    const vista = descriptorEstado('sin-horarios', { fecha: '2026-11-25', vista: 'dia', franjas: [] })
    expect(textos(vista ?? { etiqueta: 'div' })).toContain('Todavía no cargaste tus horarios')
    expect(textos(vista ?? { etiqueta: 'div' })).toContain('Definí los días y las franjas en las que atendés para empezar a recibir turnos.')
    const enlace = deClase(vista ?? { etiqueta: 'div' }, 'empty')[0]?.hijos?.find((hijo) => hijo.etiqueta === 'a')
    expect(enlace?.atributos?.['href']).toBe('/config')
    expect(enlace?.texto).toBe('Ir a Configuración')
  })

  it('explica el día sin turnos y el día sin atención', () => {
    const sinTurnos = descriptorEstado('sin-turnos', { fecha: '2026-11-25', vista: 'dia', franjas })
    const sinAtencion = descriptorEstado('sin-franjas', { fecha: '2026-11-28', vista: 'dia', franjas })
    expect(textos(sinTurnos ?? { etiqueta: 'div' })).toContain('No tenés turnos este día')
    expect(textos(sinTurnos ?? { etiqueta: 'div' }).join(' ')).toContain('Atendés de 09:00 a 13:00 y de 16:00 a 19:00')
    expect(textos(sinAtencion ?? { etiqueta: 'div' })).toContain('El sábado no atendés')
    expect(textos(sinAtencion ?? { etiqueta: 'div' })).toContain('No hay franjas cargadas para este día.')
  })

  it('usa el equivalente semanal cuando corresponde', () => {
    const vista = descriptorEstado('sin-turnos', { fecha: '2026-11-25', vista: 'semana', franjas })
    expect(textos(vista ?? { etiqueta: 'div' })).toContain('No tenés turnos esta semana')
  })
})

describe('avisos de cancelación y error sin agenda parcial', () => {
  it('conserva la copia aprobada de los estados 6, 7 y 8', () => {
    const empezo = textos(descriptorAviso('ya-empezo')).join(' ')
    const cambio = textos(descriptorAviso('cambio')).join(' ')
    const error = textos(descriptorAviso('error')).join(' ')

    expect(empezo).toContain('No se pudo cancelar')
    expect(empezo).toContain('Este turno ya empezó. Actualizamos la agenda.')
    expect(cambio).toContain('Este turno cambió')
    expect(cambio).toContain('Puede que el paciente lo haya cancelado o cambiado de horario. Actualizamos la agenda.')
    expect(error).toContain('No pudimos cargar la agenda')
    expect(error).toContain('Revisá la conexión y probá de nuevo.')
    expect(descriptorAviso('error').atributos?.['role']).toBe('alert')
    expect(deClase(descriptorAviso('error'), 'reintentar-agenda')).toHaveLength(1)
  })

  it('el estado de error no dibuja contenido de agenda', () => {
    const vista = descriptorEstado('error', {
      fecha: '2026-11-25',
      vista: 'dia',
      franjas,
      turnos: [{ id: 1, inicio: '2026-11-25T09:00:00-03:00', fin: '2026-11-25T09:30:00-03:00', estado: 'RESERVADO', origen: 'ONLINE', paciente: { nombre: 'Ana' }, prestacion: {}, canceladoPor: null, canceladoEn: null }],
      bloqueos: [] as BloqueoAgenda[],
    })
    const todo = textos(vista ?? { etiqueta: 'div' }).join(' ')
    expect(todo).toContain('No pudimos cargar la agenda')
    expect(todo).not.toContain('Ana')
    expect(todo).not.toContain('Mañana')
  })
})
