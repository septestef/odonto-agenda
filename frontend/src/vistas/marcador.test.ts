import { describe, expect, it } from 'vitest'
import { vistaMarcador } from './marcador'

describe('vistas marcadoras', () => {
  it('muestra solo el texto del marcador en cada ruta de funcionalidad', () => {
    expect(vistaMarcador({ tipo: 'ruta', nombre: 'agenda', parametros: {} })).toEqual({
      etiqueta: 'div',
      clase: 'marcador',
      texto: 'Acá vas a ver tu agenda.',
    })
    expect(vistaMarcador({ tipo: 'ruta', nombre: 'config', parametros: {} })).toEqual({
      etiqueta: 'div',
      clase: 'marcador',
      texto: 'Acá vas a ajustar tu configuración.',
    })
    expect(vistaMarcador({ tipo: 'ruta', nombre: 'reservar', parametros: { slug: 'ana-gomez' } })).toEqual({
      etiqueta: 'div',
      clase: 'marcador',
      texto: 'Acá vas a reservar tu turno.',
    })
    expect(vistaMarcador({ tipo: 'ruta', nombre: 'turno', parametros: { token: 'abc123' } })).toEqual({
      etiqueta: 'div',
      clase: 'marcador',
      texto: 'Acá vas a gestionar tu turno.',
    })
  })

  it('la vista no encontrado ofrece un enlace interno a una ruta válida', () => {
    const descriptor = vistaMarcador({ tipo: 'no-encontrado', ruta: '/no-existe' })

    expect(descriptor.hijos?.[0]).toMatchObject({
      etiqueta: 'h1',
      texto: 'No encontramos esa página.',
    })
    expect(descriptor.hijos?.[1]).toMatchObject({
      etiqueta: 'a',
      texto: 'Volver a la agenda',
      atributos: { href: '/agenda' },
      accion: 'navegar',
    })
  })

  it('el marcador público ignora los parámetros de la ruta', () => {
    const descriptor = vistaMarcador({ tipo: 'ruta', nombre: 'reservar', parametros: { slug: 'otro-slug' } })

    expect(descriptor.texto).toBe('Acá vas a reservar tu turno.')
    expect(descriptor.hijos).toBeUndefined()
  })

  it('el marcador del turno no consulta nada aunque venga con token', () => {
    const descriptor = vistaMarcador({ tipo: 'ruta', nombre: 'turno', parametros: { token: 'abc123' } })

    expect(descriptor).toEqual({ etiqueta: 'div', clase: 'marcador', texto: 'Acá vas a gestionar tu turno.' })
  })

  it('cualquier ruta desconocida muestra el mismo enlace de vuelta', () => {
    const descriptor = vistaMarcador({ tipo: 'no-encontrado', ruta: '/config/otra-cosa' })

    expect(descriptor.hijos?.[1]?.atributos?.['href']).toBe('/agenda')
    expect(descriptor.texto).toBeUndefined()
  })
})
