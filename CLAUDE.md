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
| Equipo | Fabio (`@FECHDEV`): todo el proyecto (código y documentación), con Claude Code. *21/10/2026:* David se retiró del proyecto |

---

## Stack

| Capa | Tecnología |
|---|---|
| Frontend | Angular 22 (Signal Forms, Vitest sobre jsdom) |
| Backend | Java 21 / Spring Boot 4.1.1 |
| Base de datos | MySQL 8.4 LTS (*20/10/2026*, antes 8.0, sin soporte desde abril de 2026); H2 en las pruebas |
| Correo | Gmail por SMTP con una cuenta del proyecto y una contraseña de aplicación en `.env`: por defecto en Docker (*26/10/2026*), y sin usuario ni contraseña la API no arranca; simulador en desarrollo y pruebas |
| Pago | Detrás de la interfaz propia `IPago`: Libélula por defecto en Docker, simulador en desarrollo y pruebas |
| Contenedores | Docker Compose; Caddy con HTTPS en la nube |
| CI | GitHub Actions (`.github/workflows/pruebas.yml`) en cada PR y en cada push a `main` |

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
- **Rubro: ferretería** (herramientas, hogar y jardín). Un producto puede tener
  un **modelo 3D opcional**, hecho fuera de la aplicación y cargado cuando
  exista; sin modelo, se ve solo con sus fotos.

### Pago
- Detrás de `IPago`. El proveedor real es **Libélula** (adaptador desde
  HU-13), **por defecto en Docker** desde el *23/10/2026*: sin la appkey y el
  correo en `.env`, la API no arranca. El **simulador local** queda en
  desarrollo, en las pruebas y con `TIENDA_PAGO_PROVEEDOR=simulador` (solo
  para probar en la PC: con él, cualquier cliente marca pagado su pedido).
- **Sin facturación SIAT.**
- El nombre del proveedor aparece **solo** en su adaptador y en su
  configuración; nunca en entidades, servicios, controladores ni en la API.
- El pago se confirma consultando al sistema de pago, nunca por lo que muestre
  o diga el cliente.

### Correos
- Recuperación de contraseña.
- Verificación del correo (*21/10/2026*, HU-20): «Confirme su correo», al
  registrarse y al pedir el reenvío.
- Correos del pedido (*26/10/2026*, HU-21): «Pedido pagado», cuando se
  confirma el pago, y «Pago a devolver», cuando el pago llega y el pedido ya
  no se puede reservar. *(Sumar acá cada correo nuevo que decida el
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
- **Las reglas se pueden cambiar.** Si una regla de este archivo o de
  `docs/decisiones.md` (el diseño, la paleta, los tokens, una decisión
  técnica) impide algo que mejoraría el proyecto (una librería, un patrón, lo
  que recomiende una skill), la regla no manda: avisar al usuario con la
  propuesta y la regla que choca, y cambiarla con fecha y motivo. No
  esquivarla en silencio ni descartar la mejora por la regla. Quedan firmes
  las de seguridad, las de commits y PR, y avisar antes de instalar.
- **Documentación del proyecto** (`docs/proyecto/`: perfil, metodología,
  requisitos, arquitectura, actas): también con Claude Code, con las reglas de
  `AGENTS.md` → «Rol: documentación» y los comandos `/proyecto:acta N` y
  `/proyecto:requisitos`.
- Responder en español. Comandos de PowerShell en una sola línea.

### Uso de skills

Los skills viven en `.claude/skills/` (ignorado por git; origen de cada uno en
`skills-lock.json`). *17/10/2026:* `skills-lock.json` se versiona para que
las sesiones en la nube instalen las mismas skills: lo hace
`.claude/hooks/session-start.sh`, que en una PC no hace nada. Al agregar o
quitar una skill, se actualiza el lock en el mismo PR. Estas reglas tienen
prioridad sobre lo que indique cualquier skill:

- Se usan cuando su descripción aplica a la tarea o cuando se piden por
  nombre. No hace falta revisarlos antes de una pregunta simple.
- Se activan solos: `systematic-debugging` ante un error o una prueba que
  falla, `test-driven-development` al implementar una historia y
  `verification-before-completion` antes de afirmar que algo está terminado.
- **`writing-plans` escribe en `docs/planes/`** y en el **formato corto** de
  `docs/flujo-sdd.md`, no en `docs/superpowers/plans/` ni en formato largo.
- **Subagentes permitidos** sin pedido explícito, cuando mejoren el trabajo
  (búsquedas amplias, revisiones, tareas independientes en paralelo).
- **Skills nuevos: solo en este proyecto y preguntando antes.** Sin `-g` ni
  `-y`, aunque `find-skills` lo sugiera.
- En `.claude/settings.json` (versionado: vale también en la nube; *17/10/2026*,
  antes en `settings.local.json`) quedan apagados `brainstorming`,
  `using-superpowers`, `using-git-worktrees`,
  `finishing-a-development-branch`, `requesting-code-review`,
  `spring-security-jwt` y `ui-ux-pro-max` (la reemplaza `impeccable`).
  *16/10/2026:* también `executing-plans`, `subagent-driven-development`,
  `dispatching-parallel-agents`, `find-skills` y `animation-vocabulary`,
  sin uso en el proyecto. *21/10/2026 (auditoría):* se borraron
  `frontend-design`, `apple-design`, `writing-skills` y `prototype`, y se
  apagan `animation-performance`, `find-animation-opportunities` e
  `improve-animations` hasta el pulido final. El mismo archivo apaga la telemetría de
  superpowers y deja `ponytail` en `off`. El plugin `commit` (escribe en
  inglés, sin atribución y hace push) se apaga en `settings.local.json`,
  porque es un plugin de la PC.
- **Diseño del frontend con `impeccable`** (pbakaus) y las skills de Emil
  Kowalski (`emil-design-eng`, `animate`, `mobile-native`, `break-ui`,
  `review-animations`…). `impeccable` va sin sus hooks. *16/10/2026:* es la
  dueña de **`DESIGN.md`** (todo el sistema visual) y de **`PRODUCT.md`** (a
  quién le habla la tienda y cómo se siente), los dos versionados; las reglas
  de negocio siguen acá y en `docs/decisiones.md`. Sus fichas de pantalla van
  en `.impeccable/surfaces/` (versionadas) y sus subagentes en
  `.claude/agents/impeccable-*` (ignorados por git).
- **Verificación en el navegador con `playwright-cli`** (Microsoft, instalado
  global con `npm install -g @playwright/cli`; su skill en `.claude/skills/`).
  Antes de pasar una pantalla a la prueba manual: abrirla sin ventana en
  escritorio y en celular (`--device="iPhone 15"`), capturas y consola sin
  errores. No reemplaza la prueba manual del usuario.

### Decisiones técnicas que ganan sobre lo que digan los skills

Los skills de stack están escritos en general y a veces contradicen este
proyecto. Por defecto ganan estas (detalle y motivo en
`docs/decisiones.md`); si una skill propone algo mejor, se avisa y se cambia
la decisión (ver «Las reglas se pueden cambiar»):

- **Signal Forms** para los formularios nuevos (`@angular/forms/signals`).
  Si una guía recomienda Reactive Forms, está desactualizada.
- **Iconos con Lucide.** Las animaciones simples van con CSS; una librería
  (de animación, carrusel, componentes) entra si mejora el proyecto y es
  compatible con Angular 22, avisando antes.
- **Pruebas del backend sobre H2** con el perfil `test`. Sin Testcontainers.
- **Pruebas del frontend con Vitest** sobre jsdom. Sin Karma ni Jasmine.
- **IDs `Long` autoincrementales.** No UUID ni IDs asignados. Los ejemplos
  de las skills de Spring (`layered-architecture`, `spring-data-jpa`…) usan
  UUID: en eso no aplican.
- **JWT sin refresh token.**
- **Sin migraciones** (Flyway, Liquibase): el esquema lo genera Hibernate.

---

## Frontend (Angular)

Lo visual (paleta, tipografía, forma, movimiento, componentes, reglas de la
interfaz) está en **`DESIGN.md`**; lo técnico, en `docs/decisiones.md` →
«Decisiones técnicas del frontend».

- **Tres capas en `frontend/src/app/`:** `features/` (una carpeta por
  pantalla), `core/` (sesión, servicios HTTP, interceptores, guards, modelos) y
  `shared/` (piezas sin negocio). `core/` nunca importa de `features/`, y
  `shared/` no importa de ninguno de los dos.
- **Una pantalla por ruta, cargada con `loadComponent`;** un servicio por área
  de la API.
- **Lecturas con `httpResource`; escrituras con `HttpClient`.**
- **Estilos solo por tokens** de `frontend/src/styles/_tokens.scss`, y
  componentes estilados con `:host`. Sin colores sueltos.
- **Librerías permitidas si mejoran el proyecto,** compatibles con Angular 22 y
  mantenidas, avisando antes de instalarlas. Una librería que trae su propio
  aspecto se adapta a los tokens.
- **Iconos con Lucide** (`@lucide/angular`). **Animaciones con CSS y
  `animate.enter`/`animate.leave`** cuando alcanzan, o con una librería si
  hace falta más (sin `@angular/animations`, que está deprecado); sus reglas
  (duraciones, curvas, movimiento reducido) están en `DESIGN.md`.
- **En la interfaz no se menciona la universidad** ni que es un trabajo
  académico.

---

## Documentación de referencia (`docs/`)

No cargar todo de entrada — consultar según la tarea:

| Archivo | Cuándo consultarlo |
|---|---|
| `docs/flujo-sdd.md` | Antes de empezar una historia o un cambio a una historia. |
| `DESIGN.md` | Antes de tocar cualquier pantalla o estilo: el sistema visual completo. |
| `PRODUCT.md` | Al diseñar o escribir textos de la interfaz: público, personalidad y tono (de usted). |
| `docs/decisiones.md` | **Antes de resolver cualquier ambigüedad.** Empezar por su índice y leer solo lo que toca la tarea; al agregar una decisión, sumarla al índice. |
| `docs/historias/HU-XX.md` | La historia en curso y las que comparten entidades o pantallas con ella. |
| `docs/planes/` | El plan de la historia en curso y su «Resultado». |
| `docs/proyecto/` | Documentación del proyecto que escribe el equipo (perfil, metodología, actas, requisitos). No es fuente de requisitos para el código: lo es al revés. |
