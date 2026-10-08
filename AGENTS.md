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

Para quien documenta el proyecto con opencode u otro agente. La mayor parte de
la documentación del proyecto la escribe este rol: estas reglas mandan sobre lo
que sugiera cualquier otra instrucción, salvo las de «Siempre».

### Alcance

**Se escribe solo en `docs/proyecto/`**, en Markdown:

| Archivo | Contenido | Sale de |
|---|---|---|
| `perfil.md` | Problema, objetivos, alcance, fuera de alcance, equipo, plazo | `CLAUDE.md` → «Proyecto» y «Reglas de negocio»; backlog (issues) |
| `metodologia.md` | Scrum en este proyecto: roles, eventos, artefactos, flujo SDD, Definition of Done, ramas y PR | `docs/flujo-sdd.md`, `CLAUDE.md` → «Forma de trabajo» |
| `requisitos.md` | Requisitos funcionales («HU-XX RF-N») y no funcionales | `docs/historias/`, `CLAUDE.md`, `docs/decisiones.md` (comando `/requisitos`) |
| `arquitectura.md` | Stack, capas, módulos, modelo de datos, API, seguridad, despliegue | El código (`backend/`, `frontend/`), `docker-compose*.yml`, `docs/decisiones.md` |
| `backlog.md` | Historias por sprint y su estado | `gh issue list --state all` y el Project |
| `sprints/sprint-N.md` | Acta de cada sprint: planning, review y retro | Issues, PR y lo que cuente el usuario (comando `/acta N`) |
| `diagramas/` | Diagramas (casos de uso, clases, entidad-relación, secuencia, despliegue) en Mermaid dentro de Markdown | El código y las historias |

Si la universidad pide otro capítulo o estructura, se agrega acá con el mismo
criterio: cada archivo dice de dónde sale.

**No se toca nada fuera de `docs/proyecto/`:** ni `backend/`, `frontend/`,
`CLAUDE.md`, `AGENTS.md`, `docs/historias/`, `docs/planes/`,
`docs/decisiones.md`, `docs/flujo-sdd.md`, `.claude/`, `.opencode/` ni
`.github/`. Un PR que los toque necesita la aprobación del dueño del código
(`.github/CODEOWNERS`).

### Fuentes y veracidad

- **Solo datos reales del repositorio:** historias, decisiones, issues, PR,
  planes y código. **No se inventan funciones, pantallas, endpoints, fechas,
  reuniones ni cifras.** Si falta un dato, se pregunta al usuario o se deja
  «[FALTA DATO]».
- **Implementado ≠ planificado.** Algo está implementado solo si su PR está
  mergeado en `main`. Lo que está en el backlog se describe como planificado,
  con su sprint; nunca como hecho.
- **Si dos fuentes se contradicen** (una historia y el código, dos
  decisiones), no se corrige ninguna ni se elige una: se avisa al usuario para
  que lo resuelva quien programa.
- Los RF se citan con su identificador completo («HU-XX RF-N») y **nunca se
  renumeran**: las pruebas los citan.
- Cada afirmación técnica dice de dónde sale (archivo o sección) cuando no es
  obvio.

### Estilo

- En español, en tono formal e impersonal, con frases cortas.
- Los nombres de clases, endpoints, archivos y comandos van en `código`, tal
  como están en el repositorio.
- Diagramas en Mermaid (bloques ```` ```mermaid ````), para que se versionen y
  se exporten.
- Acá sí se nombra a la universidad y que es un proyecto académico (la regla de
  no mencionarla es solo para la interfaz de la aplicación).

### Seguridad

- **Ningún secreto ni dato personal:** ni el contenido del `.env`,
  contraseñas, claves, tokens, correos o teléfonos reales. Los ejemplos usan
  datos inventados y evidentes (`cliente@ejemplo.com`).
- **No se copian manuales de proveedores** (ni el de pago): se describe la
  interfaz propia (`IPago`) y se dice que el proveedor real va detrás de ella.
- El nombre del proveedor de pago puede aparecer en la documentación, nunca
  como si fuera parte de la API de la tienda.

### Comandos de opencode (`.opencode/commands/`)

- `/acta N`: escribe `docs/proyecto/sprints/sprint-N.md` con los issues y PR
  del sprint y lo que cuente el usuario.
- `/requisitos`: pasa los RF de `docs/historias/` a `docs/proyecto/requisitos.md`.

Los dos necesitan `gh` con la sesión iniciada (`gh auth login`). Con `gh` solo
se **lee** (`issue list`, `issue view`, `pr list`, `pr view`): no se crean,
editan, cierran ni mergean issues o PR.

### Cómo entra

- Rama `docs/…` desde `main` actualizado (`docs/sprint-1`, `docs/requisitos`…).
- Commits y PR **solo cuando el usuario lo pide**. El PR sigue
  `.github/pull_request_template.md`: «Trabajo de documentación, sin issue» (o
  `Closes #N` si hay un issue de documentación) y, en «Qué cambia», los
  archivos y de qué fuentes salieron.
- Con el CI en verde, el PR se puede mergear: `docs/proyecto/` no necesita la
  aprobación del dueño del código.

### El Word final

Al final se exporta `docs/proyecto/` al Word con el formato de la universidad
(pandoc con la plantilla de la universidad como `--reference-doc`, o un
generador). **El Markdown es la fuente:** las correcciones se hacen en el
Markdown, no en el Word.
