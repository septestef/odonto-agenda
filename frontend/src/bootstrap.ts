import { arrancar } from './main'
import type { HistorialNavegacion } from './router'
import { leerCookieDeCadena } from './shared/api-client'
import { crearAdaptadorNodos } from './shared/ui/adaptador'

function historialDelNavegador(): HistorialNavegacion {
  return {
    rutaActual: () => `${window.location.pathname}${window.location.search}`,
    pushState: (url) => window.history.pushState({}, '', url),
    replaceState: (url) => window.history.replaceState({}, '', url),
    suscribirPopstate: (callback) => {
      window.addEventListener('popstate', callback)
      return () => window.removeEventListener('popstate', callback)
    },
  }
}

const app = document.querySelector<HTMLElement>('#app')

if (app) {
  arrancar(app, crearAdaptadorNodos(document), {
    historial: historialDelNavegador(),
    fetch: (url, init) => window.fetch(url, init),
    leerCookie: (nombre) => leerCookieDeCadena(document.cookie, nombre),
    suscribirPagehide: (callback) => {
      const alOcultar = (evento: PageTransitionEvent): void => callback(evento.persisted)
      window.addEventListener('pagehide', alOcultar)
      return () => window.removeEventListener('pagehide', alOcultar)
    },
  })
}
