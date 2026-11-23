import { afterEach, describe, expect, it, vi } from 'vitest'
import { crearClienteApi } from '../../shared/api-client'
import { crearAdaptadorNodos } from '../../shared/ui/adaptador'
import type { EventoUI, NodoUI } from '../../shared/ui/adaptador'
import type { Descriptor } from '../../shared/ui/componentes'
import { crearControlAgenda } from './control'

const URL_FICTICIA = 'https://wa.me/12025550142?text=Mensaje%20de%20prueba'
const ahora = new Date('2026-11-25T10:10:00-03:00')

class Nodo implements NodoUI {
  textContent = ''
  atributos = new Map<string, string>()
  eventos = new Map<string, (evento?: EventoUI) => void>()
  hijos: Nodo[] = []
  append(...hijos: Nodo[]) { this.hijos.push(...hijos) }
  setAttribute(nombre: string, valor: string) { this.atributos.set(nombre, valor) }
  getAttribute(nombre: string) { return this.atributos.get(nombre) ?? null }
  addEventListener(tipo: string, evento: (evento?: EventoUI) => void) { this.eventos.set(tipo, evento) }
}

function nodos(d: Descriptor): Descriptor[] {
  return [d, ...(d.hijos ?? []).flatMap(nodos)]
}

function popupFalso() {
  const navegaciones: Array<{ opener: unknown; href: string; rel: string; target: string }> = []
  const enlace = { href: '', rel: '', target: '' }
  const popup = {
    opener: { ficticio: true } as unknown,
    closed: false,
    location: { href: 'about:blank' },
    close: vi.fn(),
    document: {
      createElement: vi.fn(() => ({
        ...enlace,
        click() {
          navegaciones.push({ opener: popup.opener, href: this.href, rel: this.rel, target: this.target })
        },
      })),
    },
  }
  return { popup, navegaciones }
}

async function preparar({
  ancho = 390, telefono = '+12025550142', estado = 'RESERVADO', inicio = '2026-11-25T11:15:00-03:00',
} = {}) {
  const turno = {
    id: 3, inicio, fin: '2026-11-25T12:00:00-03:00', estado, origen: 'MANUAL',
    paciente: { nombre: 'Ejemplo', telefono }, prestacion: {}, canceladoPor: null, canceladoEn: null,
  }
  const pendientes: Array<(respuesta: Response) => void> = []
  const fetch = vi.fn(async (ruta: string) => {
    if (ruta.endsWith('/whatsapp')) {
      return new Promise<Response>((resolve) => pendientes.push(resolve))
    }
    if (ruta === '/api/bloqueos') return Response.json({ bloqueos: [] })
    if (ruta === '/api/horarios') return Response.json({ franjas: [{ diaSemana: 3, inicio: '09:00', fin: '13:00' }] })
    return Response.json([turno, { ...turno, id: 4 }])
  })
  const sesionVencida = vi.fn()
  const cliente = crearClienteApi({ fetch, notificarSesionVencida: sesionVencida })
  const alCambiar = vi.fn()
  const control = crearControlAgenda({ cliente, ahora, ancho, alCambiar })
  function actuar(accion: string, id = 3) {
    const nodo = new Nodo()
    nodo.setAttribute('data-turno-id', String(id))
    return control.manejadores[accion]?.(nodo)
  }
  const descriptor = () => nodos(control.descriptor())
  const buscar = (accion: string) => descriptor().find((n) => n.accion === accion)
  const cierre = () => descriptor().find((n) => n.clase?.startsWith('detalle detalle--'))
  const manual = () => descriptor().find((n) => n.texto === 'Abrir WhatsApp')
  const mensajes = () => descriptor().map((n) => n.texto).filter(Boolean).join(' ')
  await control.cargar()
  actuar('ver-turno')
  return { control, actuar, buscar, manual, cierre, mensajes, pendientes, fetch, alCambiar, descriptor, sesionVencida }
}

afterEach(() => vi.unstubAllGlobals())

describe('WhatsApp en detalle Día/Semana', () => {
  it.each([390, 1440])('presenta una acción secundaria en ambas vistas (%i px)', async (ancho) => {
    const vista = await preparar({ ancho })
    expect(vista.buscar('preparar-whatsapp')).toMatchObject({ etiqueta: 'button' })
    expect(vista.buscar('cancelar-turno')).toBeDefined()
    expect(vista.pendientes).toHaveLength(0)
    const nodo = new Nodo()
    nodo.setAttribute('data-vista', ancho === 390 ? 'semana' : 'dia')
    vista.control.manejadores['cambiar-vista']?.(nodo)
    await vista.control.cargar()
    vista.actuar('ver-turno')
    expect(vista.buscar('preparar-whatsapp')).toMatchObject({ etiqueta: 'button' })
  })

  it.each(['', '3511234567', '+1 202 555 0142'])('no habilita teléfono ausente o no internacional (%s) sin bloquear Cancelar', async (telefono) => {
    const vista = await preparar({ telefono })
    expect(vista.buscar('preparar-whatsapp')?.atributos?.disabled).toBe('')
    expect(vista.mensajes()).toContain('El paciente no tiene un teléfono internacional válido.')
    expect(vista.buscar('cancelar-turno')).toBeDefined()
    vista.actuar('preparar-whatsapp')
    expect(vista.pendientes).toHaveLength(0)
  })

  it.each(['ATENDIDO', 'AUSENTE', 'CANCELADO', 'RESERVADO'])('omite WhatsApp si ya empezó o el estado no es elegible (%s)', async (estado) => {
    const vista = await preparar({ estado, inicio: '2026-11-25T10:10:00-03:00' })
    expect(vista.buscar('preparar-whatsapp')).toBeUndefined()
  })

  it('reserva la pestaña desde el gesto, aísla opener antes del GET y navega una sola vez sin referrer', async () => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    const abrir = vi.fn(() => { expect(vista.pendientes).toHaveLength(0); return popup })
    vi.stubGlobal('window', { open: abrir })
    const preparando = vista.actuar('preparar-whatsapp')
    vista.actuar('preparar-whatsapp')
    expect(abrir).toHaveBeenCalledExactlyOnceWith('about:blank', '_blank')
    expect(popup.opener).toBeNull()
    expect(vista.pendientes).toHaveLength(1)
    expect(vista.buscar('preparar-whatsapp')).toMatchObject({
      enfocar: true, atributos: { 'aria-busy': 'true', 'aria-disabled': 'true' },
    })
    expect(vista.mensajes()).toContain('Preparando WhatsApp…')
    vista.pendientes[0]?.(Response.json({ url: URL_FICTICIA }))
    await preparando
    expect(navegaciones).toEqual([{ opener: null, href: URL_FICTICIA, rel: 'noopener noreferrer', target: '_self' }])
    expect(vista.manual()).toBeUndefined()
    expect(popup.close).not.toHaveBeenCalled()
  })

  it.each(['bloqueado', 'cerrado', 'sin aislamiento'])('ofrece únicamente un enlace manual cuando el popup queda %s', async (caso) => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    if (caso === 'sin aislamiento') Object.defineProperty(popup, 'opener', { writable: false })
    const abrir = vi.fn(() => caso === 'bloqueado' ? null : popup)
    vi.stubGlobal('window', { open: abrir })
    const preparando = vista.actuar('preparar-whatsapp')
    if (caso === 'cerrado') popup.closed = true
    expect(vista.pendientes).toHaveLength(1)
    vista.pendientes[0]?.(Response.json({ url: URL_FICTICIA }))
    await preparando
    expect(abrir).toHaveBeenCalledOnce()
    expect(navegaciones).toHaveLength(0)
    expect(vista.manual()).toMatchObject({
      etiqueta: 'a', atributos: { href: URL_FICTICIA, target: '_blank', rel: 'noopener noreferrer' },
    })
    expect(vista.mensajes()).toContain('El navegador bloqueó la apertura.')
    if (caso === 'sin aislamiento') expect(popup.close).toHaveBeenCalledOnce()
    vista.actuar('ver-turno', 4)
    expect(vista.manual()).toBeUndefined()
  })

  it.each([
    [200, { url: 'https://example.invalid/' }, 'No pudimos preparar WhatsApp. Volvé a intentar.'],
    [409, { codigo: 'RN-NO-06', mensaje: 'no mostrar' }, 'Actualizá la agenda'],
    [422, { codigo: 'RN-NO-07', mensaje: 'Falta la dirección del consultorio.' }, 'Falta la dirección del consultorio.'],
    [403, { codigo: 'ACCESO_DENEGADO', mensaje: 'no mostrar' }, 'No tenés acceso'],
    [401, { codigo: 'NO_AUTENTICADO', mensaje: 'no mostrar' }, 'Tu sesión venció'],
  ])('cierra la reserva y muestra aviso local ante %i', async (status, cuerpo, mensaje) => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    vi.stubGlobal('window', { open: () => popup })
    const preparando = vista.actuar('preparar-whatsapp')
    expect(vista.pendientes).toHaveLength(1)
    vista.pendientes[0]?.(Response.json(cuerpo, { status }))
    await preparando
    expect(popup.close).toHaveBeenCalledOnce()
    expect(navegaciones).toHaveLength(0)
    expect(vista.manual()).toBeUndefined()
    expect(vista.mensajes()).toContain(mensaje)
    expect(vista.mensajes()).not.toContain('no mostrar')
    expect(vista.pendientes).toHaveLength(1)
    if (status === 409) {
      const pedidos = vista.fetch.mock.calls.length
      vista.actuar('actualizar-agenda-whatsapp')
      expect(vista.fetch.mock.calls.length).toBeGreaterThan(pedidos)
    }
  })

  it('Escape cierra la reserva pendiente y devuelve el foco al turno', async () => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    vi.stubGlobal('window', { open: () => popup })
    const preparando = vista.actuar('preparar-whatsapp')
    const raiz = new Nodo()
    const cierre = vista.cierre()
    expect(cierre?.teclas?.Escape).toBe('cerrar-detalle')
    if (cierre === undefined) throw new Error('Falta el detalle')
    crearAdaptadorNodos({ createElement: () => new Nodo() }).montar(raiz, cierre, vista.control.manejadores)
    const preventDefault = vi.fn()
    raiz.hijos[0]?.eventos.get('keydown')?.({ key: 'Escape', preventDefault })
    expect(preventDefault).toHaveBeenCalledOnce()
    expect(popup.close).toHaveBeenCalledOnce()
    const trasCerrar = vista.descriptor()
    expect(trasCerrar.some((n) => n.clase?.startsWith('detalle detalle--'))).toBe(false)
    expect(trasCerrar.some((n) => n.accion === 'ver-turno' && n.atributos?.['data-turno-id'] === '3' && n.enfocar)).toBe(true)
    vista.pendientes[0]?.(Response.json({ url: URL_FICTICIA }))
    await preparando
    expect(navegaciones).toHaveLength(0)
  })

  it('descarta la respuesta anterior aunque llegue después de la del nuevo turno', async () => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    const abrir = vi.fn().mockReturnValueOnce(popup).mockReturnValueOnce(null)
    vi.stubGlobal('window', { open: abrir })
    const vieja = vista.actuar('preparar-whatsapp')
    vista.actuar('ver-turno', 4)
    expect(popup.close).toHaveBeenCalledOnce()
    const nueva = vista.actuar('preparar-whatsapp', 4)
    expect(vista.pendientes).toHaveLength(2)
    const nuevaUrl = `${URL_FICTICIA}%20nuevo`
    vista.pendientes[1]?.(Response.json({ url: nuevaUrl }))
    await nueva
    const cambios = vista.alCambiar.mock.calls.length
    vista.pendientes[0]?.(Response.json({ url: URL_FICTICIA }))
    await vieja
    expect(vista.manual()?.atributos?.href).toBe(nuevaUrl)
    expect(vista.alCambiar).toHaveBeenCalledTimes(cambios)
    expect(navegaciones).toHaveLength(0)
  })

  it('descarta la preparación al pedir Cancelar sin afectar la confirmación', async () => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    vi.stubGlobal('window', { open: () => popup })
    const preparando = vista.actuar('preparar-whatsapp')
    vista.actuar('cancelar-turno')
    expect(popup.close).toHaveBeenCalledOnce()
    expect(vista.buscar('confirmar-cancelacion')).toBeDefined()
    vista.pendientes[0]?.(Response.json({ url: URL_FICTICIA }))
    await preparando
    expect(navegaciones).toHaveLength(0)
    expect(vista.manual()).toBeUndefined()
  })

  it('enfoca No, volver y Escape de la confirmación restaura el foco al Cancelar del detalle', async () => {
    const vista = await preparar()
    const cancelar = new Nodo()
    cancelar.setAttribute('data-turno-id', '3')
    cancelar.setAttribute('data-origen-cancelacion', 'detalle')
    vista.control.manejadores['cancelar-turno']?.(cancelar)
    const descriptores = vista.descriptor()
    const dialogo = descriptores.find((n) => n.atributos?.role === 'alertdialog')
    expect(descriptores.find((n) => n.accion === 'cerrar-confirmacion')?.enfocar).toBe(true)
    if (dialogo === undefined) throw new Error('Falta la confirmación')
    const raiz = new Nodo()
    crearAdaptadorNodos({ createElement: () => new Nodo() }).montar(raiz, dialogo, vista.control.manejadores)
    raiz.hijos[0]?.eventos.get('keydown')?.({ key: 'Escape', preventDefault: vi.fn() })
    expect(vista.descriptor().find((n) => n.clase?.includes('detalle__cancelar'))?.enfocar).toBe(true)
    expect(vista.buscar('confirmar-cancelacion')).toBeUndefined()
  })

  it.each(['volver', 'Escape'])('al cerrar con %s devuelve el foco a Cancelar de la tarjeta diaria', async (via) => {
    const vista = await preparar()
    vista.actuar('cerrar-detalle')
    const disparador = vista.descriptor().find((n) => n.accion === 'cancelar-turno' &&
      n.atributos?.['data-turno-id'] === '3')
    if (disparador === undefined) throw new Error('Falta Cancelar en la tarjeta')
    const boton = new Nodo()
    for (const [nombre, valor] of Object.entries(disparador.atributos ?? {})) boton.setAttribute(nombre, valor)
    vista.control.manejadores['cancelar-turno']?.(boton)
    const dialogo = vista.descriptor().find((n) => n.atributos?.role === 'alertdialog')
    if (dialogo === undefined) throw new Error('Falta la confirmación')
    if (via === 'Escape') {
      const raiz = new Nodo()
      crearAdaptadorNodos({ createElement: () => new Nodo() }).montar(raiz, dialogo, vista.control.manejadores)
      raiz.hijos[0]?.eventos.get('keydown')?.({ key: 'Escape', preventDefault: vi.fn() })
    } else {
      vista.actuar('cerrar-confirmacion')
    }
    const trasCerrar = vista.descriptor()
    expect(trasCerrar.find((n) => n.accion === 'cancelar-turno' && n.texto === 'Cancelar' &&
      n.atributos?.['data-turno-id'] === '3')?.enfocar).toBe(true)
    expect(trasCerrar.some((n) => n.atributos?.role === 'alertdialog')).toBe(false)
  })

  it('un 401 tardío conserva la señal de sesión sin cambiar el detalle vigente', async () => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    vi.stubGlobal('window', { open: () => popup })
    const preparando = vista.actuar('preparar-whatsapp')
    vista.actuar('ver-turno', 4)
    const cambios = vista.alCambiar.mock.calls.length
    vista.pendientes[0]?.(Response.json({ codigo: 'NO_AUTENTICADO', mensaje: 'no mostrar' }, { status: 401 }))
    await preparando
    expect(vista.sesionVencida).toHaveBeenCalledOnce()
    expect(vista.alCambiar).toHaveBeenCalledTimes(cambios)
    expect(vista.buscar('preparar-whatsapp')?.atributos?.['data-turno-id']).toBe('4')
    expect(vista.mensajes()).not.toContain('Tu sesión venció')
    expect(navegaciones).toHaveLength(0)
  })

  it('no navega ni cierra la pestaña si el usuario ya la llevó a otro destino', async () => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    vi.stubGlobal('window', { open: () => popup })
    const preparando = vista.actuar('preparar-whatsapp')
    popup.location.href = 'https://example.invalid/otro-destino'
    vista.pendientes[0]?.(Response.json({ url: URL_FICTICIA }))
    await preparando
    expect(popup.close).not.toHaveBeenCalled()
    expect(navegaciones).toHaveLength(0)
    expect(vista.manual()).toBeDefined()
  })

  it('al cambiar la vista descarta la reserva aunque se vuelva a elegir el mismo turno', async () => {
    const vista = await preparar()
    const { popup, navegaciones } = popupFalso()
    vi.stubGlobal('window', { open: () => popup })
    const preparando = vista.actuar('preparar-whatsapp')
    const nodo = new Nodo()
    nodo.setAttribute('data-vista', 'semana')
    vista.control.manejadores['cambiar-vista']?.(nodo)
    expect(popup.close).toHaveBeenCalledOnce()
    await vista.control.cargar()
    vista.actuar('ver-turno')
    vista.pendientes[0]?.(Response.json({ url: URL_FICTICIA }))
    await preparando
    expect(vista.manual()).toBeUndefined()
    expect(navegaciones).toHaveLength(0)
  })
})
