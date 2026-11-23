import { describe, expect, it } from 'vitest'
// @ts-expect-error: este proyecto no declara tipos de Node; readFileSync se usa solo en el test.
import { readFileSync } from 'node:fs'
import configuracion from '../../vite.config'

const estilos: string = readFileSync(new URL('../estilos/app.css', import.meta.url), 'utf8')

function normalizar(ruta: string) {
  return ruta.replace(/\\/g, '/').replace(/\/$/, '')
}

const rutaDesignSystem = normalizar(
  decodeURIComponent(new URL('../../../design-system/', import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'),
)

describe('configuración del design system', () => {
  it('resuelve el alias @design-system hacia la raíz design-system/', () => {
    const alias = configuracion.resolve?.alias
    let reemplazo: string | undefined
    if (alias !== undefined && Array.isArray(alias)) {
      reemplazo = alias.find((item) => item.find === '@design-system')?.replacement
    } else if (alias !== undefined && '@design-system' in alias) {
      reemplazo = alias['@design-system']
    }

    expect(reemplazo === undefined ? undefined : normalizar(reemplazo)).toBe(rutaDesignSystem)
  })

  it('permite leer design-system desde el servidor de desarrollo', () => {
    const permitidos = (configuracion.server?.fs?.allow ?? []).map(normalizar)

    expect(permitidos).toContain(rutaDesignSystem)
  })

  it('preserva el proxy /api del backend', () => {
    expect(configuracion.server?.proxy).toMatchObject({
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    })
  })

  it('app.css importa los tokens desde el alias del design system', () => {
    expect(estilos).toContain("@import '@design-system/tokens.css';")
  })

  it('las horas de cada franja ocupan el ancho de su día y no se salen de la columna', () => {
    const regla = /\.franja input\s*\{([^}]*)\}/.exec(estilos)?.[1] ?? ''

    expect(regla).toContain('width: 100%')
    expect(regla).toContain('min-width: 0')
  })
})
