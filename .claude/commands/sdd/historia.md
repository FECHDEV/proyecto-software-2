---
description: Completa una historia de usuario a partir de su issue - "Como… quiero… para…", criterios EARS y escenarios Dado/Cuando/Entonces
argument-hint: [HU-XX]
---

Escribir `docs/historias/$0.md` para la historia `$0`, a partir de su issue de
GitHub y de una entrevista corta con el usuario. Adaptado del prompt «Spec
(entrevista)» y la plantilla `spec.md` de
[hello-sdd](https://github.com/mouredev/hello-sdd).

## 1. Leer la historia

- Buscar el issue: `gh issue list --search "$0 in:title" --state all`, y leerlo
  con `gh issue view <número>`. El título trae «$0 Como … quiero … para …» y el
  cuerpo, lo que se sepa hasta ahora.
- Leer las reglas de negocio de `CLAUDE.md` y las secciones de
  `docs/decisiones.md` que toquen la historia (empezar por su índice).
- Leer las historias ya escritas en `docs/historias/` que compartan entidades o
  pantallas con esta, para no contradecirlas.

Si no hay issue con ese número, parar y avisar: el backlog vive en GitHub
Projects y cada historia nace como issue.

## 2. Entrevistar — no inventar

Lo que no esté en el issue, en `CLAUDE.md` ni en `decisiones.md` se pregunta,
de a una pregunta por vez (máximo 8, con `AskUserQuestion`), empezando por lo
que más cambia el diseño:

- quién la usa (rol) y qué gana;
- datos que entran y reglas de cada uno;
- qué pasa en los errores y en los casos límite (vacío, duplicado, dos a la vez,
  sin permiso, datos basura);
- qué queda fuera.

Cada respuesta que fije una regla nueva va a `docs/decisiones.md` →
«Decisiones del proyecto», con fecha y motivo. Si una respuesta contradice una
decisión ya tomada o una historia cerrada, señalarlo y parar: se decide antes
de seguir.

## 3. Si el archivo ya existe: ampliar, no reescribir

La historia está cerrada y se le agrega algo: la decisión del cambio va
primero a `decisiones.md`, con fecha; los RF nuevos **siguen la numeración**
(si el último es RF-12, el primero nuevo es RF-13) y **nunca se renumeran** los
anteriores, porque las pruebas ya los citan. Se agrega una nota con la fecha en
«Contexto».

## Formato de salida

```markdown
# $0 — <título corto>

**Como** <rol>, **quiero** <acción>, **para** <beneficio>.

Issue: #<número>

## Contexto
<Qué resuelve y para quién, en dos o tres frases.>

Fuentes: <issue, secciones de decisiones.md y reglas de CLAUDE.md usadas>.

<Decisiones tomadas con el usuario en la entrevista, con fecha.>

## Requisitos funcionales (EARS)
- RF-1: CUANDO <evento>, EL SISTEMA <respuesta esperada>.
- RF-2: SI <condición no deseada>, ENTONCES EL SISTEMA <respuesta>.
- RF-3: MIENTRAS <estado>, EL SISTEMA <comportamiento>.
- RF-4: EL SISTEMA <comportamiento permanente>.

## Escenarios
### <nombre del escenario> (RF-1)
- **Dado** <situación inicial>
- **Cuando** <acción>
- **Entonces** <resultado observable>

## Casos límite
<Vacíos, duplicados, concurrencia, permisos, datos basura en la URL o en la respuesta…>

## Fuera de alcance
<Lo que esta historia no hace, aunque se parezca.>

## Criterios de aceptación
<Ej.: todos los RF con prueba en verde + prueba manual de los escenarios.>

## Dudas abiertas
- [NECESITA ACLARACIÓN] <duda, si quedó alguna>
```

**Numeración:** «RF-N» es siempre el RF **de esta historia** (RF-1, RF-2…). Las
pruebas citan «$0 RF-N», o «RF-N» en un archivo cuyo encabezado o `describe`
nombra a `$0`. Cada escenario dice qué RF ilustra, y cada RF tiene al menos un
escenario o un caso límite.

## Siguiente paso

Avisar que la historia está lista y que sigue el **plan corto** en
`docs/planes/AAAA-MM-DD-hu-xx-nombre-corto.md` (formato en `docs/flujo-sdd.md`
→ «Formato de los planes»), y después `/sdd:analizar $0`. Si el issue no tiene
el enlace al archivo, ofrecer agregarlo como comentario.
