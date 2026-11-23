import { describe, expect, it, vi } from 'vitest'
import type { ClienteApi } from '../../shared/api-client'
import type { NodoUI } from '../../shared/ui/adaptador'
import type { Descriptor } from '../../shared/ui/componentes'
import { crearControlAgenda } from './control'
import type { FranjaHorario, TurnoAgenda } from './tipos'

class NodoPrueba implements NodoUI {
  textContent: string | null = null
  readonly atributos: Record<string, string> = {}
  append(): void {}
  setAttribute(nombre: string, valor: string): void {
    this.atributos[nombre] = valor
  }
  addEventListener(): void {}
  getAttribute(nombre: string): string | null {
    return this.atributos[nombre] ?? null
  }
  con(nombre: string, valor: string): NodoPrueba {
    this.setAttribute(nombre, valor)
    return this
  }
}

const franjas: FranjaHorario[] = [
  { diaSemana: 3, inicio: '09:00', fin: '13:00' },
]

function franjasDe(dias: readonly number[]): FranjaHorario[] {
  return dias.map((diaSemana) => ({ diaSemana, inicio: '09:00', fin: '18:00' }))
}

function turno(): TurnoAgenda {
  return {
    id: 3,
    inicio: '2026-11-25T11:15:00-03:00',
    fin: '2026-11-25T12:00:00-03:00',
    estado: 'RESERVADO',
    origen: 'ONLINE',
    paciente: { nombre: 'Martín', apellido: 'Sosa' },
    prestacion: { nombre: 'Limpieza', duracionMinutos: 45 },
    canceladoPor: null,
    canceladoEn: null,
  }
}

function clienteFalso(opciones: {
  turnos?: unknown
  errorCancelar?: unknown
  errorTurnos?: unknown
  franjas?: readonly FranjaHorario[]
} = {}) {
  const franjasConsultadas = opciones.franjas ?? franjas
  const pedir = vi.fn(async (metodo: string, ruta: string) => {
    if (metodo === 'GET' && ruta === '/api/horarios') {
      return { franjas: franjasConsultadas }
    }
    if (metodo === 'GET' && ruta === '/api/bloqueos') {
      return { bloqueos: [] }
    }
    if (metodo === 'GET' && ruta.startsWith('/api/turnos')) {
      if (opciones.errorTurnos !== undefined) {
        throw opciones.errorTurnos
      }
      return opciones.turnos ?? [turno()]
    }
    if (metodo === 'POST' && ruta === '/api/turnos/3/cancelar') {
      if (opciones.errorCancelar !== undefined) {
        throw opciones.errorCancelar
      }
      return undefined
    }
    return undefined
  })
  return {
    cliente: { pedir, suscribirSesionVencida: () => () => {} } as unknown as ClienteApi,
    pedir,
  }
}

function textos(descriptor: Descriptor | undefined): string[] {
  if (descriptor === undefined) {
    return []
  }
  return [
    ...(descriptor.texto === undefined ? [] : [descriptor.texto]),
    ...Object.values(descriptor.atributos ?? {}),
    ...(descriptor.hijos ?? []).flatMap(textos),
  ]
}

function deClase(descriptor: Descriptor, clase: string): Descriptor[] {
  const propios = (descriptor.clase ?? '').split(' ').includes(clase) ? [descriptor] : []
  return [...propios, ...(descriptor.hijos ?? []).flatMap((hijo) => deClase(hijo, clase))]
}

function tituloDe(descriptor: Descriptor): string | undefined {
  if (descriptor.etiqueta === 'h1') {
    return descriptor.texto
  }
  return (descriptor.hijos ?? []).map(tituloDe).find((texto) => texto !== undefined)
}

async function estabilizar(): Promise<void> {
  for (let vuelta = 0; vuelta < 12; vuelta += 1) {
    await Promise.resolve()
  }
}

describe('control de la agenda integrada', () => {
  const ahora = new Date('2026-11-25T10:10:00-03:00')

  it('elige la vista por ancho y consulta el rango semiabierto', async () => {
    const { cliente, pedir } = clienteFalso()
    const control = crearControlAgenda({ cliente, ancho: 390, ahora, alCambiar: () => {} })
    await control.cargar()
    await estabilizar()

    expect(deClase(control.descriptor(), 'dia')).toHaveLength(1)
    expect(pedir).toHaveBeenCalledWith('GET', '/api/turnos?desde=2026-11-25&hasta=2026-11-26')
    expect(pedir).toHaveBeenCalledWith('GET', '/api/horarios')
    expect(pedir).toHaveBeenCalledWith('GET', '/api/bloqueos')

    const ancho = crearControlAgenda({ cliente, ancho: 1440, ahora, alCambiar: () => {} })
    await ancho.cargar()
    await estabilizar()
    expect(deClase(ancho.descriptor(), 'semana-contenedor')).toHaveLength(1)
    expect(pedir).toHaveBeenCalledWith('GET', '/api/turnos?desde=2026-11-23&hasta=2026-11-30')
  })

  it('titula la semana hasta el último día con franjas y el día con la fecha puntual', async () => {
    const { cliente } = clienteFalso({ franjas: franjasDe([1, 2, 3, 4, 5]) })
    const control = crearControlAgenda({ cliente, ancho: 1440, ahora, alCambiar: () => {} })
    await control.cargar()
    await estabilizar()

    // El mockup dibuja lun–vie: sábado 28 y domingo 29 no tienen franjas.
    expect(tituloDe(control.descriptor())).toBe('23 al 27 de noviembre')

    control.manejadores['siguiente']?.(new NodoPrueba())
    await estabilizar()
    expect(tituloDe(control.descriptor())).toBe('30 de noviembre al 4 de diciembre')

    control.manejadores['cambiar-vista']?.(new NodoPrueba().con('data-vista', 'dia'))
    await estabilizar()
    expect(tituloDe(control.descriptor())).toBe('Miércoles 2 de diciembre')

    const diario = crearControlAgenda({ cliente, ancho: 390, ahora, alCambiar: () => {} })
    await diario.cargar()
    await estabilizar()
    expect(tituloDe(diario.descriptor())).toBe('Miércoles 25 de noviembre')
  })

  it('cae al rango completo con franjas todos los días, sin franjas y durante la carga', async () => {
    const conTodos = clienteFalso({ franjas: franjasDe([1, 2, 3, 4, 5, 6, 7]) })
    const completo = crearControlAgenda({ cliente: conTodos.cliente, ancho: 1440, ahora, alCambiar: () => {} })
    await completo.cargar()
    await estabilizar()
    expect(tituloDe(completo.descriptor())).toBe('23 al 29 de noviembre')

    const sinFranjas = clienteFalso({ franjas: [] })
    const vacio = crearControlAgenda({ cliente: sinFranjas.cliente, ancho: 1440, ahora, alCambiar: () => {} })
    await vacio.cargar()
    await estabilizar()
    expect(tituloDe(vacio.descriptor())).toBe('23 al 29 de noviembre')

    const cargando = crearControlAgenda({ cliente: sinFranjas.cliente, ancho: 1440, ahora, alCambiar: () => {} })
    expect(tituloDe(cargando.descriptor())).toBe('23 al 29 de noviembre')
  })

  it('navega, vuelve a hoy y alterna vista sin persistir', async () => {
    const { cliente, pedir } = clienteFalso()
    const control = crearControlAgenda({ cliente, ancho: 390, ahora, alCambiar: () => {} })
    await control.cargar()
    await estabilizar()

    control.manejadores['siguiente']?.(new NodoPrueba())
    await estabilizar()
    expect(pedir).toHaveBeenCalledWith('GET', '/api/turnos?desde=2026-11-26&hasta=2026-11-27')

    control.manejadores['cambiar-vista']?.(new NodoPrueba().con('data-vista', 'semana'))
    await estabilizar()
    expect(deClase(control.descriptor(), 'semana-contenedor')).toHaveLength(1)

    control.manejadores['hoy']?.(new NodoPrueba())
    await estabilizar()
    expect(pedir).toHaveBeenCalledWith('GET', '/api/turnos?desde=2026-11-23&hasta=2026-11-30')
  })

  it('abre el detalle, confirma, recarga y muestra el toast aprobado', async () => {
    const { cliente, pedir } = clienteFalso()
    let cambios = 0
    const control = crearControlAgenda({ cliente, ancho: 1440, ahora, alCambiar: () => { cambios += 1 } })
    await control.cargar()
    await estabilizar()

    control.manejadores['ver-turno']?.(new NodoPrueba().con('data-turno-id', '3'))
    expect(deClase(control.descriptor(), 'detalle')).toHaveLength(1)

    control.manejadores['cancelar-turno']?.(new NodoPrueba().con('data-turno-id', '3'))
    expect(deClase(control.descriptor(), 'dialogo-confirmacion')).toHaveLength(1)

    await control.manejadores['confirmar-cancelacion']?.(new NodoPrueba())
    await estabilizar()

    expect(pedir).toHaveBeenCalledWith('POST', '/api/turnos/3/cancelar')
    expect(pedir.mock.calls.filter((llamada) => llamada[0] === 'POST')).toHaveLength(1)
    expect(textos(control.descriptor())).toContain('Turno cancelado')
    expect(deClase(control.descriptor(), 'dialogo-confirmacion')).toHaveLength(0)
    expect(cambios).toBeGreaterThan(0)
  })

  it('en Día a 1440 px pone el panel en una columna junto a la lista, fuera de la cabecera y sin velo', async () => {
    const { cliente } = clienteFalso()
    const control = crearControlAgenda({ cliente, ancho: 1440, ahora, alCambiar: () => {} })
    await control.cargar()
    await estabilizar()
    control.manejadores['cambiar-vista']?.(new NodoPrueba().con('data-vista', 'dia'))
    await estabilizar()
    control.manejadores['ver-turno']?.(new NodoPrueba().con('data-turno-id', '3'))

    const raiz = control.descriptor()
    const cuerpo = deClase(raiz, 'agenda-cuerpo--con-panel')
    expect(cuerpo).toHaveLength(1)
    // La cabecera (Día/Semana y navegación) queda fuera del cuerpo: el panel no la puede tapar.
    expect(raiz.hijos?.[0]?.clase).toBe('agenda-head')
    expect(deClase(cuerpo[0] as Descriptor, 'agenda-head')).toHaveLength(0)
    const [principal, panel] = cuerpo[0]?.hijos ?? []
    expect(deClase(principal as Descriptor, 'dia')).toHaveLength(1)
    expect((panel?.clase ?? '').split(' ')).toContain('detalle--panel')
    expect(deClase(raiz, 'velo')).toHaveLength(0)
  })

  it('atenúa el fondo con el cajón semanal y con la hoja, y suma otro velo a la confirmación', async () => {
    for (const ancho of [1440, 390]) {
      const { cliente } = clienteFalso()
      const control = crearControlAgenda({ cliente, ancho, ahora, alCambiar: () => {} })
      await control.cargar()
      await estabilizar()
      control.manejadores['ver-turno']?.(new NodoPrueba().con('data-turno-id', '3'))

      const conDetalle = control.descriptor()
      expect(deClase(conDetalle, 'agenda-cuerpo')).toHaveLength(0)
      const hijos = conDetalle.hijos ?? []
      const indiceDetalle = hijos.findIndex((hijo) => (hijo.clase ?? '').split(' ').includes('detalle'))
      expect((hijos[indiceDetalle - 1]?.clase ?? '').split(' ')).toContain('velo--detalle')

      control.manejadores['cancelar-turno']?.(new NodoPrueba().con('data-turno-id', '3'))
      const conConfirmacion = control.descriptor().hijos ?? []
      const indiceDialogo = conConfirmacion.findIndex((hijo) => hijo.clase === 'dialogo-confirmacion')
      const velo = conConfirmacion[indiceDialogo - 1]
      expect((velo?.clase ?? '').split(' ')).toContain('velo--confirmacion')
      expect(velo?.accion).toBeUndefined()
      expect(velo?.atributos?.['aria-hidden']).toBe('true')

      control.manejadores['cerrar-detalle']?.(new NodoPrueba())
      expect(deClase(control.descriptor(), 'velo')).toHaveLength(0)
    }
  })

  it('muestra el aviso correspondiente al 409 RN-TU-07 y recarga', async () => {
    const { cliente, pedir } = clienteFalso({
      errorCancelar: Object.assign(new Error('empezó'), { codigo: 'RN-TU-07' }),
    })
    const control = crearControlAgenda({ cliente, ancho: 390, ahora, alCambiar: () => {} })
    await control.cargar()
    await estabilizar()
    control.manejadores['cancelar-turno']?.(new NodoPrueba().con('data-turno-id', '3'))

    await control.manejadores['confirmar-cancelacion']?.(new NodoPrueba())
    await estabilizar()

    expect(textos(control.descriptor()).join(' ')).toContain('Este turno ya empezó. Actualizamos la agenda.')
    expect(pedir.mock.calls.filter((llamada) => llamada[0] === 'POST')).toHaveLength(1)
    expect(pedir.mock.calls.filter((llamada) => llamada[1]?.toString().startsWith('/api/turnos?'))).toHaveLength(2)
  })

  it('muestra el aviso de cambio y el de error sin agenda parcial', async () => {
    for (const [codigo, esperado] of [
      ['NO_ENCONTRADO', 'Puede que el paciente lo haya cancelado o cambiado de horario. Actualizamos la agenda.'],
      ['RN-TU-01', 'Puede que el paciente lo haya cancelado o cambiado de horario. Actualizamos la agenda.'],
      ['ERROR_INTERNO', 'No pudimos cargar la agenda'],
      ['SIN_CONEXION', 'No pudimos cargar la agenda'],
    ] as const) {
      const { cliente } = clienteFalso({
        errorCancelar: Object.assign(new Error('falló'), { codigo }),
      })
      const control = crearControlAgenda({ cliente, ancho: 390, ahora, alCambiar: () => {} })
      await control.cargar()
      await estabilizar()
      control.manejadores['cancelar-turno']?.(new NodoPrueba().con('data-turno-id', '3'))
      await control.manejadores['confirmar-cancelacion']?.(new NodoPrueba())
      await estabilizar()

      expect(textos(control.descriptor()).join(' ')).toContain(esperado)
    }
  })

  it('muestra error sin agenda parcial y permite reintentar', async () => {
    const { cliente } = clienteFalso({ errorTurnos: Object.assign(new Error('falló'), { codigo: 'ERROR_INTERNO' }) })
    const control = crearControlAgenda({ cliente, ancho: 390, ahora, alCambiar: () => {} })
    await control.cargar()
    await estabilizar()

    expect(textos(control.descriptor()).join(' ')).toContain('No pudimos cargar la agenda')
    expect(deClase(control.descriptor(), 'dia')).toHaveLength(0)
    expect(deClase(control.descriptor(), 'reintentar-agenda')).toHaveLength(1)
  })

  it('muestra el estado sin horarios con enlace a configuración', async () => {
    const pedir = vi.fn(async (_metodo: string, ruta: string) => {
      if (ruta === '/api/horarios') {
        return { franjas: [] }
      }
      if (ruta === '/api/bloqueos') {
        return { bloqueos: [] }
      }
      return []
    })
    const control = crearControlAgenda({
      cliente: { pedir, suscribirSesionVencida: () => () => {} } as unknown as ClienteApi,
      ancho: 390,
      ahora,
      alCambiar: () => {},
    })
    await control.cargar()
    await estabilizar()
    expect(textos(control.descriptor())).toContain('Todavía no cargaste tus horarios')
  })
})
