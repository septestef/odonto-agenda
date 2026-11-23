# Proposal

## Why

La demo del miercoles 2026-11-25 necesita un arranque reproducible en localhost y un ensayo integrado de lo que ya entro. Los changes funcionales archivados no reemplazan la comprobacion conjunta de datos, correo real, concurrencia y pantallas en la notebook.

## What Changes

- Preparar el frente A (Lauti): arranque desde cero con reset de `public` ya encargado para apply habilitado tras aprobar la propuesta, exclusivamente en una DB local confirmada, exclusiva de demo y descartable, perfil `demo`, Flyway, frontend empaquetado y login en `http://localhost:8080`.
- Revisar `.env.example` sin secretos y documentar en README la seccion exacta `Correr la demo en la notebook`, con secuencia verificada y checklist del dia.
- Verificar V900 para la agenda del 25/11 y los huecos de reserva; si hace falta corregir datos, agregar una nueva migracion 9xx en `db/demo`, nunca editar V900 mergeada.
- Planificar suite completa y ensayo Gmail con datos sinteticos, entrega/spam y gestion desde el link, sin exponer credenciales ni tokens.
- Preparar el frente B (Jero): guion, smoke manual si no hay browser automatizable, evidencia sanitizada y comparacion visual con los mockups aprobados. Jero espera la propuesta integrada en `main` antes de crear su rama.
- Congelar alcance real: incluir WhatsApp y turno manual ya archivados; excluir recordatorio automatico US-014 (C-21), US-013 e historias post-demo. No redisenar ni corregir UI en este change.

## Capabilities

### New Capabilities

- `preparacion-demo`: contrato operativo de arranque seguro, ensayo y evidencia de aceptacion integrada para el 25/11; complementa, sin duplicar ni cambiar, `configuracion-entorno`, `datos-demo` y `entrega-frontend`.

### Modified Capabilities

Ninguna. No se cambian reglas de reserva, autenticacion, correo ni contratos de API existentes.

## Impact

- Dependencias verificadas en CHANGES y archivos archivados: C-06, C-11 y C-13 completos; C-14 entro solo con WhatsApp y C-15 con turno manual. C-12 esta archivada, no activa.
- Futuro apply A limitado a `README.md`, `.env.example`, `backend/**` (seed/config y tests necesarios) y seccion A de `tasks.md`. Si requiere frontend o build raiz, detenerse y solicitar ampliacion antes de editar.
- B documentara guion y evidencias dentro de este change. Diferencias que requieran codigo fuera del alcance se reportan para decision, no se corrigen implicitamente.
- Sin dependencias nuevas ni cambios visuales. No se crea mockup nuevo.
- El encargo ya autoriza reset local y ensayo Gmail tras aprobar la propuesta y habilitar apply, con revision de datos/config. Antes de ejecutar se informa y verifica el destino concreto; pedir aclaracion solo ante ambiguedad o desvio de alcance. Credenciales reales las configura el usuario fuera del chat.
- Esta entrega es solo propuesta local. Commit, push y PR solo de propuesta quedan pendientes de pedido explicito; usar Conventional Commits con scope `C-16`, sin atribucion IA. Integracion de implementacion, CHANGES C-16 `[x]` y archive son cierre futuro despues de B y revision final aprobada.
