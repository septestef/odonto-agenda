import { arrancar } from './main'
import type { HistorialNavegacion } from './router'
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
  })
}
