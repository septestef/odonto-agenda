import type { Linter } from 'eslint'
import tseslint from 'typescript-eslint'

const propiedadesProhibidas = ['innerHTML', 'outerHTML', 'insertAdjacentHTML'] as const

function restriccionPara(propiedad: (typeof propiedadesProhibidas)[number]) {
  return {
    selector: `MemberExpression[property.name='${propiedad}']`,
    message: `No se permite ${propiedad}: insertá el texto con textContent.`,
  }
}

export const configSeguridadHtml: Linter.Config = {
  files: ['**/*.ts'],
  languageOptions: { parser: tseslint.parser },
  rules: {
    'no-restricted-syntax': [
      'error',
      ...propiedadesProhibidas.map(restriccionPara),
    ],
  },
}
