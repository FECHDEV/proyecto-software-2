---
description: Cierra una historia implementada (Definition of Done) - cobertura RF contra el código, suites completas, revisiones, prueba manual y resultado en el plan
argument-hint: [HU-XX]
---

Cerrar `$0` después de implementarla y antes de abrir el PR. Es la
**Definition of Done** del equipo: sigue, en orden, lo que pide
`docs/flujo-sdd.md`. No saltear pasos ni hacerlos «a ojo»:
cada revisión se corre de verdad.

## 1. Cobertura RF → prueba, contra el código

- **Cada RF tiene su prueba.** Para cada RF de `docs/historias/$0.md`,
  buscar en las pruebas del backend (`backend/src/test/`) y del frontend
  (`*.spec.ts`) las que lo citan. Cuentan:
  - «$0 RF-N»;
  - «RF-N» suelto dentro de un archivo de pruebas cuyo encabezado o `describe`
    nombra a `$0`;
  - un rango o una lista que lo incluya, como «RF-19 a RF-24» o «RF-19, RF-20 y
    RF-24».

  «RF-N» es siempre el de la historia. Si la historia se
  amplió, alcanza con los RF nuevos y los viejos que el cambio tocó. Si fue
  **solo frontend**, los RF sin comportamiento en pantalla cuentan como
  cubiertos con su prueba del backend: listarlos así en el resultado.
- **Los «Criterios de aceptación» y los escenarios de la historia se cumplen**,
  uno por uno.
- **Lo planeado se cumplió.** Comparar con el plan de la historia: anotar lo que se
  implementó distinto de lo planeado, o lo que no se implementó.
- **Un RF sin prueba es bloqueante:** se escribe la prueba antes de seguir.

## 2. Suites completas (`verification-before-completion`)

- Backend: `./mvnw test` en `backend/`.
- Frontend: `npx ng test --watch=false` y `npx ng build` en `frontend/`, sin
  avisos.
- Anotar los números exactos. Si algo falla, `systematic-debugging` y se vuelve
  a correr todo.

## 3. Revisiones que le tocan a esta historia

Las que pide `docs/flujo-sdd.md` → «Revisión antes del PR». La
lista vive solo ahí: leerla en lugar de suponerla. Hoy son:

- `/code-review`, siempre;
- `/security-review` en seguridad, pagos y superficies nuevas;
- `web-design-guidelines`, si hay frontend, bajando las reglas actualizadas.

Cada hallazgo real se corrige con su prueba y se vuelve al paso 2. Los
descartados se anotan con su motivo.

## 4. Prueba manual

Pedirle al usuario la prueba manual de los escenarios y de los casos límite
más importantes. Indicarle cómo hacerla (`docker compose up -d --build`). No
dar la historia por cerrada sin su confirmación.

## 5. Resultado en el plan

Agregar al plan de la historia una sección **«Resultado»** con:

- la tabla de cobertura RF → pruebas;
- los números de las suites;
- los hallazgos de cada revisión: los corregidos y los descartados, con su
  motivo;
- el resultado de la prueba manual;
- lo que quedó distinto de lo planeado.

Las reglas técnicas nuevas que hayan surgido van a `docs/decisiones.md`.

## 6. PR

Preguntar al usuario si se commitea y se abre el PR. `CLAUDE.md`: nada de
commits, pushes ni PRs sin pedido explícito. El PR sigue
`.github/pull_request_template.md`: qué historia cierra (`Closes #N`, el issue
de la historia), qué cambia y cómo se probó.

Con el PR abierto, esperar el CI (`gh pr checks <número> --watch`) y avisar si
salió **verde o rojo**. El ruleset de `main` exige PR y los status checks del
CI, así que en rojo GitHub no deja mergear: se corrige en la misma rama.
