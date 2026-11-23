import { describe, expect, it, vi } from 'vitest'
import { crearClienteApi } from '../../shared/api-client'
import type { FetchLike } from '../../shared/api-client'
import { consultarWhatsApp } from './api'

const url = 'https://wa.me/12025550142?text=Direcci%C3%B3n%3A%20A%26B%2B%23%25'

function clienteCon(cuerpo: unknown, status = 200) {
  const fetch = vi.fn<FetchLike>(async () =>
    Response.json(cuerpo, { status }))
  const sesion = vi.fn()
  const cliente = crearClienteApi({ fetch, leerCookie: () => 'no-se-debe-leer', notificarSesionVencida: sesion })
  return { cliente, fetch, sesion }
}

describe('cliente WhatsApp de agenda', () => {
  it('consulta por GET de sesión sin CSRF y conserva la codificación del mensaje', async () => {
    const { cliente, fetch } = clienteCon({ url })
    expect(await consultarWhatsApp(cliente, 3)).toEqual({ tipo: 'lista', url })
    expect(fetch).toHaveBeenCalledOnce()
    const [ruta, init] = fetch.mock.calls[0] ?? []
    expect(ruta).toBe('/api/turnos/3/whatsapp')
    expect(init).toMatchObject({ method: 'GET', credentials: 'same-origin' })
    expect(init?.body).toBeUndefined()
    expect(new Headers(init?.headers).has('X-XSRF-TOKEN')).toBe(false)
  })

  it.each([
    ['nulo', null], ['array', [{ url }]], ['sin URL', {}], ['no string', { url: 3 }],
    ['campo extra', { url, extra: 'no mostrar' }],
    ['http', { url: url.replace('https:', 'http:') }],
    ['subdominio', { url: url.replace('wa.me', 'wa.me.example.invalid') }],
    ['credenciales', { url: url.replace('wa.me', 'otro@wa.me') }],
    ['puerto', { url: url.replace('wa.me', 'wa.me:444') }],
    ['puerto explícito', { url: url.replace('wa.me', 'wa.me:443') }],
    ['fragmento vacío', { url: `${url}#` }],
    ['espacio de borde', { url: ` ${url}` }],
    ['salto final', { url: `${url}\n` }],
    ['URL normalizada', { url: url.replace('/120', '/a/../120') }],
    ['URL relativa', { url: url.slice(6) }],
    ['forma nacional', { url: 'https://wa.me/012025550142?text=hola' }],
    ['falta text', { url: url.split('?')[0] }],
    ['text duplicado', { url: `${url}&text=otro` }],
    ['otro parámetro', { url: `${url}&otro=1` }],
    ['menor que crudo', { url: `${url}<` }],
    ['comilla doble cruda', { url: `${url}"` }],
    ['apóstrofo crudo', { url: `${url}'` }],
    ['igual crudo', { url: `${url}=otro` }],
    ['más crudo', { url: `${url}+otro` }],
    ['text en blanco', { url: 'https://wa.me/12025550142?text=%20%20' }],
    ['escape inválido', { url: `${url}%QZ` }],
    ['UTF-8 inválido', { url: `${url}%FF` }],
  ])('no entrega URL con payload inválido (%s)', async (_caso, cuerpo) => {
    expect(await consultarWhatsApp(clienteCon(cuerpo).cliente, 3)).toEqual({ tipo: 'error' })
  })

  it('conserva los caracteres reservados codificados como los entrega el backend', async () => {
    const urlCodificada = 'https://wa.me/12025550142?text=Direcci%C3%B3n%3A%20%3C%22A%27%22%3E%20%3D%20%2B%26%23%25'
    expect(await consultarWhatsApp(clienteCon({ url: urlCodificada }).cliente, 3))
      .toEqual({ tipo: 'lista', url: urlCodificada })
  })

  it('acepta sin reescribir la respuesta completa de la fixture de integración del backend', async () => {
    const urlBackend = 'https://wa.me/5493511234567?text=Te%20recordamos%20tu%20turno%20del%2026%2F11%2F2026'
      + '%20a%20las%2022%3A30%20para%20Limpieza%20%26%20revisi%C3%B3n.'
      + '%20Direcci%C3%B3n%3A%20Av.%20Col%C3%B3n%20%2B%20%2320%25.'
    expect(await consultarWhatsApp(clienteCon({ url: urlBackend }).cliente, 3))
      .toEqual({ tipo: 'lista', url: urlBackend })
  })

  it.each([
    [401, 'NO_AUTENTICADO', 'No mostrar el mensaje crudo.', 'sesion-vencida'],
    [403, 'ACCESO_DENEGADO', 'No mostrar el mensaje crudo.', 'sin-acceso'],
    [404, 'NO_ENCONTRADO', 'No mostrar el mensaje crudo.', 'cambio'],
    [409, 'RN-NO-06', 'No mostrar el mensaje crudo.', 'cambio'],
    [422, 'RN-NO-07', 'El paciente no tiene un teléfono internacional válido.', 'telefono'],
    [422, 'RN-NO-07', 'Falta la dirección del consultorio.', 'direccion'],
    [422, 'RN-NO-07', 'No mostrar el mensaje crudo.', 'error'],
    [500, 'ERROR_INTERNO', 'No mostrar el mensaje crudo.', 'error'],
  ])('traduce la respuesta %i (%s) sin propagar datos crudos', async (status, codigo, mensaje, tipo) => {
    const { cliente, sesion } = clienteCon({ codigo, mensaje, detalles: { privado: 'no mostrar' } }, status)
    expect(await consultarWhatsApp(cliente, 3)).toEqual({ tipo })
    expect(sesion).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
  })

  it('traduce falla de red sin propagar el error de transporte', async () => {
    const cliente = crearClienteApi({ fetch: async () => { throw new Error('no mostrar') } })
    expect(await consultarWhatsApp(cliente, 3)).toEqual({ tipo: 'error' })
  })

  it('traduce una respuesta no JSON', async () => {
    const cliente = crearClienteApi({ fetch: async () => new Response('no mostrar') })
    expect(await consultarWhatsApp(cliente, 3)).toEqual({ tipo: 'error' })
  })
})
