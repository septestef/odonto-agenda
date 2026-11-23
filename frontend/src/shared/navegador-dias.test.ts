import { describe, expect, it, vi } from 'vitest'
import type { Descriptor } from './ui/componentes'
import {
  diasDeSemana,
  estaEnVentana,
  manejadoresNavegadorDias,
  moverSemana,
  navegadorDias,
  semanaDe,
} from './navegador-dias'

const VENTANA = { fechaMinima: '2026-11-24', fechaMaxima: '2027-01-21' }

function buscar(descriptores: readonly Descriptor[], accion: string): Descriptor | undefined {
  for (const descriptor of descriptores) {
    if (descriptor.accion === accion) {
      return descriptor
    }
    const encontrado = buscar(descriptor.hijos ?? [], accion)
    if (encontrado !== undefined) {
      return encontrado
    }
  }
  return undefined
}

function buscarClase(descriptores: readonly Descriptor[], clase: string): Descriptor | undefined {
  for (const descriptor of descriptores) {
    if (descriptor.clase === clase) {
      return descriptor
    }
    const encontrado = buscarClase(descriptor.hijos ?? [], clase)
    if (encontrado !== undefined) {
      return encontrado
    }
  }
  return undefined
}

describe('navegador de días', () => {
  it('ubica una fecha en su semana contada desde la fecha mínima', () => {
    expect(semanaDe(VENTANA, '2026-11-24')).toBe('2026-11-24')
    expect(semanaDe(VENTANA, '2026-11-30')).toBe('2026-11-24')
    expect(semanaDe(VENTANA, '2026-12-01')).toBe('2026-12-01')
    expect(semanaDe(VENTANA, '2027-01-21')).toBe('2027-01-19')
  })

  it('no mueve la semana fuera de la ventana', () => {
    expect(moverSemana(VENTANA, '2026-11-24', -1)).toBeUndefined()
    expect(moverSemana(VENTANA, '2026-11-24', 1)).toBe('2026-12-01')
    expect(moverSemana(VENTANA, '2027-01-19', 1)).toBeUndefined()
    expect(moverSemana(VENTANA, '2026-11-24', 0.5)).toBeUndefined()
  })

  it('arma siete días consecutivos', () => {
    expect(diasDeSemana('2026-11-28')).toEqual([
      '2026-11-28',
      '2026-11-29',
      '2026-11-30',
      '2026-12-01',
      '2026-12-02',
      '2026-12-03',
      '2026-12-04',
    ])
    expect(estaEnVentana(VENTANA, '2027-01-22')).toBe(false)
  })

  it('deshabilita los bordes y los días fuera de la ventana', () => {
    const primera = navegadorDias({ ventana: VENTANA, inicioSemana: '2026-11-24' })
    expect(buscar(primera, 'semana-anterior')?.atributos?.['disabled']).toBe('')
    expect(buscar(primera, 'semana-siguiente')?.atributos?.['disabled']).toBeUndefined()

    const ultima = navegadorDias({ ventana: VENTANA, inicioSemana: '2027-01-19' })
    expect(buscar(ultima, 'semana-siguiente')?.atributos?.['disabled']).toBe('')
    expect(buscar(ultima, 'dia:2027-01-21')?.atributos?.['disabled']).toBeUndefined()
    expect(buscar(ultima, 'dia:2027-01-22')?.atributos?.['disabled']).toBe('')
    expect(buscar(ultima, 'salto-fecha')?.atributos).toMatchObject({ min: '2026-11-24', max: '2027-01-21' })
  })

  it('marca el día seleccionado y el día resaltado con su leyenda', () => {
    const descriptores = navegadorDias({
      ventana: VENTANA,
      inicioSemana: '2026-11-24',
      seleccionada: '2026-11-26',
      diaMarcado: '2026-11-27',
      idLeyenda: 'ley-actual',
    })

    expect(buscar(descriptores, 'dia:2026-11-26')?.atributos?.['aria-pressed']).toBe('true')
    const marcado = buscar(descriptores, 'dia:2026-11-27')
    expect(marcado?.clase).toBe('day day--actual')
    expect(marcado?.atributos?.['aria-describedby']).toBe('ley-actual')
    expect(buscar(descriptores, 'salto-fecha')?.atributos?.['value']).toBe('2026-11-26')
    expect(buscarClase(descriptores, 'days-range')?.texto).toBe('Semana del 24 al 30 de noviembre')
  })

  it('solo registra manejadores para días dentro de la ventana', () => {
    const elegirFecha = vi.fn()
    const cambiarSemana = vi.fn()
    const manejadores = manejadoresNavegadorDias(VENTANA, '2027-01-19', { elegirFecha, cambiarSemana })

    expect(manejadores['dia:2027-01-21']).toBeDefined()
    expect(manejadores['dia:2027-01-22']).toBeUndefined()

    manejadores['dia:2027-01-20']?.({})
    manejadores['salto-fecha']?.({ value: '2026-12-10' })
    manejadores['semana-anterior']?.({})
    expect(elegirFecha).toHaveBeenNthCalledWith(1, '2027-01-20')
    expect(elegirFecha).toHaveBeenNthCalledWith(2, '2026-12-10')
    expect(cambiarSemana).toHaveBeenCalledWith(-1)
  })
})
