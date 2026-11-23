import { describe, expect, it } from 'vitest'
import type { DatosPaciente, DisponibilidadPublica, EstadoManual, PerfilManual, ReservaConfirmada } from './modelo'
import { esConflictoManual, estadoInicialManual, reducir } from './estado'

const PERFIL: PerfilManual = {
  fechaMinima: '2026-11-23',
  fechaMaxima: '2027-01-22',
  prestaciones: [
    { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
    { id: 2, nombre: 'Control', duracionMinutos: 15 },
  ],
}

const DISPONIBILIDAD: DisponibilidadPublica = {
  fecha: '2026-11-25',
  prestacionId: 1,
  duracionMinutos: 30,
  horarios: [
    { inicio: '2026-11-25T09:15:00-03:00', hora: '09:15' },
    { inicio: '2026-11-25T09:45:00-03:00', hora: '09:45' },
  ],
}

const DATOS: DatosPaciente = {
  dni: '30123456',
  nombre: 'Luis',
  apellido: 'Ramírez',
  telefono: '+5493511234567',
  email: 'luis@ejemplo.com',
}

const RESERVA: ReservaConfirmada = {
  inicio: '2026-11-25T09:15:00-03:00',
  fin: '2026-11-25T09:45:00-03:00',
  prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
  odontologo: { nombre: 'Ana', apellido: 'Gómez', direccionConsultorio: null },
  email: DATOS.email,
}

function conPerfil(): EstadoManual {
  return reducir(estadoInicialManual(), { tipo: 'perfil-cargado', perfil: PERFIL })
}

function hastaFormulario(): EstadoManual {
  let estado = conPerfil()
  estado = reducir(estado, { tipo: 'elegir-prestacion', prestacionId: 1, fecha: '2026-11-25' })
  estado = reducir(estado, { tipo: 'disponibilidad-cargada', disponibilidad: DISPONIBILIDAD })
  estado = reducir(estado, { tipo: 'elegir-horario', horario: DISPONIBILIDAD.horarios[0]! })
  return reducir(estado, { tipo: 'ir-a-datos' })
}

describe('reducir del turno manual', () => {
  it('carga el perfil y arranca en la primera semana', () => {
    const estado = conPerfil()
    expect(estado.paso).toBe('eligiendo-prestacion')
    expect(estado.inicioSemana).toBe(PERFIL.fechaMinima)
  })

  it('muestra el estado vacío cuando no hay horarios', () => {
    let estado = conPerfil()
    estado = reducir(estado, { tipo: 'elegir-prestacion', prestacionId: 2, fecha: '2026-11-25' })
    estado = reducir(estado, {
      tipo: 'disponibilidad-cargada',
      disponibilidad: { ...DISPONIBILIDAD, horarios: [], prestacionId: 2, duracionMinutos: 15 },
    })
    expect(estado.paso).toBe('sin-horarios')
  })

  it('no cambia el estado ante una prestación inexistente', () => {
    const estado = conPerfil()
    expect(reducir(estado, { tipo: 'elegir-prestacion', prestacionId: 99, fecha: '2026-11-25' })).toBe(estado)
  })

  it('no cambia de semana fuera de la ventana de 60 días', () => {
    const estado = conPerfil()
    expect(reducir(estado, { tipo: 'cambiar-semana', inicioSemana: '2028-01-01' })).toBe(estado)
  })

  it('conserva los datos del paciente al refrescar por conflicto', () => {
    let estado = hastaFormulario()
    estado = reducir(estado, { tipo: 'confirmar', datos: DATOS })
    expect(estado.paso).toBe('enviando')

    const error = { codigo: 'RN-TU-01', mensaje: 'Conflicto' }
    estado = reducir(estado, { tipo: 'conflicto', error })
    expect(estado.paso).toBe('conflicto')
    expect(estado.datos).toEqual(DATOS)
    expect(estado.horario).toBeUndefined()
    expect(estado.cargandoHorarios).toBe(true)

    estado = reducir(estado, {
      tipo: 'disponibilidad-inicio',
      fecha: '2026-11-25',
      conservarError: true,
    })
    estado = reducir(estado, {
      tipo: 'disponibilidad-cargada',
      disponibilidad: { ...DISPONIBILIDAD, horarios: [DISPONIBILIDAD.horarios[1]!] },
      conservarError: true,
    })

    expect(estado.datos).toEqual(DATOS)
    expect(estado.error).toEqual(error)
    expect(estado.horarios).toHaveLength(1)
    expect(estado.horario).toBeUndefined()
  })

  it('mapea errores de validación a campos sin perder los datos', () => {
    let estado = hastaFormulario()
    estado = reducir(estado, { tipo: 'confirmar', datos: DATOS })
    estado = reducir(estado, {
      tipo: 'validacion-servidor',
      datos: DATOS,
      errores: { email: 'El email no es válido' },
      error: { codigo: 'VALIDACION', mensaje: 'Datos inválidos' },
    })
    expect(estado.paso).toBe('completando-datos')
    expect(estado.errores.email).toBe('El email no es válido')
    expect(estado.datos).toEqual(DATOS)
  })

  it('mantiene los datos ante un error de red y permite reintentar', () => {
    let estado = hastaFormulario()
    estado = reducir(estado, { tipo: 'confirmar', datos: DATOS })
    const error = { codigo: 'ERROR_DESCONOCIDO', mensaje: 'Sin conexión' }
    estado = reducir(estado, { tipo: 'error-envio', error })
    expect(estado.paso).toBe('error-envio')
    expect(estado.datos).toEqual(DATOS)
    estado = reducir(estado, { tipo: 'reintentar-envio' })
    expect(estado.paso).toBe('enviando')
  })

  it('registra la reserva confirmada', () => {
    let estado = hastaFormulario()
    estado = reducir(estado, { tipo: 'confirmar', datos: DATOS })
    estado = reducir(estado, { tipo: 'exito', reserva: RESERVA })
    expect(estado.paso).toBe('exito')
    expect(estado.reserva).toEqual(RESERVA)
  })

  it('solo vuelve a datos desde un error de envío', () => {
    const estado = hastaFormulario()
    expect(reducir(estado, { tipo: 'volver-a-datos' })).toBe(estado)
  })
})

describe('esConflictoManual', () => {
  it.each(['RN-TU-01', 'RN-TU-11', 'RN-TU-12', 'RN-AG-05'])('detecta %s', (codigo) => {
    expect(esConflictoManual(codigo)).toBe(true)
  })

  it('no trata la validación como conflicto de horario', () => {
    expect(esConflictoManual('VALIDACION')).toBe(false)
  })
})
