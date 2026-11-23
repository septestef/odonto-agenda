import { describe, expect, it } from 'vitest'
import type { PerfilReserva } from '../reservar/modelo'
import { estadoInicialGestion, reaccionARechazo, reducir } from './estado'
import type { AccionGestion, EstadoGestion, TurnoGestion } from './modelo'

const TURNO: TurnoGestion = {
  inicio: '2026-11-27T09:00:00-03:00',
  fin: '2026-11-27T09:30:00-03:00',
  estado: 'RESERVADO',
  prestacion: { id: 1, nombre: 'Consulta' },
  odontologo: { slug: 'demo', nombre: 'Ana', apellido: 'Pérez', direccionConsultorio: 'Av. Colón 1234' },
  puedeCancelar: true,
  puedeReprogramar: true,
}

const CANCELADO: TurnoGestion = { ...TURNO, estado: 'CANCELADO', puedeCancelar: false, puedeReprogramar: false }

const PERFIL: PerfilReserva = {
  slug: 'demo',
  nombre: 'Ana',
  apellido: 'Pérez',
  direccionConsultorio: null,
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  fechaMinima: '2026-11-24',
  fechaMaxima: '2027-01-21',
  prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }],
}

const HORARIO = { inicio: '2026-11-26T10:30:00-03:00', hora: '10:30' }

function aplicar(estado: EstadoGestion, ...acciones: AccionGestion[]): EstadoGestion {
  return acciones.reduce(reducir, estado)
}

function activo(turno: TurnoGestion = TURNO): EstadoGestion {
  return aplicar(estadoInicialGestion(), { tipo: 'turno-cargado', turno })
}

function eligiendo(): EstadoGestion {
  return aplicar(
    activo(),
    { tipo: 'abrir-reprogramacion' },
    { tipo: 'perfil-cargado', perfil: PERFIL, fecha: '2026-11-26', inicioSemana: '2026-11-24' },
    { tipo: 'disponibilidad-inicio', fecha: '2026-11-26' },
    { tipo: 'disponibilidad-cargada', fecha: '2026-11-26', horarios: [HORARIO] },
    { tipo: 'elegir-horario', horario: HORARIO },
  )
}

const error = (codigo: string) => ({ codigo, mensaje: 'Mensaje del servidor' })

describe('estado de la gestión del turno', () => {
  it('muestra el turno activo solo si el servidor permite alguna acción', () => {
    expect(activo().vista).toBe('activo')
    expect(activo(CANCELADO).vista).toBe('solo-lectura')
    expect(activo({ ...TURNO, puedeReprogramar: false }).vista).toBe('activo')
  })

  it('distingue un link inválido de un error de carga', () => {
    const inicial = estadoInicialGestion()
    expect(reducir(inicial, { tipo: 'turno-error', error: error('NO_ENCONTRADO') }).vista).toBe('error-link')
    expect(reducir(inicial, { tipo: 'turno-error', error: error('SIN_CONEXION') }).vista).toBe('error-carga')
    expect(reducir(inicial, { tipo: 'turno-error', error: error('ERROR_DESCONOCIDO') }).vista).toBe('error-carga')
  })

  it('cancela con confirmación, relee y queda en solo lectura', () => {
    const confirmando = reducir(activo(), { tipo: 'abrir-cancelacion' })
    expect(confirmando.vista).toBe('confirmar-cancelacion')

    const enviando = reducir(confirmando, { tipo: 'enviar-cancelacion' })
    expect(enviando.enviando).toBe(true)

    const releyendo = reducir(enviando, { tipo: 'cancelacion-ok' })
    expect(releyendo).toMatchObject({ vista: 'cargando', resultado: { tipo: 'cancelado' } })

    const final = reducir(releyendo, { tipo: 'turno-cargado', turno: CANCELADO })
    expect(final).toMatchObject({ vista: 'solo-lectura', resultado: { tipo: 'cancelado' } })
  })

  it('al arrepentirse vuelve al turno y pide devolver el foco a "Cancelar turno"', () => {
    const cerrado = aplicar(activo(), { tipo: 'abrir-cancelacion' }, { tipo: 'cerrar-cancelacion' })
    expect(cerrado).toMatchObject({ vista: 'activo', enfocarCancelar: true })
    expect(reducir(cerrado, { tipo: 'cargar' }).enfocarCancelar).toBe(false)
  })

  it('no abre acciones que el servidor no permite', () => {
    const sinCambio = activo({ ...TURNO, puedeReprogramar: false })
    expect(reducir(sinCambio, { tipo: 'abrir-reprogramacion' })).toBe(sinCambio)
    const soloLectura = activo(CANCELADO)
    expect(reducir(soloLectura, { tipo: 'abrir-cancelacion' })).toBe(soloLectura)
  })

  it('bloquea el doble envío de la cancelación y de la reprogramación', () => {
    const cancelando = aplicar(activo(), { tipo: 'abrir-cancelacion' }, { tipo: 'enviar-cancelacion' })
    expect(reducir(cancelando, { tipo: 'enviar-cancelacion' })).toBe(cancelando)
    expect(reducir(cancelando, { tipo: 'cerrar-cancelacion' })).toBe(cancelando)

    const cambiando = reducir(eligiendo(), { tipo: 'enviar-reprogramacion' })
    expect(cambiando.enviando).toBe(true)
    expect(reducir(cambiando, { tipo: 'enviar-reprogramacion' })).toBe(cambiando)
    expect(reducir(cambiando, { tipo: 'elegir-horario', horario: HORARIO })).toBe(cambiando)
  })

  it('no confirma un cambio sin horario elegido', () => {
    const sinHorario = aplicar(
      activo(),
      { tipo: 'abrir-reprogramacion' },
      { tipo: 'perfil-cargado', perfil: PERFIL, fecha: '2026-11-26', inicioSemana: '2026-11-24' },
    )
    expect(reducir(sinHorario, { tipo: 'enviar-reprogramacion' })).toBe(sinHorario)
  })

  it('reprograma, relee y recuerda el horario elegido para el aviso', () => {
    const final = aplicar(
      eligiendo(),
      { tipo: 'enviar-reprogramacion' },
      { tipo: 'reprogramacion-ok', inicio: HORARIO.inicio },
      { tipo: 'turno-cargado', turno: CANCELADO },
    )
    expect(final).toMatchObject({
      vista: 'solo-lectura',
      resultado: { tipo: 'reprogramado', inicio: HORARIO.inicio },
      horarios: [],
    })
    expect(final.horario).toBeUndefined()
  })

  it('ante un horario tomado avisa, limpia la selección y conserva día y semana al refrescar', () => {
    const rechazado = aplicar(
      eligiendo(),
      { tipo: 'enviar-reprogramacion' },
      { tipo: 'horario-rechazado', error: error('RN-TU-01') },
    )
    expect(rechazado).toMatchObject({ aviso: 'horario-tomado', fecha: '2026-11-26', inicioSemana: '2026-11-24' })
    expect(rechazado.horario).toBeUndefined()
    expect(rechazado.enviando).toBe(false)

    const refrescando = reducir(rechazado, { tipo: 'disponibilidad-inicio', fecha: '2026-11-26' })
    expect(refrescando.aviso).toBe('horario-tomado')
    const refrescado = reducir(refrescando, { tipo: 'disponibilidad-cargada', fecha: '2026-11-26', horarios: [] })
    expect(refrescado.aviso).toBe('horario-tomado')

    const otroDia = reducir(refrescado, { tipo: 'disponibilidad-inicio', fecha: '2026-11-30' })
    expect(otroDia.aviso).toBeUndefined()
  })

  it('un 422 de agenda avisa que el horario ya no está disponible', () => {
    const rechazado = reducir(eligiendo(), { tipo: 'horario-rechazado', error: error('RN-AG-05') })
    expect(rechazado.aviso).toBe('horario-no-disponible')
  })

  it('un turno que ya no se puede modificar se relee y queda con el aviso de rechazo', () => {
    const rechazado = aplicar(
      activo(),
      { tipo: 'abrir-cancelacion' },
      { tipo: 'enviar-cancelacion' },
      { tipo: 'accion-rechazada', error: error('RN-TU-07') },
    )
    expect(rechazado).toMatchObject({ vista: 'cargando', resultado: { tipo: 'rechazado' } })

    const final = reducir(rechazado, { tipo: 'turno-cargado', turno: { ...TURNO, puedeCancelar: false, puedeReprogramar: false } })
    expect(final).toMatchObject({ vista: 'solo-lectura', resultado: { tipo: 'rechazado' } })
  })

  it('RN-SE-04 relee el turno y conserva el aviso para la variante sin cambio', () => {
    const final = aplicar(
      eligiendo(),
      { tipo: 'enviar-reprogramacion' },
      { tipo: 'no-reprogramable', error: error('RN-SE-04') },
      { tipo: 'turno-cargado', turno: { ...TURNO, puedeReprogramar: false } },
    )
    expect(final).toMatchObject({ vista: 'activo', aviso: 'no-reprogramable' })
  })

  it('un error de red conserva la selección y no reintenta solo', () => {
    const fallo = aplicar(
      eligiendo(),
      { tipo: 'enviar-reprogramacion' },
      { tipo: 'error-envio', error: error('SIN_CONEXION'), accion: 'reprogramar' },
    )
    expect(fallo).toMatchObject({ vista: 'reprogramando', enviando: false, aviso: 'sin-conexion-reprogramar' })
    expect(fallo.horario).toEqual(HORARIO)

    const cancelacion = aplicar(
      activo(),
      { tipo: 'abrir-cancelacion' },
      { tipo: 'enviar-cancelacion' },
      { tipo: 'error-envio', error: error('ERROR_INTERNO'), accion: 'cancelar' },
    )
    expect(cancelacion).toMatchObject({ vista: 'confirmar-cancelacion', enviando: false, aviso: 'sin-conexion-cancelar' })
  })

  it('ignora una disponibilidad que llega tarde para otro día', () => {
    const estado = reducir(eligiendo(), { tipo: 'disponibilidad-inicio', fecha: '2026-11-30' })
    const tarde = reducir(estado, { tipo: 'disponibilidad-cargada', fecha: '2026-11-26', horarios: [HORARIO] })
    expect(tarde).toBe(estado)
  })

  it('volver al turno descarta la selección', () => {
    const vuelto = reducir(eligiendo(), { tipo: 'volver-al-turno' })
    expect(vuelto.vista).toBe('activo')
    expect(vuelto.horario).toBeUndefined()
    expect(vuelto.fecha).toBeUndefined()
  })

  it.each([
    ['NO_ENCONTRADO', 'cancelar', 'accion-rechazada'],
    ['RN-TU-07', 'cancelar', 'accion-rechazada'],
    ['RN-SE-02', 'reprogramar', 'accion-rechazada'],
    ['RN-SE-04', 'reprogramar', 'no-reprogramable'],
    ['RN-TU-01', 'reprogramar', 'horario-rechazado'],
    ['RN-TU-03', 'reprogramar', 'horario-rechazado'],
    ['RN-TU-11', 'reprogramar', 'horario-rechazado'],
    ['RN-AG-05', 'reprogramar', 'horario-rechazado'],
    ['VALIDACION', 'reprogramar', 'horario-rechazado'],
    ['SOLICITUD_INVALIDA', 'reprogramar', 'horario-rechazado'],
    ['SIN_CONEXION', 'reprogramar', 'error-envio'],
    ['ERROR_INTERNO', 'cancelar', 'error-envio'],
    ['RN-TU-01', 'cancelar', 'error-envio'],
  ] as const)('clasifica %s al %s como %s', (codigo, accion, reaccion) => {
    expect(reaccionARechazo(codigo, accion)).toBe(reaccion)
  })
})
