export type Etiqueta =
  | 'a'
  | 'article'
  | 'button'
  | 'dialog'
  | 'div'
  | 'form'
  | 'h1'
  | 'h2'
  | 'h3'
  | 'header'
  | 'img'
  | 'input'
  | 'label'
  | 'main'
  | 'nav'
  | 'p'
  | 'section'
  | 'span'
  | 'strong'

export interface Descriptor {
  etiqueta: Etiqueta
  texto?: string
  clase?: string
  atributos?: Readonly<Record<string, string>>
  hijos?: readonly Descriptor[]
  accion?: string
}

export interface OpcionesBoton {
  texto: string
  textoEnviando?: string
  accion?: string
  variante?: 'primaria' | 'secundaria' | 'fantasma'
  enviando?: boolean
  deshabilitado?: boolean
  tipo?: 'submit' | 'button'
  clase?: string
}

function atributosDeBoton(opciones: OpcionesBoton, enviando: boolean): Record<string, string> {
  const deshabilitado = enviando || opciones.deshabilitado === true

  return {
    type: opciones.tipo ?? 'submit',
    ...(deshabilitado ? { disabled: '' } : {}),
    ...(enviando ? { 'aria-busy': 'true' } : {}),
  }
}

export function boton(opciones: OpcionesBoton): Descriptor {
  const enviando = opciones.enviando === true
  const texto = enviando && opciones.textoEnviando !== undefined ? opciones.textoEnviando : opciones.texto

  return {
    etiqueta: 'button',
    texto,
    clase: ['btn', `btn--${opciones.variante ?? 'primaria'}`, ...(opciones.clase === undefined ? [] : [opciones.clase])].join(' '),
    atributos: atributosDeBoton(opciones, enviando),
    ...(opciones.accion === undefined ? {} : { accion: opciones.accion }),
  }
}

export interface OpcionesAlerta {
  texto: string
  detalle?: string
  tipo?: 'error' | 'aviso'
  id?: string
}

export function alerta(opciones: OpcionesAlerta): Descriptor {
  const tipo = opciones.tipo ?? 'error'

  return {
    etiqueta: 'div',
    texto: opciones.texto,
    clase: `alerta alerta--${tipo}`,
    atributos: {
      role: tipo === 'error' ? 'alert' : 'status',
      ...(tipo === 'aviso' ? { 'aria-live': 'polite' } : {}),
      ...(opciones.id === undefined ? {} : { id: opciones.id }),
    },
    ...(opciones.detalle === undefined
      ? {}
      : { hijos: [{ etiqueta: 'span', texto: opciones.detalle }] }),
  }
}

export interface OpcionesCampo {
  etiqueta: string
  nombre: string
  tipo: 'email' | 'password' | 'text'
  autocompletado?: string
  valor?: string
  error?: string
  deshabilitado?: boolean
  id?: string
}

export interface OpcionesModal {
  titulo: string
  contenido: string
  confirmar: string
  cancelar: string
  abierto?: boolean
}

export function modal(opciones: OpcionesModal): Descriptor {
  return {
    etiqueta: 'dialog',
    clase: 'modal',
    atributos: {
      'aria-labelledby': 'titulo-modal',
      ...(opciones.abierto === true ? { open: '' } : {}),
    },
    hijos: [
      { etiqueta: 'h2', texto: opciones.titulo, atributos: { id: 'titulo-modal' } },
      { etiqueta: 'p', texto: opciones.contenido },
      {
        etiqueta: 'div',
        clase: 'modal__acciones',
        hijos: [
          boton({ texto: opciones.cancelar, accion: 'cancelar-modal', variante: 'secundaria', tipo: 'button' }),
          boton({ texto: opciones.confirmar, accion: 'confirmar-modal', tipo: 'button' }),
        ],
      },
    ],
  }
}

export interface OpcionesToast {
  mensaje: string
  cerrable?: boolean
  tipo?: 'error' | 'aviso'
}

export function toast(opciones: OpcionesToast): Descriptor {
  const tipo = opciones.tipo ?? 'aviso'

  return {
    etiqueta: 'div',
    clase: `toast toast--${tipo}`,
    atributos: {
      role: tipo === 'error' ? 'alert' : 'status',
      'aria-live': 'polite',
    },
    hijos: [
      { etiqueta: 'span', texto: opciones.mensaje },
      ...(opciones.cerrable === true
        ? [boton({ texto: 'Cerrar', accion: 'cerrar-toast', variante: 'fantasma', tipo: 'button' })]
        : []),
    ],
  }
}

export function campo(opciones: OpcionesCampo): Descriptor {
  const id = opciones.id ?? `campo-${opciones.nombre}`
  const idError = `${id}-error`
  const conError = opciones.error !== undefined

  const etiqueta: Descriptor = {
    etiqueta: 'label',
    texto: opciones.etiqueta,
    atributos: { for: id },
  }
  const input: Descriptor = {
    etiqueta: 'input',
    atributos: {
      id,
      name: opciones.nombre,
      type: opciones.tipo,
      'data-campo': opciones.nombre,
      ...(opciones.autocompletado === undefined ? {} : { autocomplete: opciones.autocompletado }),
      ...(opciones.valor === undefined ? {} : { value: opciones.valor }),
      ...(conError ? { 'aria-invalid': 'true', 'aria-describedby': idError } : {}),
      ...(opciones.deshabilitado === true ? { disabled: '' } : {}),
    },
  }
  const error = conError ? [alerta({ texto: opciones.error ?? '', tipo: 'error', id: idError })] : []

  return {
    etiqueta: 'div',
    clase: 'campo',
    hijos: [etiqueta, input, ...error],
  }
}
