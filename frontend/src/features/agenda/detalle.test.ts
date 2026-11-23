import { describe, expect, it } from 'vitest'
import type { Descriptor } from '../../shared/ui/componentes'
import { contenedorDetalle, descriptorDetalle } from './detalle'
import { descriptorSemana } from './vista-semana'
import type { BloqueoAgenda, TurnoAgenda } from './tipos'

function turno(reservado = true): TurnoAgenda {
  return {
    id: 3,
    inicio: '2026-11-25T11:15:00-03:00',
    fin: '2026-11-25T12:00:00-03:00',
    estado: 'RESERVADO',
    origen: 'ONLINE',
    paciente: {
      id: 5,
      dni: '28.456.789',
      nombre: 'Martín',
      apellido: 'Sosa',
      telefono: '+54 9 351 765 4321',
      email: 'martin.sosa@ejemplo.com',
    },
    prestacion: { id: 2, nombre: 'Limpieza', duracionMinutos: 45 },
    canceladoPor: null,
    canceladoEn: null,
    ...(reservado ? {} : { estado: 'ATENDIDO' as const }),
  }
}

function bloqueo(): BloqueoAgenda {
  return {
    id: 7,
    inicio: '2026-11-25T11:00:00-03:00',
    fin: '2026-11-25T12:00:00-03:00',
    motivo: 'Trámite personal',
    turnosAfectados: [
      {
        id: 3,
        inicio: '2026-11-25T11:15:00-03:00',
        fin: '2026-11-25T12:00:00-03:00',
        estado: 'RESERVADO',
      },
    ],
  }
}

function todosLosTextos(descriptor: Descriptor): string[] {
  return [
    ...(descriptor.texto === undefined ? [] : [descriptor.texto]),
    ...Object.values(descriptor.atributos ?? {}),
    ...(descriptor.hijos ?? []).flatMap(todosLosTextos),
  ]
}

function deClase(descriptor: Descriptor, clase: string): Descriptor[] {
  const propios = (descriptor.clase ?? '').split(' ').includes(clase) ? [descriptor] : []
  return [...propios, ...(descriptor.hijos ?? []).flatMap((hijo) => deClase(hijo, clase))]
}

describe('contenedor del detalle del turno', () => {
  const ahora = new Date('2026-11-25T10:10:00-03:00')

  it('usa hoja en celular, panel fijo en día de escritorio y cajón en semana de escritorio', () => {
    const comun = { turno: turno(), ahora, bloqueo: undefined }

    const hoja = contenedorDetalle({ ...comun, vista: 'dia', ancho: 390 })
    const panel = contenedorDetalle({ ...comun, vista: 'dia', ancho: 1440 })
    const cajon = contenedorDetalle({ ...comun, vista: 'semana', ancho: 1440 })

    expect((hoja.clase ?? '').split(' ')).toContain('detalle--hoja')
    expect((panel.clase ?? '').split(' ')).toContain('detalle--panel')
    expect((cajon.clase ?? '').split(' ')).toContain('detalle--cajon')
  })

  it('expone diálogo modal, cierre con foco inicial y el turno elegido con aria-current en la grilla', () => {
    const vista = contenedorDetalle({
      turno: turno(),
      vista: 'semana',
      ancho: 1440,
      ahora,
      bloqueo: undefined,
    })
    expect(vista.atributos?.['role']).toBe('dialog')
    expect(vista.atributos?.['aria-modal']).toBe('true')
    expect(vista.atributos?.['aria-labelledby']).toBe('detalle-titulo')
    const cierre = deClase(vista, 'detalle__cerrar')[0]
    expect(cierre?.atributos?.['autofocus']).toBe('')
    expect(cierre?.atributos?.['aria-label']).toBe('Cerrar el detalle')

    const grilla = descriptorSemana({
      fecha: '2026-11-25',
      ahora,
      franjas: [{ diaSemana: 3, inicio: '09:00', fin: '13:00' }],
      turnos: [turno()],
      bloqueos: [],
      seleccionadoId: 3,
    })
    expect(deClase(grilla, 'ev--sel')[0]?.atributos?.['aria-current']).toBe('true')
  })
})

describe('campos del detalle del turno', () => {
  const ahora = new Date('2026-11-25T10:10:00-03:00')

  it('muestra los campos del contrato P2 con las etiquetas aprobadas', () => {
    const vista = descriptorDetalle({ turno: turno(), ahora, bloqueo: bloqueo() })
    const pares = deClase(vista, 'dato').map((dato) => todosLosTextos(dato).join(' '))

    expect(todosLosTextos(vista)).toContain('Martín Sosa')
    expect(todosLosTextos(vista)).toContain('Miércoles 25/11 · 11:15 a 12:00')
    expect(todosLosTextos(vista)).toContain('Reservado')
    expect(todosLosTextos(vista)).toContain('Online')
    expect(pares).toContain('Prestación Limpieza (45 min)')
    expect(pares).toContain('DNI 28.456.789')
    expect(pares.some((par) => par.includes('Teléfono +54 9 351 765 4321'))).toBe(true)
    expect(pares).toContain('Email martin.sosa@ejemplo.com')
    expect(pares).toContain('Origen Lo reservó el paciente online')
    expect(deClase(vista, 'detalle__cancelar')).toHaveLength(1)
  })

  it('avisa que el turno sigue reservado cuando está dentro de un bloqueo', () => {
    const vista = descriptorDetalle({ turno: turno(), ahora, bloqueo: bloqueo() })
    expect(todosLosTextos(vista).join(' ')).toContain('Está dentro de un bloqueo')
    expect(todosLosTextos(vista).join(' ')).toContain(
      '"Trámite personal", 11:00 a 12:00. El turno sigue reservado hasta que lo canceles.',
    )
  })

  it('omite filas ausentes y no renderiza secretos ni metadatos', () => {
    const incompleto: TurnoAgenda = {
      ...turno(),
      paciente: { apellido: 'Sosa' },
      prestacion: {},
      ...{
        token: 'secreto',
        hash: 'abc123',
        email_gestion: 'gestion@ejemplo.com',
        odontologoId: 9,
        auditoria: 'creado por nadie',
      },
    } as unknown as TurnoAgenda
    const vista = descriptorDetalle({ turno: incompleto, ahora, bloqueo: undefined })
    const textos = todosLosTextos(vista)

    expect(textos).toContain('Sosa')
    expect(textos.join(' ')).not.toContain('undefined')
    expect(textos.join(' ')).not.toContain('NaN')
    for (const secreto of ['secreto', 'abc123', 'gestion@ejemplo.com', 'odontologoId', 'auditoria']) {
      expect(textos.join(' ')).not.toContain(secreto)
    }
    expect(deClase(vista, 'dato').length).toBeGreaterThan(0)
  })

  it('no ofrece Cancelar para turnos finalizados', () => {
    const vista = descriptorDetalle({ turno: turno(false), ahora, bloqueo: undefined })
    expect(deClase(vista, 'detalle__cancelar')).toHaveLength(0)
  })
})
