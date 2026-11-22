import codigoBrand from '@design-system/brand.js?raw'

const coincidencia = /var BRAND_NAME = "([^"]+)"/.exec(codigoBrand)
const nombre = coincidencia?.[1]

if (nombre === undefined) {
  throw new Error('No se pudo leer el nombre de marca de design-system/brand.js')
}

/** Único nombre de marca del frontend; se deriva del archivo aprobado del design system. */
export const NOMBRE_MARCA: string = nombre
