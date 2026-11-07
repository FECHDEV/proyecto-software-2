# Lista de cambios pedidos

Agregados y ajustes que pidió Fabio, para verlos en profundidad antes de
implementarlos. Nada de esta lista está empezado. Cada punto, al decidirse,
pasa por el flujo de `docs/flujo-sdd.md`: historia nueva o ampliación de una
cerrada, plan corto, `/sdd:analizar`, TDD y `/sdd:cerrar`.

*Lista del 07/11/2026.* Ya hecho de la misma tanda: el comprobante del
mostrador al centro y «Descargar QR» en el pago del pedido (PR #110).

## 1. Recibo de la compra para el cliente

- **Qué:** que el cliente imprima o guarde el comprobante de su compra desde
  «Mis pedidos» (`/mis-pedidos/:id`), con el mismo ticket del mostrador.
- **Alcance propuesto:** compras en línea y en tienda; solo con el pedido
  «Pagado» o «Entregado»; con la leyenda «No válido como factura» (sin SIAT).
- **Cómo entra:** ampliación de HU-14 («Mis pedidos»).
- **A ver:** si el ticket se comparte con el del mostrador (`features/comprobante`)
  o se mueve a `shared/`; qué datos muestra al cliente (¿«Atendió»?).

## 2. Buscar al comprador por nombre en el mostrador

- **Qué:** en «Nueva venta», buscar la cuenta del cliente por nombre o
  apellido, además del correo exacto de hoy.
- **Decidido (07/11/2026):** desde 3 letras, hasta 10 resultados, y en cada
  uno el nombre y el correo enmascarado (`fa***@gmail.com`) para distinguir
  a dos clientes con el mismo nombre.
- **Cómo entra:** ampliación de HU-23 (RF-4 hoy pide el correo exacto para
  que el Empleado no pueda recorrer la lista de clientes).
- **A ver:** búsqueda sin tildes ni mayúsculas; solo Clientes activos; que el
  Contador siga sin acceso.

## 3. Descuentos (HU-24 nueva)

- **Qué:** el Administrador crea descuentos que aparecen solos en la tienda.
- **Decidido (07/11/2026):** porcentaje sobre un **producto o una categoría**,
  con **fecha de inicio y de fin**. Se aplica solo: precio tachado, precio
  nuevo y «−15 %» en el catálogo, el detalle, el carrito y el mostrador. Al
  vencer vuelve el precio normal, sin tocar el precio del producto.
- **Reglas propuestas:** el precio con descuento lo calcula siempre el
  servidor y se guarda en la línea del pedido; el pedido ya confirmado no
  cambia si el descuento vence o se edita.
- **A ver:**
  - si un producto tiene descuento propio y de su categoría a la vez: ¿gana
    el mayor?;
  - tope del porcentaje (¿hasta 90 %?) y si se permite un descuento sobre el
    costo (vender a pérdida);
  - redondeo a centavos (HALF_UP, como el resto del dinero);
  - qué cambia en HU-23 («precio de hoy, sin rebajas»): el mostrador usaría
    el precio con descuento;
  - reportes: cuánto se descontó en el período y ventas con y sin descuento;
  - el precio de los datos para buscadores (JSON-LD) con el descuento.
- **Para después:** descuento por cantidad («desde 10 unidades, −10 %») y
  cupones. Descartado: precios dinámicos por demanda o stock.

## 4. Reportes de clientes y más reportes

Lo que hay hoy (HU-18 y HU-23): resumen de ventas, ventas por día y por día
de la semana, más y menos vendidos, «Sin venderse», ventas por tipo de
cliente, **«Clientes inactivos»** (solo los que *no* compraron en el
período) y «Pedidos perdidos».

- **Qué falta:** los clientes que **sí** compran. Propuesta de una sección
  «Clientes» (o ampliar la actual) con, por cliente y en el período:
  - cantidad de compras y total gastado;
  - ticket promedio (gasto ÷ compras);
  - primera y última compra;
  - **cada cuánto compra:** días promedio entre una compra y la siguiente;
  - canal (en línea, en tienda) y lo que más compra (producto o categoría).
- **Otros reportes a considerar:**
  - ranking de mejores clientes (por gasto y por cantidad de compras);
  - clientes nuevos del período y cuántos volvieron a comprar;
  - ventas por categoría;
  - productos más vendidos por cliente o por tipo de cliente (los más y menos
    vendidos ya existen en general);
  - ventas por hora del día (para el mostrador);
  - ventas por Empleado en el mostrador.
- **Cómo entra:** historia nueva de reportes (o ampliación de HU-18), con el
  Excel y el PDF de HU-19.
- **A ver:**
  - quién ve los datos de cada cliente (hoy los reportes son del Contador y
    del Administrador);
  - el consumidor final del mostrador no tiene cuenta: no entra en los
    reportes por cliente;
  - qué cuenta como compra (la regla de HU-18 RF-4: «Pagado» o «Entregado»,
    en la fecha del pago);
  - cuáles de estos reportes valen la pena y cuáles se descartan.
