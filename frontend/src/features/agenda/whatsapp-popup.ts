const PESTANA_VACIA = 'about:blank'

function estaVacia(pestana: Window): boolean {
  try {
    return !pestana.closed && pestana.location.href === PESTANA_VACIA
  } catch {
    // El usuario pudo reutilizarla para otro destino.
    return false
  }
}

export function cerrarPestanaPendiente(pestana: Window | undefined): void {
  if (pestana === undefined || !estaVacia(pestana)) return
  try {
    pestana.close()
  } catch {
    // No intentar operar sobre una ventana que el navegador ya no permite controlar.
  }
}

export function reservarPestanaWhatsApp(): Window | undefined {
  if (typeof window === 'undefined') return undefined
  let pestana: Window | undefined
  try {
    // Con `noopener` en window.open, el navegador puede no entregar el handle.
    pestana = window.open(PESTANA_VACIA, '_blank') ?? undefined
    if (pestana !== undefined && estaVacia(pestana)) {
      pestana.opener = null
      if (pestana.opener === null) return pestana
    }
  } catch {
    // Si no podemos aislarla, la única salida es el enlace manual.
  }
  cerrarPestanaPendiente(pestana)
  return undefined
}

export function navegarPestanaWhatsApp(pestana: Window | undefined, url: string): boolean {
  if (pestana === undefined || !estaVacia(pestana)) return false
  try {
    if (pestana.opener !== null) return false
    const enlace = pestana.document.createElement('a')
    enlace.href = url
    enlace.target = '_self'
    enlace.rel = 'noopener noreferrer'
    enlace.click()
    return true
  } catch {
    return false
  }
}
