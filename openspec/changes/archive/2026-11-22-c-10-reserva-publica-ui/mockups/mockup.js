/* Navegación de días del mockup C-10.
   Solo para la demo visual: no consume la API ni decide reglas de negocio. */

const HOY = '2026-11-22'
const FECHA_MINIMA_RESERVA = '2026-11-25'
const FECHA_MAXIMA = '2027-01-21'
const HORA_SELECCIONADA = '10:30'

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']
const DIAS_CORTOS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb']
const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]

const diasEl = document.getElementById('dias')
if (diasEl) {
  const wrapEl = document.getElementById('dias-wrap')
  const rangoEl = document.getElementById('rango-semana')
  const resumenEl = document.getElementById('resumen-fecha')
  const anteriorEl = document.getElementById('semana-anterior')
  const siguienteEl = document.getElementById('semana-siguiente')
  const saltoEl = document.getElementById('salto-fecha')

  let inicioSemana = HOY
  let seleccionada = diasEl.dataset.seleccionada ?? '2026-11-26'

  const aFecha = (iso) => {
    const [anio, mes, dia] = iso.split('-').map(Number)
    return new Date(Date.UTC(anio, mes - 1, dia))
  }

  const aIso = (fecha) => fecha.toISOString().slice(0, 10)

  const sumarDias = (iso, dias) => {
    const fecha = aFecha(iso)
    fecha.setUTCDate(fecha.getUTCDate() + dias)
    return aIso(fecha)
  }

  const diferenciaDias = (desde, hasta) =>
    Math.round((aFecha(desde) - aFecha(hasta)) / 86400000)

  const capitalizar = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1)

  const esFinDeSemana = (iso) => {
    const dia = aFecha(iso).getUTCDay()
    return dia === 0 || dia === 6
  }

  const esDeshabilitada = (iso) =>
    iso < HOY || iso > FECHA_MAXIMA || iso < FECHA_MINIMA_RESERVA || esFinDeSemana(iso)

  const formatearFecha = (iso, conHora) => {
    const fecha = aFecha(iso)
    const texto = `${capitalizar(DIAS[fecha.getUTCDay()])} ${fecha.getUTCDate()} de ${MESES[fecha.getUTCMonth()]}`
    return conHora ? `${texto}, ${HORA_SELECCIONADA}` : texto
  }

  const formatearRango = (inicio) => {
    const fin = sumarDias(inicio, 6)
    const fechaInicio = aFecha(inicio)
    const fechaFin = aFecha(fin)
    const diaInicio = fechaInicio.getUTCDate()
    const diaFin = fechaFin.getUTCDate()
    const mesInicio = MESES[fechaInicio.getUTCMonth()]
    const mesFin = MESES[fechaFin.getUTCMonth()]

    if (fechaInicio.getUTCMonth() === fechaFin.getUTCMonth()) {
      return `Semana del ${diaInicio} al ${diaFin} de ${mesInicio}`
    }
    return `Semana del ${diaInicio} de ${mesInicio} al ${diaFin} de ${mesFin}`
  }

  const buscarPrimeraDisponible = (inicio) => {
    for (let indice = 0; indice < 7; indice += 1) {
      const iso = sumarDias(inicio, indice)
      if (!esDeshabilitada(iso)) return iso
    }
    return null
  }

  const centrarSeleccionada = () => {
    const boton = diasEl.querySelector(`[data-fecha="${seleccionada}"]`)
    if (!boton) return
    const objetivo = boton.offsetLeft - (diasEl.clientWidth - boton.offsetWidth) / 2
    diasEl.scrollLeft = Math.max(0, Math.min(objetivo, diasEl.scrollWidth - diasEl.clientWidth))
  }

  const actualizarFades = () => {
    if (!wrapEl) return
    const maximo = diasEl.scrollWidth - diasEl.clientWidth
    wrapEl.classList.toggle('is-scrolled', diasEl.scrollLeft > 4)
    wrapEl.classList.toggle('is-at-end', diasEl.scrollLeft >= maximo - 4)
  }

  const actualizarControles = () => {
    if (anteriorEl) anteriorEl.disabled = inicioSemana <= HOY
    if (siguienteEl) siguienteEl.disabled = sumarDias(inicioSemana, 7) > FECHA_MAXIMA
    if (rangoEl) rangoEl.textContent = formatearRango(inicioSemana)
    if (resumenEl) resumenEl.textContent = formatearFecha(seleccionada, true)
    if (saltoEl) saltoEl.value = seleccionada
  }

  const renderizar = () => {
    const fragmento = document.createDocumentFragment()
    for (let indice = 0; indice < 7; indice += 1) {
      const iso = sumarDias(inicioSemana, indice)
      const fecha = aFecha(iso)
      const deshabilitada = esDeshabilitada(iso)
      const boton = document.createElement('button')
      boton.type = 'button'
      boton.className = 'day'
      boton.dataset.fecha = iso
      boton.disabled = deshabilitada
      boton.setAttribute('aria-pressed', String(iso === seleccionada))
      boton.setAttribute(
        'aria-label',
        `${formatearFecha(iso, false)}${deshabilitada ? ', sin horarios' : ''}`,
      )
      const dia = document.createElement('small')
      dia.textContent = DIAS_CORTOS[fecha.getUTCDay()]
      const numero = document.createElement('strong')
      numero.textContent = String(fecha.getUTCDate())
      boton.append(dia, numero)
      if (!deshabilitada) {
        boton.addEventListener('click', () => {
          seleccionada = iso
          renderizar()
          actualizarControles()
        })
      }
      fragmento.append(boton)
    }
    diasEl.replaceChildren(fragmento)
    actualizarControles()
    window.requestAnimationFrame(() => {
      centrarSeleccionada()
      actualizarFades()
    })
  }

  const irASemana = (direccion) => {
    const destino = sumarDias(inicioSemana, direccion * 7)
    if (destino < HOY || destino > FECHA_MAXIMA) return
    inicioSemana = destino
    const fin = sumarDias(inicioSemana, 6)
    if (seleccionada < inicioSemana || seleccionada > fin || esDeshabilitada(seleccionada)) {
      const primera = buscarPrimeraDisponible(inicioSemana)
      if (primera) seleccionada = primera
    }
    renderizar()
  }

  const irAFecha = (iso) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(iso) || iso < HOY || iso > FECHA_MAXIMA) {
      if (saltoEl) saltoEl.value = seleccionada
      return
    }
    const pagina = Math.floor(diferenciaDias(iso, HOY) / 7)
    const ultimaPagina = Math.floor(diferenciaDias(FECHA_MAXIMA, HOY) / 7)
    const paginaAcotada = Math.max(0, Math.min(pagina, ultimaPagina))
    inicioSemana = sumarDias(HOY, paginaAcotada * 7)
    seleccionada = esDeshabilitada(iso)
      ? buscarPrimeraDisponible(inicioSemana) ?? seleccionada
      : iso
    renderizar()
  }

  if (anteriorEl) anteriorEl.addEventListener('click', () => irASemana(-1))
  if (siguienteEl) siguienteEl.addEventListener('click', () => irASemana(1))
  if (saltoEl) saltoEl.addEventListener('change', () => irAFecha(saltoEl.value))
  diasEl.addEventListener('scroll', actualizarFades, { passive: true })
  window.addEventListener('resize', actualizarFades)

  renderizar()
}
