# Instrucciones para agentes de código

Para opencode, Codex y cualquier otro agente que trabaje en este repositorio.
**La fuente completa de reglas es `CLAUDE.md`**: leerla antes de cambiar
cualquier cosa. El detalle del flujo para implementar una historia está en
`docs/flujo-sdd.md`. Lo de abajo es lo que nunca se puede saltar.

## Siempre

- **Nunca trabajar en `main`.** Todo entra por Pull Request, con el CI en
  verde: el código en `feature/hu-XX-nombre-corto`, `fix/…` o `chore/…`, y la
  documentación en `docs/…`. Cada PR sigue `.github/pull_request_template.md` y
  cierra su issue (`Closes #N`).
- **No commitear, no pushear y no abrir PRs** sin pedido explícito del usuario.
- **No instalar dependencias** (`npm install`, cambios en `pom.xml`) sin avisar
  antes.
- **Ningún secreto en el repositorio:** credenciales solo en `.env`. Nunca
  copiar manuales de proveedores ni claves ajenas. El agente nunca escribe
  contraseñas: las pone el usuario.
- **No inventar comportamiento:** las reglas de negocio están en `CLAUDE.md`,
  las historias en `docs/historias/` y las decisiones en `docs/decisiones.md`
  (empezar por su índice). Si algo falta o contradice lo documentado,
  preguntar.

## Flujo para una historia (SDD)

1. **Historia:** `docs/historias/HU-XX.md` a partir del issue
   (`.claude/commands/sdd/historia.md`).
2. **Plan corto:** `docs/planes/AAAA-MM-DD-hu-xx-….md`
   (`docs/flujo-sdd.md` → «Formato de los planes»).
3. **Análisis antes de programar** (`.claude/commands/sdd/analizar.md`).
4. **TDD:** una tarea a la vez, la prueba primero. Cada prueba cita su RF
   («HU-XX RF-N»).
5. **Cierre = Definition of Done** (`.claude/commands/sdd/cerrar.md`).
6. **PR.**

## Comandos

- Backend: `cd backend && ./mvnw test` (Java 21, sobre H2; no necesita MySQL).
- Frontend: `cd frontend && npx ng test --watch=false && npx ng build` (Vitest).
- Todo junto: `docker compose up -d --build`.

## Rol: documentación

Para quien documenta el proyecto (con opencode u otro agente).

- **Dónde:** en Markdown, dentro de `docs/proyecto/`. Ahí se puede escribir
  libremente: perfil del proyecto, metodología Scrum, actas de cada sprint
  (planning, review y retro) y requisitos.
- **De dónde sale lo que se escribe:** solo de los datos reales del
  repositorio:
  - las historias (`docs/historias/`);
  - las decisiones (`docs/decisiones.md`);
  - los issues y los PR de cada sprint (`gh issue list`, `gh pr list
    --state merged`);
  - el código, para describir la arquitectura.

  **No se inventan funciones** ni se describe algo que el repositorio no
  muestre. Si falta un dato, se pregunta.
- **Cómo entra:** por PR, desde una rama `docs/…`.
- **Lo que no se toca:** `backend/`, `frontend/`, `CLAUDE.md`, `AGENTS.md`,
  `docs/historias/`, `docs/planes/` y `docs/decisiones.md`. Un cambio al código
  va por PR revisado por el dueño del código (`.github/CODEOWNERS`).
- **El Word:** al final se exporta `docs/proyecto/` con el formato de la
  universidad (pandoc o un generador); el Markdown es la fuente.
