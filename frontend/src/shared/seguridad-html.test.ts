import { Linter } from 'eslint'
import { describe, expect, it } from 'vitest'
import { configSeguridadHtml } from './seguridad-html'

function errores(codigo: string) {
  const linter = new Linter()
  return linter.verify(codigo, configSeguridadHtml, 'ejemplo.ts')
}

describe('configSeguridadHtml', () => {
  it('rechaza el uso de innerHTML', () => {
    const mensajes = errores("const nodo = document.createElement('div'); nodo.innerHTML = '<b>hola</b>';")

    expect(mensajes.some((mensaje) => mensaje.message.includes('innerHTML'))).toBe(true)
  })

  it.each([
    ['outerHTML', "const nodo = document.createElement('div'); nodo.outerHTML = '<b>hola</b>';"],
    ['insertAdjacentHTML', "const nodo = document.createElement('div'); nodo.insertAdjacentHTML('beforeend', '<b>hola</b>');"],
  ])('rechaza el uso de %s', (propiedad, codigo) => {
    const mensajes = errores(codigo)

    expect(mensajes.some((mensaje) => mensaje.message.includes(propiedad))).toBe(true)
  })

  it('permite insertar texto con textContent', () => {
    expect(errores("const nodo = document.createElement('div'); nodo.textContent = 'Hola';")).toEqual([])
  })
})
