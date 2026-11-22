import { describe, expect, it } from 'vitest'
// @ts-expect-error: este proyecto no declara tipos de Node; readFileSync se usa solo en el test.
import { readFileSync } from 'node:fs'
import configuracion from '../../vite.config'
import { NOMBRE_MARCA } from './brand'

function leer(ruta: string): string {
  return readFileSync(new URL(ruta, import.meta.url), 'utf8')
}

function rutaAliasDesignSystem(): string | undefined {
  const alias = configuracion.resolve?.alias
  if (Array.isArray(alias)) {
    return alias.find((entrada) => entrada.find === '@design-system')?.replacement
  }
  if (alias !== undefined && '@design-system' in alias) {
    return alias['@design-system']
  }
  return undefined
}

const nombreDeclarado = /var BRAND_NAME = "([^"]+)"/.exec(leer('../../../design-system/brand.js'))?.[1]
const documento = leer('../../index.html')

describe('marca y favicon', () => {
  it('toma el nombre de marca de design-system/brand.js', () => {
    expect(NOMBRE_MARCA).toBe(nombreDeclarado)
  })

  it('usa el mismo nombre en el documento', () => {
    expect(documento).toContain(`<title>${NOMBRE_MARCA}</title>`)
  })

  it('referencia el logo del design system como favicon', () => {
    expect(documento).toContain('rel="icon"')
    expect(documento).toContain('href="../design-system/assets/logo.svg"')
  })

  it('el logo referenciado existe en el repositorio', () => {
    const logo = leer('../../../design-system/assets/logo.svg')

    expect(logo).toContain('<svg')
  })

  it('el shell usa el nombre centralizado en lugar de un literal', () => {
    const codigo = leer('../main.ts')

    expect(codigo).toContain("import { NOMBRE_MARCA } from './shared/brand'")
    expect(codigo).toContain('app.textContent = NOMBRE_MARCA')
    expect(codigo.includes("'OdontoAgenda'")).toBe(false)
  })

  it('el alias @design-system apunta a la carpeta que contiene el logo', () => {
    const normalizada = rutaAliasDesignSystem()?.replace(/\\/g, '/').replace(/\/$/, '')
    const esperada = decodeURIComponent(new URL('../../../design-system/', import.meta.url).pathname)
      .replace(/^\/([A-Za-z]:)/, '$1')
      .replace(/\/$/, '')

    expect(normalizada).toBe(esperada)
  })
})
