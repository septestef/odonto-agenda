import { describe, expect, it } from 'vitest'
import type { Descriptor } from '../../shared/ui/componentes'
import { descriptorSemana } from './vista-semana'

function deClase(descriptor: Descriptor, clase: string): Descriptor[] {
  const propios = (descriptor.clase ?? '').split(' ').includes(clase) ? [descriptor] : []
  return [...propios, ...(descriptor.hijos ?? []).flatMap((hijo) => deClase(hijo, clase))]
}

function texto(descriptor: Descriptor): string {
  return [
    ...(descriptor.texto === undefined ? [] : [descriptor.texto]),
    ...(descriptor.hijos ?? []).map(texto),
  ].join(' ')
}

describe('eventos de la vista semanal', () => {
  const fecha = '2026-11-25'
  const franjas = [1, 2, 3, 4, 5].map((diaSemana) => ({
    diaSemana,
    inicio: '09:00',
    fin: '13:00',
  }))
  const turnos = [
    {
      id: 1,
      inicio: '2026-11-23T09:00:00-03:00',
      fin: '2026-11-23T09:30:00-03:00',
      estado: 'RESERVADO' as const,
      origen: 'MANUAL' as const,
      paciente: { apellido: 'Ruiz' },
      prestacion: { nombre: 'Consulta' },
      canceladoPor: null,
      canceladoEn: null,
    },
    {
      id: 2,
      inicio: `${fecha}T10:30:00-03:00`,
      fin: `${fecha}T11:00:00-03:00`,
      estado: 'RESERVADO' as const,
      origen: 'ONLINE' as const,
      paciente: { apellido: 'Pérez' },
      prestacion: { nombre: 'Consulta' },
      canceladoPor: null,
      canceladoEn: null,
    },
    {
      id: 3,
      inicio: `${fecha}T11:15:00-03:00`,
      fin: `${fecha}T12:00:00-03:00`,
      estado: 'RESERVADO' as const,
      origen: 'ONLINE' as const,
      paciente: { apellido: 'Sosa' },
      prestacion: { nombre: 'Limpieza' },
      canceladoPor: null,
      canceladoEn: null,
    },
    {
      id: 4,
      inicio: '2026-11-27T10:30:00-03:00',
      fin: '2026-11-27T11:00:00-03:00',
      estado: 'RESERVADO' as const,
      origen: 'ONLINE' as const,
      paciente: { apellido: 'Gómez' },
      prestacion: { nombre: 'Consulta' },
      canceladoPor: null,
      canceladoEn: null,
    },
  ]
  const bloqueos = [
    {
      id: 7,
      inicio: `${fecha}T11:00:00-03:00`,
      fin: `${fecha}T12:00:00-03:00`,
      motivo: 'Trámite personal',
      turnosAfectados: [
        {
          id: 3,
          inicio: `${fecha}T11:15:00-03:00`,
          fin: `${fecha}T12:00:00-03:00`,
          estado: 'RESERVADO' as const,
          pacienteApellido: 'Sosa',
          prestacionNombre: 'Limpieza',
        },
      ],
    },
    {
      id: 8,
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
      motivo: 'Control particular',
      turnosAfectados: [
        {
          id: 4,
          inicio: '2026-11-27T10:30:00-03:00',
          fin: '2026-11-27T11:00:00-03:00',
          estado: 'RESERVADO' as const,
          pacienteApellido: 'Gómez',
          prestacionNombre: 'Consulta',
        },
      ],
    },
  ]

  it('posiciona turnos y bloqueos por minutos y marca los afectados y el seleccionado', () => {
    const vista = descriptorSemana({
      fecha,
      ahora: new Date(`${fecha}T10:10:00-03:00`),
      franjas,
      turnos,
      bloqueos,
      seleccionadoId: 2,
    })

    const eventos = deClase(vista, 'ev')
    expect(eventos).toHaveLength(4)
    const porId = (id: number): Descriptor | undefined =>
      eventos.find((evento) => evento.atributos?.['data-turno-id'] === String(id))

    expect(porId(1)?.atributos?.['style']).toContain('top:calc(var(--min) * 0)')
    expect(porId(1)?.atributos?.['style']).toContain('height:calc(var(--min) * 30)')
    expect(porId(2)?.atributos?.['style']).toContain('top:calc(var(--min) * 90)')
    expect((porId(2)?.clase ?? '').split(' ')).toContain('ev--sel')
    expect((porId(3)?.clase ?? '').split(' ')).toContain('ev--afectado')
    expect((porId(4)?.clase ?? '').split(' ')).toContain('ev--afectado')
    expect(texto(porId(2) ?? vista)).toContain('10:30 Pérez Consulta')

    const bloques = deClase(vista, 'bloq')
    expect(bloques).toHaveLength(2)
    expect(bloques[0]?.atributos?.['style']).toContain('top:calc(var(--min) * 120)')
    expect(texto(bloques[0] ?? vista)).toContain('Bloqueo · Trámite personal')

    const ahora = deClase(vista, 'linea-ahora')
    expect(ahora).toHaveLength(1)
    expect(ahora[0]?.atributos?.['style']).toContain('top:calc(var(--min) * 70)')
  })

  it('resume los turnos dentro de bloqueos sin modificar su estado', () => {
    const vista = descriptorSemana({
      fecha,
      ahora: new Date(`${fecha}T10:10:00-03:00`),
      franjas,
      turnos,
      bloqueos,
      seleccionadoId: undefined,
    })

    expect(texto(vista)).toContain('Hay 2 turnos dentro de bloqueos esta semana')
    expect(texto(vista)).toContain('Miércoles 25 a las 11:15 y viernes 27 a las 10:30. Tocalos para revisarlos o cancelarlos.')
    expect(deClase(vista, 'leyenda')).toHaveLength(1)
    expect(deClase(vista, 'ev--cancelado')).toHaveLength(0)
  })
})

describe('columnas de la vista semanal', () => {
  const ahora = new Date('2026-11-25T13:10:00-03:00')

  it('dibuja Lun–Vie, resalta hoy y marca la columna donde empieza el desplazamiento', () => {
    const franjas = [1, 2, 3, 4, 5].map((diaSemana) => ({
      diaSemana,
      inicio: '09:00',
      fin: '13:00',
    }))
    const vista = descriptorSemana({
      fecha: '2026-11-25',
      ahora,
      franjas,
      turnos: [],
      bloqueos: [],
      seleccionadoId: undefined,
    })

    const cabeceras = deClase(vista, 'semana__cab')
    expect(cabeceras.map(texto).filter((item) => item !== '')).toEqual([
      'Lun 23',
      'Mar 24',
      'Mié 25 · hoy',
      'Jue 26',
      'Vie 27',
    ])
    const hoy = deClase(vista, 'semana__cab--hoy')
    expect(hoy).toHaveLength(1)
    expect(hoy[0]?.atributos?.['style']).toContain('scroll-start-target: auto')
    expect(deClase(vista, 'semana__col')).toHaveLength(5)
    expect(deClase(vista, 'semana')[0]?.atributos?.['aria-label']).toBe('Agenda semanal, desplazable')
  })

  it('omite días sin franjas y conserva el lunes aunque hoy no tenga atención', () => {
    const vista = descriptorSemana({
      fecha: '2026-11-26',
      ahora,
      franjas: [
        { diaSemana: 1, inicio: '09:00', fin: '13:00' },
        { diaSemana: 3, inicio: '09:00', fin: '13:00' },
        { diaSemana: 5, inicio: '09:00', fin: '13:00' },
      ],
      turnos: [],
      bloqueos: [],
      seleccionadoId: undefined,
    })

    expect(deClase(vista, 'semana__cab').map(texto).filter((item) => item !== '')).toEqual([
      'Lun 23',
      'Mié 25 · hoy',
      'Vie 27',
    ])
    expect(deClase(vista, 'semana__col')).toHaveLength(3)
  })
})
