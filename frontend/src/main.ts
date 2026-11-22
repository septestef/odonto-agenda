import { NOMBRE_MARCA } from './shared/brand'

const app = document.querySelector<HTMLElement>('#app')

if (app) {
  app.textContent = NOMBRE_MARCA
}
