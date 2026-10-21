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

### Scrum adaptado

*19/10/2026.* El equipo era de dos personas con dedicación parcial. *21/10/2026:*
David se retiró y el proyecto queda a cargo de Fabio solo, que programa y
documenta con Claude Code. Por eso Scrum se usa adaptado:

- **Sprints por objetivo, no por calendario.** Cada sprint tiene un objetivo
  (un incremento que se puede mostrar, como «el cliente arma su carrito») y
  termina cuando sus historias cumplen la Definition of Done, no en una fecha
  fija. Para el informe se anotan las fechas reales de inicio y de cierre.
- **Lo que no se cambia:** el backlog priorizado en el Project, una historia
  por issue, la Definition of Done (`/sdd:cerrar`) y la revisión del
  incremento antes de mergear.
- **Eventos juntos y breves:** la planning es elegir las historias del
  siguiente objetivo y pasarlas a *Sprint*. La review es la prueba manual de
  cada historia más la demo de lo mergeado. La retro son los ajustes con
  fecha en este archivo y en `decisiones.md`. No hay daily: con una
  persona, el tablero y los PR cumplen ese papel.
- **Roles:** Fabio es Product Owner, desarrollador y quien documenta. No hay
  Scrum Master aparte: el flujo SDD hace cumplir el proceso, y las revisiones
  antes del PR (subagente de interfaz, `/code-review`, `/security-review`)
  reemplazan la revisión de un compañero.
- **En el Project,** el campo «Sprint» (opciones fijas, sin fechas) dice a
  qué sprint pertenece cada issue.

| Sprint | Objetivo | Issues |
|---|---|---|
| 1 | Cuentas y sistema de diseño | chore de diseño, HU-01 a HU-05 |
| 2 | Catálogo | HU-06 a HU-10, spike 3D, datos de demostración |
| 3 | Compra | HU-11 a HU-14, HU-20 |
| 4 | Operación y 3D | HU-15 a HU-19 |

**Motivo:** con poca gente y horarios variables, un sprint de fecha fija
cierra a medias o queda vacío. El objetivo por sprint conserva lo que importa
de Scrum, que es entregar incrementos que funcionan, revisarlos y ajustar el
proceso, sin inventar fechas.

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
   («HU-XX RF-N»). Qué pruebas se corren (*19/10/2026*):
   - mientras se programa, solo las de lo que se toca
     (`./mvnw test -Dtest=Clase`, `npx ng test --watch=false --include=…`);
   - al terminar una tarea, la suite completa solo si tocó código
     compartido (entidades, DTO, `config/`, `core/`, `shared/`, rutas,
     estilos globales); si no, sus pruebas y `npx ng build`;
   - en `/sdd:cerrar` y en el CI, siempre las suites completas;
   - backend y frontend nunca a la vez: cargan tanto la máquina que las
     pruebas con límite de tiempo fallan sin motivo.

   **Motivo:** en HU-12, un campo nuevo de `CarritoResponse` rompió pruebas
   de cuatro archivos ajenos y el reloj de `ClockConfig` es de toda la
   aplicación: lo compartido necesita la suite. Lo demás ya pasó y no
   cambió; repetirlo en cada tarea solo cuesta tiempo.
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

*16/10/2026 (revisión de HU-10).* En dos rondas, porque cada corrección puede
romper otra cosa:

1. **Interfaz, con un subagente,** antes de la prueba manual y solo si la
   historia tiene pantallas: `web-design-guidelines`, `break-ui`,
   `review-animations` y `playwright-cli` en el navegador (escritorio,
   celular, modo claro, peores datos). Encuentra lo que solo se ve abriendo
   la pantalla.
2. **Código y seguridad, con los comandos,** sobre el diff final, después de
   corregir: `/code-review` siempre, pidiéndole que lea también la historia y
   el plan, y `/security-review` cuando corresponda (abajo). Son los que
   cuenta la Definition of Done.
3. **Cada hallazgo se verifica** contra el código antes de corregirlo o
   descartarlo: los comandos no comprueban lo que informan.
4. **El «Resultado» dice quién encontró qué:** un subagente no es
   `/code-review`.
5. **Hasta dónde:** lo que encuentren los comandos se verifica y se corrige
   (con su prueba) o se descarta con motivo, y `/code-review` se repite una
   sola vez más si las correcciones cambiaron lógica. Lo que quede después va
   al «Resultado» como pendiente: cada ronda encuentra algo, y sin un tope no
   se termina.

**Motivo:** en HU-10 el subagente de interfaz encontró problemas que solo se
ven en el navegador (foto recortada, foco invisible en modo claro), y ningún
comando los ve; el subagente de código y seguridad llegó a lo mismo que
`/code-review` y `/security-review`, así que se quitó. `/code-review`, corrido
después de corregir, encontró dos bugs que habían entrado al corregir. Si en
otra historia `/code-review` deja pasar algo grande, se vuelve a sumar el
subagente de código.

Lo que revisa cada uno:

- `/code-review` sobre los cambios de la rama: siempre.
- `/security-review` en lo que toca **seguridad** (cuentas, sesión, roles,
  contraseñas) y **pagos y dinero**, y cuando la historia abre una **superficie
  nueva** para datos que vienen de afuera: subida de archivos, exportaciones,
  cualquier endpoint público.
- `web-design-guidelines` sobre los componentes, si la historia tiene frontend.
- `review-animations` sobre las animaciones nuevas o cambiadas, si las hay.
  Solo se invoca a mano (`/review-animations`); un subagente sigue
  `.claude/skills/review-animations/SKILL.md` leyéndolo.
- `break-ui` sobre las pantallas nuevas que muestran datos (listas, tarjetas,
  fichas): con los peores datos realistas, antes de la prueba manual.

### Las revisiones, sin gastar de más

*19/10/2026 (cierre de HU-12).* Auditoría de los subagentes de HU-12: los
cuatro encontraron cosas reales, pero parte del costo no aportaba. Se
conserva cada revisión y se acota lo que lee:

- **Interfaz:** se le pasa una lista fija (escritorio y celular, modo
  oscuro y claro, y los estados de la historia), los simuladores que ya
  existan y el servidor ya levantado; hasta unas 12 capturas, y un informe
  corto (archivo:línea, qué pasa, corrección). En HU-12 gastó 146 mil
  tokens en 65 llamadas, la mayoría capturas, para 4 problemas reales.
- **`/security-review`:** solo los archivos que tocan el tema (controladores,
  servicios, `SecurityConfig`, DTO y entidades del backend), sin
  documentación ni pruebas. Un solo subagente que busca y descarta los
  falsos positivos; la segunda fase de la skill (un subagente por hallazgo)
  solo si hay hallazgos.
- **`/code-review`:** la primera ronda sobre el diff de código, sin la
  documentación (la historia y el plan se le nombran aparte); la segunda,
  solo sobre las correcciones.
- **`/code-review` y `/security-review` pueden ir en paralelo:** solo leen.
  Mientras corre una revisión no se edita el código que revisa.

**Motivo:** en HU-12 los cuatro subagentes gastaron unos 438 mil tokens. Las
dos rondas de `/code-review` encontraron los errores más graves (un pedido
que quedaba pendiente para siempre, un interbloqueo en MySQL, dos errores
que entraron al corregir) y la de interfaz, los únicos de accesibilidad:
ninguna sobra. Lo que sobraba era leer de más.

## Dónde va cada commit de una historia

- **Primer commit de la rama de la historia:** la historia
  (`docs/historias/HU-XX.md`), sus decisiones en `decisiones.md` y el plan.
- **Antes de abrir el PR:** el resultado de las revisiones y de la prueba
  manual se anota en el plan, y las reglas técnicas nuevas en `decisiones.md`.

Todo entra a `main` por PR: también la documentación.
