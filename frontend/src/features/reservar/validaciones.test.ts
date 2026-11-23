import { describe, expect, it } from 'vitest'
import {
  erroresPorCampo,
  hayErrores,
  validarApellido,
  validarDatosPaciente,
  validarDni,
  validarEmail,
  validarNombre,
  validarTelefono,
} from './validaciones'

describe('validación de DNI', () => {
  it.each(['1234567', '12345678', ' 12345678 '])('acepta %s', (valor) => {
    expect(validarDni(valor)).toBeUndefined()
  })

  it.each(['123456', '123456789', '12.345.678', 'abcdefgh', ''])('rechaza %s', (valor) => {
    expect(validarDni(valor)).toBe('Ingresá tu DNI sin puntos, 7 u 8 dígitos.')
  })
})

describe('validación de teléfono E.164', () => {
  it.each(['+5493511234567', '+12345678', ' +5493511234567 '])('acepta %s', (valor) => {
    expect(validarTelefono(valor)).toBeUndefined()
  })

  it.each(['3511234567', '+0123456789', '+5493511234567890123', '', '5493511234567'])(
    'rechaza %s',
    (valor) => {
      expect(validarTelefono(valor)).toBe(
        'Ingresá el teléfono con código de país, por ejemplo +5493511234567.',
      )
    },
  )
})

describe('validación de email', () => {
  it.each(['ana@consultorio.com', 'ana.gomez+turnos@mail.com', ' a@b.com '])('acepta %s', (valor) => {
    expect(validarEmail(valor)).toBeUndefined()
  })

  it.each(['ana@', '@consultorio.com', 'ana consultorio.com', 'ana@@mail.com', 'a@b.c', ''])(
    'rechaza %s',
    (valor) => {
      expect(validarEmail(valor)).toBe('Ingresá un email válido.')
    },
  )
})

describe('validación de nombre y apellido', () => {
  it('acepta nombres y apellidos con contenido', () => {
    expect(validarNombre(' Ana ')).toBeUndefined()
    expect(validarApellido('Gómez')).toBeUndefined()
  })

  it('rechaza vacíos y solo espacios', () => {
    expect(validarNombre('   ')).toBe('Ingresá tu nombre.')
    expect(validarApellido('')).toBe('Ingresá tu apellido.')
  })

  it('rechaza textos de más de 100 caracteres', () => {
    expect(validarNombre('a'.repeat(101))).toBe('El nombre es demasiado largo.')
    expect(validarApellido('a'.repeat(101))).toBe('El apellido es demasiado largo.')
    expect(validarNombre('a'.repeat(100))).toBeUndefined()
  })
})

describe('validación del conjunto de datos', () => {
  it('devuelve un mensaje por cada campo inválido', () => {
    const errores = validarDatosPaciente({
      dni: '12.345.678',
      nombre: '',
      apellido: '',
      telefono: '3511234567',
      email: 'ana@',
    })

    expect(errores).toEqual({
      dni: 'Ingresá tu DNI sin puntos, 7 u 8 dígitos.',
      nombre: 'Ingresá tu nombre.',
      apellido: 'Ingresá tu apellido.',
      telefono: 'Ingresá el teléfono con código de país, por ejemplo +5493511234567.',
      email: 'Ingresá un email válido.',
    })
    expect(hayErrores(errores)).toBe(true)
  })

  it('no devuelve errores con datos válidos', () => {
    const errores = validarDatosPaciente({
      dni: '12345678',
      nombre: 'Ana',
      apellido: 'Gómez',
      telefono: '+5493511234567',
      email: 'ana@consultorio.com',
    })

    expect(errores).toEqual({})
    expect(hayErrores(errores)).toBe(false)
  })
})

describe('errores por campo del servidor', () => {
  it('traduce una lista de detalles de VALIDACION', () => {
    expect(
      erroresPorCampo([
        { campo: 'email', mensaje: 'El email ya está en uso.' },
        { campo: 'dni', mensaje: 'El DNI no es válido.' },
        { campo: 'otro', mensaje: 'Ignorado.' },
        'detalle inválido',
      ]),
    ).toEqual({
      email: 'El email ya está en uso.',
      dni: 'El DNI no es válido.',
    })
  })

  it('traduce un mapa de detalles con strings u objetos', () => {
    expect(
      erroresPorCampo({
        telefono: 'El teléfono no es válido.',
        nombre: { mensaje: 'El nombre es obligatorio.' },
        apellido: { otro: 'sin mensaje' },
        desconocido: 'Ignorado.',
      }),
    ).toEqual({
      telefono: 'El teléfono no es válido.',
      nombre: 'El nombre es obligatorio.',
    })
  })

  it('devuelve un mapa vacío ante detalles ausentes o con otra forma', () => {
    expect(erroresPorCampo(undefined)).toEqual({})
    expect(erroresPorCampo('texto')).toEqual({})
    expect(erroresPorCampo([{ campo: 1, mensaje: 'x' }])).toEqual({})
  })
})
