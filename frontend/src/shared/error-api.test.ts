import { describe, expect, it } from 'vitest'
import { interpretarErrorApi } from './error-api'

const GENERICO = {
  codigo: 'ERROR_DESCONOCIDO',
  mensaje: 'Ocurrió un error inesperado. Probá de nuevo en unos minutos.',
}

describe('interpretarErrorApi', () => {
  it('devuelve el error cuando el cuerpo es válido', () => {
    const cuerpo = { codigo: 'RN-TU-01', mensaje: 'Ese horario acaba de ser tomado, elegí otro' }

    expect(interpretarErrorApi(cuerpo)).toEqual(cuerpo)
  })

  it('conserva los detalles cuando vienen en el cuerpo', () => {
    const detalles = [{ campo: 'nombre', mensaje: 'es obligatorio' }]

    const error = interpretarErrorApi({ codigo: 'VALIDACION', mensaje: 'Hay datos inválidos', detalles })

    expect(error.detalles).toEqual(detalles)
    expect(error.codigo).toBe('VALIDACION')
  })

  it('no agrega la clave detalles cuando el cuerpo no la trae', () => {
    const error = interpretarErrorApi({ codigo: 'NO_ENCONTRADO', mensaje: 'No encontramos lo que buscabas.' })

    expect('detalles' in error).toBe(false)
  })

  it.each([
    ['null', null],
    ['undefined', undefined],
    ['un string', 'Internal Server Error'],
    ['un número', 500],
    ['un arreglo', [{ codigo: 'X', mensaje: 'Y' }]],
  ])('devuelve el error genérico cuando el cuerpo es %s', (_nombre, cuerpo) => {
    expect(interpretarErrorApi(cuerpo)).toEqual(GENERICO)
  })

  it('devuelve el error genérico cuando falta codigo', () => {
    expect(interpretarErrorApi({ mensaje: 'algo' })).toEqual(GENERICO)
  })

  it('devuelve el error genérico cuando falta mensaje', () => {
    expect(interpretarErrorApi({ codigo: 'VALIDACION' })).toEqual(GENERICO)
  })

  it('devuelve el error genérico cuando codigo o mensaje no son strings', () => {
    expect(interpretarErrorApi({ codigo: 42, mensaje: 'algo' })).toEqual(GENERICO)
    expect(interpretarErrorApi({ codigo: 'X', mensaje: { texto: 'algo' } })).toEqual(GENERICO)
  })
})
