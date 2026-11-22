import { describe, expect, it } from 'vitest'
import { traducirError } from './errores'

function errorApi(codigo: string, mensaje: string, detalles?: unknown): Error {
  return Object.assign(new Error(mensaje), {
    codigo,
    mensaje,
    ...(detalles === undefined ? {} : { detalles }),
  })
}

describe('traducción de errores del backend', () => {
  it('asocia RN-AG-01 con la franja indicada en detalles', () => {
    const resultado = traducirError(
      errorApi('RN-AG-01', 'Las franjas del mismo día no pueden solaparse', [
        { campo: 'franjas[1]', mensaje: 'Se superpone con la otra franja.' },
      ]),
    )

    expect(resultado.alerta).toBe('Las franjas del mismo día no pueden solaparse')
    expect(resultado.erroresCampo['franjas[1]']).toBe('Se superpone con la otra franja.')
    expect(resultado.primero).toBe('franjas[1]')
  })

  it('asocia RN-AG-03 con duracionMinutos', () => {
    const resultado = traducirError(
      errorApi('RN-AG-03', 'La duración tiene que ser un múltiplo de 15 minutos.', [
        { campo: 'duracionMinutos', mensaje: 'La duración tiene que ser un múltiplo de 15 minutos.' },
      ]),
    )

    expect(resultado.erroresCampo['duracionMinutos']).toBe('La duración tiene que ser un múltiplo de 15 minutos.')
  })

  it('muestra alerta general y campo para VALIDACION', () => {
    const resultado = traducirError(
      errorApi('VALIDACION', 'Revisá los datos cargados.', [{ campo: 'nombre', mensaje: 'Ese nombre ya está en uso.' }]),
    )

    expect(resultado.alerta).toBe('Revisá los datos cargados.')
    expect(resultado.erroresCampo['nombre']).toBe('Ese nombre ya está en uso.')
  })

  it('usa el mismo mensaje para un recurso ajeno y uno inexistente', () => {
    const resultado = traducirError(
      errorApi('NO_ENCONTRADO', 'La restricción unique_prestacion_odontologo_nombre fue violada; id 42 de otra cuenta'),
    )

    expect(resultado.alerta).toBe('No encontramos ese recurso.')
    expect(resultado.alerta).not.toContain('restricción')
    expect(resultado.erroresCampo).toEqual({})
  })

  it('no reinterpretamos código de sesión como dato inválido', () => {
    const sinSesion = traducirError(errorApi('NO_AUTENTICADO', 'Token expirado'))
    const sinAcceso = traducirError(errorApi('ACCESO_DENEGADO', 'No tenés permiso'))

    expect(sinSesion.alerta).toBe('Tu sesión venció. Ingresá de nuevo.')
    expect(sinAcceso.alerta).toBe('Tu sesión venció. Ingresá de nuevo.')
    expect(sinSesion.erroresCampo).toEqual({})
    expect(sinAcceso.erroresCampo).toEqual({})
  })

  it('ofrece reintento ante SIN_CONEXION', () => {
    const resultado = traducirError(errorApi('SIN_CONEXION', 'Revisá tu conexión e intentá de nuevo.'))

    expect(resultado.alerta).toBe('Revisá tu conexión e intentá de nuevo.')
    expect(resultado.reintentar).toBe(true)
  })

  it('inserta el mensaje con marcas HTML como texto literal', () => {
    const resultado = traducirError(errorApi('ERROR_INTERNO', '<script>algo</script>'))

    expect(resultado.alerta).toBe('<script>algo</script>')
  })
})
