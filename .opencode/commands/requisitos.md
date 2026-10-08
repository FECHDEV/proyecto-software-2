---
description: Actualiza docs/proyecto/requisitos.md con los requisitos de las historias escritas
---

Actualizar `docs/proyecto/requisitos.md` con los requisitos de
`docs/historias/`. Reglas en `AGENTS.md` → «Rol: documentación».

## 1. Leer

- Todas las historias de `docs/historias/HU-*.md`.
- `docs/proyecto/requisitos.md`, si ya existe: se amplía, no se reescribe.
- Para los no funcionales: `CLAUDE.md` (stack, reglas de negocio, seguridad) y
  `docs/decisiones.md` (empezar por su índice).

## 2. Escribir

- **Funcionales:** uno por cada RF de cada historia, con su identificador
  completo «HU-XX RF-N» (la numeración es por historia y no se cambia: las
  pruebas la citan). El texto se copia del RF, en lenguaje claro; no se
  agregan requisitos que la historia no tenga.
- **No funcionales:** solo los que estén escritos en `CLAUDE.md` o en
  `decisiones.md` (seguridad, stack, despliegue, correo, pago…), citando de
  dónde salen.
- Si una historia cambió (RF nuevos al final), se suman sus RF nuevos; los
  anteriores no se tocan.

## Formato de salida

```markdown
# Requisitos

## Requisitos funcionales

### HU-XX — <título de la historia>
| ID | Requisito | Rol |
|---|---|---|
| HU-XX RF-1 | <requisito> | <rol> |

## Requisitos no funcionales
| ID | Requisito | Fuente |
|---|---|---|
| RNF-1 | <requisito> | `decisiones.md` → <sección> |
```

Al terminar, decir qué historias se sumaron y recordar que entra por PR desde
una rama `docs/…`.
