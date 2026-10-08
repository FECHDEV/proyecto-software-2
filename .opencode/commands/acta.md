---
description: Escribe el acta de un sprint (planning, review y retro) en docs/proyecto/sprints/sprint-N.md
---

Escribir `docs/proyecto/sprints/sprint-$1.md`, el acta del sprint $1, con
datos reales del repositorio y de lo que cuente el usuario. Reglas en
`AGENTS.md` → «Rol: documentación».

## 1. Preguntar lo que el repositorio no sabe

De a una pregunta por vez:

- fecha de inicio y de fin del sprint;
- quiénes participaron;
- el objetivo del sprint, si no está en el issue o en el Project;
- lo que se dijo en la review y en la retro (qué salió bien, qué no, qué se
  cambia).

Si el archivo ya existe, se amplía: no se reescribe lo que ya está.

## 2. Juntar los datos del sprint

- Historias del sprint: `gh issue list --state all --search "HU- in:title"`
  y, para cada una, `gh issue view <número>`. Quedarse con las que el usuario
  confirme como parte del sprint.
- Lo que se terminó: `gh pr list --state merged --search "merged:<inicio>..<fin>"`.
  Cada PR dice qué issue cierra (`Closes #N`).
- Lo que quedó sin terminar: las historias del sprint con el issue abierto.
- De cada historia terminada: su `docs/historias/HU-XX.md` (qué hace) y el
  «Resultado» de su plan en `docs/planes/` (pruebas, prueba manual).
- Decisiones nuevas del sprint: las de `docs/decisiones.md` → «Decisiones del
  proyecto» con fecha dentro del sprint.

No se inventa nada. Si un dato falta, se pregunta o se deja marcado
«[FALTA DATO]».

## Formato de salida

```markdown
# Sprint $1 — <nombre corto>

Del <inicio> al <fin>. Participantes: <nombres>.

## Planning
**Objetivo:** <una frase>.

| Historia | Issue | Estado al cierre |
|---|---|---|
| HU-XX <título> | #N | Hecho / Pendiente |

## Review
<Qué se mostró, por historia, y lo que dijo quien revisó.>

| PR | Historia | Mergeado |
|---|---|---|
| #N <título> | HU-XX | <fecha> |

## Retrospectiva
- **Salió bien:** …
- **A mejorar:** …
- **Cambios para el próximo sprint:** …

## Decisiones del sprint
- <fecha> — <decisión> (`docs/decisiones.md` → <sección>)
```

Al terminar, recordar que entra por PR desde una rama `docs/sprint-$1`.
