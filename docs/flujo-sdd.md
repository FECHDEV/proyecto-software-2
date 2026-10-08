# Flujo SDD con Scrum

Cómo se implementa una historia de usuario: del issue del backlog al PR
mergeado. **Leer este archivo antes de empezar una historia o un cambio a una
historia.** Las reglas que valen en todas las sesiones (ramas, commits,
dependencias, subagentes) están en `CLAUDE.md`. Los comandos `/sdd:*` están en
`.claude/commands/sdd/`.

Es el flujo de [hello-sdd](https://github.com/mouredev/hello-sdd) con el paso
de análisis de Spec Kit, dentro de sprints de Scrum.

## Scrum en este repositorio

| Scrum | Dónde vive |
|---|---|
| Product backlog | **GitHub Projects**: una historia por issue, con título `HU-XX Como … quiero … para …` |
| Tablero | Columnas *Backlog → Sprint → En curso → En revisión → Hecho* |
| Sprint planning | Las historias que pasan a *Sprint*, con su `docs/historias/HU-XX.md` y su plan |
| Definition of Done | Lo que exige `/sdd:cerrar` (abajo) |
| Sprint review | Demo de los PR mergeados en el sprint |
| Retrospectiva | Ajustes a este flujo y a `docs/decisiones.md` |
| Actas | `docs/proyecto/` (planning, review y retro de cada sprint) |

Cada PR cierra su issue (`Closes #N`), y el issue pasa a *Hecho* al mergear.

## Historia nueva

```
/sdd:historia HU-XX → plan corto → /sdd:analizar HU-XX → TDD → /sdd:cerrar HU-XX → PR → CI en verde → merge
```

1. **`/sdd:historia HU-XX`:** lee el issue, entrevista para completar lo que
   falta y escribe `docs/historias/HU-XX.md` con «Como… quiero… para…», los
   criterios en EARS (RF-N numerados por historia) y los escenarios
   «Dado / Cuando / Entonces». Las reglas nuevas que salgan van a
   `decisiones.md`.
2. **Plan corto** en `docs/planes/AAAA-MM-DD-hu-xx-nombre-corto.md`, en el
   formato de abajo.
3. **`/sdd:analizar HU-XX`:** solo lee y revisa la coherencia de la historia,
   el plan, `CLAUDE.md` y `decisiones.md`. Con un hallazgo crítico no se
   empieza a programar.
4. **TDD:** una tarea a la vez, la prueba primero. Cada prueba cita su RF
   («HU-XX RF-N»).
5. **`/sdd:cerrar HU-XX`:** la Definition of Done (abajo), con el «Resultado»
   anotado en el plan.
6. **PR** con la plantilla de `.github/pull_request_template.md` y
   `Closes #N`. El ruleset de `main` exige PR y los status checks del CI: en
   rojo no se mergea.

## Variantes

- **Cambio a una historia ya cerrada** (primero la spec, después el código):
  1. La decisión, con fecha, en `decisiones.md`.
  2. `/sdd:historia HU-XX`, que **amplía** el archivo existente: los RF nuevos
     siguen la numeración y no se renumera nada.
  3. Un **plan nuevo** en formato corto. El plan viejo no se toca: es el
     historial.
  4. `/sdd:analizar` → TDD → `/sdd:cerrar` → PR.
- **Solo frontend de una historia cuyo backend ya existe:** no se vuelve a
  correr `/sdd:historia`; se sigue desde el plan.
- **Trabajo técnico sin historia** (configuración, dependencias, refactor): rama
  `chore/…` o `fix/…`, PR con CI en verde; no pasa por `/sdd:*`.

## Numeración de los RF

«RF-N» es siempre el RF **de la historia**, según su archivo (RF-1, RF-2…). Las
pruebas citan «HU-XX RF-N», o «RF-N» en un archivo cuyo encabezado o `describe`
nombra a la historia. Los RF ya numerados no se renumeran.

## Formato de los planes

Cortos, con el mismo nivel en backend y frontend. Cada tarea lleva:

- los archivos que toca y qué hace;
- las firmas de lo nuevo: clases, métodos públicos, DTO y endpoints;
- los nombres de sus pruebas, cada una con el RF que cubre (ningún RF sin
  prueba), más los casos borde: respuestas tardías, dos operaciones a la vez,
  datos basura en la URL o en la respuesta;
- las decisiones tomadas y su motivo.

**Código completo en el plan solo para lo delicado:** bloqueos y concurrencia,
transacciones, pagos y dinero, seguridad (tokens, contraseñas, autorización).
Lo rutinario (DTO, mapeos, pantallas, estilos, pruebas obvias) se escribe una
sola vez, en el repositorio. Si el plan lo va a ejecutar otra herramienta u otra
sesión sin contexto, se escribe en formato largo.

**Motivo:** el código del plan se reescribía casi igual en el repositorio y
quedaba desactualizado. La calidad la cuidan TDD, la suite completa y las
revisiones antes del PR.

## Definition of Done (`/sdd:cerrar`)

Una historia está terminada cuando:

- **cada RF tiene su prueba**, verificado contra el código;
- **las suites completas están en verde**: `./mvnw test`, `npx ng test
  --watch=false` y `npx ng build` sin avisos;
- pasó las **revisiones antes del PR** (abajo), con los hallazgos corregidos o
  descartados con motivo;
- tuvo **prueba manual** de sus escenarios, confirmada por quien la pidió;
- el **«Resultado»** está anotado en el plan;
- el **CI está en verde** en el PR.

## Revisión antes del PR

- `/code-review` sobre los cambios de la rama: siempre.
- `/security-review` en lo que toca **seguridad** (cuentas, sesión, roles,
  contraseñas) y **pagos y dinero**, y cuando la historia abre una **superficie
  nueva** para datos que vienen de afuera: subida de archivos, exportaciones,
  cualquier endpoint público.
- `web-design-guidelines` sobre los componentes, si la historia tiene frontend.

## Dónde va cada commit de una historia

- **Primer commit de la rama de la historia:** la historia
  (`docs/historias/HU-XX.md`), sus decisiones en `decisiones.md` y el plan.
- **Antes de abrir el PR:** el resultado de las revisiones y de la prueba
  manual se anota en el plan, y las reglas técnicas nuevas en `decisiones.md`.

Todo entra a `main` por PR: también la documentación.
