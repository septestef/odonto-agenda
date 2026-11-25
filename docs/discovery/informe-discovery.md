# Informe de Discovery: sistemas odontológicos de turnos y agenda en Argentina y Latinoamérica

**Producto**: OdontoAgenda (repositorio `turnotardeutn`)
**Materia**: Desarrollo de Software, Trabajo Global: del Discovery al primer Change, con Spec-Driven Development y Active Stack
**Institución**: UTN, Facultad Regional Mendoza
**Integrantes**: Estefanía Septfons Accorinti · Facundo Nicolás Pereyra · Leandro Navarrete · Marcos Pedraza · Lautaro Acosta · Jerónimo Canale Onofri
**Fecha del relevamiento**: 2026-11-17 · **Fecha del informe**: 2026-11-25

<!-- pagebreak -->

## Resumen ejecutivo

**Qué se investigó.** Relevamos 20 sistemas de gestión de turnos y agenda que pueden usar los consultorios odontológicos: 11 con foco o presencia en Argentina, 3 de Latinoamérica y 6 internacionales. Solo usamos fuentes públicas: sitios oficiales, páginas de precios, centros de ayuda, marketplaces y agregadores. Cada fuente tiene su URL y su fecha de consulta (2026-11-17). Puntuamos cada sistema de 0 a 5 en siete criterios ponderados y separamos en todo el informe lo **comprobado**, la **afirmación comercial** y lo **no evidenciado**.

**Principales hallazgos.**

- La agenda multiprofesional, los recordatorios por WhatsApp, la reserva online 24/7 y el odontograma ya son el **estándar del mercado**. No diferencian a nadie.
- Los líderes por evidencia pública son DentalSoft (3,95), Dentalink (3,95), OdontoClinIA (3,90), AgendaPro (3,65) y DentalCore (3,40, recalculado después de la verificación de fuentes). La puntuación mide **evidencia pública, no calidad real**.
- **Vacíos del mercado argentino**: ningún sistema odontológico local evidencia lista de espera ni relleno automático de cancelaciones. Casi ninguno trata el sillón como recurso de la agenda. Nadie declara la Ley 27.706 de historia clínica electrónica. La mitad no publica sus precios.
- **Verificación personal**: el equipo abrió 7 fuentes de tipos distintos el 19 y 20/11/2026. Resultado: 3 confirmadas, 3 parciales y 1 no respaldada. DentalCore figuraba como caído y en realidad funciona, así que su fila se rehízo.
- Casi toda la evidencia es **afirmación comercial**. No probamos ningún producto. Lo comprobado se limita a precios publicados, capturas, sitios operativos y títulos de un centro de ayuda.

**MVP recomendado.** Una agenda con **prevención estricta de solapamientos**, duración por prestación, bloqueos, **reserva online sin cuenta con DNI**, cancelación y reprogramación por el paciente, y recordatorios. Como diferenciadores proponemos la lista de espera con oferta automática, una política anti-ausentismo y un precio público. Para la demo del 25/11/2026 recortamos el alcance al odontólogo independiente (un profesional, sin sillones ni WhatsApp API). El detalle y el porqué están en la sección D.

<!-- pagebreak -->

## Convención de evidencia

| Etiqueta | Significado |
|---|---|
| **Comprobado (C)** | Lo muestra el producto, la documentación, el centro de ayuda, una captura, un video oficial o una página de precios pública. |
| **Afirmación comercial (AC)** | Lo dice el proveedor en texto de marketing, sin demostrarlo. |
| **No evidenciado** | No se encontró públicamente. La celda no se completa por deducción. |

"Odonto" = odontograma · "Perio" = periodontograma · "OS" = obras sociales/prepagas · "MP" = Mercado Pago · "WA" = WhatsApp. Las notas completas de cada sistema están en `docs/discovery/sources/`.

## A. Tabla comparativa (ordenada por relevancia para Argentina)

| # | Producto / Proveedor | País · Segmento | Modalidad | Agenda y reserva online | WhatsApp y recordatorios | Clínica (Odonto / Perio) | OS · ARCA · MP | Precio publicado | Fuente y fecha |
|---|---|---|---|---|---|---|---|---|---|
| 1 | **DentalSoft** / razón social No evidenciado | AR · consultorio a clínica (≤10 prof.) | SaaS web | Día/semana/mes, sobreturnos con detección de solapamiento (AC, no verificable con la prueba web), bloqueos, multisucursal (AC); reserva online en 4 pasos (C, verificado 2026-11-19) | Recordatorios por WA y e-mail (C, verificado 2026-11-19); confirmación que actualiza agenda, IA en WA (AC) | Odonto FDI 18 estados (AC) / Perio No evidenciado | OS y liquidaciones (AC) · ARCA No evidenciado · MP y señas (AC) | $30.000 y $60.000 por prof./mes; gratis 3 meses (C) | https://dentalsoft.com.ar/ · 2026-11-17 |
| 2 | **AgendaPro** (vertical dental AR) / AgendaPro | LatAm, sitio AR · consultorio a multisede | SaaS + app | Multi-prof., por sillón, multisede, 24/7, Reserve con Google (AC) | WA automático, IA "Alex" en WA/IG/Messenger (AC) | Odonto, anamnesis, firma (AC) / Perio No evidenciado | OS No evidenciado · ARCA No evidenciado · MP, señas, cuotas (AC) | "Desde ARS 13.900/mes", prueba 14 días (AC) | https://agendapro.com/ar/dental/software-odontologico · 2026-11-17 |
| 3 | **OdontoClinIA** / Emprinet | AR · consultorio a cadenas y sector público | Web | Por profesional, especialidad y sillón; turnero web y app (AC) | IA en WA que agenda, confirma, cancela y reprograma (AC) | Odonto versionado 52 piezas, CIE-10 (AC) / Perio No evidenciado | OS/prepagas, PUCO, ARCA (AC) · MP No evidenciado | No (demo) | https://www.clinia.com.ar/odontologia · 2026-11-17 |
| 4 | **DentalTec** / Tándem Digital | AR · odontólogos y círculos/federaciones | Web | Turnos por odontólogo (AC); reserva online No evidenciado | WA con confirmación (AC) | Odonto, CIE-10 (AC) / Perio No evidenciado | OS por web services, validación, ARCA (AC) · MP No evidenciado | Solo packs WA en USD (C) | https://web.dentaltec.com.ar/ · 2026-11-17 |
| 5 | **Bilog** / Bilog | AR · consultorio, clínica, financiadores | Web + app | Multi-prof., reserva/cancelación online (AC) | Recordatorios y confirmación (AC); WA integrado No evidenciado | Odonto (C, captura) / Perio No evidenciado | Liquidaciones OS (AC) · ARCA No evidenciado · MP No evidenciado | No | https://bilog.com.ar/ · 2026-11-17 |
| 6 | **Dentalink** / Dentalink | CL/LatAm, módulo AR · independiente a multisucursal | SaaS nube | Online 24/7, reagendamiento automático (AC) | WA, confirmaciones, Contact Center IA (AC) | Odonto + Perio + ortodoncia + IA RX (AC) | OS AR y receta electrónica AR (C, centro de ayuda) · ARCA No evidenciado · MP No evidenciado | No (cotización); IA USD 50 (AC) | https://www.softwaredentalink.com · 2026-11-17 |
| 7 | **DentiClick** / razón social No evidenciado | AR/VE · independiente a centro | Web/PWA | Día/semana/mes, mini-web con reservas 24/7 (AC) | No evidenciado | Odonto versionado (AC) / Perio No evidenciado | Liquidaciones OS (AC) · ARCA No evidenciado · MP No evidenciado | US$0 / 12 / 29 / 35 (C) | https://www.denticlick.app/ · 2026-11-17 |
| 8 | **Calu** / razón social No evidenciado | AR (inferido) · salud general | SaaS | Agendas múltiples, reserva 24 h (AC) | WA recordatorio y confirmación (AC) | No evidenciado / No evidenciado | OS No evidenciado · ARCA No evidenciado · MP cobro anticipado (C) | Parcial (costo diario), prueba 7 días (C) | https://calu.app/ · 2026-11-17 |
| 9 | **Doctoralia** (AR) / Doctoralia Internet S.L. (Docplanner) | ES · marketplace | Web + app | Búsqueda y reserva pública (C) | SMS (AC) | No evidenciado / No evidenciado | No evidenciado | No | https://www.doctoraliar.com/ · 2026-11-17 |
| 10 | **Turnito** / razón social No evidenciado | AR + LatAm · multirrubro | SaaS | Página de reserva sin registro (C, verificado 2026-11-19), link para redes (AC) | WA/e-mail/Telegram (C por plan) | No evidenciado / No evidenciado | OS No evidenciado · ARCA No evidenciado · MP y seña con bloqueo de horario (AC, no verificable sin reservar) | ARS 0 a 36.800 + IVA (C, corregido 2026-11-19) | https://www.turnito.app/ · 2026-11-17 |
| 11 | **DentalCore** / empresa No evidenciado | AR/LatAm (8 países declarados) · consultorio y clínica | No evidenciado | Agenda con recordatorios, sala de espera (AC); reserva online No evidenciado | Recordatorios por WA, e-mail o SMS (AC) | Odonto + Perio, módulos por especialidad, recetas y consentimientos (AC) | OS/prepagas y seguros · factura ARCA · cobros con MP (AC) | ≈ $80.000 mínimo + $15.000 por odontólogo extra (AC) | https://dentalcore.app/ · 2026-11-19 (el 2026-11-17 rechazó la conexión) |
| 12 | **XIAgenda** / empresa No evidenciado | AR · consultorios | No evidenciado | IA multicanal, lista de espera automática (AC, agregador) | Recordatorio con confirmación (AC) | No evidenciado | MP señas (AC) | USD 15/miembro en México (AC, agregador); precio en ARS No evidenciado | https://www.comparasoftware.com/xiagenda · 2026-11-17 (sitio oficial No evidenciado) |
| 13 | **DENTIDESK** / razón social No evidenciado | CL/RD · clínica, hospital, universidad | SaaS + app | Agendamiento online (AC) | Recordatorios (AC); WA "próximamente" | Odonto + fichas de especialidad (AC) | No evidenciado para AR (SII/Alegra) | No; prueba 15 días (AC) | https://www.dentidesk.com/ · 2026-11-17 |
| 14 | **Bewe** / Bewe | ES/LatAm · pymes | SaaS + app | IA en WA/IG/web (AC) | WA (AC) | No evidenciado | No factura ni procesa pagos (declarado) | USD 29/59/89 (C) | https://www.bewe.ai/ · 2026-11-17 |
| 15 | **Clinic Cloud** / Doctoralia España SL | ES · clínicas | Nube | Multisede, reserva vía Doctoralia (AC) | WA/SMS/e-mail (AC) | No evidenciado / No evidenciado | España (VeriFactu) | No; prueba (AC) | https://www.clinic-cloud.com/ · 2026-11-17 |
| 16 | **Gesden** / Infomed (Henry Schein) | ES · clínicas | Escritorio + nube | Agenda multigabinete (AC terceros) | Pack WA (AC terceros) | Odonto + Perio + presupuesto ligado (AC terceros) | España | No (≈130 €/mes según terceros) | https://www.gesden.es/ (conexión rechazada) · 2026-11-17 |
| 17 | **Open Dental** / Open Dental Software | EE. UU. · consultorios | Self-hosted (+ nube EE. UU.) | Web Sched (AC) | Texting (C en tarifas) | Tooth chart 3D (AC) / Perio No evidenciado | Seguros EE. UU. | USD 89/sede "otros países" (C) | https://www.opendental.com/site/fees.html · 2026-11-17 |
| 18 | **Curve Dental** / CD Newco, LLC | EE. UU./CA · consultorios y grupos | Nube | Reserva online, multisede (AC) | Recordatorios, recare (AC) | Charting + Perio + imágenes (AC) | Seguros, Curve Pay | No | https://www.curvedental.com/ · 2026-11-17 |
| 19 | **Dentally** / Henry Schein One UK Ltd | UK · consultorios | Nube | Portal, lista de espera y relleno de cancelaciones (AC) | Recordatorios y confirmaciones (AC) | Charting + IA imágenes (AC) / Perio No evidenciado | UK/NHS | No | https://dentally.com/ · 2026-11-17 |
| 20 | **Dentrix** / Henry Schein One | EE. UU. · consultorios y grupos | Local + nube (Ascend) | Reserva online (AC) | Recordatorios, mensajería bidireccional (AC) | Charting + imágenes IA (AC) / Perio No evidenciado | Seguros EE. UU. | No | https://www.dentrix.com/ · 2026-11-17 |

<!-- pagebreak -->

## B. Matriz de puntuación (0–5)

**Pesos**: **T** Gestión de turnos y automatización 25 % · **C** Funcionalidad odontológica clínica 20 % · **I** Integraciones locales y WhatsApp 15 % · **A** Administración, cobros y facturación 15 % · **P** Experiencia del paciente 10 % · **S** Seguridad, exportación y trazabilidad 10 % · **$** Precio y facilidad de adopción 5 %.

**Total ponderado** = Σ(puntaje × peso) / 100, en escala de 0 a 5.

| Rank | Sistema | T | C | I | A | P | S | $ | **Total** |
|---|---|---|---|---|---|---|---|---|---|
| 1 | DentalSoft | 4 | 4 | 4 | 4 | 4 | 3 | 5 | **3,95** |
| 2 | Dentalink | 4 | 5 | 3 | 4 | 4 | 4 | 2 | **3,95** |
| 3 | OdontoClinIA | 4 | 4 | 4 | 4 | 4 | 4 | 2 | **3,90** |
| 4 | AgendaPro | 4 | 3 | 4 | 3 | 5 | 3 | 4 | **3,65** |
| 5 | DentalCore | 3 | 4 | 4 | 4 | 2 | 3 | 3 | **3,40** |
| 6 | DentalTec | 3 | 3 | 4 | 4 | 2 | 4 | 2 | **3,25** |
| 7 | Curve Dental | 4 | 5 | 1 | 2 | 4 | 3 | 1 | **3,20** |
| 8 | Bilog | 3 | 4 | 2 | 4 | 3 | 2 | 2 | **3,05** |
| 9 | DENTIDESK | 3 | 4 | 2 | 3 | 3 | 3 | 3 | **3,05** |
| 10 | Dentally | 5 | 3 | 1 | 2 | 4 | 2 | 1 | **2,95** |
| 11 | DentiClick | 3 | 4 | 1 | 3 | 3 | 2 | 5 | **2,90** |
| 12 | Dentrix | 4 | 4 | 1 | 2 | 4 | 2 | 1 | **2,90** |
| 13 | Calu | 4 | 1 | 3 | 3 | 4 | 1 | 4 | **2,80** |
| 14 | Open Dental | 3 | 3 | 1 | 2 | 3 | 2 | 4 | **2,50** |
| 15 | Clinic Cloud | 3 | 2 | 1 | 2 | 3 | 3 | 2 | **2,30** |
| 16 | Turnito | 3 | 0 | 3 | 2 | 4 | 1 | 5 | **2,25** |
| 17 | Gesden | 3 | 3 | 1 | 2 | 1 | 2 | 2 | **2,20** |
| 18 | Doctoralia (AR) | 3 | 1 | 2 | 1 | 5 | 2 | 2 | **2,20** |
| 19 | XIAgenda | 3 | 0 | 2 | 2 | 3 | 0 | 3 | **1,80** |
| 20 | Bewe | 3 | 0 | 2 | 1 | 3 | 1 | 4 | **1,80** |

Desempate DentalSoft/Dentalink: por relevancia local (precio público en ARS y foco 100 % argentino).

### Nota metodológica

- **Qué significa cada nota**: 0 = sin evidencia de la capacidad o declaradamente ausente; 1 = mínima o solo para otro mercado; 2 = parcial; 3 = cobertura básica del criterio; 4 = cobertura amplia con algún faltante relevante; 5 = cobertura amplia **y** consistente entre varias fuentes del proveedor.
- **Tratamiento de lo "No evidenciado"**: puntúa como ausente. No se completa por deducción ni por lo que "seguramente" tenga el producto.
- **Afirmaciones comerciales**: puntúan, pero un 5 exige evidencia amplia y consistente. Las fuentes indirectas (terceros, agregadores, fragmentos de buscador) se penalizan.
- **Consecuencia**: los productos con webs escuetas (Bilog, Dentrix) pueden estar **subestimados**. La matriz mide *evidencia pública*, no calidad real.

### Justificación breve por sistema

- **DentalSoft**: T4 por sobreturnos, solapamiento, bloqueos y confirmación (AC); lista de espera y sillones No evidenciado. C4 por odontograma FDI, ortodoncia y planes; perio No evidenciado. I4 por WA, MP y Google Calendar; ARCA y API No evidenciado. A4 por caja, señas, OS y liquidaciones; sin ARCA. P4 por reserva en 4 pasos con DNI y portal. S3 por backups, exportación, roles y trazabilidad parcial; sin leyes declaradas. $5 por 3 meses gratis y precios publicados.
- **Dentalink**: T4 por online 24/7, WA y reagendamiento automático. C5 por odonto + perio + ortodoncia + imágenes + firma + IA RX (AC). I3 por API declarada y WA; ARCA y MP No evidenciado; módulo AR comprobado. A4 por caja, liquidaciones y OS AR (C). P4. S4 por ISO 27001, cifrado y respaldos diarios (AC). $2 por no publicar precios.
- **OdontoClinIA**: T4 por agenda por sillón e IA en WA con reprogramación; sin sobreturnos ni lista de espera. C4 por odontograma versionado y CIE-10; sin perio. I4 por RENAPER, PUCO, ARCA, WA y ReNaPDiS. A4 por caja con arqueo, OS/prepagas y ARCA; sin MP. P4 por turnero web/app. S4 por backup cada 10 min, versionado y registro operativo. $2 por no publicar precio.
- **AgendaPro**: T4 por multisillón, multisede e IA; lista de espera No evidenciado. C3 por odonto + anamnesis; sin perio ni RX. I4 por WA, MP y Google Calendar/Reserve. A3 por caja, señas y cuotas; ARCA y OS No evidenciado. P5 por reserva 24/7 multicanal + Google. S3 por leyes 25.326/26.529 declaradas (AC). $4 por precio "desde" y prueba de 14 días.
- **DentalTec**: T3 por turnos y WA con confirmación; sin reserva online. C3 por odonto, CIE-10 e imágenes. I4 por ARCA + OS por web services. A4 por facturación OS y liquidaciones; caja y MP No evidenciado. P2 porque el paciente no reserva. S4 por 10 roles y auditoría completa (AC). $2 porque el precio del software no está publicado.
- **Curve Dental**: T4, C5 (perio, imágenes, plan), I1 (sin integraciones locales), A2 (seguros EE. UU.), P4, S3 (HIPAA, AC), $1 (sin precio ni prueba).
- **Bilog**: T3, C4 (odonto comprobado por captura), I2, A4 (liquidaciones OS), P3 (reserva online + app), S2, $2. Probablemente subestimado por web escueta.
- **DENTIDESK**: T3, C4 (fichas de especialidad), I2 (sin integraciones AR; WA a futuro), A3, P3, S3 (firma digital, ley chilena), $3 (prueba de 15 días).
- **Dentally**: T5 por ser la única evidencia clara de lista de espera + relleno de cancelaciones; C3; I1; A2; P4; S2; $1.
- **DentiClick**: T3, sin recordatorios evidenciados; C4 por odonto versionado; I1; A3; P3 por mini-web 24/7; S2 por backup por correo; $5 por plan gratis y US$12.
- **Dentrix**: T4, C4, I1, A2, P4, S2, $1.
- **Calu**: T4 por WA y reservas; C1, sin odontología; I3 por WA + MP; A3 por cobro anticipado; P4; S1; $4.
- **Open Dental**: T3, C3, I1, A2, P3, S2, $4 (precio "otros países" publicado y costos extra visibles).
- **Clinic Cloud**: T3, C2 (odontograma No evidenciado), I1, A2, P3, S3 (RGPD, AC), $2.
- **Turnito**: T3, C0, I3 (WA, GCal, MP), A2 (señas sin facturación), P4 (sin registro), S1, $5 (gratis permanente).
- **Gesden**: evidencia solo de terceros; T3, C3, I1, A2, P1, S2, $2.
- **Doctoralia (AR)**: T3, C1, I2, A1, P5 (marketplace y opiniones), S2, $2.
- **DentalCore** (recalculado el 2026-11-25 con lo que el equipo verificó en el sitio el 2026-11-19; antes tenía 2,00 por evidencia solo indirecta): T3 por agenda con recordatorios por WA, e-mail o SMS; reserva online, lista de espera y control de solapamiento No evidenciado. C4 por HC, odontograma y periodontograma, módulos por especialidad, recetas y consentimientos; imágenes/RX No evidenciado; no llega a 5 porque es una sola fuente comercial. I4 por WA, ARCA, MP y OS; API y Google Calendar No evidenciado. A4 por factura ARCA, OS/prepagas, plan de tratamiento con precios, caja diaria y cobros con MP. P2 porque la reserva del paciente No evidenciado. S3 por base aislada por clínica, exportación completa en HL7 FHIR y roles por persona; auditoría, respaldos y leyes argentinas No evidenciado. $3 por precio publicado (≈ $80.000 + $15.000 por odontólogo extra) sin prueba gratuita evidenciada. Todo es afirmación comercial.
- **XIAgenda**: fuente única de agregador; T3 por lista de espera e IA; S0.
- **Bewe**: sin clínica, no factura ni procesa pagos; T3 por IA conversacional.

<!-- pagebreak -->

## C. Análisis competitivo

### C.1 Funcionalidades estándar del mercado (paridad obligatoria)

- Agenda multiprofesional con vistas día/semana/mes y acceso web/móvil sin instalación.
- Recordatorios automáticos con confirmación. En Argentina, **WhatsApp es el canal estándar** (DentalSoft, AgendaPro, ClinIA, DentalTec, Calu, Turnito).
- Reserva online 24/7 por link o mini-web.
- Historia clínica digital con odontograma interactivo (FDI) y adjuntos.
- Presupuestos, caja/cobros y reportes básicos de ocupación y facturación.
- Multisucursal en planes superiores; migración y capacitación incluidas como argumento de venta.
- En Argentina, cobertura de obras sociales y liquidaciones (DentalSoft, DentalTec, Bilog, DentiClick, ClinIA, Dentalink AR).

### C.2 Diferenciadores reales observados

- **Validación de prácticas contra obras sociales en tiempo real** y cierre multinivel para círculos/federaciones (DentalTec).
- **Cumplimiento regulatorio argentino visible**: recetario en ReNaPDiS, PUCO, RENAPER, ARCA (OdontoClinIA).
- **Odontograma versionado** con bloqueo, comparación e historial por pieza (ClinIA, DentiClick, DentalSoft parcial).
- **Seña con Mercado Pago que bloquea el horario al acreditarse** (Turnito, DentalSoft Pro, Calu, XIAgenda).
- **IA conversacional multicanal** para agendar y reprogramar (AgendaPro, Dentalink, ClinIA, Bewe).
- **Lista de espera con relleno automático de cancelaciones** (Dentally; XIAgenda según agregador).
- **Captación**: marketplace y Reserve con Google (Doctoralia, AgendaPro).

Criterio crítico: varios de estos diferenciadores son **afirmaciones comerciales**. Solo la seña con MP de Turnito y Calu y el módulo argentino de Dentalink tienen evidencia comprobada.

### C.3 Vacíos frecuentes del mercado argentino

1. **Lista de espera y relleno automático de huecos**: no evidenciada en ningún sistema odontológico argentino relevado.
2. **Sillón/box como recurso de agenda**: solo AgendaPro y ClinIA lo declaran. DentalSoft, DentalTec y Bilog no lo evidencian.
3. **Periodontograma**: entre los proveedores con foco en Argentina, solo DentalCore lo declara (AC, sitio verificado el 2026-11-19). El resto que lo ofrece es extranjero (Dentalink, Gesden, Curve).
4. **Integración de punta a punta** entre turno, seña (MP), práctica, OS y factura ARCA: cada actor cubre una parte. DentalSoft cubre MP sin ARCA y DentalTec, ARCA sin MP. DentalCore declara el circuito completo (OS, ARCA y MP), pero solo como afirmación comercial: no se puede comprobar sin pagar el sistema.
5. **Recall clínico** (controles semestrales, mantenimiento periodontal, ortodoncia): apenas aparecen "pacientes inactivos" u "oportunidades". No se evidencia recall por tratamiento.
6. **Cumplimiento legal declarado**: solo AgendaPro menciona las leyes 25.326 y 26.529. **Ninguno declara la Ley 27.706** de historia clínica electrónica. La auditoría de accesos a la HC rara vez está evidenciada.
7. **Transparencia de precios**: la mitad de los actores locales no publica precios (Bilog, DentalTec, ClinIA, Dentalink).
8. **API pública documentada**: no evidenciada en ningún actor argentino.
9. **Reprogramación autogestionada** por el paciente con reglas (anticipación mínima, penalidad por ausencia): poco evidenciada fuera de los chatbots con IA.

### C.4 Oportunidades de innovación

- **Agenda inteligente por sillón + profesional** con duración variable por práctica, sobreturnos controlados y prevención de solapamiento, más una **lista de espera que ofrece automáticamente por WhatsApp** el hueco liberado.
- **Política anti-ausentismo configurable**: seña con MP opcional por paciente o práctica, confirmación obligatoria por WhatsApp, liberación automática si no confirma e historial de ausencias.
- **Recall clínico ligado al odontograma y al plan de tratamiento** (controles, limpiezas, ortodoncia) con campañas por WhatsApp.
- **Cumplimiento "by design"**: auditoría de accesos a la HC, firma y versionado de registros (leyes 26.529 y 27.706), consentimiento de datos sensibles (Ley 25.326) y exportación completa del paciente, declarado públicamente como diferencial.
- **Circuito OS completo**: padrón/cobertura (PUCO), nomenclador, coseguro en el presupuesto, liquidación y factura ARCA, con integración posterior a círculos.
- **Precio transparente sin cobro por profesional en el plan de entrada**, con WhatsApp incluido hasta un cupo.

<!-- pagebreak -->

## D. Recomendación final

### D.1 Cinco competidores prioritarios para analizar mediante demo

1. **DentalSoft**: referencia directa de agenda local (sobreturnos, bloqueos, señas, OS) y precio público.
2. **AgendaPro (vertical dental AR)**: mayor músculo comercial e IA multicanal. Hay que validar la profundidad odontológica y ARCA.
3. **Dentalink (con módulo Argentina)**: techo funcional clínico de la región. Hay que validar OS/ARCA y el precio en ARS.
4. **OdontoClinIA**: referencia de cumplimiento regulatorio argentino y de odontograma versionado.
5. **DentalTec**: referencia en obras sociales, validación de prácticas y entidades odontológicas.

Como reserva queda Bilog, por trayectoria y base instalada declarada. **Ajuste del 2026-11-25**: tras la verificación, DentalCore subió al 5.º puesto de la matriz (3,40). Se mantiene DentalTec en la lista por su especialización en obras sociales, y DentalCore se suma como sexto candidato: conviene ver en una demo si lo que declara su sitio (periodontograma, circuito OS + ARCA + MP) es real.

### D.2 Tres productos de referencia de experiencia de usuario

1. **Dentally**: lista de espera, relleno de cancelaciones y portal del paciente (agenda orientada a la ocupación).
2. **Turnito**: reserva por link sin registro y seña con bloqueo del horario (mínima fricción para el paciente argentino).
3. **Doctoralia**: búsqueda, confirmación inmediata y reputación (experiencia del paciente y captación).

La UX no se evaluó con uso real. La elección se basa en los flujos públicos descritos.

### D.3 MVP sugerido

**Imprescindible (paridad)**

- Agenda multiprofesional y **multisillón**, vistas día/semana, duración variable por práctica, bloqueos, sobreturnos controlados y prevención de solapamiento.
- Reserva online por link o mini-web 24/7 con identificación por DNI.
- Recordatorios y confirmación por WhatsApp (API oficial) con actualización automática del estado del turno; cancelación y reprogramación por el paciente.
- Ficha del paciente con anamnesis, evoluciones y odontograma FDI con historial.
- Obra social/prepaga y plan por paciente; presupuesto simple con coseguro.
- Caja diaria y registro de pagos; reportes de ocupación y ausentismo.
- Roles (profesional, secretaría, administración), auditoría de accesos a la HC, backups y exportación de los datos del paciente.

**Diferenciador**

- **Lista de espera con oferta automática** del turno liberado por WhatsApp.
- **Política anti-ausentismo**: seña con Mercado Pago configurable y liberación automática si el paciente no confirma.
- **Recall clínico** ligado a tratamientos (controles y limpiezas).
- Cumplimiento declarado de las leyes 25.326, 26.529 y 27.706, con trazabilidad visible.
- Precio público, con plan de entrada sin cargo por profesional.

**Etapas posteriores**

- Facturación electrónica ARCA y liquidaciones a OS con nomenclador; validación de cobertura (PUCO).
- Periodontograma, módulo de ortodoncia, imágenes/RX e integraciones con radiología.
- Receta electrónica (ReNaPDiS) y firma digital de consentimientos.
- IA conversacional para agendar; Reserve con Google; API pública.
- Multisucursal avanzado, liquidación a profesionales e inventario.

### D.4 Del MVP recomendado al alcance de la demo (25/11/2026)

El MVP de D.3 describe un producto comercial completo. Con 8 días de plazo y presupuesto cero, el equipo eligió como segmento al **odontólogo independiente** y recortó el MVP a su núcleo verificable. El checklist de `docs/discovery/discovery.md` (§5 y §6) registra ese recorte.

| Del MVP recomendado | En la demo | Motivo |
|---|---|---|
| Prevención de solapamiento, duración por práctica, bloqueos | **Sí**: anti-solapamiento estricto con exclusion constraint en PostgreSQL | Es el núcleo del dominio y una regla de negocio verificable |
| Reserva online 24/7 con DNI | **Sí**, sin cuenta del paciente | Estándar del mercado (Turnito, Doctoralia) |
| Cancelación y reprogramación por el paciente | **Sí**, por link único enviado por email | Vacío C.3-9 |
| Recordatorios por WhatsApp (API oficial) | **Parcial**: email + botón `wa.me` | La API de WhatsApp es paga |
| Multiprofesional y multisillón | **No** (un profesional) | Segmento elegido: odontólogo independiente |
| Ficha, odontograma, OS, caja, roles | **No** (post-demo: C-17 y C-18) | No entran en el plazo |
| Precio público | Definido: $25.000–35.000 ARS/mes | Vacío C.3-7, sin validar con usuarios |

<!-- pagebreak -->

## Verificación de fuentes

> **Esta verificación la hizo el equipo**: abrimos personalmente cada URL el 19 y 20/11/2026 y anotamos si lo que el informe afirmaba estaba realmente en esa página. No cargamos datos personales ni pagamos ningún sistema, así que lo que exigía reservar, registrarse o pagar quedó sin comprobar.
>
> Resultados: **Confirmado** · **Parcial** (está, pero con otra cifra o alcance, o una parte no se pudo comprobar) · **No respaldado** (lo que afirmaba el informe no es cierto). Cada Parcial o No respaldado se corrigió en la tabla A con "Ajustar".

| # | Tipo de fuente | URL | Qué afirma el informe | Verificó / fecha | Qué encontramos | Resultado | Corrección aplicada |
|---|---|---|---|---|---|---|---|
| 1 | Sitio oficial | https://dentalsoft.com.ar/ | Sobreturnos con detección de solapamiento, bloqueos, recordatorios por WA y e-mail, reserva con DNI en 4 pasos | Equipo · 2026-11-19 | Con la prueba web confirmamos los avisos por WhatsApp y e-mail y la reserva en 4 pasos. La detección de solapamiento no se pudo comprobar con la prueba web. | **Parcial** | Tabla A: recordatorios y reserva en 4 pasos pasan a Comprobado; la detección de solapamiento queda como afirmación comercial no verificable. |
| 2 | Página de precios | https://www.opendental.com/site/fees.html | USD 89 por sede para "otros países"; texting con costo aparte | Equipo · 2026-11-19 | El precio de USD 89 por sede para "otros países" y el texting con costo aparte están en la página. | **Confirmado** | Ninguna. |
| 3 | Centro de ayuda | https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina | Módulo exclusivo para Argentina: obras sociales (alta, cobro, conciliación) y receta electrónica | Equipo · 2026-11-19 | Los artículos del centro de ayuda confirman el módulo para Argentina (obras sociales y receta electrónica). | **Confirmado** | Ninguna. |
| 4 | Agregador / marketplace de software | https://www.comparasoftware.com/xiagenda | XIAgenda: lista de espera automática, IA multicanal, señas con MP, USD 15 por miembro | Equipo · 2026-11-20 | La ficha describe señas con MP, lista de espera e IA multicanal. El precio de USD 15 por miembro corresponde a México. El precio en ARS y las señas no se pueden comprobar sin comprometer datos personales. | **Parcial** | Tabla A: el precio pasa a "USD 15/miembro en México; precio en ARS No evidenciado". |
| 5 | Plataforma pública de reserva | https://www.doctoraliar.com/ | Búsqueda de profesionales y reserva pública operativa (Comprobado) | Equipo · 2026-11-20 | Se puede buscar especialistas por especialidad y por zona (o cercanías), ver horarios disponibles, precios en algunos casos, reseñas, puntuación, foto, ubicación con mapa interactivo, filtrar por consulta en línea y reservar una cita. | **Confirmado** | Ninguna. |
| 6 | Página de planes | https://www.turnito.app/ | Planes de ARS 0 a 34.800 + IVA; seña con MP que bloquea el horario; reserva sin registro | Equipo · 2026-11-19 | Los planes van de ARS 0 a 36.800 + IVA, no a 34.800. Confirmamos que no hace falta cuenta para reservar. El bloqueo del horario con seña de MP no se pudo comprobar sin hacer una reserva. | **Parcial** | Tabla A: precio corregido a 36.800; la reserva sin registro pasa a Comprobado; la seña con bloqueo pasa a afirmación comercial. |
| 7 | Sitio oficial declarado caído | https://dentalcore.app/ | El sitio rechazó la conexión el 2026-11-17; todo lo de DentalCore es evidencia indirecta | Equipo · 2026-11-19 | El sitio **no está caído**: funciona. Publica un precio mínimo de alrededor de $80.000 más $15.000 por cada odontólogo extra, y lista funciones de clínica (HC, odontograma y periodontograma, módulos por especialidad, recetas y consentimientos), agenda con recordatorios por WA, e-mail o SMS, factura electrónica (ARCA y otros 7 países), obras sociales, cobros con Mercado Pago, exportación en HL7 FHIR y roles por persona. Las funciones no se pueden comprobar sin pagar el sistema. | **No respaldado** | Tabla A: la fila de DentalCore se rehízo con lo que publica el sitio, como afirmación comercial. También se corrigieron las referencias y los vacíos C.3-3 (periodontograma) y C.3-4 (integración de punta a punta). El puntaje de la matriz B se recalculó: pasó de 2,00 a 3,40 (del puesto 18 al 5). |

### Hallazgos que ya registró el relevamiento (no reemplazan la verificación del equipo)

Durante la investigación, el agente ya había dejado constancia de estos puntos. Son insumo para la reflexión, pero **no cuentan** como verificación personal:

- **Candidatos que no existen como producto comercial vigente**: Odontosys (solo aparece como proyecto académico), Ontoclick, Sistema Odonto (solo tesis), MediTurnos, Medicloud, Odontologic y Flexibook. Se excluyeron de la tabla en lugar de completarlos por deducción.
- **Reservo** (reservo.cl) devolvió contenido mínimo e inconsistente (un consultorio médico argentino, sin odontología) y se excluyó.
- **Cifras de clientes**: todas autodeclaradas y varias inconsistentes (AgendaPro, DentalTec, Clinic Cloud).
- **Dato de mercado no verificado**: una tesis de MBA (UdeSA) estimaría unos 70.000 profesionales odontológicos en Argentina. Solo se vio el fragmento del buscador, no la fuente primaria, así que el informe no lo usa como dato.
- **Fuentes inaccesibles**: ver la columna "Estado" en las Referencias.

<!-- pagebreak -->

## Metodología y limitaciones

- **Método**: búsqueda web y lectura de páginas oficiales el 2026-11-17. No se crearon cuentas, no se enviaron formularios, no se pidieron demos ni se probó ningún software. No se eludieron muros de pago, CAPTCHA ni protecciones anti-bot. No se contactó a ningún proveedor.
- **Verificación del equipo** (19 y 20/11/2026): 7 fuentes abiertas personalmente; DentalSoft se revisó con su prueba web. No se cargaron datos personales ni se pagó ningún sistema.
- **Naturaleza de la evidencia**: casi todo es *afirmación comercial* de las webs de los proveedores. Lo "comprobado" se limita a precios publicados, capturas visibles, sitios públicos operativos y títulos del centro de ayuda de Dentalink.
- **Calu** existe (calu.app), pero es una agenda de salud generalista, sin odontología evidenciada.
- **Reseñas**: no se pudieron verificar en Capterra/G2 porque bloquearon el acceso.
- **Moneda**: DentalSoft y Calu no explicitan la moneda (se presume ARS). Los precios están sujetos a inflación.
- **Puntuación**: mide evidencia pública, no calidad real. Los productos con webs escuetas pueden estar subestimados.

## Referencias

Todas las fuentes se consultaron el **2026-11-17**. "OK" = la página respondió y se leyó. "Solo fragmento del buscador" = el dato sale del resumen de un buscador, no de la página abierta. Los demás estados explican por qué no se pudo leer.

| Sistema | URL | Tipo | Estado |
|---|---|---|---|
| AgendaPro | https://agendapro.com/ar/dental/software-odontologico | Sitio oficial | OK |
| AgendaPro | https://agendapro.com/ar/agenda-medica | Sitio oficial | Solo fragmento del buscador |
| AgendaPro | https://agendapro.com/ar/precios | Página de precios | HTTP 404 |
| Bewe | https://www.bewe.ai/ (redirección desde https://www.bewe.io/) | Sitio oficial | OK |
| Bilog | https://bilog.com.ar/ | Sitio oficial | OK |
| Calu | https://calu.app/ | Sitio oficial | OK |
| Clinic Cloud | https://www.clinic-cloud.com/ | Sitio oficial | OK |
| Curve Dental | https://www.curvedental.com/ | Sitio oficial | OK |
| DentalCore | https://dentalcore.app/ | Sitio oficial | Conexión rechazada el 2026-11-17; operativo el 2026-11-19 (verificación del equipo) |
| DentalCore | https://www.capterra.ca/software/1102553/Dentalcore | Marketplace | HTTP 403 |
| Dentalink | https://www.softwaredentalink.com (redirección desde https://www.dentalink.cl/) | Sitio oficial | OK |
| Dentalink | https://web-es.softwaredentalink.com/en/planes | Página de planes | OK |
| Dentalink | https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina | Centro de ayuda | OK |
| Dentally | https://dentally.com/ | Sitio oficial | OK |
| Dentally | https://www.dentally.com/en/pricing | Página de precios | HTTP 404 |
| DentalSoft | https://dentalsoft.com.ar/ | Sitio oficial | OK |
| DentalSoft | https://www.dentalsoft.com.ar/blog/como-elegir-software-gestion-consultorio-odontologico | Blog del proveedor | Solo fragmento del buscador |
| DentalSoft | https://www.getapp.com/all-software/a/dentalsoft/ | Marketplace | HTTP 403 |
| DentalTec | https://web.dentaltec.com.ar/ | Sitio oficial | OK |
| DentalTec | https://web.dentaltec.com.ar/funcionalidades | Sitio oficial | OK |
| DentalTec | https://web.dentaltec.com.ar/software-odontologos-argentina | Sitio oficial | Solo fragmento del buscador |
| DentiClick | https://www.denticlick.app/ | Sitio oficial | OK |
| DENTIDESK | https://www.dentidesk.com/ | Sitio oficial | OK |
| DENTIDESK | https://www.dentidesk.com/precios | Página de precios | HTTP 404 |
| Dentrix | https://www.dentrix.com/ | Sitio oficial | OK |
| Doctoralia | https://www.doctoraliar.com/ | Plataforma de reserva | OK |
| Doctoralia | https://www.doctoralia.com.ar/ | Plataforma de reserva | Conexión rechazada |
| Doctoralia | https://apps.apple.com/es/app/doctoralia-para-especialistas/id1237598188 | Tienda de apps | Solo fragmento del buscador |
| Doctoralia | https://pabau.com/es/blog/precios-de-doctoralia-pro/ | Tercero (blog) | Solo fragmento del buscador |
| Gesden | https://www.gesden.es/ | Sitio oficial | Conexión rechazada |
| Gesden | https://pabau.com/es/blog/precios-de-infomed/ | Tercero (blog) | Solo fragmento del buscador |
| Gesden | https://www.softwaredoit.es/infomed/infomed.html | Tercero (distribuidor) | Solo fragmento del buscador |
| OdontoClinIA | https://www.clinia.com.ar/odontologia | Sitio oficial | OK |
| Open Dental | https://www.opendental.com/ | Sitio oficial | OK |
| Open Dental | https://www.opendental.com/site/fees.html | Página de precios | OK |
| Turnito | https://www.turnito.app/ | Sitio oficial y planes | OK |
| XIAgenda | https://www.xiagenda.com/ | Sitio oficial | DNS no resuelve |
| XIAgenda | https://www.comparasoftware.com/xiagenda | Agregador | OK |
| Contexto de mercado | https://repositorio.udesa.edu.ar/jspui/bitstream/10908/23801/1/%5bP%5d%20%5bW%5d%20MBA%20Nervi%2c%20Santiago%20Luis.pdf | Tesis (UdeSA) | Solo fragmento del buscador |

La sección "Fuentes" de cada nota en `docs/discovery/sources/` tiene el detalle de qué dato salió de cada URL.
