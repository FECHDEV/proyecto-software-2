---
description: Revisa, antes de programar, que la historia, el plan y las reglas del proyecto sean coherentes (como /speckit.analyze)
argument-hint: [HU-XX]
---

Analizar la coherencia de `$0` **antes de implementarlo**, entre:

- su historia, en `docs/historias/$0.md`;
- su plan, el archivo más reciente de `docs/planes/` que nombre a `$0`;
- las reglas del proyecto: `CLAUDE.md` (la constitución) y `docs/decisiones.md`.

Adaptado del paso `/speckit.analyze` de Spec Kit. Va entre el plan y la
implementación.

## Solo lectura

Este comando **no modifica ningún archivo**. Entrega un informe; las
correcciones las decide el usuario y se hacen después, en el plan o en la
historia.

## Qué revisar

1. **Cobertura RF → plan:** cada RF de la historia tiene al menos una tarea
   **y** al menos una prueba con nombre en el plan. Un RF sin prueba es
   **crítico**. «RF-N» es el RF de la historia (`docs/flujo-sdd.md` →
   «Numeración de los RF»).
   - Si la historia se está **ampliando**, se revisan solo los RF nuevos, más los
     viejos que el cambio toque.
   - Si el plan es **solo de frontend** (el backend de la historia ya existe), se exigen
     tarea y prueba en el plan solo para los RF con comportamiento en pantalla:
     lo que el usuario ve o hace. Los demás se dan por cubiertos **si ya tienen
     su prueba en el backend**, y el informe los lista así, sin callarlos.
2. **Tareas sin RF:** cada tarea responde a algún RF o caso límite. Si no, tiene
   que estar marcada como técnica (infraestructura, refactor) o sobra.
3. **Casos límite y escenarios:** cada caso límite y cada escenario de la
   historia tiene su prueba en el plan.
4. **Contradicciones con lo decidido:** el plan no contradice ninguna decisión
   de `docs/decisiones.md` ni una regla de negocio de `CLAUDE.md`. Revisar en
   especial:
   - no inventar comportamiento;
   - capas;
   - nombres de proveedores solo en su adaptador;
   - ningún correo que `CLAUDE.md` no defina;
   - el control de acceso en el backend.
5. **Dudas abiertas:** no queda ningún `[NECESITA ACLARACIÓN]` sin resolver en
   la historia.
6. **Nombres coherentes:** entidades, estados, endpoints y códigos de error se
   llaman igual en la historia, el plan, las historias anteriores y el código.
7. **Formato del plan:** respeta `docs/flujo-sdd.md` → «Formato de los
   planes». Lleva código completo solo en lo delicado, y en lo delicado
   (bloqueos, transacciones, pagos, seguridad) **no falta**.
8. **Revisiones que le tocan:** el plan termina con las que pide
   `docs/flujo-sdd.md` → «Revisión antes del PR» para esta historia. La
   lista vive solo ahí: no copiarla.

## Formato del informe

Una tabla, de lo más grave a lo menos grave:

| Gravedad | Revisión | Dónde | Qué pasa | Qué corregir |
|---|---|---|---|---|

- **Crítico:** un RF sin prueba, una contradicción con `decisiones.md` o
  `CLAUDE.md`, o una duda abierta. **No se empieza a programar** hasta
  corregirlo.
- **Medio:** un caso límite sin prueba, una tarea sin RF, nombres que no
  coinciden, o falta código completo en algo delicado.
- **Menor:** formato, redacción.

Al final, la tabla de cobertura **RF → tarea → pruebas** completa. Si no hay
hallazgos críticos, avisar que se puede seguir con la implementación (TDD).
