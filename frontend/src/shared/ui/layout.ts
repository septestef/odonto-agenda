import urlLogo from '@design-system/assets/logo.svg?url'
import { NOMBRE_MARCA } from '../brand'
import type { OdontologoDto } from '../guard-sesion'
import { boton } from './componentes'
import type { Descriptor } from './componentes'

export interface OpcionesLayout {
  odontologo: OdontologoDto
  ruta: string
  contenido: Descriptor
}

function enlace(ruta: string, texto: string, activo: boolean): Descriptor {
  return {
    etiqueta: 'a',
    texto,
    clase: 'nav__link',
    atributos: {
      href: ruta,
      'data-accion': 'navegar',
      ...(activo ? { 'aria-current': 'page' } : {}),
    },
    accion: 'navegar',
  }
}

function appbar(opciones: OpcionesLayout): Descriptor {
  const nombre = `${opciones.odontologo.nombre} ${opciones.odontologo.apellido}`

  return {
    etiqueta: 'header',
    clase: 'appbar',
    hijos: [
      {
        etiqueta: 'div',
        clase: 'appbar__inner',
        hijos: [
          { etiqueta: 'img', clase: 'appbar__logo', atributos: { src: urlLogo, alt: NOMBRE_MARCA } },
          { etiqueta: 'span', texto: NOMBRE_MARCA, clase: 'appbar__brand' },
          {
            etiqueta: 'nav',
            clase: 'nav',
            atributos: { 'aria-label': 'Navegación principal' },
            hijos: [
              enlace('/agenda', 'Agenda', opciones.ruta === '/agenda'),
              enlace('/config', 'Configuración', opciones.ruta === '/config'),
            ],
          },
          { etiqueta: 'span', texto: nombre, clase: 'appbar__usuario' },
          boton({
            texto: 'Cerrar sesión',
            accion: 'cerrar-sesion',
            variante: 'fantasma',
            tipo: 'button',
            clase: 'btn--sm',
          }),
        ],
      },
    ],
  }
}

/** Layout del odontólogo: estructura y texto, sin tocar el documento. */
export function layout(opciones: OpcionesLayout): Descriptor {
  return {
    etiqueta: 'div',
    clase: 'layout',
    hijos: [
      { etiqueta: 'a', texto: 'Saltar al contenido', clase: 'skip', atributos: { href: '#contenido' } },
      appbar(opciones),
      {
        etiqueta: 'main',
        clase: 'contenido',
        atributos: { id: 'contenido', tabindex: '-1' },
        hijos: [opciones.contenido],
      },
    ],
  }
}
