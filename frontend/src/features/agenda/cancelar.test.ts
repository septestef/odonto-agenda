import { describe, expect, it, vi } from 'vitest'
import type { ClienteApi } from '../../shared/api-client'
import { cancelarTurno, interpretarCancelacion } from './cancelar'

function clienteQueRegistra(respuesta: unknown): { cliente: ClienteApi; pedir: ReturnType<typeof vi.fn> } {
  const pedir = vi.fn(async () => respuesta)
  return {
    cliente: { pedir, suscribirSesionVencida: () => () => {} } as unknown as ClienteApi,
    pedir,
  }
}

function errorApi(codigo: string): Error & { codigo: string } {
  return Object.assign(new Error('falló'), { codigo })
}

describe('POST de cancelación', () => {
  it('envía la ruta por id sin cuerpo y sin reintentos', async () => {
    const { cliente, pedir } = clienteQueRegistra(undefined)
    await cancelarTurno(cliente, 17)

    expect(pedir).toHaveBeenCalledTimes(1)
    expect(pedir).toHaveBeenCalledWith('POST', '/api/turnos/17/cancelar')
    expect(pedir.mock.calls[0]).toHaveLength(2)
  })

  it('considera éxito el 204 repetido', () => {
    expect(interpretarCancelacion(undefined)).toBe('cancelado')
  })
})

describe('mapeo de respuestas de cancelación', () => {
  it('traduce los códigos del contrato sin reenviar la solicitud', () => {
    expect(interpretarCancelacion(errorApi('RN-TU-07'))).toBe('ya-empezo')
    expect(interpretarCancelacion(errorApi('NO_ENCONTRADO'))).toBe('cambio')
    expect(interpretarCancelacion(errorApi('RN-TU-01'))).toBe('cambio')
    expect(interpretarCancelacion(errorApi('ERROR_INTERNO'))).toBe('error')
    expect(interpretarCancelacion(errorApi('SIN_CONEXION'))).toBe('error')
    expect(interpretarCancelacion(new Error('otra'))).toBe('error')
    expect(interpretarCancelacion({})).toBe('error')
  })

  it('deja la sesión vencida al manejo del api-client', () => {
    expect(interpretarCancelacion(errorApi('NO_AUTENTICADO'))).toBe('sesion-vencida')
    expect(interpretarCancelacion(errorApi('CSRF_INVALIDO'))).toBe('sesion-vencida')
  })
})
