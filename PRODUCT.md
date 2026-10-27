# Product

<!-- impeccable:product-schema 1 -->

Quién usa la tienda y cómo se siente. Las reglas del negocio (roles, pago,
stock, pedidos) viven en `CLAUDE.md` y `docs/decisiones.md`; lo visual, en
`DESIGN.md`. Este archivo no las repite.

## Platform

web

## Users

- **Clientes de una ferretería de Montero** (Santa Cruz, Bolivia): dueños de
  casa y gente que arregla, construye o cuida el jardín. Buscan una
  herramienta o un material concreto y quieren saber si hay y cuánto cuesta.
  Llegan **mitad desde el celular y mitad desde la computadora**: a veces
  comparan con calma en casa, a veces buscan rápido algo puntual.
- **Personal de la tienda** (Administrador, Empleado, Contador): trabajan en
  el panel (`/panel`) con productos, inventario y, más adelante, pedidos y
  reportes. Para ellos la interfaz es una herramienta de todos los días.

## Product Purpose

La tienda en línea propia de una ferretería de Montero (herramientas, hogar y
jardín): un solo vendedor y un solo inventario, no un marketplace. El cliente
encuentra el producto, ve si hay, lo compra y sigue su pedido; el personal
mantiene el catálogo y el stock al día. Éxito: que el cliente encuentre lo que
busca en pocos segundos y confíe en comprar sin ir al local.

## Positioning

Una ferretería de barrio de Montero, de confianza, con una tienda en línea
seria y ordenada, que además deja ver algunos productos **en 3D y en realidad
aumentada** antes de comprarlos. Lo clásico de la ferretería (herramienta de
verdad, de obra) con toques modernos.

## Operating Context

- Catálogo de 15 a 25 productos de demostración en 4 a 6 categorías, en
  bolivianos; productos con unidad de venta (unidad, metro, kilo, litro, caja,
  par, rollo).
- Compra con pago en línea por QR (Libélula; el simulador solo para probar
  en la PC) y retiro en la tienda, sin envío; el personal prepara y entrega
  el pedido.
- Se muestra en reuniones de revisión y en la defensa del proyecto.

## Capabilities and Constraints

- Hecho: cuentas y roles, categorías, productos con hasta cinco fotos,
  inventario, catálogo público (portada con carruseles y `/catalogo`),
  detalle del producto, visor 3D y realidad aumentada, carrito, pedidos,
  pago, despacho, reportes y los correos (verificación, recuperación y
  avisos del pedido).
- Angular 22 en el frontend; estilos por tokens; iconos Lucide.
- **Pendiente de datos reales:** nombre comercial definitivo, dirección,
  horarios y contacto del local. No se inventan.

## Brand Commitments

- **Nombre:** «Tienda Montero» (configurable en `core/marca.ts` y
  `TIENDA_MARCA`).
- **Personalidad:** robusta, de obra; ordenada y fácil; local y confiable;
  clásica con toques modernos (3D, realidad aumentada).
- **Tono:** se trata al cliente de **usted**, con frases cortas y claras,
  sin jerga de marketing ni exageraciones. *(21/10/2026: todas las pantallas
  pasaron a usted.)*
- **La interfaz no menciona la universidad** ni que es un trabajo académico.

## Evidence on Hand

- Sin fotos propias todavía: el catálogo de demostración usará fotos con
  licencia libre (issue #25) y modelos 3D de Poly Haven (CC0).
- Sin testimonios, clientes, cifras de ventas ni premios: **no se inventan**.
- Precios reales de la demostración: los que cargue el equipo.

## Product Principles

1. **Encontrar primero:** búsqueda, categorías y precio con su unidad siempre
   a la vista.
2. **Honestidad:** «Agotado» cuando no hay, nunca la cantidad en stock; nada
   de urgencias falsas.
3. **De confianza, no de cadena:** cercana como la ferretería del barrio,
   seria como una herramienta buena.
4. **Lo moderno al servicio del producto:** el 3D y la realidad aumentada
   ayudan a decidir, no decoran.

## Accessibility & Inclusion

WCAG AA: contraste de 4.5:1 en texto (3:1 en bordes de control) en los dos
modos, uso completo con teclado, áreas tocables de 44 px y movimiento
reducido respetado.
