import { describe, expect, it } from 'vitest'
import type { FetchLike } from '../../shared/api-client'
import { crearApiPublica } from './api-publica'
import type { SolicitudReserva } from './modelo'

const PERFIL = {
  slug: 'ana-gomez',
  nombre: 'Ana',
  apellido: 'Gómez',
  direccionConsultorio: 'Av. Siempreviva 742',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  fechaMinima: '2026-11-26',
  fechaMaxima: '2027-01-24',
  prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }],
}

const DISPONIBILIDAD = {
  fecha: '2026-11-27',
  prestacionId: 1,
  duracionMinutos: 30,
  horarios: [{ inicio: '2026-11-27T12:00:00Z', hora: '09:00' }],
}

const RESERVA = {
  inicio: '2026-11-27T12:00:00Z',
  fin: '2026-11-27T12:30:00Z',
  prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
  odontologo: { nombre: 'Ana', apellido: 'Gómez', direccionConsultorio: 'Av. Siempreviva 742' },
  email: 'ana@consultorio.com',
}

const SOLICITUD: SolicitudReserva = {
  prestacionId: 1,
  inicio: '2026-11-27T12:00:00Z',
  dni: '12345678',
  nombre: 'Ana',
  apellido: 'Gómez',
  telefono: '+5493511234567',
  email: 'ana@consultorio.com',
}

interface Pedido {
  url: string
  init: RequestInit | undefined
}

function respuesta(cuerpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } })
}

function apiQueResponde(cuerpo: unknown, status = 200) {
  const pedidos: Pedido[] = []
  const fetchFalso: FetchLike = async (url, init) => {
    pedidos.push({ url, init })
    return respuesta(cuerpo, status)
  }

  return { api: crearApiPublica({ fetch: fetchFalso }), pedidos }
}

describe('carga del perfil público', () => {
  it('pide el perfil por slug y descarta campos extra', async () => {
    const { api, pedidos } = apiQueResponde({ ...PERFIL, email: 'privado@consultorio.com', token: 'secreto' })

    const perfil = await api.perfil('ana-gomez')

    expect(pedidos[0]?.url).toBe('/api/public/ana-gomez')
    expect(perfil).toEqual(PERFIL)
    expect(perfil).not.toHaveProperty('email')
    expect(perfil).not.toHaveProperty('token')
  })

  it('codifica el slug en la URL', async () => {
    const { api, pedidos } = apiQueResponde(PERFIL)

    await api.perfil('ana gomez/uno')

    expect(pedidos[0]?.url).toBe('/api/public/ana%20gomez%2Funo')
  })

  it('normaliza una dirección ausente a null', async () => {
    const { api } = apiQueResponde({ ...PERFIL, direccionConsultorio: undefined })

    await expect(api.perfil('ana-gomez')).resolves.toMatchObject({ direccionConsultorio: null })
  })

  it('rechaza un perfil con fechas inválidas', async () => {
    const { api } = apiQueResponde({ ...PERFIL, fechaMinima: '2026-02-31' })

    await expect(api.perfil('ana-gomez')).rejects.toMatchObject({ codigo: 'RESPUESTA_INVALIDA' })
  })

  it('rechaza un perfil con prestaciones mal formadas', async () => {
    const { api } = apiQueResponde({ ...PERFIL, prestaciones: [{ id: 'uno', nombre: 'Consulta' }] })

    await expect(api.perfil('ana-gomez')).rejects.toMatchObject({ codigo: 'RESPUESTA_INVALIDA' })
  })
})

describe('disponibilidad pública', () => {
  it('envía prestacionId y fecha como query y normaliza los horarios', async () => {
    const { api, pedidos } = apiQueResponde({
      ...DISPONIBILIDAD,
      horarios: [{ ...DISPONIBILIDAD.horarios[0], fin: '2026-11-27T12:30:00Z', token: 'secreto' }],
    })

    const disponibilidad = await api.disponibilidad('ana-gomez', 1, '2026-11-27')

    expect(pedidos[0]?.url).toBe('/api/public/ana-gomez/disponibilidad?prestacionId=1&fecha=2026-11-27')
    expect(disponibilidad.horarios).toEqual([{ inicio: '2026-11-27T12:00:00Z', hora: '09:00' }])
  })

  it('rechaza una disponibilidad con horarios mal formados', async () => {
    const { api } = apiQueResponde({ ...DISPONIBILIDAD, horarios: [{ inicio: 1 }] })

    await expect(api.disponibilidad('ana-gomez', 1, '2026-11-27')).rejects.toMatchObject({
      codigo: 'RESPUESTA_INVALIDA',
    })
  })
})

describe('reserva pública', () => {
  it('envía el POST con el cuerpo exacto y normaliza la respuesta', async () => {
    const { api, pedidos } = apiQueResponde({ ...RESERVA, token: 'no-debe-salir' }, 201)

    const reserva = await api.reservar('ana-gomez', SOLICITUD)

    expect(pedidos[0]?.url).toBe('/api/public/ana-gomez/turnos')
    expect(pedidos[0]?.init?.method).toBe('POST')
    expect(pedidos[0]?.init?.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(JSON.parse(String(pedidos[0]?.init?.body))).toEqual(SOLICITUD)
    expect(reserva).toEqual(RESERVA)
    expect(reserva).not.toHaveProperty('token')
  })

  it('conserva el inicio con offset tal como llegó', async () => {
    const inicio = '2026-11-27T09:00:00-03:00'
    const { api } = apiQueResponde({ ...RESERVA, inicio }, 201)

    const reserva = await api.reservar('ana-gomez', { ...SOLICITUD, inicio })

    expect(reserva.inicio).toBe(inicio)
  })
})

describe('normalización de errores', () => {
  it('propaga un 409 con su código', async () => {
    const { api } = apiQueResponde({ codigo: 'RN-TU-01', mensaje: 'Horario tomado' }, 409)

    await expect(api.reservar('ana-gomez', SOLICITUD)).rejects.toMatchObject({
      codigo: 'RN-TU-01',
      mensaje: 'Horario tomado',
    })
  })

  it('propaga un 400 VALIDACION con sus detalles', async () => {
    const detalles = [{ campo: 'email', mensaje: 'Email inválido' }]
    const { api } = apiQueResponde({ codigo: 'VALIDACION', mensaje: 'Revisá los datos', detalles }, 400)

    await expect(api.reservar('ana-gomez', SOLICITUD)).rejects.toMatchObject({
      codigo: 'VALIDACION',
      detalles,
    })
  })

  it.each([
    [404, 'NO_ENCONTRADO'],
    [500, 'ERROR_INTERNO'],
  ])('propaga el %s como %s', async (status, codigo) => {
    const { api } = apiQueResponde({ codigo, mensaje: 'Error del servidor' }, status)

    await expect(api.perfil('ana-gomez')).rejects.toMatchObject({ codigo })
  })

  it('convierte una falla de red en SIN_CONEXION', async () => {
    const fetchRoto: FetchLike = async () => {
      throw new TypeError('Failed to fetch')
    }
    const api = crearApiPublica({ fetch: fetchRoto })

    await expect(api.perfil('ana-gomez')).rejects.toMatchObject({ codigo: 'SIN_CONEXION' })
  })

  it('convierte un JSON inesperado en RESPUESTA_INVALIDA', async () => {
    const fetchBasura: FetchLike = async () => new Response('no es json', { status: 200 })
    const api = crearApiPublica({ fetch: fetchBasura })

    await expect(api.perfil('ana-gomez')).rejects.toMatchObject({ codigo: 'RESPUESTA_INVALIDA' })
  })

  it('usa un error genérico ante un cuerpo de error sin forma de ErrorApi', async () => {
    const fetchRaro: FetchLike = async () => new Response(JSON.stringify({ otra: 'cosa' }), { status: 500 })
    const api = crearApiPublica({ fetch: fetchRaro })

    await expect(api.perfil('ana-gomez')).rejects.toMatchObject({ codigo: 'ERROR_DESCONOCIDO' })
  })
})
