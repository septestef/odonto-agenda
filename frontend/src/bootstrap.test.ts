import { afterEach, expect, it, vi } from 'vitest'
import { arrancar } from './main'

vi.mock('./main', () => ({ arrancar: vi.fn() }))

afterEach(() => vi.unstubAllGlobals())

it('el bootstrap conecta pagehide real y permite quitar el escucha', async () => {
  const navegador = new EventTarget()
  vi.stubGlobal('window', navegador)
  vi.stubGlobal('document', { querySelector: () => ({}) })
  await import('./bootstrap')

  const opciones = vi.mocked(arrancar).mock.calls[0]?.[2]
  const cancelar = vi.fn()
  const quitar = opciones?.suscribirPagehide?.(cancelar)
  navegador.dispatchEvent(Object.assign(new Event('pagehide'), { persisted: true }))
  expect(cancelar).toHaveBeenCalledExactlyOnceWith(true)
  quitar?.()
  navegador.dispatchEvent(new Event('pagehide'))
  expect(cancelar).toHaveBeenCalledOnce()
})
