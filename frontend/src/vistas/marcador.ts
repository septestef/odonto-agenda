import type { NombreRuta, ResultadoRuta } from '../router'
import type { Descriptor } from '../shared/ui/componentes'

const TEXTOS: Readonly<Record<NombreRuta, string>> = {
  login: 'Ingresá a tu agenda',
  agenda: 'Acá vas a ver tu agenda.',
  config: 'Acá vas a ajustar tu configuración.',
  reservar: 'Acá vas a reservar tu turno.',
  turno: 'Acá vas a gestionar tu turno.',
}

function enlaceDeVuelta(): Descriptor {
  return {
    etiqueta: 'a',
    texto: 'Volver a la agenda',
    atributos: { href: '/agenda' },
    accion: 'navegar',
  }
}

/** Marcador sin lógica de negocio: solo texto o enlace de vuelta para rutas inexistentes. */
export function vistaMarcador(vista: ResultadoRuta): Descriptor {
  if (vista.tipo === 'no-encontrado') {
    return {
      etiqueta: 'div',
      clase: 'no-encontrado',
      hijos: [{ etiqueta: 'h1', texto: 'No encontramos esa página.' }, enlaceDeVuelta()],
    }
  }

  return { etiqueta: 'div', clase: 'marcador', texto: TEXTOS[vista.nombre] }
}
