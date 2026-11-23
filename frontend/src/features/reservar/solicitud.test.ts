import { describe, expect, it } from 'vitest'
import { armarSolicitud } from './solicitud'

const DATOS_VALIDOS = {
  dni: ' 12345678 ',
  nombre: ' Ana ',
  apellido: ' Gómez ',
  telefono: ' +5493511234567 ',
  email: ' ana@consultorio.com ',
}

describe('armado de la solicitud de reserva', () => {
  it('arma el cuerpo con la prestación, el inicio original y los datos recortados', () => {
    const resultado = armarSolicitud(7, '2026-11-27T12:00:00Z', DATOS_VALIDOS)

    expect(resultado).toEqual({
      ok: true,
      solicitud: {
        prestacionId: 7,
        inicio: '2026-11-27T12:00:00Z',
        dni: '12345678',
        nombre: 'Ana',
        apellido: 'Gómez',
        telefono: '+5493511234567',
        email: 'ana@consultorio.com',
      },
    })
  })

  it('conserva el offset del inicio sin re-serializarlo', () => {
    const inicio = '2026-11-27T09:00:00-03:00'
    const resultado = armarSolicitud(3, inicio, DATOS_VALIDOS)

    expect(resultado.ok).toBe(true)
    if (resultado.ok) {
      expect(resultado.solicitud.inicio).toBe(inicio)
      expect(resultado.solicitud.inicio).not.toBe(new Date(inicio).toISOString())
    }
  })

  it('no recorta espacios internos de los datos', () => {
    const resultado = armarSolicitud(1, '2026-11-27T12:00:00Z', {
      ...DATOS_VALIDOS,
      nombre: '  Ana  María  ',
    })

    expect(resultado.ok).toBe(true)
    if (resultado.ok) {
      expect(resultado.solicitud.nombre).toBe('Ana  María')
    }
  })

  it('devuelve errores y no arma la solicitud con datos inválidos', () => {
    const resultado = armarSolicitud(1, '2026-11-27T12:00:00Z', {
      dni: '12.345.678',
      nombre: '',
      apellido: '',
      telefono: '3511234567',
      email: 'ana@',
    })

    expect(resultado.ok).toBe(false)
    if (!resultado.ok) {
      expect(resultado.errores).toMatchObject({
        dni: expect.any(String),
        nombre: expect.any(String),
        apellido: expect.any(String),
        telefono: expect.any(String),
        email: expect.any(String),
      })
    }
  })

  it.each([0, -1, 1.5])('rechaza la prestación inválida %s', (prestacionId) => {
    expect(() => armarSolicitud(prestacionId, '2026-11-27T12:00:00Z', DATOS_VALIDOS)).toThrow(
      'La selección del turno está incompleta',
    )
  })

  it('rechaza un inicio vacío', () => {
    expect(() => armarSolicitud(1, '   ', DATOS_VALIDOS)).toThrow('La selección del turno está incompleta')
  })
})
