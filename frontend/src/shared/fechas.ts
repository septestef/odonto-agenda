const ZONA_ARGENTINA = 'America/Argentina/Buenos_Aires'

function partesEnArgentina(instante: Date, opciones: Intl.DateTimeFormatOptions): Intl.DateTimeFormatPart[] {
  return new Intl.DateTimeFormat('es-AR', { timeZone: ZONA_ARGENTINA, ...opciones }).formatToParts(instante)
}

function valorParte(partes: Intl.DateTimeFormatPart[], tipo: Intl.DateTimeFormatPartTypes): string {
  const valor = partes.find((parte) => parte.type === tipo)?.value
  if (valor === undefined) {
    throw new Error(`No se pudo formatear ${tipo} en zona Argentina`)
  }
  return valor
}

const FORMATO_FECHA_LOCAL = /^(\d{4})-(\d{2})-(\d{2})$/

type TripletaFecha = readonly [anio: number, mes: number, dia: number]

function tripletasDe(fecha: string): TripletaFecha | undefined {
  const coincidencia = FORMATO_FECHA_LOCAL.exec(fecha)
  const textoAnio = coincidencia?.[1]
  const textoMes = coincidencia?.[2]
  const textoDia = coincidencia?.[3]
  if (textoAnio === undefined || textoMes === undefined || textoDia === undefined) {
    return undefined
  }

  return [Number(textoAnio), Number(textoMes), Number(textoDia)]
}

function crearFechaUTC(anio: number, mes: number, dia: number): Date {
  const fecha = new Date(0)
  fecha.setUTCFullYear(anio, mes - 1, dia)
  fecha.setUTCHours(0, 0, 0, 0)

  return fecha
}

function formatearFechaUTC(fecha: Date): string {
  return `${String(fecha.getUTCFullYear()).padStart(4, '0')}-${String(fecha.getUTCMonth() + 1).padStart(2, '0')}-${String(fecha.getUTCDate()).padStart(2, '0')}`
}

function esTripletaFecha(anio: number, mes: number, dia: number): boolean {
  if (!Number.isInteger(anio) || !Number.isInteger(mes) || !Number.isInteger(dia)) {
    return false
  }
  if (mes < 1 || mes > 12 || dia < 1 || dia > 31) {
    return false
  }

  const fecha = crearFechaUTC(anio, mes, dia)

  return fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia
}

/** Indica si el texto es una fecha real con formato `YYYY-MM-DD`. */
export function esFechaValida(valor: string): boolean {
  const tripleta = tripletasDe(valor)
  if (tripleta === undefined) {
    return false
  }

  const [anio, mes, dia] = tripleta

  return esTripletaFecha(anio, mes, dia)
}

/** Suma días enteros a una fecha local `YYYY-MM-DD`, respetando meses, años y bisiestos. */
export function sumarDias(fecha: string, dias: number): string {
  const tripleta = tripletasDe(fecha)
  if (tripleta === undefined || !Number.isInteger(dias)) {
    throw new Error('No se puede operar sobre una fecha inválida')
  }

  const [anio, mes, dia] = tripleta
  if (!esTripletaFecha(anio, mes, dia)) {
    throw new Error('No se puede operar sobre una fecha inválida')
  }

  return formatearFechaUTC(crearFechaUTC(anio, mes, dia + dias))
}

/** Resta días enteros a una fecha local `YYYY-MM-DD`. */
export function restarDias(fecha: string, dias: number): string {
  return sumarDias(fecha, -dias)
}

function unirFecha(partes: Intl.DateTimeFormatPart[]): string {
  return `${valorParte(partes, 'year')}-${valorParte(partes, 'month')}-${valorParte(partes, 'day')}`
}

/** Devuelve la fecha local de Argentina en formato `YYYY-MM-DD`. */
export function fechaLocalArgentina(instante: Date): string {
  return unirFecha(partesEnArgentina(instante, { year: 'numeric', month: '2-digit', day: '2-digit' }))
}

/** Devuelve la hora local de Argentina de un instante, con dos dígitos por componente. */
export function horaArgentina(instante: Date): string {
  const partes = partesEnArgentina(instante, { hour: '2-digit', minute: '2-digit', hour12: false })

  return `${valorParte(partes, 'hour')}:${valorParte(partes, 'minute')}`
}

function formatearHoraResuelta(instante: Date): string {
  return `${String(instante.getUTCHours()).padStart(2, '0')}:${String(instante.getUTCMinutes()).padStart(2, '0')}`
}

/** Formatea un rango de horas ya resueltas en la zona de negocio, sin ajustar zona. */
export function rangoHorario(inicio: Date, fin: Date): string {
  return `${formatearHoraResuelta(inicio)}–${formatearHoraResuelta(fin)}`
}
