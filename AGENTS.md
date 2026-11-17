# OdontoAgenda — Instrucciones para Agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Se generó a partir de `knowledge-base/`, `CHANGES.md`, `design-system/` y `.atl/skill-registry.md`. Si lo editás, re-sincronizá los dos archivos.

**Producto**: OdontoAgenda, SaaS de turnos para odontólogos independientes en Argentina. El paciente reserva online con su DNI, sin cuenta, y gestiona su turno desde un link único que recibe por email. Lo central del dominio es el anti-solapamiento estricto.
**Repo / carpeta**: `turnotardeutn`. **Demo funcional en localhost: miércoles 2026-11-25.** Presupuesto cero.

---

## Stack Tecnológico

| Capa | Tecnologías | Versión mínima |
|---|---|---|
| Frontend | TypeScript vanilla, Vite (servidor de desarrollo y build) | TS 5.x, Vite 5.x |
| Backend | Java, Spring Boot (Web, Security, Data JPA, Validation, Mail), Hibernate | Java 25 (LTS), Spring Boot compatible con Java 25 |
| Build | Gradle (Kotlin DSL) con wrapper | 9.x (requerido por JDK 25) |
| Base de datos | PostgreSQL con `btree_gist` | 15+ |
| Migraciones | Flyway | Gestionada por Spring Boot |
| Email | Gmail SMTP (contraseña de aplicación) | — |
| Tests | JUnit 5 + Spring Boot Test, Vitest | — |

Arquitectura: monolito modular por funcionalidad. En la demo, el build de Vite se sirve como estático desde Spring (mismo origen, sin CORS).
Detalle completo: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md) y [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md).

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/`. **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---|---|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Propósito, alcance v0.1 y lo que queda fuera |
| [02_descripcion_general.md](knowledge-base/02_descripcion_general.md) | Stack, arquitectura, endpoints REST |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Auth, permisos, rutas públicas (paciente sin cuenta) |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | Entidades, ERD, exclusion constraint, seed |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas codificadas RN-AU/AG/TU/PA/NO/SE/GL |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias US-001..015 con criterios de aceptación |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Disponibilidad, reserva, cancelar o reprogramar, recordatorios |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Patrones, directorios, seguridad, variables de entorno, testing |
| [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md) | Decisiones DD-01..09 y supuestos SU-01..06 |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | ⚠️ Inconsistencias y preguntas a revisar antes de codear |

> ⚠️ No hay preguntas de prioridad **Alta** abiertas. Antes de los changes que las tocan, revisá IN-01 (cancelación en cualquier momento; 24 h solo para reservar) y PA-02/PA-03 (turno manual y pasos de 15 minutos).

Contexto de mercado: [discovery/discovery.md](discovery/discovery.md) y [discovery/research-report.md](discovery/research-report.md).

### Design system (política `system-and-mockups`, revisión 2 aprobada)

Todo cambio visual necesita un **mockup HTML aprobado** en `openspec/changes/<name>/mockups/` **antes del apply**. Los mockups y la UI real importan [design-system/tokens.css](design-system/tokens.css) y `design-system/brand.js`, y siguen [design-system/components.html](design-system/components.html). Marca y reglas de uso: [design-system/brand.md](design-system/brand.md). El logo está en `design-system/assets/logo.svg`.

---

## Skills Disponibles

| Agente | Rol | Skills que carga |
|---|---|---|
| **Backend Core** | Dominio Spring Boot: agenda, disponibilidad, turnos, JPA, scheduling | `java-springboot`, `springboot-patterns`, `java-junit` |
| **Backend Seguridad** | Sesión del odontólogo, CSRF, tokens de turno, aislamiento entre odontólogos, rate limiting | `springboot-security`, `java-junit` |
| **Frontend** | SPA en TypeScript vanilla + Vite, pantallas del odontólogo y del paciente | `vitest`, `javascript-testing-patterns`, `emil-design-eng`, `design-taste-frontend-v1`* |
| **Orquestación / SDD** | Ciclo OpenSpec: proponer, implementar, sincronizar, archivar | `openspec-propose`, `openspec-apply-change`, `openspec-update-change`, `openspec-sync-specs`, `openspec-archive-change`, `openspec-explore` |

\* `design-taste-frontend-v1` asume React y Tailwind. En este proyecto manda el design system aprobado (`tokens.css` + `components.html`) por encima de lo que esa skill trae por defecto.

Cargá la skill que corresponde al contexto ANTES de escribir código.

> Los compact rules de cada skill los resuelve el orquestador desde `.atl/skill-registry.md` (generado por `skill-registry`; no versionado — no está en el repo).

---

## Roadmap de Changes

El plan completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 20 changes en 8 fases. **Pre-demo**: C-01..C-16. **Post-demo**: C-17..C-20 (no se tocan antes del 25/11).
- **Camino crítico pre-demo** (7): `C-01 → C-02 → C-07 → C-08 → C-09 → C-11 → C-16`.
- **Recortables si hay atraso**: primero C-15 (turno manual) y después C-14 (recordatorios y WhatsApp).
- **Primer change**: `C-01 foundation-setup`.
- **Primer change post-demo**: `C-17 historia-clinica-basica`; después `C-18 odontograma`.
- Los changes **[UI]** (C-04, C-06, C-10, C-11, C-13, C-14, C-15, C-17, C-18, C-19) requieren mockup aprobado. Conviene aprobar en lote los de C-04, C-06, C-10, C-11 y C-13 al principio.

**Antes de cualquier `/opsx:propose`**: leé [CHANGES.md](CHANGES.md), identificá las dependencias del change y los archivos de "Leer antes".

---

## Reglas Duras (específicas del proyecto)

> Reglas globales ya definidas en `~/.claude/CLAUDE.md` (orquestador OPSX/`openspec` CLI, governance por dominio, engram, etapa de diseño y mockups): el proyecto las hereda. Acá viven solo las reglas **específicas de este proyecto** y las universales que el global no cubre.

Son contrato: romperlas es un defecto.

### Backend y datos
- **NUNCA** validar el anti-solapamiento solo en Java → la garantía es la exclusion constraint `turno_sin_solapamiento` de PostgreSQL (RN-TU-01, DD-03). Todo cambio que toque reservas mantiene el test de reservas concurrentes (exactamente una gana).
- **NUNCA** usar H2 ni otra base embebida en los tests → los tests de integración corren contra PostgreSQL real (base `turnotarde_test`).
- **NUNCA** llamar a `Instant.now()`, `LocalDateTime.now()` ni similares directamente → usar el `java.time.Clock` inyectado. Se persiste en `timestamptz` (UTC); `America/Argentina/Buenos_Aires` solo para interpretar y mostrar.

### Seguridad y API
- **NUNCA** tomar el `odontologo_id` del request → sale siempre de la sesión. Un recurso de otro odontólogo devuelve **404** (RN-SE-01).
- **NUNCA** exponer entidades JPA en la API → usar `record` DTOs con Bean Validation.
- **NUNCA** commitear secretos (contraseña de la base, contraseña de aplicación de Gmail, `DEMO_PASSWORD`) ni guardar en claro los tokens de turno → secretos solo por variables de entorno (`.env` en `.gitignore`, `.env.example` versionado); de los tokens solo se guarda el hash SHA-256 (RN-SE-02).
- Toda regla de negocio que rechaza una operación responde, a través del `@RestControllerAdvice` central, `{ "codigo": "RN-XX-NN", "mensaje": "..." }` con el código de [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md).

### Frontend
- `tsconfig` en modo `strict`. **NUNCA** usar `any` → tipar, o usar `unknown` con validación.
- **NUNCA** hardcodear colores, espaciados, radios ni fuentes → usar las variables de `design-system/tokens.css`.
- Todos los textos de la UI van en español rioplatense con voseo ("Elegí un horario", "Cancelá tu turno").

### Git y flujo
- **NUNCA** hacer commit ni push sin que el usuario lo pida explícitamente.
- Commits con **Conventional Commits** y el código del change como scope: `feat(C-07): reserva online con anti-solapamiento`, `test(C-07): ...`, `fix(C-09): ...`.
- **NUNCA** agregar líneas `Co-Authored-By` de IA ni atribución a la IA en los commits.
- **NUNCA** recortar tests por falta de tiempo → se recortan historias S/C (DD-08).

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)          → entender el dominio
2. Identificar el change en CHANGES.md             → respetar dependencias y GATES
3. /opsx:propose C-NN-nombre                       → proposal + design + specs + tasks
3b. Si es [UI]: mockup en openspec/changes/<name>/mockups/ → aprobación explícita del usuario
4. Implementar las tasks (cargando las skills)     → respetando las reglas duras
5. Tests en verde → /opsx:archive C-NN-nombre      → marcar [x] en CHANGES.md
```

Aplicá TODAS las reglas duras en cada paso. Si la KB y este archivo se contradicen, prevalecen las reglas duras.
