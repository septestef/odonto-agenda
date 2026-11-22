import { describe, expect, it } from 'vitest'
import { crearVistaBloqueos } from './bloqueos'
import type { ApiConfig, DatosBloqueo } from '../api-config'
import type { Bloqueo } from '../modelos'
import type { Descriptor } from '../../../shared/ui/componentes'

interface Pedido {
  metodo: string
  url: string
  cuerpo: unknown
}

const TURNO = {
  id: 12,
  inicio: '2026-11-27T10:30:00-03:00',
  fin: '2026-11-27T11:15:00-03:00',
  estado: 'RESERVADO',
  pacienteNombre: 'Ana',
  pacienteApellido: 'Pérez',
  prestacionNombre: 'Limpieza',
}

function apiFalsa(opciones: {
  pedidos: Pedido[]
  bloqueos?: readonly Bloqueo[]
  crear?: (datos: DatosBloqueo) => Bloqueo
}): ApiConfig {
  let listado = [...(opciones.bloqueos ?? [])]
  return {
    async horarios() {
      return { franjas: [] }
    },
    async guardarHorario() {
      return { franjas: [] }
    },
    async prestaciones() {
      return []
    },
    async crearPrestacion() {
      throw new Error('no usado')
    },
    async editarPrestacion() {
      throw new Error('no usado')
    },
    async bajaPrestacion() {},
    async bloqueos() {
      return listado
    },
    async crearBloqueo(datos) {
      opciones.pedidos.push({ metodo: 'POST', url: '/api/bloqueos', cuerpo: datos })
      const creado = opciones.crear === undefined
        ? { id: 7, ...datos, turnosAfectados: [] }
        : opciones.crear(datos)
      listado = [...listado, creado]
      return creado
    },
    async bajaBloqueo(id) {
      opciones.pedidos.push({ metodo: 'DELETE', url: `/api/bloqueos/${id}`, cuerpo: undefined })
      listado = listado.filter((item) => item.id !== id)
    },
  }
}

function textos(descriptor: Descriptor): string[] {
  const encontrados: string[] = []
  function recorrer(nodo: Descriptor): void {
    if (nodo.texto !== undefined && nodo.texto.trim() !== '') {
      encontrados.push(nodo.texto)
    }
    for (const hijo of nodo.hijos ?? []) {
      recorrer(hijo)
    }
  }
  recorrer(descriptor)
  return encontrados
}

function acciones(descriptor: Descriptor): string[] {
  const encontrados: string[] = []
  function recorrer(nodo: Descriptor): void {
    if (nodo.accion !== undefined) {
      encontrados.push(nodo.accion)
    }
    for (const hijo of nodo.hijos ?? []) {
      recorrer(hijo)
    }
  }
  recorrer(descriptor)
  return encontrados
}

function atributosPorCampo(descriptor: Descriptor): Readonly<Record<string, Readonly<Record<string, string>>>> {
  const encontrados: Record<string, Record<string, string>> = {}
  function recorrer(nodo: Descriptor): void {
    const campo = nodo.atributos?.['data-campo']
    if (campo !== undefined && nodo.atributos !== undefined) {
      encontrados[campo] = { ...nodo.atributos }
    }
    for (const hijo of nodo.hijos ?? []) {
      recorrer(hijo)
    }
  }
  recorrer(descriptor)
  return encontrados
}

const RANGO = {
  fechaInicio: '2026-11-27',
  horaInicio: '10:00',
  fechaFin: '2026-11-27',
  horaFin: '12:00',
}

describe('vista de bloqueos', () => {
  it('crea el bloqueo del 27/11 enviando instantes con offset y motivo', async () => {
    const pedidos: Pedido[] = []
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos }) })

    await vista.crear({ ...RANGO, motivo: 'Mantenimiento' })

    expect(pedidos).toEqual([{
      metodo: 'POST',
      url: '/api/bloqueos',
      cuerpo: {
        inicio: '2026-11-27T10:00:00-03:00',
        fin: '2026-11-27T12:00:00-03:00',
        motivo: 'Mantenimiento',
      },
    }])
    const visibles = textos(vista.descriptor())
    expect(visibles).toContain('27/11/2026 · 10:00–12:00')
    expect(visibles).toContain('Mantenimiento')
    expect(visibles).toContain('Sin turnos afectados')
  })

  it('muestra un turno RESERVADO con paciente, prestación, horario y acción visible', async () => {
    const pedidos: Pedido[] = []
    const bloqueo: Bloqueo = {
      id: 7,
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
      motivo: 'Mantenimiento',
      turnosAfectados: [TURNO],
    }
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos, bloqueos: [bloqueo] }) })

    await vista.cargar()

    const visibles = textos(vista.descriptor())
    expect(visibles).toContain('Hay 1 turno dentro del bloqueo')
    expect(visibles).toContain('Revisalo y decidí si lo cancelás.')
    expect(visibles).toContain('Crear un bloqueo no cancela tus turnos.')
    expect(visibles).toContain('10:30–11:15')
    expect(visibles).toContain('Ana Pérez · Limpieza')
    expect(visibles).toContain('Reservado')
    expect(visibles).toContain('Cancelá el turno')
  })

  it('rechaza localmente un rango invertido sin POST y lleva el foco al fin', async () => {
    const pedidos: Pedido[] = []
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos }) })

    await vista.crear({ ...RANGO, horaFin: '09:00' })

    const descriptor = vista.descriptor()
    expect(pedidos).toEqual([])
    expect(textos(descriptor)).toContain('La hora de fin debe ser posterior a la de inicio.')
    expect(textos(descriptor)).toContain('Corregí el rango antes de agregar el bloqueo.')
    expect(atributosPorCampo(descriptor)['horaFin']?.['aria-invalid']).toBe('true')
    expect(atributosPorCampo(descriptor)['horaFin']).toHaveProperty('autofocus')
  })

  it('muestra el aviso en plural y el orden del mockup con dos turnos afectados', async () => {
    const bloqueo: Bloqueo = {
      id: 7,
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
      motivo: 'Mantenimiento',
      turnosAfectados: [TURNO, {
        ...TURNO,
        id: 13,
        inicio: '2026-11-27T11:15:00-03:00',
        fin: '2026-11-27T12:00:00-03:00',
        pacienteNombre: 'Jorge',
        pacienteApellido: 'Ruiz',
        prestacionNombre: 'Consulta',
      }],
    }
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos: [], bloqueos: [bloqueo] }) })

    await vista.cargar()

    const visibles = textos(vista.descriptor())
    expect(visibles).toContain('Hay 2 turnos dentro del bloqueo')
    expect(visibles).toContain('Revisalos y decidí si los cancelás.')
    expect(visibles.indexOf('Hay 2 turnos dentro del bloqueo')).toBeLessThan(
      visibles.indexOf('Crear un bloqueo no cancela tus turnos.'),
    )
    expect(visibles.indexOf('Crear un bloqueo no cancela tus turnos.')).toBeLessThan(
      visibles.indexOf('Nuevo bloqueo'),
    )
    expect(visibles.indexOf('Nuevo bloqueo')).toBeLessThan(visibles.indexOf('Tus bloqueos'))
    expect(visibles.filter((texto) => texto === 'Cancelá el turno')).toHaveLength(2)
  })

  it('no envía pedido al activar "Cancelá el turno" y conserva el turno y su estado', async () => {
    const pedidos: Pedido[] = []
    const bloqueo: Bloqueo = {
      id: 7,
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
      turnosAfectados: [TURNO],
    }
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos, bloqueos: [bloqueo] }) })
    await vista.cargar()

    vista.cancelarTurno()

    expect(pedidos).toEqual([])
    const visibles = textos(vista.descriptor())
    expect(visibles).toContain('Reservado')
    expect(visibles).toContain('Cancelá el turno')
    expect(visibles).toContain('Esta acción se habilita con la gestión de turnos del odontólogo.')
  })

  it('quita un bloqueo con DELETE y lo saca del listado sin tocar los turnos', async () => {
    const pedidos: Pedido[] = []
    const bloqueo: Bloqueo = {
      id: 7,
      inicio: '2026-11-27T10:00:00-03:00',
      fin: '2026-11-27T12:00:00-03:00',
      turnosAfectados: [],
    }
    const vista = crearVistaBloqueos({ api: apiFalsa({ pedidos, bloqueos: [bloqueo] }) })
    await vista.cargar()

    await vista.baja(7)

    expect(pedidos).toEqual([{ metodo: 'DELETE', url: '/api/bloqueos/7', cuerpo: undefined }])
    expect(textos(vista.descriptor())).not.toContain('27/11/2026 · 10:00–12:00')
    expect(acciones(vista.descriptor())).not.toContain('baja-bloqueo')
  })
})
