import { describe, expect, it } from 'vitest'
import type { DatosPaciente } from './modelo'
import { armarSolicitudManual } from './solicitud'

const DATOS: DatosPaciente = {
  dni: ' 30123456 ',
  nombre: ' Luis ',
  apellido: ' Ramírez ',
  telefono: ' +5493511234567 ',
  email: ' luis@ejemplo.com ',
}

describe('armado de la solicitud manual', () => {
  it('recorta los datos y conserva el inicio con offset', () => {
    const resultado = armarSolicitudManual(7, '2026-11-25T09:15:00-03:00', DATOS)

    expect(resultado).toEqual({
      ok: true,
      solicitud: {
        prestacionId: 7,
        inicio: '2026-11-25T09:15:00-03:00',
        dni: '30123456',
        nombre: 'Luis',
        apellido: 'Ramírez',
        telefono: '+5493511234567',
        email: 'luis@ejemplo.com',
      },
    })
  })

  it('no incluye un odontologoId: el dueño sale de la sesión', () => {
    const resultado = armarSolicitudManual(1, '2026-11-25T09:15:00-03:00', DATOS)
    expect(resultado.ok && resultado.solicitud).not.toHaveProperty('odontologoId')
  })

  it('devuelve errores por campo con el copy del paciente sin construir la solicitud', () => {
    const resultado = armarSolicitudManual(1, '2026-11-25T09:15:00-03:00', {
      ...DATOS,
      dni: '12.345.678',
      nombre: '',
      apellido: '',
      telefono: '3511234567',
      email: 'luis@',
    })

    expect(resultado.ok).toBe(false)
    if (!resultado.ok) {
      expect(resultado.errores).toEqual({
        dni: 'Ingresá el DNI del paciente sin puntos, 7 u 8 dígitos.',
        nombre: 'Ingresá el nombre del paciente.',
        apellido: 'Ingresá el apellido del paciente.',
        telefono: 'Ingresá el teléfono del paciente con código de país, por ejemplo +5493511234567.',
        email: 'Ingresá un email válido del paciente.',
      })
    }
  })

  it('rechaza una selección incompleta', () => {
    expect(() => armarSolicitudManual(0, '2026-11-25T09:15:00-03:00', DATOS)).toThrow()
    expect(() => armarSolicitudManual(1, '   ', DATOS)).toThrow()
  })
})
