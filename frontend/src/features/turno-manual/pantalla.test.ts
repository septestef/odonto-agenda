import { describe, expect, it, vi } from 'vitest'
import type { ApiManual } from './api'
import type { DatosPaciente, DisponibilidadPublica, PerfilManual, ReservaConfirmada } from './modelo'
import { crearControlManual, textosDeManual } from './pantalla'

const PERFIL: PerfilManual = {
  fechaMinima: '2026-11-23',
  fechaMaxima: '2027-01-22',
  prestaciones: [
    { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
    { id: 2, nombre: 'Control', duracionMinutos: 15 },
  ],
}

const DISPONIBILIDAD: DisponibilidadPublica = {
  fecha: '2026-11-25',
  prestacionId: 1,
  duracionMinutos: 30,
  horarios: [
    { inicio: '2026-11-25T09:15:00-03:00', hora: '09:15' },
    { inicio: '2026-11-25T09:45:00-03:00', hora: '09:45' },
  ],
}

const DATOS: DatosPaciente = {
  dni: '30123456',
  nombre: 'Luis',
  apellido: 'Ramírez',
  telefono: '+5493511234567',
  email: 'luis@ejemplo.com',
}

const RESERVA: ReservaConfirmada = {
  inicio: '2026-11-25T09:15:00-03:00',
  fin: '2026-11-25T09:45:00-03:00',
  prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
  odontologo: { nombre: 'Ana', apellido: 'Gómez', direccionConsultorio: null },
  email: DATOS.email,
}

interface ApiFalsa {
  api: ApiManual
  perfil: ReturnType<typeof vi.fn>
  disponibilidad: ReturnType<typeof vi.fn>
  crear: ReturnType<typeof vi.fn>
}

function apiFalsa(carga?: {
  crear?: (solicitud: unknown) => Promise<ReservaConfirmada>
  disponibilidad?: (prestacionId: number, fecha: string) => Promise<DisponibilidadPublica>
}): ApiFalsa {
  const perfil = vi.fn(async () => PERFIL)
  const disponibilidad = vi.fn(
    carga?.disponibilidad ?? (async () => DISPONIBILIDAD),
  )
  const crear = vi.fn(carga?.crear ?? (async () => RESERVA))
  return { api: { perfil, disponibilidad, crear }, perfil, disponibilidad, crear }
}

async function esperar(): Promise<void> {
  for (let vuelta = 0; vuelta < 10; vuelta += 1) {
    await Promise.resolve()
  }
}

function errorApi(codigo: string, mensaje: string, detalles?: unknown): Error & {
  codigo: string
  mensaje: string
  detalles?: unknown
} {
  return Object.assign(new Error(mensaje), { codigo, mensaje, ...(detalles === undefined ? {} : { detalles }) })
}

describe('pantalla de turno manual', () => {
  it('abre en un diálogo accesible con el esqueleto de carga', () => {
    const { api } = apiFalsa()
    const control = crearControlManual({ api })
    const descriptor = control.descriptor()

    expect(descriptor.clase).toBe('overlay-manual')
    expect(descriptor.hijos?.[0]?.etiqueta).toBe('dialog')
    expect(descriptor.hijos?.[0]?.atributos?.['aria-labelledby']).toBe('titulo-formulario-manual')
    expect(textosDeManual(descriptor)).toContain('Cargando tus prestaciones.')
  })

  it('muestra prestaciones, navegador de días y selector de horarios compartidos', async () => {
    const falsa = apiFalsa()
    const control = crearControlManual({ api: falsa.api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    const textos = textosDeManual(control.descriptor())
    expect(textos).toContain('Cargar un turno')
    expect(textos).toContain('1 · Prestación')
    expect(textos).toContain('3 · Horario')
    expect(textos).toContain('09:15')
    expect(falsa.disponibilidad).toHaveBeenCalledWith(1, PERFIL.fechaMinima)
    expect(control.manejadores()['horario-0']).toBeTypeOf('function')
  })

  it('muestra el resumen del turno elegido y el formulario del paciente', async () => {
    const falsa = apiFalsa()
    const control = crearControlManual({ api: falsa.api, nombreOdontologo: 'Dra. Ana Gómez' })
    await control.iniciar()
    await control.elegirPrestacion(1)
    control.elegirHorario(control.estado().horarios[0]!)
    control.irADatos()

    const textos = textosDeManual(control.descriptor())
    expect(textos).toContain('Prestación')
    expect(textos).toContain('Dra. Ana Gómez')
    expect(textos).toContain('El link para consultar, cancelar o reprogramar llega por email al paciente.')
  })

  it('muestra un estado vacío cuando el día no tiene horarios', async () => {
    const falsa = apiFalsa({
      disponibilidad: async () => ({ ...DISPONIBILIDAD, horarios: [] }),
    })
    const control = crearControlManual({ api: falsa.api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    expect(control.estado().paso).toBe('sin-horarios')
    expect(textosDeManual(control.descriptor())).toContain('No hay horarios libres este día')
  })

  it('confirma el turno y dispara el callback de éxito', async () => {
    const falsa = apiFalsa()
    const alExito = vi.fn()
    const control = crearControlManual({ api: falsa.api, alExito })
    await control.iniciar()
    await control.elegirPrestacion(1)
    control.elegirHorario(control.estado().horarios[0]!)
    control.irADatos()

    await control.confirmar(DATOS)

    expect(falsa.crear).toHaveBeenCalledWith({
      prestacionId: 1,
      inicio: '2026-11-25T09:15:00-03:00',
      dni: DATOS.dni,
      nombre: DATOS.nombre,
      apellido: DATOS.apellido,
      telefono: DATOS.telefono,
      email: DATOS.email,
    })
    expect(alExito).toHaveBeenCalledWith(RESERVA)
    expect(control.estado().paso).toBe('exito')
    expect(textosDeManual(control.descriptor())).toContain('Turno cargado')
  })

  it('anuncia el conflicto RN-TU-01 con el texto exacto y refresca sin perder datos', async () => {
    const falsa = apiFalsa({
      crear: vi.fn(async () => {
        throw errorApi('RN-TU-01', 'El horario ya está reservado')
      }),
      disponibilidad: vi.fn(async (_id: number, fecha: string) =>
        fecha === DISPONIBILIDAD.fecha
          ? { ...DISPONIBILIDAD, horarios: [DISPONIBILIDAD.horarios[1]!] }
          : DISPONIBILIDAD,
      ),
    })
    const control = crearControlManual({ api: falsa.api })
    await control.iniciar()
    await control.elegirPrestacion(1)
    control.elegirHorario(control.estado().horarios[0]!)
    control.irADatos()

    await control.confirmar(DATOS)
    await esperar()

    const textos = textosDeManual(control.descriptor())
    expect(textos).toContain('Ese horario acaba de ser tomado, elegí otro')
    expect(textos).toContain('Continuar con los datos del paciente')
    expect(textos).toContain('Los datos siguen cargados')
    expect(textos.join(' ')).toContain('30123456')
    expect(control.estado().datos).toEqual(DATOS)
    expect(control.estado().horarios).toEqual([DISPONIBILIDAD.horarios[1]])
    expect(falsa.disponibilidad).toHaveBeenCalledTimes(2)
  })

  it.each([
    ['RN-TU-11', 'Solo se pueden cargar turnos hasta 60 días hacia adelante'],
    ['RN-AG-05', 'Ese horario no está disponible en la agenda'],
    ['RN-TU-12', 'Ese horario ya pasó'],
  ])('anuncia el rechazo %s y recarga horarios', async (codigo, texto) => {
    const falsa = apiFalsa({
      crear: vi.fn(async () => {
        throw errorApi(codigo, 'Rechazado')
      }),
    })
    const control = crearControlManual({ api: falsa.api })
    await control.iniciar()
    await control.elegirPrestacion(1)
    control.elegirHorario(control.estado().horarios[0]!)
    control.irADatos()

    await control.confirmar(DATOS)
    await esperar()

    expect(textosDeManual(control.descriptor())).toContain(texto)
    expect(control.estado().datos).toEqual(DATOS)
    expect(falsa.disponibilidad).toHaveBeenCalledTimes(2)
  })

  it('mapea VALIDACION a errores por campo y mantiene los datos', async () => {
    const falsa = apiFalsa({
      crear: vi.fn(async () => {
        throw errorApi('VALIDACION', 'Datos inválidos', [
          { campo: 'email', mensaje: 'El email no es válido' },
        ])
      }),
    })
    const control = crearControlManual({ api: falsa.api })
    await control.iniciar()
    await control.elegirPrestacion(1)
    control.elegirHorario(control.estado().horarios[0]!)
    control.irADatos()

    await control.confirmar(DATOS)

    expect(control.estado().paso).toBe('completando-datos')
    expect(control.estado().errores.email).toBe('El email no es válido')
    expect(control.estado().datos).toEqual(DATOS)
  })

  it.each(['NO_AUTORIZADO', 'PROHIBIDO', 'ERROR_DESCONOCIDO'])(
    'conserva los datos ante %s y permite volver a intentar',
    async (codigo) => {
      const falsa = apiFalsa({
        crear: vi.fn(async () => {
          throw errorApi(codigo, 'Sin_accesso')
        }),
      })
      const control = crearControlManual({ api: falsa.api })
      await control.iniciar()
      await control.elegirPrestacion(1)
      control.elegirHorario(control.estado().horarios[0]!)
      control.irADatos()

      await control.confirmar(DATOS)

      expect(control.estado().paso).toBe('error-envio')
      expect(control.estado().datos).toEqual(DATOS)
      expect(textosDeManual(control.descriptor())).toContain('Reintentar')
    },
  )

  it('valida localmente antes de enviar y no toca la API si faltan datos', async () => {
    const falsa = apiFalsa()
    const control = crearControlManual({ api: falsa.api })
    await control.iniciar()
    await control.elegirPrestacion(1)
    control.elegirHorario(control.estado().horarios[0]!)
    control.irADatos()

    await control.confirmar({ ...DATOS, dni: '123' })

    expect(falsa.crear).not.toHaveBeenCalled()
    expect(control.estado().paso).toBe('completando-datos')
    expect(control.estado().errores.dni).toBe('Ingresá el DNI del paciente sin puntos, 7 u 8 dígitos.')
  })

  it('cierra el diálogo con el botón de cerrar', async () => {
    const falsa = apiFalsa()
    const alCerrar = vi.fn()
    const control = crearControlManual({ api: falsa.api, alCerrar })
    await control.iniciar()

    control.manejadores()['cerrar-manual']?.({} as never)

    expect(alCerrar).toHaveBeenCalledOnce()
  })
})
