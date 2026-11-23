# Spec Delta

## Purpose

Ofrecer una lectura paginada y aislada de los pacientes propios del odontólogo, con una respuesta JSON estable para sus consumidores.

P3/P7/P9 aprobadas y apply autorizado explícitamente el 2026-11-22; primera etapa limitada a preparación/baseline según design.md.

## ADDED Requirements

### Requirement: Lectura privada de pacientes propios
`GET /api/pacientes` MUST requerir sesión ODONTOLOGO y devolver únicamente pacientes de ese dueño, incluso si no tienen turnos futuros. MUST ignorar IDs de odontólogo en el pedido como fuente de pertenencia. GET MUST NOT requerir CSRF. Sin sesión MUST responder 401 `NO_AUTENTICADO`; con sesión sin rol permitido MUST responder 403 `ACCESO_DENEGADO`. No define búsqueda ni alta/edición de pacientes.

#### Scenario: Mismo DNI con dos odontólogos
- **WHEN** A consulta e intenta indicar el dueño B en query/header y ambos tienen un paciente con el mismo DNI
- **THEN** solo recibe el paciente de A, aun si solo tiene turnos históricos

#### Scenario: Acceso anónimo o sin rol
- **WHEN** se consulta sin sesión o con sesión sin rol ODONTOLOGO
- **THEN** se recibe respectivamente 401 o 403 uniforme, sin contenido de pacientes

### Requirement: Datos de paciente sin secretos de gestión
Cada paciente MUST contener exactamente `id` (int64), `dni` (string), `nombre`, `apellido`, `telefono`, `email` (strings), no nulos. email MUST salir del contacto mutable de paciente, nunca del email_gestion de un turno. MUST NOT incluir datos de otros dueños, token/hash (`token_hash`/`tokenHash`), email de gestión (`email_gestion`/`emailGestion`), datos clínicos o auditoría, tampoco valores reservados por eco en listados, objetos anidados o errores.

#### Scenario: Contacto actualizado sin filtrar snapshot de turno
- **WHEN** un paciente tiene email vigente `actual@example.test` y un turno con email_gestion centinela distinto y token_hash centinela
- **THEN** el listado solo informa el email vigente contratado; ni el listado ni sus errores incluyen campos o valores reservados de gestión

### Requirement: Paginación de pacientes
La API MUST aceptar `page` int32 opcional por defecto 0 y `size` int32 opcional por defecto 20, con `page >= 0` y `1 <= size <= 100`. MUST responder 200 con exactamente `contenido` (array de pacientes), `page`, `size`, `totalElementos` (int64) y `totalPaginas` (techo de total/size, 0 si total=0). Consulta y conteo MUST aplicar el mismo dueño. Página excedida MUST dar array vacío con totales reales. MUST NOT exponer un objeto de paginación dependiente del framework.

#### Scenario: Página siguiente y límite permitido
- **WHEN** se solicita page=1&size=100 con 101 pacientes propios sin modificaciones concurrentes
- **THEN** contenido tiene el último paciente, totalElementos=101 y totalPaginas=2, sin repetir los primeros 100

#### Scenario: Vacío y página excedida
- **WHEN** no hay pacientes o se pide una página posterior a la última
- **THEN** contenido es []; en el primer caso ambos totales son 0 y en el segundo conservan los valores reales

### Requirement: Orden de pacientes
La API MUST ordenar por `apellido,nombre,id` ascendente, usando el orden de PostgreSQL sin prometer collation lingüística especial. MUST NOT agregar búsqueda ni orden configurable.

#### Scenario: Defaults y desempate
- **WHEN** se omiten page/size y hay más de 20 pacientes propios, algunos con mismo apellido y nombre
- **THEN** se devuelven los primeros 20, page=0, size=20, totales propios y desempate por id

### Requirement: Conversión de parámetros de paginación
Un `page` o `size` vacío, no entero o fuera de int32 MUST responder 400 `SOLICITUD_INVALIDA`, mensaje `No pudimos interpretar la solicitud.`, sin detalles. Defaults MUST aplicarse solo por ausencia.

#### Scenario: Conversión y vacío
- **WHEN** page o size es vacío, texto, decimal o excede int32
- **THEN** se responde SOLICITUD_INVALIDA sin aplicar defaults ni consultar datos

### Requirement: Validación explícita de paginación
page negativo o size fuera de 1..100 MUST responder 400 `VALIDACION`, mensaje `Hay datos inválidos. Revisalos y probá de nuevo.`, con detalles por cada campo inválido: `page` / `Ingresá una página mayor o igual a 0.` y `size` / `Elegí un tamaño entre 1 y 100.`. Un page positivo grande válido MUST NOT causar overflow ni error interno al calcular offset.

#### Scenario: Bordes semánticos
- **WHEN** se envía page=-1 o size=0 o size=101
- **THEN** se responde VALIDACION con cada detalle pertinente; size=1 y size=100 se aceptan

#### Scenario: Offset grande
- **WHEN** se pide page=2147483647&size=100 en una colección pequeña
- **THEN** se recibe 200 con contenido vacío y totales reales, sin desbordamiento
