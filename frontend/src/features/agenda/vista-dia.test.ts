import { describe, expect, it } from 'vitest'
import type { Descriptor } from '../../shared/ui/componentes'
import type { TurnoAgenda } from './tipos'
import { descriptorDia } from './vista-dia'

function turno(id: number, inicio: string, fin: string, nombre: string, estado: TurnoAgenda['estado'] = 'RESERVADO'): TurnoAgenda {
  return {
    id,
    inicio,
    fin,
    estado,
    origen: id === 1 ? 'MANUAL' : 'ONLINE',
    paciente: { nombre, apellido: 'Pérez' },
    prestacion: { nombre: 'Consulta', duracionMinutos: 30 },
    canceladoPor: null,
    canceladoEn: null,
  }
}

function todosLosTextos(descriptor: Descriptor): string[] {
  return [
    ...(descriptor.texto === undefined ? [] : [descriptor.texto]),
    ...(descriptor.hijos ?? []).flatMap(todosLosTextos),
  ]
}

function deClase(descriptor: Descriptor, clase: string): Descriptor[] {
  const propios = (descriptor.clase ?? '').split(' ').includes(clase) ? [descriptor] : []
  return [...propios, ...(descriptor.hijos ?? []).flatMap((hijo) => deClase(hijo, clase))]
}

function textosDe(descriptor: Descriptor): string[] {
  return todosLosTextos(descriptor)
}

describe('fusión de la vista diaria', () => {
  const fecha = '2026-11-25'
  const franjas = [
    { diaSemana: 3, inicio: '09:00', fin: '13:00' },
    { diaSemana: 3, inicio: '16:00', fin: '19:00' },
  ]
  const turnos = [
    turno(1, `${fecha}T09:00:00-03:00`, `${fecha}T09:30:00-03:00`, 'Jorge'),
    turno(2, `${fecha}T09:30:00-03:00`, `${fecha}T10:15:00-03:00`, 'Lucía'),
    turno(3, `${fecha}T10:30:00-03:00`, `${fecha}T11:00:00-03:00`, 'Ana'),
    turno(4, `${fecha}T11:15:00-03:00`, `${fecha}T12:00:00-03:00`, 'Martín'),
    {
      ...turno(5, `${fecha}T16:00:00-03:00`, `${fecha}T16:30:00-03:00`, 'María', 'CANCELADO'),
      canceladoPor: 'PACIENTE' as const,
      canceladoEn: `${fecha}T09:00:00-03:00`,
    },
    turno(6, `${fecha}T16:30:00-03:00`, `${fecha}T17:15:00-03:00`, 'Pablo'),
    turno(7, `${fecha}T17:30:00-03:00`, `${fecha}T18:30:00-03:00`, 'Sofía'),
  ]
  const bloqueos = [
    {
      id: 7,
      inicio: `${fecha}T11:00:00-03:00`,
      fin: `${fecha}T12:00:00-03:00`,
      motivo: 'Trámite personal',
      turnosAfectados: [
        {
          id: 4,
          inicio: `${fecha}T11:15:00-03:00`,
          fin: `${fecha}T12:00:00-03:00`,
          estado: 'RESERVADO' as const,
          pacienteNombre: 'Martín',
          pacienteApellido: 'Sosa',
          prestacionNombre: 'Limpieza',
        },
      ],
    },
  ]

  it('muestra huecos libres, fuera de horario y el bloqueo sin cancelar sus turnos', () => {
    const vista = descriptorDia({
      fecha,
      ahora: new Date(`${fecha}T10:10:00-03:00`),
      franjas,
      turnos,
      bloqueos,
      seleccionadoId: 3,
    })
    const textos = textosDe(vista)

    expect(textos).toContain('10:15–10:30 · libre')
    expect(textos).toContain('12:00–13:00 · libre')
    expect(textos).toContain('13:00 – 16:00 · fuera del horario de atención')
    expect(textos).toContain('17:15–17:30 · libre')
    expect(textos).toContain('18:30–19:00 · libre')
    expect(textos).toContain('11:00–12:00')
    expect(textos).toContain('Trámite personal')
    expect(textos).toContain('Hay 1 turno dentro del bloqueo. El bloqueo no lo cancela: decidí vos.')
    const afectados = deClase(vista, 'afectado')
    expect(afectados).toHaveLength(1)
    expect(textosDe(afectados[0] ?? vista).join(' ')).toContain('11:15–12:00')
    expect(textosDe(afectados[0] ?? vista).join(' ')).toContain('Martín Sosa · Limpieza')
    expect(textosDe(afectados[0] ?? vista)).toContain('Cancelar turno')
  })

  it('tacha cancelados y atenúa pasados/en curso sin ofrecer Cancelar', () => {
    const vista = descriptorDia({
      fecha,
      ahora: new Date(`${fecha}T09:10:00-03:00`),
      franjas,
      turnos,
      bloqueos: [],
      seleccionadoId: undefined,
    })
    const porId = (id: number): Descriptor =>
      deClase(vista, 'turno').find(
        (item) => item.atributos?.['data-turno-id'] === String(id),
      ) ?? vista

    expect((porId(1).clase ?? '').split(' ')).toContain('turno--pasado')
    expect(textosDe(porId(1)).join(' ')).toContain('en curso')
    expect(textosDe(porId(1))).not.toContain('Cancelar')

    expect((porId(5).clase ?? '').split(' ')).toContain('turno--cancelado')
    expect(textosDe(porId(5)).join(' ')).toContain('canceló el paciente')
    expect(textosDe(porId(5))).not.toContain('Cancelar')

    expect(textosDe(porId(3))).toContain('Cancelar')
  })
})

describe('bloqueos en la vista diaria', () => {
  const fecha = '2026-11-25'

  it('lista todos los turnos afectados y conserva sus estados', () => {
    const vista = descriptorDia({
      fecha,
      ahora: new Date(`${fecha}T10:10:00-03:00`),
      franjas: [{ diaSemana: 3, inicio: '09:00', fin: '13:00' }],
      turnos: [
        turno(4, `${fecha}T11:15:00-03:00`, `${fecha}T11:45:00-03:00`, 'Martín'),
        turno(6, `${fecha}T12:00:00-03:00`, `${fecha}T12:30:00-03:00`, 'Lucía', 'CANCELADO'),
      ],
      bloqueos: [
        {
          id: 7,
          inicio: `${fecha}T11:00:00-03:00`,
          fin: `${fecha}T13:00:00-03:00`,
          motivo: 'Trámite personal',
          turnosAfectados: [
            { id: 4, inicio: `${fecha}T11:15:00-03:00`, fin: `${fecha}T11:45:00-03:00`, estado: 'RESERVADO' },
            { id: 6, inicio: `${fecha}T12:00:00-03:00`, fin: `${fecha}T12:30:00-03:00`, estado: 'CANCELADO' },
          ],
        },
      ],
      seleccionadoId: undefined,
    })

    const afectados = deClase(vista, 'afectado')
    expect(afectados.map((item) => item.atributos?.['data-turno-id'])).toEqual(['4', '6'])
    expect(textosDe(vista).join(' ')).toContain('Hay 2 turnos dentro del bloqueo. El bloqueo no lo cancela: decidí vos.')
    expect(textosDe(vista)).toContain('Trámite personal')

    const porId = (id: number): Descriptor =>
      deClase(vista, 'turno').find((item) => item.atributos?.['data-turno-id'] === String(id)) ?? vista
    expect((porId(4).clase ?? '').split(' ')).toContain('turno--reservado')
    expect((porId(6).clase ?? '').split(' ')).toContain('turno--cancelado')
  })
})

describe('vista diaria', () => {
  it('agrupa por franja, ordena los turnos y muestra horas, estado y origen', () => {
    const vista = descriptorDia({
      fecha: '2026-11-25',
      ahora: new Date('2026-11-25T14:00:00-03:00'),
      franjas: [
        { diaSemana: 3, inicio: '09:00', fin: '13:00' },
        { diaSemana: 3, inicio: '16:00', fin: '19:00' },
      ],
      turnos: [
        turno(3, '2026-11-25T16:30:00-03:00', '2026-11-25T17:00:00-03:00', 'Pablo'),
        turno(1, '2026-11-25T09:00:00-03:00', '2026-11-25T09:30:00-03:00', 'Jorge'),
        turno(2, '2026-11-25T09:30:00-03:00', '2026-11-25T10:15:00-03:00', 'Lucía'),
      ],
      bloqueos: [],
      seleccionadoId: undefined,
    })

    expect(vista.clase).toBe('dia')
    const franjas = deClase(vista, 'franja')
    expect(franjas).toHaveLength(2)
    expect(todosLosTextos(franjas[0] ?? vista)).toContain('Mañana')
    expect(todosLosTextos(franjas[0] ?? vista)).toContain('09:00 – 13:00')
    expect(todosLosTextos(franjas[1] ?? vista)).toContain('Tarde')

    const turnosRenderizados = deClase(vista, 'turno')
    expect(turnosRenderizados.map((item) => todosLosTextos(item).find((texto) => texto.includes('–')))).toEqual([
      '09:00–09:30',
      '09:30–10:15',
      '16:30–17:00',
    ])
    expect(todosLosTextos(turnosRenderizados[0] ?? vista)).toContain('Manual')
    expect(todosLosTextos(turnosRenderizados[1] ?? vista)).toContain('Online')
    expect(todosLosTextos(turnosRenderizados[1] ?? vista)).toContain('Reservado')
  })

  it('dibuja la línea "ahora" dentro de la franja que contiene la hora visual', () => {
    const entrada = {
      fecha: '2026-11-25',
      franjas: [{ diaSemana: 3, inicio: '09:00', fin: '13:00' }],
      turnos: [turno(1, '2026-11-25T09:00:00-03:00', '2026-11-25T09:30:00-03:00', 'Jorge')],
      bloqueos: [],
      seleccionadoId: undefined,
    }

    const dentro = descriptorDia({ ...entrada, ahora: new Date('2026-11-25T13:10:00-03:00') })
    expect(deClase(dentro, 'ahora')).toHaveLength(0)

    const enFranja = descriptorDia({ ...entrada, ahora: new Date('2026-11-25T10:10:00-03:00') })
    expect(todosLosTextos(deClase(enFranja, 'ahora')[0] ?? enFranja)).toContain('Ahora · 10:10')
  })
})
