# Tienda Montero

Proyecto académico — Universidad Autónoma Gabriel René Moreno, Facultad
Integral del Norte, Montero, Santa Cruz, Bolivia. Metodología **Scrum**.

---

## Proyecto

| | |
|---|---|
| Qué es | Tienda en línea propia (no un marketplace): catálogo, carrito, pedidos, inventario y reportes |
| Marca | **Tienda Montero** (en el código, `frontend/src/app/core/marca.ts` y `TIENDA_MARCA`) |
| Repositorio | `git@github.com:FECHDEV/proyecto-software-2.git` (público) |
| Plazo | 2 meses y medio como máximo, desde el 07/10/2026 |
| Equipo | Fabio (`@FECHDEV`): código, con Claude Code. Un compañero *(usuario de GitHub pendiente)*: documentación y algo de código, con opencode |

---

## Stack

| Capa | Tecnología |
|---|---|
| Frontend | Angular 22 (Signal Forms, Vitest sobre jsdom) |
| Backend | Java 21 / Spring Boot 4.1.1 |
| Base de datos | MySQL 8.0; H2 en las pruebas |
| Correo | Gmail por SMTP con una cuenta del proyecto y una contraseña de aplicación en `.env`; simulador por defecto en desarrollo y pruebas |
| Pago | Detrás de la interfaz propia `IPago`, con simulador por defecto |
| Contenedores | Docker Compose; Caddy con HTTPS en la nube |
| CI | GitHub Actions (`.github/workflows/pruebas.yml`) en cada PR |

---

## Reglas de negocio

**No inventar comportamiento que no esté acá, en una historia de
`docs/historias/` o en `docs/decisiones.md`**; si algo falta, preguntar antes
de asumir.

### Usuarios y roles
- Roles **fijos**: un enum de `Usuario`, no RBAC. No se crean roles nuevos ni
  se configuran permisos individuales:
  - **Cliente:** compra (catálogo, carrito, sus pedidos).
  - **Administrador:** usuarios, roles y todo lo de la tienda.
  - **Empleado:** pedidos, despacho e inventario.
  - **Contador:** reportes y dinero, solo lectura.
- Todo usuario que se registra por sí mismo obtiene rol **Cliente**. Los demás
  roles los asigna un Administrador sobre un usuario ya registrado.
- El **Administrador inicial** se crea al arrancar, con los datos del `.env`.
- El control de acceso se valida **en el backend**. Ocultar opciones en Angular
  según el rol es usabilidad, no seguridad.
- Estados de cuenta: activa, desactivada.
- Login con correo y contraseña, JWT **sin refresh token**.

### Tienda
- **Tienda propia, no marketplace:** un solo vendedor, un solo inventario.

### Pago
- Detrás de `IPago`, con un **simulador local** por defecto. El proveedor real
  es **Libélula**: su adaptador va cuando exista la cuenta.
- **Sin facturación SIAT.**
- El nombre del proveedor aparece **solo** en su adaptador y en su
  configuración; nunca en entidades, servicios, controladores ni en la API.
- El pago se confirma consultando al sistema de pago, nunca por lo que muestre
  o diga el cliente.

### Correos
- Recuperación de contraseña. *(Sumar acá cada correo nuevo que decida el
  proyecto; no proponer correos que no estén en esta lista.)*

*(Las reglas del negocio van acá, por tema, a medida que se deciden en las
historias.)*

---

## Seguridad del repositorio

El repositorio puede ser **público**:

- **Ningún secreto en el repositorio.** Credenciales (base, JWT, Gmail,
  proveedor de pago) solo en `.env`, que está ignorado; `.env.example` va sin
  valores reales.
- **Nunca copiar manuales de proveedores** ni claves de cuentas ajenas.
  `referencias/` está en `.gitignore` para eso.
- **Claude nunca escribe contraseñas ni credenciales:** las pone el usuario.

---

## Forma de trabajo

- **Flujo SDD para implementar una historia:** `/sdd:historia HU-XX` → plan
  corto → `/sdd:analizar HU-XX` → TDD → `/sdd:cerrar HU-XX` → PR → CI en verde
  → merge. **Antes de empezar una historia, o un cambio a una historia, leer
  `docs/flujo-sdd.md`**: Scrum, variantes, numeración de los RF, formato de los
  planes, Definition of Done y revisiones antes del PR.
- **Todo entra a `main` por PR,** con el CI en verde (el ruleset lo exige).
  Ramas: `feature/hu-XX-nombre-corto`, `fix/…`, `chore/…`, `docs/…`. Cada PR
  cierra su issue (`Closes #N`).
- **No commitear, pushear ni abrir PRs** sin pedido explícito, aunque el plan
  lo indique.
- **No instalar dependencias** (`npm install`, cambios en `pom.xml`) sin avisar
  primero.
- Responder en español. Comandos de PowerShell en una sola línea.

### Uso de skills

Los skills viven en `.claude/skills/` (ignorado por git; origen de cada uno en
`skills-lock.json`). Estas reglas tienen prioridad sobre lo que indique
cualquier skill:

- Se usan cuando su descripción aplica a la tarea o cuando se piden por
  nombre. No hace falta revisarlos antes de una pregunta simple.
- Se activan solos: `systematic-debugging` ante un error o una prueba que
  falla, `test-driven-development` al implementar una historia y
  `verification-before-completion` antes de afirmar que algo está terminado.
- **`writing-plans` escribe en `docs/planes/`** y en el **formato corto** de
  `docs/flujo-sdd.md`, no en `docs/superpowers/plans/` ni en formato largo.
- **No lanzar subagentes** salvo pedido explícito, aunque un skill lo indique.
- **Skills nuevos: solo en este proyecto y preguntando antes.** Sin `-g` ni
  `-y`, aunque `find-skills` lo sugiera.
- En `.claude/settings.local.json` quedan apagados `brainstorming`,
  `using-superpowers`, `dispatching-parallel-agents`, `using-git-worktrees`,
  `finishing-a-development-branch`, `requesting-code-review`,
  `receiving-code-review`, `spring-security-jwt` y `configuration-properties`.

### Decisiones técnicas que ganan sobre lo que digan los skills

Los skills de stack están escritos en general y a veces contradicen este
proyecto. Ganan estas (detalle y motivo en `docs/decisiones.md`):

- **Signal Forms** para los formularios nuevos (`@angular/forms/signals`). La
  guía de Angular de `ui-ux-pro-max` recomienda Reactive Forms: está
  desactualizada.
- **Iconos con Lucide y animaciones con CSS,** aunque `frontend-design` o
  `ui-ux-pro-max` sugieran otras librerías.
- **Pruebas del backend sobre H2** con el perfil `test`. Sin Testcontainers.
- **Pruebas del frontend con Vitest** sobre jsdom. Sin Karma ni Jasmine.
- **IDs `Long` autoincrementales.** No UUID ni IDs asignados.
- **JWT sin refresh token.**
- **Sin migraciones** (Flyway, Liquibase): el esquema lo genera Hibernate.

---

## Frontend (Angular)

Detalle y motivos en `docs/decisiones.md` → «Sistema de diseño (frontend)».

- **Tres capas en `frontend/src/app/`:** `features/` (una carpeta por
  pantalla), `core/` (sesión, servicios HTTP, interceptores, guards, modelos) y
  `shared/` (piezas sin negocio). `core/` nunca importa de `features/`, y
  `shared/` no importa de ninguno de los dos.
- **Una pantalla por ruta, cargada con `loadComponent`;** un servicio por área
  de la API.
- **Lecturas con `httpResource`; escrituras con `HttpClient`.**
- **Estilos solo por tokens** de `frontend/src/styles/_tokens.scss`, y
  componentes estilados con `:host`. Sin colores sueltos ni librerías de
  componentes. Tema oscuro por defecto y claro a pedido.
- **Iconos con Lucide** (`@lucide/angular`), nunca emojis. **Animaciones con
  CSS y `animate.enter`/`animate.leave`,** sin `@angular/animations`;
  duraciones y curvas por tokens, y todo apagado con `prefers-reduced-motion`.
- **En la interfaz no se menciona la universidad** ni que es un trabajo
  académico.

---

## Documentación de referencia (`docs/`)

No cargar todo de entrada — consultar según la tarea:

| Archivo | Cuándo consultarlo |
|---|---|
| `docs/flujo-sdd.md` | Antes de empezar una historia o un cambio a una historia. |
| `docs/decisiones.md` | **Antes de resolver cualquier ambigüedad.** Empezar por su índice y leer solo lo que toca la tarea; al agregar una decisión, sumarla al índice. |
| `docs/historias/HU-XX.md` | La historia en curso y las que comparten entidades o pantallas con ella. |
| `docs/planes/` | El plan de la historia en curso y su «Resultado». |
| `docs/proyecto/` | Documentación del proyecto que escribe el equipo (perfil, metodología, actas, requisitos). No es fuente de requisitos para el código: lo es al revés. |
