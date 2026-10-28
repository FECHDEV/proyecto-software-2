---
name: Tienda Montero
description: La tienda en línea de una ferretería de Montero, rotulada como el letrero de ruta de un micro cruceño.
colors:
  fondo: "#0c3326"
  superficie: "#114032"
  superficie-alta: "#164d3c"
  texto: "#f2efe6"
  texto-suave: "#b9c7bd"
  ambar: "#ffd200"
  ambar-fuerte: "#e6bd00"
  acento-texto: "#ffd200"
  sobre-ambar: "#0c3326"
  borde: "#2a5a48"
  borde-control: "#7f9a8c"
  lona: "#f2efe6"
  lona-tejida: "#e4dcc6"
  sobre-lona: "#0f2a1d"
  sobre-lona-suave: "#4d5f55"
  error-sobre-lona: "#b3261e"
  ventana: "#061a12"
  foto: "#ffffff"
  exito: "#7fd3a0"
  aviso: "#ffb45c"
  error: "#ff9e94"
  neutro: "#b9c7bd"
  exito-fondo: "#12452f"
  aviso-fondo: "#3a3214"
  error-fondo: "#3d2420"
  velo: "rgb(6 26 19 / 0.75)"
  qr-claro: "#ffffff"
  qr-oscuro: "#000000"
  fondo-claro: "#f2efe6"
  superficie-claro: "#fbfaf6"
  superficie-alta-claro: "#e7e2d4"
  texto-claro: "#0f2a1d"
  texto-suave-claro: "#4d5f55"
  acento-texto-claro: "#0e3a2e"
  sobre-ambar-claro: "#0e3a2e"
  borde-claro: "#d6d0bf"
  borde-control-claro: "#6f7d74"
  lona-claro: "#fbfaf6"
  ventana-claro: "#0c3326"
  exito-claro: "#1f6b3f"
  aviso-claro: "#8a4b00"
  error-claro: "#b3261e"
  exito-fondo-claro: "#e3f1e8"
  aviso-fondo-claro: "#f8ecd9"
  error-fondo-claro: "#f9e3e1"
  velo-claro: "rgb(15 42 29 / 0.45)"
typography:
  leyenda:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "clamp(52px, 15vw, 140px)"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "0.01em"
  headline:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "clamp(44px, 8vw, 88px)"
    fontWeight: 800
    lineHeight: 0.95
  title:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "clamp(28px, 4vw, 40px)"
    fontWeight: 800
    lineHeight: 1.1
    letterSpacing: "0.02em"
  title-panel:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "34px"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "0.01em"
  rotulo:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "18px"
    fontWeight: 700
    letterSpacing: "0.05em"
  precio:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', system-ui, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1
    fontFeature: "tabular-nums"
  body:
    fontFamily: "Barlow, 'Segoe UI', system-ui, -apple-system, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Barlow, 'Segoe UI', system-ui, -apple-system, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    letterSpacing: "0.06em"
  datos:
    fontFamily: "'JetBrains Mono', ui-monospace, Consolas, monospace"
    fontWeight: 400
rounded:
  control: "0"
  radio: "2px"
spacing:
  espacio-1: "4px"
  espacio-2: "8px"
  espacio-3: "12px"
  espacio-4: "16px"
  espacio-6: "24px"
  espacio-8: "32px"
  espacio-12: "48px"
components:
  boton-principal:
    backgroundColor: "{colors.ambar}"
    textColor: "{colors.sobre-ambar}"
    rounded: "{rounded.control}"
    height: "44px"
  boton-principal-hover:
    backgroundColor: "{colors.ambar-fuerte}"
  boton-secundario:
    backgroundColor: "transparent"
    textColor: "{colors.texto}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "44px"
  cinta-seccion:
    backgroundColor: "transparent"
    textColor: "{colors.texto}"
    typography: "{typography.rotulo}"
    padding: "0 16px"
    height: "44px"
  cinta-seccion-actual:
    backgroundColor: "{colors.lona}"
    textColor: "{colors.sobre-lona}"
  busqueda-campo:
    backgroundColor: "{colors.lona}"
    textColor: "{colors.sobre-lona}"
    rounded: "{rounded.control}"
    height: "44px"
  campo:
    backgroundColor: "{colors.fondo}"
    textColor: "{colors.texto}"
    rounded: "{rounded.control}"
    padding: "12px"
    height: "44px"
  placa-producto:
    backgroundColor: "{colors.lona}"
    textColor: "{colors.sobre-lona}"
    rounded: "{rounded.radio}"
  placa-seccion:
    backgroundColor: "{colors.lona-tejida}"
    textColor: "{colors.sobre-lona}"
    padding: "24px clamp(16px, 3vw, 32px)"
  ventana:
    backgroundColor: "{colors.ventana}"
    textColor: "{colors.ambar}"
    typography: "{typography.leyenda}"
  tarjeta-formulario:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.texto}"
    rounded: "{rounded.radio}"
    padding: "24px"
    width: "420px"
---

# Design System: Tienda Montero

Este archivo es dueño de todo el sistema visual de la aplicación: la tienda
pública y el panel del personal comparten los mismos tokens
(`frontend/src/styles/_tokens.scss`, prefijo `--app-`). Decisión del 16/10/2026.
Las reglas del negocio siguen en `CLAUDE.md` y `docs/decisiones.md`; el público,
en `PRODUCT.md`.

## Overview

**Creative North Star: "Letrero de ruta"**

La tienda se lee como el letrero de destino de un micro cruceño: una ventana
negra con la leyenda en amarillo enorme, una cinta de secciones que espera su
turno y placas de lona donde va el producto. Tres tintes y nada más: lona verde
botella a sangre como fondo, amarillo cromo solo para leyendas y la acción
principal, lona clara para el texto corrido y las placas.

La forma es la de la lona cortada: esquinas rectas, paneles y botones en sesgo
de 12° con el texto derecho adentro, costuras amarillas de un pelo entre
paneles. El estado se marca con costura, troquel y relieve, no con otro color.
La densidad es la de una ferretería ordenada: herramienta real sobre fondo
blanco, cifras grandes y legibles.

El panel del personal usa los mismos tokens con un tono más sobrio: superficies
verdes, tablas y formularios rectos, sin ventana ni cinta.

**Key Characteristics:**
- Oscuro por defecto; claro a pedido, con su propia paleta medida.
- Condensada en mayúsculas para leyendas y rótulos; Barlow para leer.
- Sesgo de 12° en lo que se pulsa; el texto y los iconos siempre derechos.
- Tejido de lona en los fondos, fundido en soft-light.
- Estado por forma (costura, troquel, relieve), nunca solo por color.

### Letrero de ruta

*16/10/2026 (HU-09).* Reemplaza a la paleta **grafito con amarillo de
señalización** del 09/10/2026 (chore #8). Piezas del mundo, como están en el
código:

- **Ventana:** marco `ventana` a todo el ancho, borde de entrada cortado en
  sesgo (`clip-path` de 28 px a la izquierda) y un contorno interior amarillo al
  30 %. Lleva la leyenda de la sección y su posición en el rollo («2 / 5»). Es
  fija bajo el encabezado y se achica al bajar (leyenda de 26 a 40 px).
- **Paso del rollo:** al cambiar de sección la leyenda vieja sube y sale
  (`--app-duracion-rapida`, `--app-curva-irse`) y la nueva entra desde abajo,
  se pasa un poco y se asienta (`--app-duracion-media`, `--app-curva-paso`). Con movimiento
  reducido, solo un fundido.
- **Rollo trabado (error de carga):** dos medias leyendas encontradas en una
  costura punteada amarilla.
- **Cinta:** secciones cortadas en sesgo con borde de 2 px del color del texto;
  la actual, en lona clara con tinta verde.
- **Placa de sección:** lona cruda (`lona-tejida`) con costura punteada amarilla
  arriba y contorno amarillo de 1 px. Dentro, los enlaces pasan a la tinta
  verde: `--app-acento-texto` se redefine a `--app-sobre-lona`.
- **Encabezado:** lona sólida con textura y raya amarilla de 3 px abajo, fijo
  en pantalla ancha, sin vidrio ni desenfoque.

## Colors

Tres tintes de lona y señal: verde botella, amarillo cromo y lona clara, con
los colores de estado a un lado.

### Primary
- **Amarillo cromo** (`ambar`): relleno de la acción principal, el logotipo,
  la leyenda de la ventana, la raya del encabezado, las costuras y la página
  actual. Siempre con `sobre-ambar` encima. `ambar-fuerte` es su hover.
- **Amarillo de texto** (`acento-texto`): enlaces y contorno de foco. En el modo
  claro es verde (`acento-texto-claro`) porque el amarillo no se lee sobre
  lona clara.

### Neutral
- **Lona verde botella** (`fondo`, `superficie`, `superficie-alta`): el fondo a
  sangre y las capas del panel, de más hondo a más alto.
- **Lona clara** (`lona`) y **lona cruda** (`lona-tejida`): placas de producto
  y placas de sección; su tinta es `sobre-lona` y `sobre-lona-suave`. Un error sobre
  la lona va en `error-sobre-lona`, igual en los dos modos: el `error` del modo
  oscuro no se lee sobre ella.
- **Marco de ventana** (`ventana`): el negro verdoso de la ventana del letrero
  y la base de la sombra de las placas.
- **Texto** (`texto`, `texto-suave`), **bordes** (`borde` para divisiones,
  `borde-control` para el contorno de campos y botones secundarios).
- **Blanco de foto** (`foto`): detrás de cada foto de producto, en los dos
  modos, como el fondo de las fotos de catálogo. *27/10/2026:* el detalle de
  un producto sin fotos muestra la llave sobre `superficie`, no sobre blanco.
- **Velo** (`velo`): oscurece la página detrás de un diálogo.
- **QR** (`qr-claro`, `qr-oscuro`): oscuro sobre claro en cualquier tema.

### Estado
- `exito`, `aviso`, `error`, `neutro`, cada uno con su fondo tenue
  (`*-fondo`). Siempre acompañados de texto y forma.

### Paleta y modos

El oscuro es el modo por defecto (`color-scheme: dark` en `html`). Un botón con
sol o luna en el encabezado alterna al claro, con o sin sesión; la elección se
guarda en `localStorage`, clave `app.tema`, y un script mínimo en `index.html`
la aplica antes de que arranque Angular. El claro redefine los tokens en
`:root[data-tema='claro']` (valores `*-claro` del frontmatter); no es el oscuro
invertido.

### Named Rules
**The Contraste Medido Rule.** Ningún color se fija sin medirlo contra su fondo
con la fórmula de luminancia de WCAG, uno por uno y en los dos modos: 4.5:1 en
texto, 3:1 en bordes de control.

**The Amarillo Escaso Rule.** El amarillo es relleno de leyendas, costuras y la
acción principal; como texto sobre lona clara no va nunca.

**The Solo Tokens Rule.** Ningún componente escribe un color suelto: todo sale
de `_tokens.scss`.

## Typography

**Display Font:** Barlow Condensed 600/700/800 (con 'Arial Narrow', system-ui)
**Body Font:** Barlow 400/500/600 (con 'Segoe UI', system-ui)
**Label/Mono Font:** JetBrains Mono 400/500 (con ui-monospace, Consolas), solo para datos

Las tres de Google Fonts, servidas por la tienda (`public/fuentes`, declaradas
en `styles/_fuentes.scss`); el texto y los títulos se precargan en `index.html`.

**Character:** La condensada en mayúsculas es la rotulación del letrero y de la
ferretería; Barlow lleva la lectura sin perder el parentesco.

### Hierarchy
- **Leyenda** (800, clamp 52–140 px, 1): el nombre de la sección en la ventana.
- **Headline** (800, clamp 44–88 px, 0.95, mayúsculas, en amarillo): el título
  de una pantalla de la tienda (catálogo), sobre la raya amarilla de 3 px.
- **Title** (800, clamp 28–40 px, mayúsculas): el título de cada carrusel.
- **Title del panel** (700, 34 px, 1.1): títulos de formularios y listas.
- **Rótulo** (700, 15–22 px, mayúsculas, espaciado 0.04–0.06em): cinta,
  «Ver todo», el total de resultados, la etiqueta «Agotado».
- **Precio en placa** (condensada 700, 24 px, cifras tabulares).
- **Body** (Barlow 400, 16 px, 1.5); nombre de producto en placa 600, 17 px,
  1.3, hasta dos líneas.
- **Label** (Barlow 600, 12 px, 0.06em, mayúsculas): etiquetas de campo y
  encabezados de tabla.
- **Datos** (JetBrains Mono): precios, códigos, cantidades y contadores en las
  pantallas del panel.

Los títulos llevan `text-wrap: balance`.

### Named Rules
**The Mono Para Datos Rule.** La monoespaciada es solo para datos (precios,
códigos, cantidades, contadores); nunca para etiquetas ni leyendas.

**The Sin Antetítulos Rule.** No hay etiqueta chica en mayúsculas encima de un
título: el título se sostiene solo.

**The Cuerpo 400 Rule.** Sobre fondo oscuro el texto fino se deshace: el cuerpo
no baja de 400.

## Layout

Columna de contenido de hasta 1120 px (`--app-ancho-maximo`), centrada, con
margen lateral que crece de 16 a 48 px (`clamp(16px, 5vw, 48px)`). *22/10/2026:*
la barra del encabezado ya no comparte ese ancho: ocupa toda la ventana, con
16 px a cada lado, y los módulos van centrados (si no entran, se desplazan de
costado desde el borde). **Motivo:** lo pidió el usuario; con más módulos
(Reportes), la barra centrada se lee mejor que una fila pegada a la izquierda. Lo angosto (formularios de 420 px) va
centrado con el mixin `pagina-angosta` de `_pagina.scss`, nunca con un
`max-width` suelto.

Espaciado en múltiplos de 4 px (escala `espacio-*`). La portada apila ventana,
cinta y placas con 24 px entre sí. Los carruseles muestran 1.4, 2.4, 3.4 y 4.4
placas (cortes en 560, 860 y 1100 px), con el borde de la siguiente visible y
frenado en cada placa. El catálogo usa una columna de filtros de 240 px y una
grilla de placas de 200 px mínimo; bajo 760 px los filtros se pliegan tras un
botón y la grilla pasa a dos columnas. Bajo 640 px el encabezado deja de ser
fijo, la búsqueda baja a su propia fila y las tablas del panel se vuelven
bloques con la etiqueta de cada dato.

### Named Rules
**The Área Tocable Rule.** Todo lo que se toca mide al menos 44 px
(`--app-area-tocable`), y todo lo que se usa con teclado tiene foco visible
(contorno de 3 px en `acento-texto`, separado 2 px).

## Elevation & Depth

Plano y textil. La profundidad sale del tejido de la lona, de las costuras y de
las placas claras sobre el fondo verde, no de sombras. Hay dos sombras, ambas
como respuesta o marco:

### Shadow Vocabulary
- **Contorno de ventana** (`box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--app-ambar) 30%, transparent)`): el filo amarillo del marco del letrero.
- **Relieve de placa** (`box-shadow: inset 0 0 0 2px var(--app-sobre-lona), 0 8px 18px color-mix(in srgb, var(--app-ventana) 35%, transparent)`): la placa de producto al pasar el mouse, junto con `translateY(-3px)`.
- **Costura engrosada** (`box-shadow: inset 0 0 0 2px currentColor`): hover de la cinta y las flechas.

### Named Rules
**The Relieve No Tinte Rule.** El hover y el estado se marcan engrosando la
costura o levantando la placa, no cambiando de color.

### Texturas

Dos fotos CC0 de Poly Haven, procesadas a gris centrado en 50 %, en relieve y en
mosaico espejado de 384 px (WebP 0.82). Se funden con el color de su superficie
con `background-blend-mode: soft-light`, así la textura no cambia la paleta.
Origen de cada archivo en su `.json` al lado.

- **Lona verde** (`--app-textura-lona`, `public/texturas/lona-verde.webp`):
  «Stretch Poplin». Fondo de la página, encabezado y franja del letrero.
- **Lona cruda** (`--app-textura-cruda`, `public/texturas/lona-cruda.webp`):
  «Cotton Jersey». Placas de sección y placas de producto.

## Shapes

Lona cortada: controles con esquina recta (`rounded.control`, 0) y placas y
tarjetas con apenas 2 px (`rounded.radio`). El sesgo de 12° (`--app-sesgo`,
`skewX(-12deg)`) va en botones principales, cinta, flechas, paginación y el
botón de filtros; su contenido se endereza con el sesgo inverso. Las etiquetas
y la ventana se cortan con `clip-path` en paralelogramo. Las costuras son
líneas punteadas (2 px en las placas, 1 px dentro de la etiqueta «Agotado»).

### Named Rules
**The Texto Derecho Rule.** El panel va en sesgo; el texto y los iconos de
adentro, siempre derechos.

## Components

### Buttons
- **Shape:** esquina recta (0); en la tienda, cortado en sesgo de 12°.
- **Principal:** relleno `ambar`, texto `sobre-ambar`, 44 px de alto; en la
  tienda en condensada 800 mayúscula (20 px). Hover `ambar-fuerte`; al pulsar
  se encoge (`scale(0.95–0.97)`) dentro del sesgo.
- **Secundario:** transparente, borde de 1 px `borde-control` en el panel (2 px
  del color del texto en la tienda); hover con `superficie-alta`.
- **Desactivado:** borde punteado y opacidad 0.4 en la tienda (0.5–0.6 en el
  panel), sin cambiar de color.

### Chips (cinta de secciones)
- **Style:** condensada 700, 18 px, mayúsculas, borde de 2 px del color del
  texto, en sesgo.
- **State:** la actual, lona clara con tinta verde; hover, costura engrosada.

### Cards / Containers
- **Placa de producto:** `lona` con tejido crudo, 2 px de esquina, costura
  interior de 1 px al 12 %. Foto 4:3 sobre `foto`, contenida, nunca teñida.
  Marca del producto en rótulo de 12 px (dato, no antetítulo), nombre,
  precio en condensada. Agotado: la foto baja a 0.55 y se cose la etiqueta
  «Agotado» en paralelogramo con ícono.
- **Tarjeta del panel:** `superficie`, borde de 1 px `borde`, 2 px de esquina,
  24 px de relleno.

### Inputs / Fields
- **Style:** fondo `fondo` (o `superficie` en los filtros), borde de 1 px
  `borde-control`, esquina recta, 44 px. La búsqueda de la tienda va en lona
  clara sin borde, unida a su botón amarillo en sesgo.
- **Cantidad con − y +:** el campo de 5 rem centrado entre dos botones de
  44 px con borde de 2 px del color del texto; en el límite, borde punteado y
  opacidad 0.4 (`shared/paso-de-cantidad`).
- **Focus:** el contorno global de 3 px (`--app-foco` y
  `--app-foco-separacion`; *24/10/2026*). Lo que lo muestra en otro elemento,
  como la etiqueta de un campo de archivo o el visor 3D, usa los mismos
  tokens.
- **Error:** borde `error` de 2 px (o costura interior en el catálogo) y el
  mensaje en `error` junto al campo; además, resumen al principio del
  formulario, que recibe el foco al fallar el envío y lleva a cada campo.
- **Avisos:** caja con borde `error` o `exito` (4 px a la izquierda en el
  panel) sobre su fondo tenue.

### Navigation
- Encabezado: marca en condensada 700 mayúscula con logotipo cuadrado amarillo;
  búsqueda ancha; sesión. La navegación va en Barlow 600, 14 px, `texto-suave`;
  la sección actual se marca con raya inferior de 2 px en `acento-texto`, no
  solo con color. En el celular, segunda fila desplazable.

### Carrusel
- Título en condensada, flechas cuadradas de 44 px en sesgo, «Ver todo» en
  rótulo con flecha que avanza 3 px al pasar el mouse; pista con frenado en
  cada placa y desplazamiento suave (instantáneo con movimiento reducido).

## Do's and Don'ts

### Do:
- **Do** sacar cada color, espacio, duración y curva de `_tokens.scss`, y
  estilar componentes con `:host`.
- **Do** medir el contraste de cada color nuevo en los dos modos (4.5:1 texto,
  3:1 bordes de control).
- **Do** marcar el estado con texto y forma (costura, troquel, relieve, raya),
  nunca solo con color.
- **Do** animar solo `transform`, `opacity`, `clip-path` y `filter`, hasta
  300 ms (400 ms solo en diálogos y paneles laterales).
- **Do** usar `--app-curva-salida` para lo que entra o responde a una acción,
  `--app-curva-irse` solo para salidas cortas con la duración rápida, y
  `--app-curva-mover` para lo que se mueve dentro de la pantalla.
- **Do** quitar el desplazamiento con `prefers-reduced-motion` y dejar los
  fundidos (`--app-desplazamiento` pasa a 0).
- **Do** usar iconos de Lucide con su tamaño de token (`--app-icono`, 18 px);
  `aria-label` si va solo, `aria-hidden` si acompaña texto.
- **Do** poner las fotos de producto sobre blanco, sin teñir.

### Don't:
- **Don't** usar emojis como iconos.
- **Don't** poner antetítulos encima de los títulos.
- **Don't** usar la monoespaciada en etiquetas o leyendas.
- **Don't** usar el amarillo como texto sobre lona clara.
- **Don't** sesgar el texto ni los iconos: solo el panel que los lleva.
- **Don't** poner vidrio ni desenfoque en el encabezado: es lona sólida.
- **Don't** redondear: 0 en controles, 2 px en placas y tarjetas.
