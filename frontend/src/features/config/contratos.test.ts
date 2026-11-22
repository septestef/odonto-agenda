import { describe, expect, it } from 'vitest'
import { campoEnDetalles, leerBloqueos, leerHorario, leerPrestaciones } from './contratos'

describe('contratos de configuración', () => {
  it('estrecha la respuesta de horarios', () => {
    expect(leerHorario({ franjas: [{ diaSemana: 1, inicio: '09:00', fin: '13:00' }] })).toEqual({
      franjas: [{ diaSemana: 1, inicio: '09:00', fin: '13:00' }],
    })
  })

  it('conserva prestaciones activas e inactivas', () => {
    const datos = {
      prestaciones: [
        { id: 1, nombre: 'Consulta', duracionMinutos: 30, activa: true },
        { id: 2, nombre: 'Limpieza', duracionMinutos: 45, activa: false },
      ],
    }

    expect(leerPrestaciones(datos)).toEqual(datos.prestaciones)
  })

  it('conserva bloqueos con turnos afectados', () => {
    const turno = {
      id: 12,
      inicio: '2026-11-27T10:30:00-03:00',
      fin: '2026-11-27T11:00:00-03:00',
      estado: 'RESERVADO',
      pacienteNombre: 'Luis',
      pacienteApellido: 'Gómez',
      prestacionNombre: 'Consulta',
    }
    const datos = {
      bloqueos: [
        {
          id: 7,
          inicio: '2026-11-27T10:00:00-03:00',
          fin: '2026-11-27T12:00:00-03:00',
          motivo: 'Control particular',
          turnosAfectados: [turno],
        },
      ],
    }

    expect(leerBloqueos(datos)).toEqual(datos.bloqueos)
  })

  it('acepta turnosAfectados vacío y conserva todos los datos del turno RESERVADO', () => {
    const sinTurnos = leerBloqueos({
      bloqueos: [
        {
          id: 8,
          inicio: '2026-12-04T14:00:00-03:00',
          fin: '2026-12-04T16:00:00-03:00',
          turnosAfectados: [],
        },
      ],
    })

    expect(sinTurnos[0]?.turnosAfectados).toEqual([])
    const turno = {
      id: 12,
      inicio: '2026-11-27T10:30:00-03:00',
      fin: '2026-11-27T11:00:00-03:00',
      estado: 'RESERVADO',
      pacienteNombre: 'Luis',
      pacienteApellido: 'Gómez',
      prestacionNombre: 'Consulta',
    }
    const conTurnos = leerBloqueos({
      bloqueos: [
        {
          id: 7,
          inicio: '2026-11-27T10:00:00-03:00',
          fin: '2026-11-27T12:00:00-03:00',
          turnosAfectados: [turno],
        },
      ],
    })

    expect(conTurnos[0]?.turnosAfectados[0]).toEqual(turno)
  })

  it('devuelve ningún campo cuando detalles está ausente o no reconocido', () => {
    expect(campoEnDetalles(undefined)).toEqual([])
    expect(campoEnDetalles({})).toEqual([])
    expect(campoEnDetalles([{ mensaje: 'sin campo' }])).toEqual([])
  })

  it('rechaza DTOs con tipos inesperados con un mensaje comprensible', () => {
    expect(() => leerHorario({ franjas: [{ diaSemana: 'lunes', inicio: '09:00', fin: '13:00' }] })).toThrow(
      'La respuesta de /api/horarios no tiene el formato esperado.',
    )
    expect(() => leerPrestaciones({ prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: '30', activa: true }] })).toThrow(
      'La respuesta de /api/prestaciones no tiene el formato esperado.',
    )
    expect(() => leerBloqueos({ bloqueos: [{ id: 7, inicio: 10, fin: 12, turnosAfectados: [] }] })).toThrow(
      'La respuesta de /api/bloqueos no tiene el formato esperado.',
    )
  })
})
