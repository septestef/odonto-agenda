import { describe, expect, it, vi } from 'vitest'
import type { FetchLike } from '../../shared/api-client'
import { crearApiGestion, esTokenValido } from './api-gestion'

const TOKEN = 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-AbCdE'

const TURNO = {
  inicio: '2026-11-27T09:00:00-03:00',
  fin: '2026-11-27T09:30:00-03:00',
  estado: 'RESERVADO',
  prestacion: { id: 1, nombre: 'Consulta' },
  odontologo: { slug: 'demo', nombre: 'Ana', apellido: 'Pérez', direccionConsultorio: null },
  puedeCancelar: true,
  puedeReprogramar: true,
}

function respuesta(status: number, cuerpo?: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: cuerpo === undefined ? () => Promise.reject(new Error('sin cuerpo')) : () => Promise.resolve(cuerpo),
  } as unknown as Response
}

function fetchCon(...respuestas: Response[]) {
  const fetch = vi.fn<FetchLike>()
  for (const item of respuestas) {
    fetch.mockResolvedValueOnce(item)
  }
  return fetch
}

async function errorDe(promesa: Promise<unknown>): Promise<Record<string, unknown>> {
  try {
    await promesa
  } catch (error: unknown) {
    return error as Record<string, unknown>
  }
  throw new Error('Se esperaba un error')
}

describe('api de gestión del turno', () => {
  it('reconoce el formato del token de C-09', () => {
    expect(TOKEN).toHaveLength(43)
    expect(esTokenValido(TOKEN)).toBe(true)
    expect(esTokenValido(`${TOKEN}=`)).toBe(false)
    expect(esTokenValido('abc')).toBe(false)
    expect(esTokenValido(`${TOKEN.slice(0, 42)}/`)).toBe(false)
  })

  it('consulta el turno y descarta campos que no conoce', async () => {
    const fetch = fetchCon(respuesta(200, { ...TURNO, paciente: { dni: '30123456' }, tokenHash: 'x' }))
    const turno = await crearApiGestion({ fetch }).consultar(TOKEN)

    expect(fetch).toHaveBeenCalledWith(`/api/public/turnos/${TOKEN}`, { credentials: 'same-origin' })
    expect(turno).toEqual(TURNO)
    expect(JSON.stringify(turno)).not.toContain('30123456')
  })

  it('rechaza una respuesta con forma inesperada', async () => {
    const fetch = fetchCon(respuesta(200, { ...TURNO, estado: 'BORRADO' }))
    const error = await errorDe(crearApiGestion({ fetch }).consultar(TOKEN))

    expect(error['codigo']).toBe('RESPUESTA_INVALIDA')
  })

  it('no llama a la API con un token mal formado', async () => {
    const fetch = fetchCon()
    const error = await errorDe(crearApiGestion({ fetch }).consultar('no-es-un-token'))

    expect(error['codigo']).toBe('NO_ENCONTRADO')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('cancela con 204 sin cuerpo', async () => {
    const fetch = fetchCon(respuesta(204))
    await expect(crearApiGestion({ fetch }).cancelar(TOKEN)).resolves.toBeUndefined()

    expect(fetch).toHaveBeenCalledWith(`/api/public/turnos/${TOKEN}/cancelar`, {
      method: 'POST',
      credentials: 'same-origin',
    })
  })

  it('reprograma enviando el inicio con el mismo texto', async () => {
    const fetch = fetchCon(respuesta(204))
    await crearApiGestion({ fetch }).reprogramar(TOKEN, '2026-11-26T10:30:00-03:00')

    const init = fetch.mock.calls[0]?.[1]
    expect(fetch.mock.calls[0]?.[0]).toBe(`/api/public/turnos/${TOKEN}/reprogramar`)
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe('{"inicio":"2026-11-26T10:30:00-03:00"}')
  })

  it.each([
    [404, 'NO_ENCONTRADO'],
    [409, 'RN-TU-07'],
    [409, 'RN-SE-02'],
    [409, 'RN-SE-04'],
    [409, 'RN-TU-01'],
    [422, 'RN-TU-03'],
    [422, 'RN-TU-11'],
    [422, 'RN-AG-05'],
    [400, 'VALIDACION'],
    [400, 'SOLICITUD_INVALIDA'],
  ])('propaga el código del rechazo %i %s', async (status, codigo) => {
    const fetch = fetchCon(respuesta(status, { codigo, mensaje: 'Mensaje del servidor' }))
    const error = await errorDe(crearApiGestion({ fetch }).reprogramar(TOKEN, TURNO.inicio))

    expect(error['codigo']).toBe(codigo)
  })

  it('convierte un 500 o un 502 sin JSON en un error genérico, no en un 404', async () => {
    const fetch = fetchCon(respuesta(502), respuesta(500, { codigo: 'ERROR_INTERNO', mensaje: 'x' }))
    const api = crearApiGestion({ fetch })

    expect((await errorDe(api.consultar(TOKEN)))['codigo']).toBe('ERROR_DESCONOCIDO')
    expect((await errorDe(api.consultar(TOKEN)))['codigo']).toBe('ERROR_INTERNO')
  })

  it('convierte una falla de red en SIN_CONEXION sin exponer el token', async () => {
    const fetch = vi.fn<FetchLike>().mockRejectedValue(new TypeError(`Failed to fetch /api/public/turnos/${TOKEN}`))
    const error = await errorDe(crearApiGestion({ fetch }).cancelar(TOKEN))

    expect(error['codigo']).toBe('SIN_CONEXION')
    expect(JSON.stringify({ ...error, mensaje: error['mensaje'] })).not.toContain(TOKEN)
  })

  it('solo pide rutas bajo /api/public/turnos/', async () => {
    const fetch = fetchCon(respuesta(200, TURNO), respuesta(204), respuesta(204))
    const api = crearApiGestion({ fetch })
    await api.consultar(TOKEN)
    await api.cancelar(TOKEN)
    await api.reprogramar(TOKEN, TURNO.inicio)

    for (const llamada of fetch.mock.calls) {
      expect(llamada[0].startsWith('/api/public/turnos/')).toBe(true)
    }
  })
})
