import type { Descriptor } from '../../shared/ui/componentes'
import { WHATSAPP_SIN_DIRECCION, WHATSAPP_SIN_TELEFONO } from './api'
import type { FallaWhatsApp } from './api'
import type { TurnoAgenda } from './tipos'

export type EstadoWhatsApp =
  | { tipo: 'disponible' | 'no-disponible' | 'preparando' }
  | { tipo: 'error'; falla: FallaWhatsApp }
  | { tipo: 'apertura-bloqueada'; url: string }

const AVISOS: Record<FallaWhatsApp, string> = {
  telefono: WHATSAPP_SIN_TELEFONO,
  direccion: WHATSAPP_SIN_DIRECCION,
  cambio: 'El turno cambió. Actualizá la agenda para ver su estado.',
  'sesion-vencida': 'Tu sesión venció. Volvé a ingresar.',
  'sin-acceso': 'No tenés acceso para preparar WhatsApp.',
  error: 'No pudimos preparar WhatsApp. Volvé a intentar.',
}

export function disponibilidadWhatsApp(turno: TurnoAgenda, ahora: Date): 'disponible' | 'no-disponible' | undefined {
  if (turno.estado !== 'RESERVADO' || !(Date.parse(turno.inicio) > ahora.getTime())) return undefined
  return /^\+[1-9][0-9]{7,14}$/.test(turno.paciente.telefono ?? '') ? 'disponible' : 'no-disponible'
}

export function descriptorWhatsApp(id: number, estado: EstadoWhatsApp, enfocar: boolean): Descriptor {
  const preparando = estado.tipo === 'preparando'
  const noDisponible = estado.tipo === 'no-disponible'
  const noReintentable = estado.tipo === 'error' && estado.falla !== 'error'
  const aviso = estado.tipo === 'error' ? AVISOS[estado.falla]
    : estado.tipo === 'apertura-bloqueada' ? 'El navegador bloqueó la apertura. Abrí WhatsApp desde el enlace.'
      : noDisponible ? WHATSAPP_SIN_TELEFONO : undefined
  const hijos: Descriptor[] = [{
    etiqueta: 'button',
    clase: 'btn btn--secondary btn--whatsapp',
    accion: 'preparar-whatsapp',
    enfocar,
    atributos: {
      type: 'button',
      'data-turno-id': String(id),
      ...(noDisponible ? { disabled: '' } : {}),
      ...(preparando || noReintentable ? { 'aria-disabled': 'true' } : {}),
      ...(preparando ? { 'aria-busy': 'true' } : {}),
      ...(aviso !== undefined ? { 'aria-describedby': 'whatsapp-aviso' } : {}),
    },
    hijos: [
      { etiqueta: 'span', clase: preparando ? 'whatsapp__carga' : 'whatsapp__icono', atributos: { 'aria-hidden': 'true' } },
      { etiqueta: 'span', texto: preparando ? 'Preparando…' : 'WhatsApp' },
    ],
  }]
  if (aviso !== undefined) {
    hijos.push({
      etiqueta: 'p',
      clase: `alert ${estado.tipo === 'error' ? 'alert--error' : 'alert--info'}`,
      atributos: { id: 'whatsapp-aviso', ...(estado.tipo === 'error' ? { role: 'alert' } : {}) },
      texto: aviso,
    })
  }
  hijos.push({
    etiqueta: 'span', clase: 'sr-only', atributos: { role: 'status' },
    texto: preparando ? 'Preparando WhatsApp…' : estado.tipo === 'error' ? '' : aviso ?? '',
  })
  if (estado.tipo === 'apertura-bloqueada') {
    hijos.push({
      etiqueta: 'a', clase: 'btn btn--secondary btn--whatsapp whatsapp__enlace', texto: 'Abrir WhatsApp',
      atributos: { href: estado.url, target: '_blank', rel: 'noopener noreferrer' },
    })
  }
  if (estado.tipo === 'error' && estado.falla === 'cambio') {
    hijos.push({
      etiqueta: 'button', clase: 'btn btn--secondary', texto: 'Actualizar agenda',
      atributos: { type: 'button' }, accion: 'actualizar-agenda-whatsapp',
    })
  }
  return { etiqueta: 'div', clase: 'detalle__whatsapp', hijos }
}
