import { describe, expect, it } from 'vitest'
import { alerta, boton, campo, modal, toast } from './componentes'

describe('descriptores de componentes base', () => {
  it('el botón en envío queda deshabilitado y ocupado', () => {
    const descriptor = boton({ texto: 'Ingresar', textoEnviando: 'Ingresando…', enviando: true, accion: 'ingresar' })

    expect(descriptor).toMatchObject({
      etiqueta: 'button',
      texto: 'Ingresando…',
      clase: 'btn btn--primaria',
      accion: 'ingresar',
      atributos: { type: 'submit', disabled: '', 'aria-busy': 'true' },
    })
  })

  it('el campo con error marca el input y muestra la alerta', () => {
    const descriptor = campo({
      etiqueta: 'Email',
      nombre: 'email',
      tipo: 'email',
      error: 'Ingresá tu email.',
    })

    expect(descriptor.etiqueta).toBe('div')
    const input = descriptor.hijos?.[1]
    expect(input).toMatchObject({
      etiqueta: 'input',
      atributos: { type: 'email', name: 'email', 'data-campo': 'email', 'aria-invalid': 'true' },
    })
    expect(descriptor.hijos?.[2]).toMatchObject({
      etiqueta: 'div',
      texto: 'Ingresá tu email.',
      atributos: { role: 'alert' },
    })
  })

  it('la alerta de error expone el rol alert', () => {
    const descriptor = alerta({ texto: 'No pudimos guardar los cambios.', tipo: 'error' })

    expect(descriptor).toMatchObject({
      etiqueta: 'div',
      texto: 'No pudimos guardar los cambios.',
      clase: 'alerta alerta--error',
      atributos: { role: 'alert' },
    })
  })

  it('el botón habilitado no tiene disabled ni aria-busy', () => {
    const descriptor = boton({ texto: 'Ingresar', variante: 'primaria' })

    expect(descriptor.texto).toBe('Ingresar')
    expect(descriptor.atributos).toEqual({ type: 'submit' })
  })

  it('el botón deshabilitado explícitamente conserva su texto', () => {
    const descriptor = boton({ texto: 'Ingresar', deshabilitado: true, textoEnviando: 'Ingresando…' })

    expect(descriptor).toMatchObject({ texto: 'Ingresar', atributos: { type: 'submit', disabled: '' } })
    expect(descriptor.atributos?.['aria-busy']).toBeUndefined()
  })

  it('el campo sin error no marca aria-invalid ni agrega alerta', () => {
    const descriptor = campo({ etiqueta: 'Contraseña', nombre: 'password', tipo: 'password' })

    expect(descriptor.hijos).toHaveLength(2)
    expect(descriptor.hijos?.[1]?.atributos?.['aria-invalid']).toBeUndefined()
  })

  it('la alerta de aviso usa rol status y zona cortés', () => {
    const descriptor = alerta({ texto: 'Tu sesión se cerró.', tipo: 'aviso' })

    expect(descriptor).toMatchObject({
      clase: 'alerta alerta--aviso',
      atributos: { role: 'status', 'aria-live': 'polite' },
    })
  })
})

describe('modal y toasts', () => {
  it('el modal abierto declara título y acciones de confirmación y cancelación', () => {
    const descriptor = modal({
      titulo: 'Cancelar turno',
      contenido: '¿Querés cancelar este turno?',
      confirmar: 'Cancelar turno',
      cancelar: 'Volver',
      abierto: true,
    })

    expect(descriptor).toMatchObject({
      etiqueta: 'dialog',
      clase: 'modal',
      atributos: { 'aria-labelledby': 'titulo-modal', open: '' },
    })
    expect(descriptor.hijos?.[0]).toMatchObject({ etiqueta: 'h2', texto: 'Cancelar turno' })
    expect(descriptor.hijos?.[2]?.hijos?.map((accion) => accion.accion)).toEqual(['cancelar-modal', 'confirmar-modal'])
  })

  it('el toast declara zona cortés y acción de cierre', () => {
    const descriptor = toast({ mensaje: 'Turno cancelado.', cerrable: true })

    expect(descriptor).toMatchObject({
      etiqueta: 'div',
      clase: 'toast toast--aviso',
      atributos: { role: 'status', 'aria-live': 'polite' },
    })
    expect(descriptor.hijos?.[0]?.texto).toBe('Turno cancelado.')
    expect(descriptor.hijos?.[1]).toMatchObject({ texto: 'Cerrar', accion: 'cerrar-toast' })
  })

  it('el modal cerrado no declara el atributo open', () => {
    const descriptor = modal({ titulo: 'Confirmar', contenido: '¿Seguimos?', confirmar: 'Sí', cancelar: 'No' })

    expect(descriptor.atributos?.['open']).toBeUndefined()
    expect(descriptor.atributos?.['aria-labelledby']).toBe('titulo-modal')
  })

  it('el toast sin cierre solo contiene el mensaje', () => {
    const descriptor = toast({ mensaje: 'Todo listo.' })

    expect(descriptor.hijos).toHaveLength(1)
    expect(descriptor.hijos?.[0]?.texto).toBe('Todo listo.')
  })

  it('el toast de error cambia el rol a alert', () => {
    const descriptor = toast({ mensaje: 'No se pudo guardar.', tipo: 'error' })

    expect(descriptor).toMatchObject({
      clase: 'toast toast--error',
      atributos: { role: 'alert', 'aria-live': 'polite' },
    })
  })
})
