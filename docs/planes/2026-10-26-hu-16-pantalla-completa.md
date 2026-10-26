# Plan — HU-16 (ampliación) El modelo en pantalla completa

Historia: `docs/historias/HU-16.md` (RF-12 a RF-14) · Issue #20 · Rama
`feature/hu-16-pantalla-completa`

Solo frontend: un botón dentro de `Visor3d`, que usan el detalle (galería) y
la vista previa del Administrador (HU-15). El plan de HU-16 del 23/10 no se
toca.

## Decisiones técnicas

- **La pantalla completa del navegador** (`requestFullscreen` sobre `.marco`,
  que contiene a `<model-viewer>` y al botón): el modelo sigue con sus
  controles, y «Ver en su espacio» va dentro de `<model-viewer>`. Sin
  dependencias nuevas.
- **El botón aparece si `document.fullscreenEnabled`** y el modelo está
  listo (no mientras se descarga ni con el error de RF-9); ya ampliado, sigue
  aunque cambie el modelo, para poder salir. Safari del iPhone no la da a
  los elementos: ahí `fullscreenEnabled` es falso y el botón no está.
- **El estado sale del navegador, no del clic:** una señal `ampliado` se
  actualiza con `document:fullscreenchange`
  (`fullscreenElement === marco`). Así Esc, el gesto atrás de Android o F11
  dejan el botón bien (RF-14). Un rechazo de `requestFullscreen` se ignora: el
  visor queda como estaba.
- **Botón de ícono** (`LucideMaximize` / `LucideMinimize`), arriba a la
  derecha del marco, de 44 px, con `aria-label` «Pantalla completa» o «Salir
  de pantalla completa» (sin `aria-pressed`: cambia el nombre). Estilos solo
  con tokens.
- **En pantalla completa** `.marco:fullscreen` pierde el alto máximo, la
  proporción y el borde, y ocupa la pantalla con el fondo de la superficie.
- **En pantalla completa `touch-action="none"`:** sin página que desplazar,
  el arrastre vertical también inclina el modelo; al salir vuelve a `pan-y`.
- **Con movimiento reducido,** el texto de los gestos (RF-7) va dentro del
  marco, arriba a la izquierda, mientras está ampliado: abajo no se vería.
- El comentario de `galeria.html` («Sin pantalla completa») pasa a decir que
  la de las fotos es solo de fotos y el visor trae la suya.

## Tarea 1 — El botón en el visor · frontend

**Archivos:** `shared/visor-3d/visor-3d.{ts,html,scss,spec.ts}`,
`shared/galeria/galeria.html` (solo el comentario).

- `Visor3d`: `inject(DOCUMENT)`, `viewChild<ElementRef<HTMLElement>>('marco')`,
  `protected readonly pantallaCompletaPermitida = documento.fullscreenEnabled === true`,
  `protected readonly ampliado = signal(false)`,
  `protected alternarPantallaCompleta(): void` y el host
  `'(document:fullscreenchange)': 'alCambiarPantallaCompleta()'`.

**Pruebas** (`visor-3d.spec.ts`; jsdom no tiene la API: se simulan
`document.fullscreenEnabled`, `fullscreenElement`, `requestFullscreen` y
`exitFullscreen`):

- `ofrece «Pantalla completa» si el navegador la permite` — RF-12
- `sin la pantalla completa del navegador (iPhone) no hay botón` — RF-12
- `sin modelo que mostrar no hay botón` — RF-12, caso límite (RF-9)
- `«Pantalla completa» amplía el marco del visor` — RF-13
- `en pantalla completa el texto de los gestos va dentro del marco` — RF-7,
  RF-13
- `con otro modelo en pantalla completa sigue el botón para salir` — RF-14
- `si el navegador la rechaza, el visor sigue igual` — RF-13, caso límite
- `«Salir de pantalla completa» la cierra` — RF-14
- `al salir con Esc o el gesto atrás, el botón vuelve a «Pantalla completa»` —
  RF-14

**Fuera de jsdom (revisión de interfaz y prueba manual):** que el modelo se
gire en pantalla completa (RF-13), «Ver en su espacio» ahí en Android, y que
en el iPhone no aparezca el botón.

## Revisiones (al cerrar)

Las de `docs/flujo-sdd.md` → «Revisión antes del PR» (tiene pantalla: primero
la de interfaz). Lo propio de esta ampliación: el detalle del taladro en
escritorio y en `iPhone 15` (sin botón), el foco visible del botón con el
teclado, y la prueba manual de Fabio en una computadora y en un celular
Android. El backend no cambia.

## Resultado

*26/10/2026.* Cerrada: la prueba manual de Fabio salió bien.

**Cobertura** (`visor-3d.spec.ts`, `describe('pantalla completa')`):

| RF | Pruebas |
|---|---|
| RF-12 | `ofrece «Pantalla completa» si el navegador la permite`, `sin la pantalla completa del navegador (iPhone) no hay botón`, `sin modelo que mostrar no hay botón` (también mientras carga) |
| RF-13 | `«Pantalla completa» amplía el marco del visor` (con `touch-action`), `en pantalla completa el texto de los gestos va dentro del marco`, `si el navegador la rechaza, el visor sigue igual` |
| RF-14 | `«Salir de pantalla completa» la cierra` (vuelve a `pan-y`), `al salir con Esc o el gesto atrás…`, `con otro modelo en pantalla completa sigue el botón para salir` |

**Suites:** backend 706 pruebas, 0 fallas, 1 omitida (sin cambios); frontend
69 archivos, 851 pruebas en verde; `ng build` sin avisos.

**Revisión de interfaz (subagente, `playwright-cli` con Edge en escritorio,
«iPhone 15» y Pixel 7, claro y oscuro):** sin errores de consola.

- Corregidos: el contorno de foco casi invisible sobre el blanco de la foto
  (separación 0 en el botón); sin respuesta al tocar (`scale(0.95)`); el
  rechazo de `exitFullscreen` sin atrapar; el texto de los gestos fuera de la
  pantalla completa con movimiento reducido (va dentro del marco).
- Descartados: las zonas seguras del notch (`viewport-fit=cover` cambia toda
  la tienda, y Chrome ya deja libre el recorte en pantalla completa); el orden
  de Tab (el botón después de «Ver en su espacio»), opcional.
- No comprobado: el iPhone de verdad (el emulador usa Chromium, donde
  `fullscreenEnabled` es verdadero); lo cubre la prueba unitaria.

**`/code-review` medium, ronda 1:**

- Corregidos: `touch-action="pan-y"` en pantalla completa (el arrastre
  vertical no inclinaba el modelo); el botón mientras el modelo cargaba
  (ahora con el modelo listo); el texto de los gestos tomaba los gestos
  (`pointer-events: none`); el texto y la etiqueta repetidos.
- Descartados: los prefijos `webkit` (Safari los quitó en 16.4); el foco que
  sale del visor con Tab en pantalla completa (es la pantalla completa del
  navegador; Esc sale).

**`/code-review` medium, ronda 2 (la última):**

- Corregidos: el botón para salir desaparecía si cambiaba el modelo estando
  ampliado; el hover como el de los secundarios (`superficie-alta`); la prueba
  de que al salir vuelve `pan-y`; la condición de los gestos en un `@let`; una
  guarda de más; el plan desactualizado.
- Descartados: radio, sesgo y tamaño del ícono (sigue a «Ver en su espacio»,
  dentro del mismo visor); el texto de los gestos fijo en pantalla completa
  (RF-7 lo pide en lugar de la mano).

**Pendiente:** «Ver en su espacio» desde la pantalla completa en Android
(WebXR con capa del DOM) necesita HTTPS: se prueba en el próximo VPS.

**Distinto de lo planeado:** el botón aparece con el modelo listo, no con el
visor descargado; se sumaron `touch-action="none"` en pantalla completa y el
texto de los gestos dentro del marco.
