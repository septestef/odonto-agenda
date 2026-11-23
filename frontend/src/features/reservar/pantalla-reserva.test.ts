import { describe, expect, it, vi } from 'vitest'
import type { NodoUI } from '../../shared/ui/adaptador'
import type { Descriptor } from '../../shared/ui/componentes'
import type { ApiPublica } from './api-publica'
import type {
  DatosPaciente,
  DisponibilidadPublica,
  PerfilReserva,
  ReservaConfirmada,
} from './modelo'
import {
  crearControlReserva,
  fechaDeInicio,
  formatearDiaCorto,
  formatearFechaLarga,
  formatearPrestacion,
  formatearRangoSemana,
  horaDeInicio,
  numeroDia,
} from './pantalla-reserva'

const PERFIL: PerfilReserva = {
  slug: 'ana-gomez',
  nombre: 'Ana',
  apellido: 'Gómez',
  direccionConsultorio: 'Av. Siempreviva 742',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  fechaMinima: '2026-11-26',
  fechaMaxima: '2027-01-24',
  prestaciones: [
    { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
    { id: 2, nombre: 'Control', duracionMinutos: 15 },
  ],
}

const DISPONIBILIDAD: DisponibilidadPublica = {
  fecha: '2026-11-27',
  prestacionId: 1,
  duracionMinutos: 30,
  horarios: [
    { inicio: '2026-11-27T12:00:00Z', hora: '09:00' },
    { inicio: '2026-11-27T12:15:00Z', hora: '09:15' },
  ],
}

const RESERVA: ReservaConfirmada = {
  inicio: '2026-11-27T12:00:00Z',
  fin: '2026-11-27T12:30:00Z',
  prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
  odontologo: { nombre: 'Ana', apellido: 'Gómez', direccionConsultorio: 'Av. Siempreviva 742' },
  email: 'ana@consultorio.com',
}

const DATOS: DatosPaciente = {
  dni: ' 12345678 ',
  nombre: ' Ana ',
  apellido: ' Gómez ',
  telefono: ' +5493511234567 ',
  email: ' ana@consultorio.com ',
}

const DATOS_INVALIDOS: DatosPaciente = {
  dni: '12.345.678',
  nombre: '',
  apellido: '',
  telefono: '3511234567',
  email: 'ana@',
}

function apiFalsa(opciones: {
  perfil?: (slug: string) => Promise<PerfilReserva>
  disponibilidad?: (slug: string, prestacionId: number, fecha: string) => Promise<DisponibilidadPublica>
  reservar?: (slug: string, solicitud: unknown) => Promise<ReservaConfirmada>
} = {}) {
  const perfil = vi.fn(opciones.perfil ?? (async () => PERFIL))
  const disponibilidad = vi.fn(opciones.disponibilidad ?? (async () => DISPONIBILIDAD))
  const reservar = vi.fn(opciones.reservar ?? (async () => RESERVA))
  const api: ApiPublica = { perfil, disponibilidad, reservar }

  return { api, perfil, disponibilidad, reservar }
}

function nodosDe(descriptor: Descriptor | undefined): Descriptor[] {
  if (descriptor === undefined) {
    return []
  }
  return [descriptor, ...(descriptor.hijos ?? []).flatMap(nodosDe)]
}

function textosDe(descriptor: Descriptor | undefined): string[] {
  return nodosDe(descriptor)
    .map((nodo) => nodo.texto)
    .filter((texto): texto is string => texto !== undefined)
}

function buscarPorAccion(descriptor: Descriptor | undefined, accion: string): Descriptor | undefined {
  return nodosDe(descriptor).find((nodo) => nodo.accion === accion)
}

function nodoFalso(valor = ''): NodoUI {
  return {
    textContent: '',
    value: valor,
    append: () => {},
    setAttribute: () => {},
    addEventListener: () => {},
  }
}

interface FormularioFalso extends NodoUI {
  querySelector(selector: string): { value: string } | null
}

function formularioFalso(datos: DatosPaciente): FormularioFalso {
  return {
    ...nodoFalso(),
    querySelector(selector: string) {
      const nombre = /\[data-campo="([^"]+)"\]/.exec(selector)?.[1]
      if (nombre === undefined) {
        return null
      }
      return { value: datos[nombre as keyof DatosPaciente] }
    },
  }
}

async function esperar(): Promise<void> {
  for (let vuelta = 0; vuelta < 8; vuelta += 1) {
    await Promise.resolve()
  }
}

async function seleccionarHastaDatos(opciones: {
  perfil?: (slug: string) => Promise<PerfilReserva>
  disponibilidad?: (slug: string, prestacionId: number, fecha: string) => Promise<DisponibilidadPublica>
  reservar?: (slug: string, solicitud: unknown) => Promise<ReservaConfirmada>
} = {}) {
  const falsa = apiFalsa(opciones)
  const control = crearControlReserva({ slug: 'ana-gomez', api: falsa.api })
  await control.iniciar()
  await control.elegirPrestacion(1)
  const horario = control.estado().horarios[0]
  if (horario !== undefined) {
    control.elegirHorario(horario)
  }
  control.irADatos()
  return { ...falsa, control }
}

describe('formato de fechas, horas y prestaciones', () => {
  it('formatea la fecha larga en español rioplatense', () => {
    expect(formatearFechaLarga('2026-11-27')).toBe('Viernes 27 de noviembre')
  })

  it('formatea el día corto y el número', () => {
    expect(formatearDiaCorto('2026-11-27').toLowerCase()).toContain('vie')
    expect(numeroDia('2026-11-27')).toBe('27')
  })

  it('formatea un rango de semana dentro del mismo mes', () => {
    expect(formatearRangoSemana('2026-11-02')).toBe('Semana del 2 al 8 de noviembre')
  })

  it('formatea un rango de semana que cruza de mes', () => {
    expect(formatearRangoSemana('2026-11-26')).toBe('Semana del 26 de noviembre al 2 de diciembre')
  })

  it('formatea una prestación con su duración', () => {
    expect(formatearPrestacion({ id: 1, nombre: 'Consulta', duracionMinutos: 30 })).toBe('Consulta · 30 min')
  })

  it('obtiene la hora argentina de un instante', () => {
    expect(horaDeInicio('2026-11-27T12:00:00Z')).toBe('09:00')
  })

  it('obtiene la fecha argentina de un instante', () => {
    expect(fechaDeInicio('2026-11-27T12:00:00Z')).toBe('2026-11-27')
  })

  it('tolera un inicio inválido al extraer la fecha', () => {
    expect(fechaDeInicio('no-es-fecha')).toBe('')
  })
})

describe('carga del perfil', () => {
  it('muestra el esqueleto antes de cargar', () => {
    const { api } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    const textos = textosDe(control.descriptor())

    expect(control.descriptor().clase).toBe('reserva-publica')
    expect(textos).toContain('Reservá tu turno')
    expect(textos).toContain('Cargando el consultorio.')
  })

  it('muestra el perfil, la dirección y las prestaciones activas', async () => {
    const { api, perfil } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })

    await control.iniciar()

    expect(perfil).toHaveBeenCalledWith('ana-gomez')
    expect(control.estado().paso).toBe('eligiendo-prestacion')
    const textos = textosDe(control.descriptor())
    expect(textos).toContain('Ana Gómez')
    expect(textos).toContain('Av. Siempreviva 742')
    expect(textos).toContain('Consulta')
    expect(textos).toContain('30 min')
    expect(textos).toContain('Paso 1 de 4: Prestación')
  })

  it('no muestra el email del odontólogo ni un token', async () => {
    const { api } = apiFalsa({
      perfil: async () => ({ ...PERFIL, email: 'privado@consultorio.com' } as PerfilReserva),
    })
    const control = crearControlReserva({ slug: 'ana-gomez', api })

    await control.iniciar()

    const textos = textosDe(control.descriptor())
    expect(textos).not.toContain('privado@consultorio.com')
    expect(textos.join(' ')).not.toContain('token')
  })

  it('muestra un error genérico ante un link inválido y permite reintentar', async () => {
    const perfil = vi
      .fn(async () => PERFIL)
      .mockRejectedValueOnce({ codigo: 'NO_ENCONTRADO', mensaje: 'No encontrado' })
    const api: ApiPublica = {
      perfil,
      disponibilidad: vi.fn(async () => DISPONIBILIDAD),
      reservar: vi.fn(async () => RESERVA),
    }
    const control = crearControlReserva({ slug: 'no-existe', api })

    await control.iniciar()
    expect(control.estado().paso).toBe('error-perfil')
    expect(textosDe(control.descriptor())).toContain('Este link no es válido')

    await control.iniciar()
    expect(control.estado().paso).toBe('eligiendo-prestacion')
  })

  it('muestra un estado vacío si el consultorio no tiene prestaciones', async () => {
    const { api } = apiFalsa({ perfil: async () => ({ ...PERFIL, prestaciones: [] }) })
    const control = crearControlReserva({ slug: 'ana-gomez', api })

    await control.iniciar()

    expect(textosDe(control.descriptor())).toContain('Este consultorio no tiene prestaciones disponibles')
  })
})

describe('selección de prestación y fecha', () => {
  it('carga la disponibilidad de la fecha mínima al elegir prestación', async () => {
    const { disponibilidad, control } = await seleccionarHastaDatos()

    expect(disponibilidad).toHaveBeenCalledWith('ana-gomez', 1, PERFIL.fechaMinima)
    expect(control.estado().prestacionId).toBe(1)
    expect(control.estado().horarios).toHaveLength(2)
  })

  it('ignora una prestación inexistente', async () => {
    const { api, disponibilidad } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()

    await control.elegirPrestacion(99)

    expect(disponibilidad).not.toHaveBeenCalled()
    expect(control.estado().paso).toBe('eligiendo-prestacion')
  })

  it('marca los días fuera de la ventana como deshabilitados', async () => {
    const { api } = apiFalsa({
      perfil: async () => ({ ...PERFIL, fechaMinima: '2027-01-20', fechaMaxima: '2027-01-24' }),
    })
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()

    const descriptor = control.descriptor()
    expect(buscarPorAccion(descriptor, 'dia:2027-01-24')?.atributos?.['disabled']).toBeUndefined()
    expect(buscarPorAccion(descriptor, 'dia:2027-01-25')?.atributos?.['disabled']).toBe('')
    expect(buscarPorAccion(descriptor, 'dia:2027-01-26')?.atributos?.['disabled']).toBe('')
  })

  it('incluye un input de fecha con min y max del perfil', async () => {
    const { api } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()

    const salto = buscarPorAccion(control.descriptor(), 'salto-fecha')
    expect(salto).toMatchObject({
      etiqueta: 'input',
      atributos: { type: 'date', min: PERFIL.fechaMinima, max: PERFIL.fechaMaxima },
    })
  })

  it('deshabilita la navegación semanal en los bordes de la ventana', async () => {
    const { api } = apiFalsa({
      perfil: async () => ({ ...PERFIL, fechaMinima: '2027-01-20', fechaMaxima: '2027-01-24' }),
    })
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()

    const descriptor = control.descriptor()
    expect(buscarPorAccion(descriptor, 'semana-anterior')?.atributos?.['disabled']).toBe('')
    expect(buscarPorAccion(descriptor, 'semana-siguiente')?.atributos?.['disabled']).toBe('')
  })

  it('ignora una fecha fuera de la ventana reservable', async () => {
    const { api, disponibilidad } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()

    await control.elegirFecha('2026-11-25')
    await control.elegirFecha('2027-01-25')

    expect(disponibilidad).not.toHaveBeenCalled()
  })

  it('carga la disponibilidad de la fecha elegida y salta a su semana', async () => {
    const { api, disponibilidad } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    await control.elegirFecha('2026-12-10')

    expect(disponibilidad).toHaveBeenCalledWith('ana-gomez', 1, '2026-12-10')
    expect(control.estado().fecha).toBe('2026-11-27')
    expect(control.estado().inicioSemana).toBe('2026-12-10')
  })

  it('navega de a semanas y no se pasa de la ventana', async () => {
    const { api } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()

    control.cambiarSemana(1)
    expect(control.estado().inicioSemana).toBe('2026-12-03')

    control.cambiarSemana(-1)
    expect(control.estado().inicioSemana).toBe('2026-11-26')

    control.cambiarSemana(-1)
    expect(control.estado().inicioSemana).toBe('2026-11-26')

    control.cambiarSemana(20)
    expect(control.estado().inicioSemana).toBe('2026-11-26')
  })

  it('busca el próximo día con horarios libres', async () => {
    const disponibilidad = vi.fn(async (_slug: string, _prestacionId: number, fecha: string) =>
      fecha === '2026-11-28' ? { ...DISPONIBILIDAD, fecha, horarios: DISPONIBILIDAD.horarios } : { ...DISPONIBILIDAD, fecha, horarios: [] },
    )
    const { api } = apiFalsa({ disponibilidad })
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    expect(control.estado().paso).toBe('sin-horarios')
    control.manejadores()['proximo-dia']?.(nodoFalso())
    await esperar()

    expect(control.estado().fecha).toBe('2026-11-28')
    expect(control.estado().horarios).toHaveLength(2)
    expect(control.estado().paso).toBe('eligiendo-horario')
  })
})

describe('horarios y formulario', () => {
  it('muestra el estado vacío con la acción de otro día', async () => {
    const { api } = apiFalsa({
      disponibilidad: async () => ({ ...DISPONIBILIDAD, horarios: [] }),
    })
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    const textos = textosDe(control.descriptor())
    expect(textos).toContain('No hay horarios libres este día')
    expect(textos).toContain('Ver el próximo día disponible')
    expect(buscarPorAccion(control.descriptor(), 'proximo-dia')).toBeDefined()
  })

  it('elige un horario y habilita el paso a datos', async () => {
    const { api } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    const horario = control.estado().horarios[1]
    expect(horario).toBeDefined()
    if (horario !== undefined) {
      control.elegirHorario(horario)
    }

    expect(control.estado().horario).toEqual(DISPONIBILIDAD.horarios[1])
    expect(buscarPorAccion(control.descriptor(), 'ir-datos')?.atributos?.['disabled']).toBeUndefined()
  })

  it('ignora un horario que no está en la disponibilidad', async () => {
    const { api } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    control.elegirHorario({ inicio: 'otro', hora: '23:00' })

    expect(control.estado().horario).toBeUndefined()
  })

  it('no avanza a datos sin horario elegido', async () => {
    const { api } = apiFalsa()
    const control = crearControlReserva({ slug: 'ana-gomez', api })
    await control.iniciar()
    await control.elegirPrestacion(1)

    control.irADatos()

    expect(control.estado().paso).toBe('eligiendo-horario')
  })

  it('muestra el resumen y los campos del paciente', async () => {
    const { control } = await seleccionarHastaDatos()

    const textos = textosDe(control.descriptor())
    expect(textos).toContain('Confirmá tu turno')
    expect(textos).toContain('Paso 4 de 4: Tus datos')
    expect(textos).toContain('Consulta · 30 min')
    expect(textos).toContain('Viernes 27 de noviembre, 09:00')
    expect(textos).toContain('Te mandamos ahí el link para gestionar tu turno.')
    const titulo = nodosDe(control.descriptor()).find((nodo) => nodo.texto === 'Confirmá tu turno')
    expect(titulo?.enfocar).toBe(true)
  })

  it('valida localmente y no envía la reserva con datos inválidos', async () => {
    const { control, reservar } = await seleccionarHastaDatos()

    await control.confirmar(DATOS_INVALIDOS)

    expect(reservar).not.toHaveBeenCalled()
    expect(control.estado().paso).toBe('completando-datos')
    expect(control.estado().errores.dni).toBeDefined()
    const textos = textosDe(control.descriptor())
    expect(textos).toContain('Ingresá tu DNI sin puntos, 7 u 8 dígitos.')
    expect(textos).toContain('Ingresá tu nombre.')
    expect(textos).toContain('Corregí los datos marcados para poder confirmar.')
    const primerCampo = nodosDe(control.descriptor()).find(
      (nodo) => nodo.etiqueta === 'input' && nodo.atributos?.['data-campo'] === 'dni',
    )
    expect(primerCampo?.enfocar).toBe(true)
  })

  it('envía una sola solicitud y muestra el éxito', async () => {
    const { control, reservar } = await seleccionarHastaDatos()

    await control.confirmar(DATOS)

    expect(reservar).toHaveBeenCalledTimes(1)
    expect(reservar).toHaveBeenCalledWith('ana-gomez', {
      prestacionId: 1,
      inicio: '2026-11-27T12:00:00Z',
      dni: '12345678',
      nombre: 'Ana',
      apellido: 'Gómez',
      telefono: '+5493511234567',
      email: 'ana@consultorio.com',
    })
    expect(control.estado().paso).toBe('exito')
    const textos = textosDe(control.descriptor())
    expect(textos).toContain('¡Turno reservado!')
    expect(textos).toContain('Te esperamos el viernes 27 de noviembre a las 09:00.')
    expect(textos).toContain('ana@consultorio.com')
  })

  it('no muestra ningún token en el éxito', async () => {
    const { control } = await seleccionarHastaDatos({
      reservar: async () => ({ ...RESERVA, token: 'token-secreto' } as ReservaConfirmada),
    })

    await control.confirmar(DATOS)

    expect(textosDe(control.descriptor()).join(' ')).not.toContain('token-secreto')
  })

  it('bloquea un segundo envío mientras la primera solicitud está en curso', async () => {
    let resolver!: (reserva: ReservaConfirmada) => void
    const pendiente = new Promise<ReservaConfirmada>((resolverPendiente) => {
      resolver = resolverPendiente
    })
    const reservar = vi.fn(async () => pendiente)
    const { control } = await seleccionarHastaDatos({ reservar })

    const primero = control.confirmar(DATOS)
    const segundo = control.confirmar(DATOS)
    await segundo

    expect(reservar).toHaveBeenCalledTimes(1)
    expect(control.estado().paso).toBe('enviando')
    expect(textosDe(control.descriptor())).toContain('Confirmando…')

    resolver(RESERVA)
    await primero
    expect(control.estado().paso).toBe('exito')
  })

  it('usa los manejadores del formulario para confirmar', async () => {
    const { control, reservar } = await seleccionarHastaDatos()

    control.manejadores()['confirmar']?.(formularioFalso(DATOS))
    await esperar()

    expect(reservar).toHaveBeenCalledTimes(1)
    expect(control.estado().paso).toBe('exito')
  })

  it('vuelve al horario conservando lo escrito', async () => {
    const { control } = await seleccionarHastaDatos()

    control.manejadores()['volver-horario']?.(formularioFalso(DATOS))

    expect(control.estado().paso).toBe('eligiendo-horario')
    expect(control.estado().datos).toEqual(DATOS)
  })
})

describe('errores de la reserva', () => {
  function rechazarCon(codigo: string, mensaje: string, detalles?: unknown) {
    return async () => {
      throw detalles === undefined ? { codigo, mensaje } : { codigo, mensaje, detalles }
    }
  }

  it('ante RN-TU-01 muestra el mensaje, refresca y conserva los datos', async () => {
    let llamadas = 0
    const disponibilidad = vi.fn(async () => {
      llamadas += 1
      return llamadas === 1
        ? DISPONIBILIDAD
        : { ...DISPONIBILIDAD, horarios: [DISPONIBILIDAD.horarios[1]!] }
    })
    const { control, reservar } = await seleccionarHastaDatos({
      disponibilidad,
      reservar: rechazarCon('RN-TU-01', 'Horario tomado'),
    })

    await control.confirmar(DATOS)

    expect(reservar).toHaveBeenCalledTimes(1)
    expect(disponibilidad).toHaveBeenCalledTimes(2)
    expect(control.estado().datos).toEqual(DATOS)
    expect(control.estado().horarios).toHaveLength(1)
    expect(control.estado().horario).toBeUndefined()
    const textos = textosDe(control.descriptor())
    expect(textos).toContain('Ese horario acaba de ser tomado, elegí otro')
    expect(textos).toContain('Tus datos siguen cargados')
  })

  it.each([
    ['RN-PA-03', 'Ya tenés 2 turnos futuros con este odontólogo'],
    ['RN-TU-03', 'Los turnos se reservan con al menos 24 h de anticipación'],
    ['RN-TU-11', 'Solo podés reservar hasta 60 días hacia adelante'],
    ['RN-AG-05', 'Ese horario dejó de estar disponible'],
  ])('traduce %s a un mensaje claro y no reintenta solo', async (codigo, mensaje) => {
    const { control, reservar } = await seleccionarHastaDatos({
      reservar: rechazarCon(codigo, 'Rechazado'),
    })

    await control.confirmar(DATOS)

    expect(reservar).toHaveBeenCalledTimes(1)
    expect(textosDe(control.descriptor())).toContain(mensaje)
  })

  it('muestra el detalle del servidor por campo ante VALIDACION', async () => {
    const { control, reservar } = await seleccionarHastaDatos({
      reservar: rechazarCon('VALIDACION', 'Revisá los datos', [
        { campo: 'email', mensaje: 'El email ya está en uso.' },
      ]),
    })

    await control.confirmar(DATOS)

    expect(reservar).toHaveBeenCalledTimes(1)
    expect(control.estado().paso).toBe('completando-datos')
    expect(control.estado().errores.email).toBe('El email ya está en uso.')
    const textos = textosDe(control.descriptor())
    expect(textos).toContain('El email ya está en uso.')
    expect(textos).toContain('Revisá estos datos')
    expect(control.estado().datos).toEqual(DATOS)
  })

  it('ante NO_ENCONTRADO recarga el perfil', async () => {
    const { control, perfil } = await seleccionarHastaDatos({
      reservar: rechazarCon('NO_ENCONTRADO', 'No encontrado'),
    })

    await control.confirmar(DATOS)

    expect(perfil).toHaveBeenCalledTimes(2)
    expect(control.estado().paso).toBe('eligiendo-prestacion')
  })

  it('ante un error de red muestra reintento sin perder los datos', async () => {
    const reservar = vi
      .fn(async () => RESERVA)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const { control } = await seleccionarHastaDatos({ reservar })

    await control.confirmar(DATOS)

    expect(control.estado().paso).toBe('error-envio')
    expect(control.estado().datos).toEqual(DATOS)
    const textos = textosDe(control.descriptor())
    expect(textos).toContain('No pudimos confirmar tu turno')
    expect(textos).toContain('Reintentar')
    expect(textos).toContain('Volver a mis datos')
  })

  it('reintenta el envío conservando la solicitud', async () => {
    const reservar = vi
      .fn(async () => RESERVA)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const { control } = await seleccionarHastaDatos({ reservar })
    await control.confirmar(DATOS)

    await control.reintentarEnvio()

    expect(reservar).toHaveBeenCalledTimes(2)
    expect(control.estado().paso).toBe('exito')
  })

  it('vuelve a los datos desde el error de envío', async () => {
    const reservar = vi
      .fn(async () => RESERVA)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const { control } = await seleccionarHastaDatos({ reservar })
    await control.confirmar(DATOS)

    control.manejadores()['volver-a-datos']?.(nodoFalso())

    expect(control.estado().paso).toBe('completando-datos')
    expect(control.estado().datos).toEqual(DATOS)
  })

  it('el botón Entendido vuelve al horario tras RN-PA-03', async () => {
    const { control } = await seleccionarHastaDatos({
      reservar: rechazarCon('RN-PA-03', 'Límite'),
    })
    await control.confirmar(DATOS)
    expect(control.estado().paso).toBe('conflicto')

    control.manejadores()['entendido']?.(nodoFalso())

    expect(control.estado().paso).toBe('eligiendo-horario')
    expect(control.estado().datos).toEqual(DATOS)
  })
})

describe('manejadores y reinicio', () => {
  it('expone las acciones de prestación, días y horarios', async () => {
    const { control } = await seleccionarHastaDatos()

    const manejadores = control.manejadores()
    expect(manejadores['prestacion:1']).toBeDefined()
    expect(manejadores['prestacion:2']).toBeDefined()
    expect(manejadores['dia:2026-11-27']).toBeDefined()
    expect(manejadores['horario-0']).toBeDefined()
    expect(manejadores['ir-datos']).toBeDefined()
    expect(manejadores['salto-fecha']).toBeDefined()
  })

  it('el salto de fecha ignora valores inválidos', async () => {
    const { control, disponibilidad } = await seleccionarHastaDatos()

    control.manejadores()['salto-fecha']?.(nodoFalso(''))
    control.manejadores()['salto-fecha']?.(nodoFalso('no-es-fecha'))
    await esperar()

    expect(disponibilidad).toHaveBeenCalledTimes(1)
  })

  it('reinicia el flujo desde el éxito', async () => {
    const { control } = await seleccionarHastaDatos()
    await control.confirmar(DATOS)

    control.manejadores()['reiniciar']?.(nodoFalso())

    expect(control.estado().paso).toBe('eligiendo-prestacion')
    expect(control.estado().datos).toEqual({ dni: '', nombre: '', apellido: '', telefono: '', email: '' })
  })
})
