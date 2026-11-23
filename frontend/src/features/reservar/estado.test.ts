import { describe, expect, it } from 'vitest'
import type { ErrorApi } from '../../shared/error-api'
import { esConflictoDeReserva, estadoInicialReserva, reducir } from './estado'
import type { DatosPaciente, DisponibilidadPublica, PerfilReserva } from './modelo'

const PERFIL: PerfilReserva = {
  slug: 'ana-gomez',
  nombre: 'Ana',
  apellido: 'Gómez',
  direccionConsultorio: 'Av. Siempreviva 742',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  fechaMinima: '2026-11-26',
  fechaMaxima: '2027-01-24',
  prestaciones: [
    { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
    { id: 2, nombre: 'Control', duracionMinutos: 15 },
  ],
}

const DATOS: DatosPaciente = {
  dni: '12345678',
  nombre: 'Ana',
  apellido: 'Gómez',
  telefono: '+5493511234567',
  email: 'ana@consultorio.com',
}

const DISPONIBILIDAD: DisponibilidadPublica = {
  fecha: '2026-11-27',
  prestacionId: 1,
  duracionMinutos: 30,
  horarios: [
    { inicio: '2026-11-27T12:00:00Z', hora: '09:00' },
    { inicio: '2026-11-27T12:15:00Z', hora: '09:15' },
  ],
}

function conPerfil() {
  return reducir(estadoInicialReserva(), { tipo: 'perfil-cargado', perfil: PERFIL })
}

function conHorarios() {
  const conPrestacion = reducir(conPerfil(), { tipo: 'elegir-prestacion', prestacionId: 1, fecha: '2026-11-27' })
  return reducir(conPrestacion, { tipo: 'disponibilidad-cargada', disponibilidad: DISPONIBILIDAD })
}

function conDatos() {
  const conHorario = reducir(conHorarios(), {
    tipo: 'elegir-horario',
    horario: DISPONIBILIDAD.horarios[0]!,
  })
  return reducir(conHorario, { tipo: 'ir-a-datos' })
}

describe('estado inicial y carga del perfil', () => {
  it('arranca cargando el perfil y sin selección', () => {
    expect(estadoInicialReserva()).toMatchObject({
      paso: 'cargando-perfil',
      horarios: [],
      cargandoHorarios: false,
      datos: { dni: '', nombre: '', apellido: '', telefono: '', email: '' },
      errores: {},
    })
  })

  it('pasa a elegir prestación al cargar el perfil', () => {
    const estado = conPerfil()

    expect(estado).toMatchObject({
      paso: 'eligiendo-prestacion',
      perfil: PERFIL,
      inicioSemana: PERFIL.fechaMinima,
    })
  })

  it('muestra error de perfil y permite reintentar', () => {
    const error: ErrorApi = { codigo: 'NO_ENCONTRADO', mensaje: 'No encontrado' }
    const conError = reducir(estadoInicialReserva(), { tipo: 'perfil-error', error })

    expect(conError).toMatchObject({ paso: 'error-perfil', error })
    expect(reducir(conError, { tipo: 'iniciar' }).paso).toBe('cargando-perfil')
  })
})

describe('selección de prestación, fecha y horario', () => {
  it('ignora una prestación inexistente', () => {
    const estado = conPerfil()

    expect(reducir(estado, { tipo: 'elegir-prestacion', prestacionId: 99, fecha: '2026-11-27' })).toBe(estado)
  })

  it('elige la prestación y deja la disponibilidad cargando', () => {
    const estado = reducir(conPerfil(), {
      tipo: 'elegir-prestacion',
      prestacionId: 1,
      fecha: '2026-11-27',
    })

    expect(estado).toMatchObject({
      paso: 'eligiendo-horario',
      prestacionId: 1,
      duracionMinutos: 30,
      fecha: '2026-11-27',
      horarios: [],
      cargandoHorarios: true,
      horario: undefined,
    })
  })

  it('pasa a sin-horarios cuando la disponibilidad viene vacía', () => {
    const conPrestacion = reducir(conPerfil(), {
      tipo: 'elegir-prestacion',
      prestacionId: 1,
      fecha: '2026-11-27',
    })
    const estado = reducir(conPrestacion, {
      tipo: 'disponibilidad-cargada',
      disponibilidad: { ...DISPONIBILIDAD, horarios: [] },
    })

    expect(estado).toMatchObject({ paso: 'sin-horarios', horarios: [], cargandoHorarios: false })
  })

  it('conserva los horarios cargados y limpia el seleccionado', () => {
    const estado = conHorarios()

    expect(estado).toMatchObject({
      paso: 'eligiendo-horario',
      horarios: DISPONIBILIDAD.horarios,
      cargandoHorarios: false,
      horario: undefined,
    })
  })

  it('ignora un horario que no está en la lista', () => {
    const estado = conHorarios()

    expect(
      reducir(estado, { tipo: 'elegir-horario', horario: { inicio: 'otro', hora: '23:00' } }),
    ).toBe(estado)
  })

  it('elige un horario disponible', () => {
    const estado = reducir(conHorarios(), { tipo: 'elegir-horario', horario: DISPONIBILIDAD.horarios[1]! })

    expect(estado.horario).toEqual(DISPONIBILIDAD.horarios[1])
  })

  it('cambia de semana y limpia fecha y horario', () => {
    const conSeleccion = reducir(conHorarios(), { tipo: 'elegir-horario', horario: DISPONIBILIDAD.horarios[0]! })
    const estado = reducir(conSeleccion, { tipo: 'cambiar-semana', inicioSemana: '2026-12-03' })

    expect(estado).toMatchObject({
      inicioSemana: '2026-12-03',
      fecha: undefined,
      horarios: [],
      horario: undefined,
      prestacionId: 1,
    })
  })

  it('no cambia de semana fuera de la ventana reservable', () => {
    const estado = conPerfil()

    expect(reducir(estado, { tipo: 'cambiar-semana', inicioSemana: '2026-01-01' })).toBe(estado)
    expect(reducir(estado, { tipo: 'cambiar-semana', inicioSemana: '2027-02-01' })).toBe(estado)
  })

  it('reinicia la disponibilidad al pedir otro día', () => {
    const conSeleccion = reducir(conHorarios(), { tipo: 'elegir-horario', horario: DISPONIBILIDAD.horarios[0]! })
    const estado = reducir(conSeleccion, { tipo: 'disponibilidad-inicio', fecha: '2026-11-28' })

    expect(estado).toMatchObject({
      paso: 'eligiendo-horario',
      fecha: '2026-11-28',
      horarios: [],
      cargandoHorarios: true,
      horario: undefined,
    })
  })

  it('registra el error de disponibilidad y permite reintentar', () => {
    const error: ErrorApi = { codigo: 'SIN_CONEXION', mensaje: 'Sin conexión' }
    const estado = reducir(conHorarios(), { tipo: 'disponibilidad-error', error })

    expect(estado).toMatchObject({ paso: 'eligiendo-horario', horarios: [], error })
  })
})

describe('paso de datos y confirmación', () => {
  it('no avanza a datos sin horario elegido', () => {
    const estado = conHorarios()

    expect(reducir(estado, { tipo: 'ir-a-datos' })).toBe(estado)
  })

  it('avanza a completar datos con el horario elegido', () => {
    expect(conDatos().paso).toBe('completando-datos')
  })

  it('guarda errores locales solo en el paso de datos', () => {
    const estado = conHorarios()
    const errores = { dni: 'Ingresá tu DNI sin puntos, 7 u 8 dígitos.' }

    expect(reducir(estado, { tipo: 'validacion-local', datos: DATOS, errores })).toBe(estado)

    const conErrores = reducir(conDatos(), { tipo: 'validacion-local', datos: DATOS, errores })
    expect(conErrores).toMatchObject({ paso: 'completando-datos', datos: DATOS, errores })
  })

  it('pasa a enviando al confirmar desde datos', () => {
    const estado = reducir(conDatos(), { tipo: 'confirmar', datos: DATOS })

    expect(estado).toMatchObject({ paso: 'enviando', datos: DATOS, errores: {}, error: undefined })
  })

  it('bloquea una segunda confirmación mientras está enviando', () => {
    const enviando = reducir(conDatos(), { tipo: 'confirmar', datos: DATOS })

    expect(reducir(enviando, { tipo: 'confirmar', datos: DATOS })).toBe(enviando)
  })

  it('solo acepta éxito desde enviando', () => {
    const reserva = {
      inicio: '2026-11-27T12:00:00Z',
      fin: '2026-11-27T12:30:00Z',
      prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
      odontologo: { nombre: 'Ana', apellido: 'Gómez', direccionConsultorio: null },
      email: 'ana@consultorio.com',
    }

    const completando = conDatos()
    expect(reducir(completando, { tipo: 'exito', reserva })).toBe(completando)
    const exito = reducir(reducir(completando, { tipo: 'confirmar', datos: DATOS }), {
      tipo: 'exito',
      reserva,
    })
    expect(exito).toMatchObject({ paso: 'exito', reserva })
  })

  it('vuelve a los datos desde un error de envío sin perder lo ingresado', () => {
    const error: ErrorApi = { codigo: 'ERROR_INTERNO', mensaje: 'Error' }
    const enviando = reducir(conDatos(), { tipo: 'confirmar', datos: DATOS })
    const conError = reducir(enviando, { tipo: 'error-envio', error })

    expect(conError).toMatchObject({ paso: 'error-envio', datos: DATOS, error })
    expect(reducir(conError, { tipo: 'reintentar-envio' })).toMatchObject({ paso: 'enviando', datos: DATOS })
    expect(reducir(conError, { tipo: 'volver-a-datos' })).toMatchObject({
      paso: 'completando-datos',
      datos: DATOS,
      error: undefined,
    })
  })
})

describe('conflicto de reserva', () => {
  it('conserva los datos, limpia el horario y pide refrescar la disponibilidad', () => {
    const enviando = reducir(conDatos(), { tipo: 'confirmar', datos: DATOS })
    const error: ErrorApi = { codigo: 'RN-TU-01', mensaje: 'Horario tomado' }
    const estado = reducir(enviando, { tipo: 'conflicto', error })

    expect(estado).toMatchObject({
      paso: 'conflicto',
      datos: DATOS,
      horario: undefined,
      cargandoHorarios: true,
      error,
      prestacionId: 1,
      fecha: '2026-11-27',
    })
  })

  it('vuelve al paso de horarios conservando los datos', () => {
    const enviando = reducir(conDatos(), { tipo: 'confirmar', datos: DATOS })
    const conflicto = reducir(enviando, {
      tipo: 'conflicto',
      error: { codigo: 'RN-PA-03', mensaje: 'Límite' },
    })
    const estado = reducir(conflicto, { tipo: 'volver-a-horario', datos: DATOS })

    expect(estado).toMatchObject({ paso: 'eligiendo-horario', datos: DATOS, errores: {}, error: undefined })
  })

  it('reinicia el flujo al elegir prestación conservando el perfil', () => {
    const exito = reducir(
      reducir(conDatos(), { tipo: 'confirmar', datos: DATOS }),
      {
        tipo: 'exito',
        reserva: {
          inicio: '2026-11-27T12:00:00Z',
          fin: '2026-11-27T12:30:00Z',
          prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
          odontologo: { nombre: 'Ana', apellido: 'Gómez', direccionConsultorio: null },
          email: 'ana@consultorio.com',
        },
      },
    )
    const estado = reducir(exito, { tipo: 'reiniciar' })

    expect(estado).toMatchObject({
      paso: 'eligiendo-prestacion',
      perfil: PERFIL,
      datos: { dni: '', nombre: '', apellido: '', telefono: '', email: '' },
    })
  })
})

describe('códigos considerados conflicto de reserva', () => {
  it.each(['RN-TU-01', 'RN-PA-03', 'RN-TU-03', 'RN-TU-11', 'RN-AG-05'])('reconoce %s', (codigo) => {
    expect(esConflictoDeReserva(codigo)).toBe(true)
  })

  it.each(['VALIDACION', 'NO_ENCONTRADO', 'ERROR_INTERNO', 'SIN_CONEXION'])('no reconoce %s', (codigo) => {
    expect(esConflictoDeReserva(codigo)).toBe(false)
  })
})
