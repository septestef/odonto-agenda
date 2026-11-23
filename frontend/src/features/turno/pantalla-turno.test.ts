import { describe, expect, it, vi } from 'vitest'
import type { Descriptor } from '../../shared/ui/componentes'
import type { NodoUI } from '../../shared/ui/adaptador'
import type { DisponibilidadPublica, PerfilReserva } from '../reservar/modelo'
import type { ApiGestion } from './api-gestion'
import type { TurnoGestion } from './modelo'
import { crearControlTurno } from './pantalla-turno'

const TOKEN = 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-AbCdE'

const TURNO: TurnoGestion = {
  inicio: '2026-11-27T09:00:00-03:00',
  fin: '2026-11-27T09:30:00-03:00',
  estado: 'RESERVADO',
  prestacion: { id: 1, nombre: 'Consulta' },
  odontologo: { slug: 'demo', nombre: 'Ana', apellido: 'Pérez', direccionConsultorio: 'Av. Colón 1234, Córdoba' },
  puedeCancelar: true,
  puedeReprogramar: true,
}

const CANCELADO: TurnoGestion = { ...TURNO, estado: 'CANCELADO', puedeCancelar: false, puedeReprogramar: false }

const PERFIL: PerfilReserva = {
  slug: 'demo',
  nombre: 'Ana',
  apellido: 'Pérez',
  direccionConsultorio: 'Av. Colón 1234, Córdoba',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  fechaMinima: '2026-11-24',
  fechaMaxima: '2027-01-21',
  prestaciones: [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }],
}

const HORARIO = { inicio: '2026-11-26T10:30:00-03:00', hora: '10:30' }

function disponibilidad(fecha: string, horarios = [HORARIO]): DisponibilidadPublica {
  return { fecha, prestacionId: 1, duracionMinutos: 30, horarios }
}

const NODO = {} as NodoUI

function rechazo(codigo: string): Error {
  return Object.assign(new Error('Mensaje del servidor'), { codigo, mensaje: 'Mensaje del servidor' })
}

function preparar(opciones: {
  consultar?: ApiGestion['consultar']
  cancelar?: ApiGestion['cancelar']
  reprogramar?: ApiGestion['reprogramar']
  disponibilidad?: (slug: string, prestacionId: number, fecha: string) => Promise<DisponibilidadPublica>
  token?: string
} = {}) {
  const consultar = vi.fn(opciones.consultar ?? (async () => TURNO))
  const cancelar = vi.fn(opciones.cancelar ?? (async () => {}))
  const reprogramar = vi.fn(opciones.reprogramar ?? (async () => {}))
  const perfil = vi.fn(async () => PERFIL)
  const disp = vi.fn(opciones.disponibilidad ?? (async (_slug: string, _id: number, fecha: string) => disponibilidad(fecha)))
  const control = crearControlTurno({
    token: opciones.token ?? TOKEN,
    api: { consultar, cancelar, reprogramar },
    apiPublica: { perfil, disponibilidad: disp },
  })
  return { control, consultar, cancelar, reprogramar, perfil, disponibilidad: disp }
}

function textos(descriptor: Descriptor): string[] {
  return [...(descriptor.texto === undefined ? [] : [descriptor.texto]), ...(descriptor.hijos ?? []).flatMap(textos)]
}

function todo(descriptor: Descriptor): string {
  return textos(descriptor).join(' | ')
}

function buscar(descriptor: Descriptor, criterio: (item: Descriptor) => boolean): Descriptor | undefined {
  if (criterio(descriptor)) {
    return descriptor
  }
  for (const hijo of descriptor.hijos ?? []) {
    const encontrado = buscar(hijo, criterio)
    if (encontrado !== undefined) {
      return encontrado
    }
  }
  return undefined
}

const porAccion = (accion: string) => (item: Descriptor) => item.accion === accion

describe('pantalla /turno/{token}', () => {
  it('muestra el turno activo con sus datos y las acciones permitidas', async () => {
    const { control, consultar } = preparar()
    await control.iniciar()
    const pantalla = control.descriptor()

    expect(consultar).toHaveBeenCalledWith(TOKEN)
    expect(todo(pantalla)).toContain('Tu turno con Ana Pérez')
    expect(todo(pantalla)).toContain('Viernes 27 de noviembre')
    expect(todo(pantalla)).toContain('09:00 a 09:30')
    expect(todo(pantalla)).toContain('Av. Colón 1234, Córdoba')
    expect(buscar(pantalla, porAccion('abrir-reprogramacion'))?.texto).toBe('Cambiar horario')
    expect(buscar(pantalla, porAccion('abrir-cancelacion'))?.texto).toBe('Cancelar turno')
    expect(todo(pantalla)).toContain('Este link es personal')
    expect(JSON.stringify(pantalla)).not.toContain(TOKEN)
  })

  it('sin permiso para reprogramar muestra solo cancelar y explica cómo cambiar', async () => {
    const { control } = preparar({ consultar: async () => ({ ...TURNO, puedeReprogramar: false }) })
    await control.iniciar()
    const pantalla = control.descriptor()

    expect(buscar(pantalla, porAccion('abrir-reprogramacion'))).toBeUndefined()
    expect(buscar(pantalla, porAccion('abrir-cancelacion'))).toBeDefined()
    expect(todo(pantalla)).toContain('Este turno no se puede cambiar desde el link')
    expect(buscar(pantalla, porAccion('navegar'))?.atributos?.['href']).toBe('/reservar/demo')
  })

  it('un 404 muestra "No encontramos este turno" y un 502 muestra un error de conexión', async () => {
    const noExiste = preparar({ consultar: async () => Promise.reject(rechazo('NO_ENCONTRADO')) })
    await noExiste.control.iniciar()
    expect(todo(noExiste.control.descriptor())).toContain('No encontramos este turno')
    expect(buscar(noExiste.control.descriptor(), porAccion('reintentar-turno'))).toBeUndefined()

    const caido = preparar({ consultar: async () => Promise.reject(rechazo('ERROR_DESCONOCIDO')) })
    await caido.control.iniciar()
    const pantalla = caido.control.descriptor()
    expect(todo(pantalla)).toContain('No pudimos conectarnos')
    expect(todo(pantalla)).not.toContain('No encontramos este turno')
    expect(buscar(pantalla, porAccion('reintentar-turno'))).toBeDefined()
  })

  it('no llama a la API con un token mal formado', async () => {
    const { control, consultar } = preparar({ token: 'abc' })
    await control.iniciar()

    expect(consultar).not.toHaveBeenCalled()
    expect(todo(control.descriptor())).toContain('No encontramos este turno')
  })

  it('confirma la cancelación, relee el turno y lo muestra cancelado', async () => {
    const { control, cancelar, consultar } = preparar()
    await control.iniciar()
    control.abrirCancelacion()
    const dialogo = control.descriptor()

    const caja = buscar(dialogo, (item) => item.atributos?.['role'] === 'alertdialog')
    expect(caja?.teclas).toEqual({ Escape: 'cerrar-cancelacion' })
    expect(todo(dialogo)).toContain('¿Cancelás tu turno?')
    expect(todo(dialogo)).toContain('Viernes 27 de noviembre, 09:00')
    expect(buscar(dialogo, porAccion('cerrar-cancelacion'))?.enfocar).toBe(true)
    expect(buscar(dialogo, (item) => item.clase === 'gestion-layout')?.atributos?.['aria-hidden']).toBe('true')

    consultar.mockResolvedValueOnce(CANCELADO)
    await control.confirmarCancelacion()

    expect(cancelar).toHaveBeenCalledTimes(1)
    expect(consultar).toHaveBeenCalledTimes(2)
    const final = control.descriptor()
    expect(todo(final)).toContain('Cancelaste tu turno')
    expect(todo(final)).toContain('Cancelado')
    expect(buscar(final, porAccion('abrir-cancelacion'))).toBeUndefined()
  })

  it('al cerrar la confirmación no cancela y devuelve el foco a "Cancelar turno"', async () => {
    const { control, cancelar } = preparar()
    await control.iniciar()
    control.abrirCancelacion()
    control.manejadores()['cerrar-cancelacion']?.(NODO)

    expect(cancelar).not.toHaveBeenCalled()
    expect(buscar(control.descriptor(), porAccion('abrir-cancelacion'))?.enfocar).toBe(true)
  })

  it('un doble toque en "Sí, cancelar turno" envía una sola solicitud', async () => {
    let terminar: () => void = () => {}
    const { control, cancelar } = preparar({ cancelar: () => new Promise<void>((resolver) => (terminar = resolver)) })
    await control.iniciar()
    control.abrirCancelacion()

    const primera = control.confirmarCancelacion()
    const segunda = control.confirmarCancelacion()
    const enviando = control.descriptor()
    expect(todo(enviando)).toContain('Cancelando…')
    expect(buscar(enviando, porAccion('confirmar-cancelacion'))?.atributos?.['disabled']).toBe('')
    terminar()
    await Promise.all([primera, segunda])

    expect(cancelar).toHaveBeenCalledTimes(1)
  })

  it('si el turno ya empezó al cancelar, avisa y lo muestra en solo lectura', async () => {
    const { control, consultar } = preparar({ cancelar: async () => Promise.reject(rechazo('RN-TU-07')) })
    await control.iniciar()
    control.abrirCancelacion()
    consultar.mockResolvedValueOnce({ ...TURNO, puedeCancelar: false, puedeReprogramar: false })
    await control.confirmarCancelacion()
    const pantalla = control.descriptor()

    expect(todo(pantalla)).toContain('No pudimos hacer el cambio')
    expect(todo(pantalla)).toContain('Pasado')
    expect(buscar(pantalla, porAccion('confirmar-cancelacion'))).toBeUndefined()
  })

  it('un error de red al cancelar avisa dentro del diálogo y no reintenta solo', async () => {
    const { control, cancelar } = preparar({ cancelar: async () => Promise.reject(rechazo('SIN_CONEXION')) })
    await control.iniciar()
    control.abrirCancelacion()
    await control.confirmarCancelacion()

    expect(cancelar).toHaveBeenCalledTimes(1)
    expect(control.estado().vista).toBe('confirmar-cancelacion')
    expect(todo(control.descriptor())).toContain('No pudimos conectarnos')
  })

  it('cambia el horario con la misma prestación y relee el turno original', async () => {
    const { control, perfil, disponibilidad: disp, reprogramar, consultar } = preparar()
    await control.iniciar()
    await control.abrirReprogramacion()

    expect(perfil).toHaveBeenCalledWith('demo')
    expect(disp).toHaveBeenCalledWith('demo', 1, '2026-11-27')
    await control.elegirFecha('2026-11-26')
    expect(disp).toHaveBeenLastCalledWith('demo', 1, '2026-11-26')

    const selector = control.descriptor()
    expect(todo(selector)).toContain('Consulta · 30 min')
    expect(todo(selector)).toContain('Horarios libres el jueves 26')
    expect(buscar(selector, porAccion('dia:2026-11-27'))?.clase).toBe('day day--actual')
    expect(buscar(selector, porAccion('confirmar-cambio'))?.atributos?.['disabled']).toBe('')

    control.manejadores()['horario-0']?.(NODO)
    const comparando = control.descriptor()
    expect(todo(comparando)).toContain('Viernes 27/11, 09:00')
    expect(todo(comparando)).toContain('Jueves 26/11, 10:30 a 11:00')

    consultar.mockResolvedValueOnce(CANCELADO)
    await control.confirmarCambio()

    expect(reprogramar).toHaveBeenCalledWith(TOKEN, '2026-11-26T10:30:00-03:00')
    const final = control.descriptor()
    expect(todo(final)).toContain('Listo, cambiaste tu turno al jueves 26/11 a las 10:30')
    expect(buscar(final, porAccion('navegar'))).toBeUndefined()
  })

  it('si el horario fue tomado, avisa y vuelve a pedir la disponibilidad del día', async () => {
    const { control, disponibilidad: disp } = preparar({
      reprogramar: async () => Promise.reject(rechazo('RN-TU-01')),
    })
    await control.iniciar()
    await control.abrirReprogramacion()
    await control.elegirFecha('2026-11-26')
    control.elegirHorario(HORARIO)
    disp.mockResolvedValueOnce(disponibilidad('2026-11-26', []))
    const llamadas = disp.mock.calls.length
    await control.confirmarCambio()
    const pantalla = control.descriptor()

    expect(disp.mock.calls.length).toBe(llamadas + 1)
    expect(disp).toHaveBeenLastCalledWith('demo', 1, '2026-11-26')
    expect(todo(pantalla)).toContain('Ese horario acaba de ser tomado')
    expect(todo(pantalla)).toContain('No hay horarios libres este día')
    expect(control.estado().horario).toBeUndefined()
  })

  it('RN-SE-04 relee el turno y deja solo la cancelación', async () => {
    const { control, consultar } = preparar({ reprogramar: async () => Promise.reject(rechazo('RN-SE-04')) })
    await control.iniciar()
    await control.abrirReprogramacion()
    control.elegirHorario({ inicio: '2026-11-27T10:30:00-03:00', hora: '10:30' })
    consultar.mockResolvedValueOnce({ ...TURNO, puedeReprogramar: false })
    await control.confirmarCambio()
    const pantalla = control.descriptor()

    expect(control.estado().vista).toBe('activo')
    expect(todo(pantalla)).toContain('Este turno no se puede cambiar desde el link')
    expect(buscar(pantalla, porAccion('abrir-reprogramacion'))).toBeUndefined()
  })

  it('muestra un turno pasado en solo lectura con "Reservar otro turno"', async () => {
    const { control } = preparar({ consultar: async () => ({ ...TURNO, puedeCancelar: false, puedeReprogramar: false }) })
    await control.iniciar()
    const pantalla = control.descriptor()

    expect(todo(pantalla)).toContain('Este turno ya pasó. Solo podés consultarlo.')
    expect(todo(pantalla)).toContain('Pasado')
    expect(buscar(pantalla, porAccion('navegar'))?.texto).toBe('Reservar otro turno')
  })

  it('el link viejo de un turno reprogramado se ve cancelado y sin acciones', async () => {
    const { control } = preparar({ consultar: async () => CANCELADO })
    await control.iniciar()
    const pantalla = control.descriptor()

    expect(todo(pantalla)).toContain('Cancelado')
    expect(todo(pantalla)).not.toContain('Av. Colón 1234, Córdoba')
    expect(buscar(pantalla, porAccion('abrir-cancelacion'))).toBeUndefined()
    expect(buscar(pantalla, porAccion('abrir-reprogramacion'))).toBeUndefined()
  })

  it('mueve el foco al título solo cuando cambia la vista', async () => {
    const { control } = preparar()
    control.descriptor()
    await control.iniciar()
    const titulo = (d: Descriptor) => buscar(d, (item) => item.etiqueta === 'h1')

    expect(titulo(control.descriptor())?.enfocar).toBe(true)
    expect(titulo(control.descriptor())?.enfocar).toBeUndefined()
  })
})
