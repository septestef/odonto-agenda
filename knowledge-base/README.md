# turnotardeutn — Base de Conocimiento

Base de conocimiento armada a partir de la fase de Discovery (`docs/discovery/`) y de las decisiones de arquitectura tomadas con el usuario el 2026-11-17.

## Índice de archivos

| Archivo | Contenido |
|---|---|
| [01_vision_y_objetivos.md](01_vision_y_objetivos.md) | Propósito, objetivos por actor, alcance de la demo v0.1 y lo que queda afuera |
| [02_descripcion_general.md](02_descripcion_general.md) | Stack, arquitectura monolítica, integraciones y endpoints REST |
| [03_actores_y_roles.md](03_actores_y_roles.md) | Odontólogo, paciente anónimo, matriz de permisos y rutas públicas |
| [04_modelo_de_datos.md](04_modelo_de_datos.md) | Entidades, ERD, exclusion constraint anti-solapamiento y seed de demo |
| [05_reglas_de_negocio.md](05_reglas_de_negocio.md) | Reglas RN-AU, AG, TU, PA, NO, SE y GL |
| [06_funcionalidades.md](06_funcionalidades.md) | 6 épicas y 15 historias de usuario con prioridad MoSCoW |
| [07_flujos_principales.md](07_flujos_principales.md) | Login, disponibilidad, reserva, cancelación/reprogramación y recordatorios |
| [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md) | Patrones, directorios, seguridad, variables de entorno y testing |
| [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md) | Decisiones DD-01..09 y supuestos SU-01..06 |
| [10_preguntas_abiertas.md](10_preguntas_abiertas.md) | Inconsistencias IN-01..02 y preguntas PA-01..09 |

## Quick Start para desarrolladores

1. Entender el dominio → [01](01_vision_y_objetivos.md), [03](03_actores_y_roles.md)
2. Entender los datos → [04](04_modelo_de_datos.md)
3. Entender las reglas → [05](05_reglas_de_negocio.md)
4. Entender la arquitectura → [02](02_descripcion_general.md), [08](08_arquitectura_propuesta.md)
5. Implementar → [07](07_flujos_principales.md), [06](06_funcionalidades.md)
6. Antes de codificar → [10](10_preguntas_abiertas.md)

## Resumen ejecutivo

turnotardeutn es un SaaS de turnos para odontólogos independientes en Argentina. El paciente reserva online con su DNI, sin cuenta, y gestiona el turno desde un link que recibe por email. La pieza central es el anti-solapamiento estricto, garantizado por PostgreSQL incluso con reservas concurrentes. La v0.1 es una demo local para el 2026-11-25, con Spring Boot, PostgreSQL y TypeScript vanilla, presupuesto cero y la mantenibilidad por encima del alcance.
