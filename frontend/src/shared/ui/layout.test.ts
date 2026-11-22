import { describe, expect, it } from 'vitest'
import { layout } from './layout'
import { NOMBRE_MARCA } from '../brand'
import type { Descriptor } from './componentes'

const ODONTOLOGO = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Gómez',
  email: 'ana@consultorio.com',
  slug: 'ana-gomez',
}

const CONTENIDO: Descriptor = { etiqueta: 'p', texto: 'Próximamente: agenda.' }

function enlaces(descriptor: Descriptor): readonly Descriptor[] {
  return descriptor.hijos?.[1]?.hijos?.[0]?.hijos?.[2]?.hijos ?? []
}

describe('layout del odontólogo', () => {
  it('incluye salto al contenido, marca, navegación, nombre y cierre de sesión', () => {
    const descriptor = layout({ odontologo: ODONTOLOGO, ruta: '/agenda', contenido: CONTENIDO })

    expect(descriptor.hijos?.[0]).toMatchObject({
      etiqueta: 'a',
      texto: 'Saltar al contenido',
      atributos: { href: '#contenido' },
    })
    const appbar = descriptor.hijos?.[1]?.hijos?.[0]?.hijos
    expect(appbar?.[0]).toMatchObject({ etiqueta: 'img', atributos: { alt: NOMBRE_MARCA } })
    expect(appbar?.[1]?.texto).toBe(NOMBRE_MARCA)
    expect(appbar?.[3]?.texto).toBe('Ana Gómez')
    expect(appbar?.[4]).toMatchObject({ texto: 'Cerrar sesión', accion: 'cerrar-sesion' })
    expect(descriptor.hijos?.[2]).toMatchObject({
      etiqueta: 'main',
      atributos: { id: 'contenido' },
      hijos: [CONTENIDO],
    })
  })

  it('marca Agenda como página actual', () => {
    const descriptor = layout({ odontologo: ODONTOLOGO, ruta: '/agenda', contenido: CONTENIDO })

    expect(enlaces(descriptor)[0]?.atributos?.['aria-current']).toBe('page')
    expect(enlaces(descriptor)[1]?.atributos?.['aria-current']).toBeUndefined()
  })

  it('marca Configuración como página actual', () => {
    const descriptor = layout({ odontologo: ODONTOLOGO, ruta: '/config', contenido: CONTENIDO })

    expect(enlaces(descriptor)[0]?.atributos?.['aria-current']).toBeUndefined()
    expect(enlaces(descriptor)[1]).toMatchObject({
      texto: 'Configuración',
      atributos: { href: '/config', 'aria-current': 'page' },
    })
  })

  it('no marca ninguna sección en una ruta pública', () => {
    const descriptor = layout({ odontologo: ODONTOLOGO, ruta: '/reservar/ana-gomez', contenido: CONTENIDO })

    expect(enlaces(descriptor).some((enlace) => 'aria-current' in (enlace.atributos ?? {}))).toBe(false)
  })

  it('mantiene el mismo contenido en escritorio y móvil y usa el nombre del odontólogo', () => {
    const escritorio = layout({ odontologo: ODONTOLOGO, ruta: '/agenda', contenido: CONTENIDO })
    const movil = layout({
      odontologo: { ...ODONTOLOGO, nombre: 'Bruno', apellido: 'Díaz' },
      ruta: '/agenda',
      contenido: CONTENIDO,
    })

    expect(movil.hijos?.[2]?.hijos).toEqual(escritorio.hijos?.[2]?.hijos)
    expect(movil.hijos?.[1]?.hijos?.[0]?.hijos?.[3]?.texto).toBe('Bruno Díaz')
    expect(JSON.stringify(escritorio)).not.toContain('Salir')
  })
})
