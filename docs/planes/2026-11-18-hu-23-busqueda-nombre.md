# HU-23 (ampliación) — El comprador por nombre

Historia: `docs/historias/HU-23.md` (RF-25 a RF-30; toca RF-4 y RF-23).
Decisión: `decisiones.md` → «Venta en tienda» (18/11/2026). Pedido en
`docs/lista-de-cambios.md` → punto 2. Issue: #119. El plan de HU-23 del 05/11/2026 no se toca: es el historial.
Corregido el 18/11/2026 tras `/sdd:analizar`: la prueba de la cuenta sin el
correo verificado y la de la cuenta que se desactiva antes del cobro.

**Dudas resueltas por Fabio el 18/11/2026** con las recomendaciones; el plan
las sigue.

## Decisiones de diseño

- **El mismo endpoint con otro parámetro:** `GET
  /api/ventas/clientes?nombre=` (con `params = "nombre"` en el
  `@GetMapping`) responde la lista; `?correo=` sigue igual, con su 404.
  **Motivo:** RF-4, sus pruebas y `VentasService.buscarCliente` no cambian, y
  la regla de `SecurityConfig` sobre `/api/ventas/clientes` (Empleado y
  Administrador, el Contador no) ya cubre la búsqueda nueva sin tocarla
  (RF-30).
- **La respuesta:** `CompradoresResponse(List<CompradorResponse> cuentas,
  boolean hayMas)`. Cada cuenta, el mismo `CompradorResponse(idUsuario,
  nombre, correo)` con el correo ya enmascarado, por una fábrica nueva
  `CompradorResponse.conCorreoOculto(Usuario)`. **Motivo:** la pantalla
  guarda al comprador elegido en el mismo `Comprador` de hoy, sea por correo
  o por nombre; `hayMas` sale de pedir 11 (duda 4; si se descarta, la
  respuesta es la lista sola).
- **El correo no se busca por una parte** (RF-28): la consulta por nombre no
  mira `correo`. Un texto con «@» lo manda la pantalla a `?correo=`; si
  llegara a `?nombre=`, se busca como nombre y no encuentra a nadie.
- **Sin tildes (duda 1):** columna `usuario.busqueda` (hasta 161), con
  `Producto.textoDeBusqueda(nombre + " " + apellido)`; la mantienen
  `registrarCliente`, `crearAdministrador` y `actualizarDatos`, y
  `BusquedaAlArrancar` completa las cuentas que ya existen. Sin índice: un
  `LIKE '%…%'` no lo usa, y el filtro por rol y estado ya tiene
  `idx_usuario_rol_estado`. **Si Fabio dice que no:** sin columna, `lower(concat(u.nombre, ' ', u.apellido))`.
- **Las 3 letras (duda 2):** se cuentan en el texto normalizado sin los
  espacios; con menos, lista vacía (la API) y aviso sin consultar (la
  pantalla). El patrón es el de siempre, `Busquedas.patron`, que escapa `%`,
  `_` y `!`.
- **Orden (duda 5):** `nombre`, `apellido`, `idUsuario`.
- **En la pantalla, una búsqueda con botón,** como la del correo: un campo
  «Nombre, apellido o correo del cliente» y «Buscar» (o Enter); con «@» va a
  `buscarCliente`, sin «@» a la búsqueda por nombre. Con `HttpClient`, como
  `buscarCliente` («una acción de quien vende», `ventas.service.ts`). **Motivo:**
  la pantalla ya funciona así y una consulta por tecla no agrega nada con 10
  resultados; si Fabio prefiere resultados al escribir, se usa
  `httpResource` con la espera de 300 ms de `BusquedaDeProductos`.
- **Un componente propio,** `features/venta/busqueda-de-comprador`
  (`app-busqueda-de-comprador`), como `BusquedaDeProductos`: los estilos de
  `venta.page.scss` ya rozan el presupuesto de 6 kB. Recibe `bloqueada` y
  emite `elegir: Comprador`; el comprador elegido y «Volver a consumidor
  final» siguen en `VentaPage`.

## Tareas

1. **Backend: la columna de búsqueda del usuario** (`entity/Usuario.java`,
   `repository/UsuarioRepository.java`, `service/BusquedaAlArrancar.java`).
   Código compartido (entidad): después, la suite completa del backend.
   - `Usuario`: `@Column(length = 161) private String busqueda;` y
     `actualizarBusqueda()`, llamado desde las dos fábricas y
     `actualizarDatos`.
   - `UsuarioRepository.findByBusquedaIsNull()`; `BusquedaAlArrancar` la
     completa (y su comentario nombra la columna nueva).
   - Pruebas:
     - `UsuarioTest`: `laBusquedaVaSinTildesNiMayusculasConUnSoloEspacio`,
       `actualizarDatosRehaceLaBusqueda` (RF-25).
     - `BusquedaAlArrancarTest`: `completaLaBusquedaDeLasCuentasQueNoLaTienen`
       (RF-25).

2. **Backend: el correo enmascarado** (`dto/CompradorResponse.java`).
   - **Delicado (datos personales), código completo:**

     ```java
     // HU-23 RF-26: la búsqueda por nombre nunca devuelve el correo completo
     public static CompradorResponse conCorreoOculto(Usuario usuario) {
     	return new CompradorResponse(usuario.getIdUsuario(), usuario.getNombre() + " " + usuario.getApellido(),
     			ocultar(usuario.getCorreo()));
     }

     // Hasta dos caracteres de la parte local, nunca la parte entera, y el dominio completo
     static String ocultar(String correo) {
     	int arroba = correo.lastIndexOf('@');
     	if (arroba < 1) {
     		return "***";
     	}
     	return correo.substring(0, Math.min(2, arroba - 1)) + "***" + correo.substring(arroba);
     }
     ```
   - Pruebas, en `dto/CompradorResponseTest` (nueva):
     - `dejaDosCaracteresYElDominio` (`fabio@gmail.com` → `fa***@gmail.com`)
       (RF-26)
     - `conDosOUnCaracterNoMuestraLaParteEntera` (`ab@x.com` → `a***@x.com`,
       `a@x.com` → `***@x.com`) (RF-26)
     - `sinArrobaNoMuestraNada` (RF-26)

3. **Backend: la búsqueda por nombre** (`repository/UsuarioRepository.java`,
   `service/VentaTiendaService.java`, `controller/VentaTiendaController.java`,
   `dto/CompradoresResponse.java`).
   - `UsuarioRepository.compradoresPorNombre(String patron, Pageable
     pagina)`: `select u from Usuario u where u.rol = CLIENTE and u.estado =
     ACTIVA and u.busqueda like :patron escape '!'`. **El rol y el estado
     van en la consulta** (RF-25), no en un filtro después: si no, el
     personal ocuparía lugares de los 10.
   - `VentaTiendaService.buscarClientesPorNombre(String nombre):
     CompradoresResponse`, `@Transactional(readOnly = true)`: menos de 3
     letras → vacía (RF-29); pide `PageRequest.of(0, RESULTADOS + 1,
     Sort.by("nombre", "apellido", "idUsuario"))`, devuelve los 10 primeros
     con `conCorreoOculto` y `hayMas` si llegó el 11.º.
   - `VentaTiendaController`: `@GetMapping(value = "/clientes", params =
     "nombre") CompradoresResponse clientes(@RequestParam String nombre)`.
   - Pruebas, en `CompradorPorNombreIntegracionTest` (nueva, con el armado de
     `VentaTiendaIntegracionTest`):
     - `encuentraPorNombreOApellidoSinMayusculasNiTildes` («ana», «PAZ»,
       «jose» → «José») (RF-25)
     - `buscaElNombreSeguidoDelApellido` («ana paz» sí, «paz ana» no) (RF-25,
       duda 2)
     - `soloClientesActivosConOSinCorreoVerificadoNiPersonalNiDesactivados`
       (RF-25, casos límite)
     - `elCorreoVieneEnmascaradoYNuncaCompleto` (que el cuerpo no contenga
       ningún correo completo) (RF-26)
     - `noBuscaEnElCorreo` («gmail» y «@mail» no encuentran a nadie por el
       correo) (RF-28)
     - `hastaDiezEnOrdenYAvisaSiHayMas` (12 «Ana»: 10, ordenados, `hayMas`)
       (RF-25, duda 4)
     - `conMenosDeTresLetrasDaVacia` («an», «a b», «   ») (RF-29)
     - `losComodinesSeBuscanComoLetras` («%%%», «___») (caso límite)
     - `unTextoMuyLargoDaVaciaSinError` (caso límite)
     - `laVentaQuedaAlNombreDeLaCuentaElegida` (con el `idUsuario` de la
       búsqueda; el comprobante lleva el nombre) (RF-27)
     - `elContadorYElClienteReciben403YSinSesion401` (RF-30)
     - `porCorreoSigueIgual`: la prueba de RF-4 de
       `VentaTiendaIntegracionTest` sigue en verde sin tocarla (RF-4, RF-28).
       Ya cubre también el caso límite de la cuenta que se desactiva entre
       la búsqueda y el cobro (400, RF-13).

4. **Frontend: el servicio y el modelo** (`core/modelos/venta.ts`,
   `core/api/ventas.service.ts`). Código compartido (`core/`): después, la
   suite del frontend.
   - `interface CompradoresEncontrados { cuentas: Comprador[]; hayMas:
     boolean }`.
   - `VentasService.buscarClientesPorNombre(nombre: string):
     Promise<CompradoresEncontrados>`, con `params: { nombre: nombre.trim() }`.
   - Pruebas, en `ventas.service.spec.ts`:
     - `RF-25: busca al comprador por nombre, recortado`

5. **Frontend: la búsqueda del comprador** (`features/venta/
   busqueda-de-comprador.{ts,html,scss}`, `venta.page.{ts,html,scss}`).
   - `BusquedaDeComprador`: `input bloqueada`, `output elegir`. Un campo de
     texto (no `type="email"`), «Buscar»; con «@» llama a `buscarCliente` y
     elige directo (RF-4); sin «@» y con 3 letras o más, lista los resultados
     como botones «nombre — correo enmascarado» (RF-25, RF-26); con menos de
     3, el aviso sin consultar (RF-29); sin resultados, «No hay una cuenta de
     cliente activa con ese nombre.»; con `hayMas`, «Hay más cuentas con ese
     nombre: escriba el apellido.» (duda 4). La cantidad de resultados se
     anuncia en una región `aria-live`. Solo cuenta la respuesta de la última
     búsqueda (un contador de búsqueda; la anterior se descarta).
   - `VentaPage`: el formulario de hoy se reemplaza por
     `<app-busqueda-de-comprador>`; `buscarComprador`, `correo`,
     `buscandoComprador` y `errorDeComprador` pasan al componente.
   - Pruebas, en `busqueda-de-comprador.spec.ts` (nueva):
     - `RF-25, RF-26: sin «@» lista las cuentas con el nombre y el correo
       enmascarado`
     - `RF-27: elegir una emite su cuenta`
     - `RF-4, RF-28: con «@» busca por correo exacto y elige directo; un 404
       dice que no hay cuenta con ese correo`
     - `RF-29: con menos de 3 letras avisa y no llama a la API`
     - `RF-29: sin resultados lo dice`
     - `con más de 10 avisa que escriba el apellido` (duda 4)
     - `una respuesta tardía de una búsqueda anterior no reemplaza la última`
       (caso límite)
     - `bloqueada no busca ni elige` (RF-27, caso límite)
     - `un nombre con HTML se muestra como texto` (caso límite)
   - En `venta.page.spec.ts`: `buscaAlCompradorPorCorreo` (RF-4) pasa a usar
     el campo nuevo, sin cambiar lo que prueba, y se suma `RF-27: elegido por
     nombre, la venta va con su idCliente y «Volver a consumidor final» lo
     quita`.

6. **Cierre.** `docs/lista-de-cambios.md`: el punto 2 pasa a «hecho» con el
   PR. `/sdd:cerrar HU-23`.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR» que le tocan: el
subagente de interfaz (cambia «Nueva venta»), `/code-review`,
`/security-review` (expone datos de cuentas de clientes al personal), y
`web-design-guidelines` y `break-ui` sobre `BusquedaDeComprador` (nombres y
correos muy largos, 10 resultados en el celular).

## Orden y suites

1 (entidad compartida): suite completa del backend. 2 y 3: sus clases, y al
final la suite. 4 (`core/`): suite del frontend. 5: sus pruebas y `npx ng
build` (el presupuesto de estilos de la pantalla), y al terminar la suite y
`npm run lint`. Backend y frontend nunca a la vez. En `/sdd:cerrar`, todo.

## Resultado

*18/11/2026, `/sdd:cerrar HU-23` (ampliación).* Implementado con TDD por
Claude (Opus); la historia, el plan y el análisis los escribió Fable, y Fable
hizo también la revisión de interfaz y la de seguridad.

### Cobertura RF → pruebas

| RF | Backend | Frontend |
|---|---|---|
| RF-25 | `CompradorPorNombreIntegracionTest` (sin tildes, nombre seguido del apellido, solo Clientes activos, hasta 10 y `hayMas`), `UsuarioTest`, `BusquedaAlArrancarTest` | `busqueda-de-comprador.spec.ts`, `ventas.service.spec.ts` |
| RF-26 | `CompradorResponseTest`, `elCorreoVieneEnmascaradoYNuncaCompleto` | `busqueda-de-comprador.spec.ts` |
| RF-27 | `laVentaQuedaAlNombreDeLaCuentaElegida` | `busqueda-de-comprador.spec.ts`, `venta.page.spec.ts` (idCliente, «Volver a consumidor final», foco) |
| RF-28 | `noBuscaEnElCorreo`; RF-4 sigue en `VentaTiendaIntegracionTest` sin tocarla | `busqueda-de-comprador.spec.ts` (con «@», correo exacto) |
| RF-29 | `conMenosDeTresLetrasDaVacia` | `busqueda-de-comprador.spec.ts` (aviso sin llamar, una tilde suelta no cuenta, sin resultados) |
| RF-30 | `elContadorYElClienteReciben403YSinSesion401` | Sin pantalla propia: el guard de `/ventas/nueva` ya saca al Contador (RF-23) |

Casos límite con prueba: comodines (`%%%`, `___`), texto de 500
caracteres, HTML en el nombre, respuesta tardía de una búsqueda anterior o
después de cambiar el texto, búsqueda con la venta bloqueada (QR a la vista),
nombre que al normalizarse pasa el largo de la columna.

### Suites

- Backend: `./mvnw test`, **862 pruebas**, 0 fallas, 1 omitida (la de
  siempre).
- Frontend: `npx ng test --watch=false`, **981 pruebas en 83 archivos**;
  `npm run lint` sin errores; `npx ng build` sin avisos (el componente propio
  deja `venta.page.scss` dentro del presupuesto de 6 kB).
- Las de extremo a extremo no usan el campo del comprador (venden a
  consumidor final): corren en el CI del PR.

### Revisiones

- **Interfaz (subagente, Fable, playwright-cli en escritorio y celular, modo
  claro y oscuro):** corregidos el borde rojo del campo con error (se había
  perdido al mover el campo al componente), el nombre largo sin espacios que
  ensanchaba la página al elegirlo, la región `aria-live` que con
  `display: none` no se anunciaba, el foco que se perdía al elegir y al
  «Volver a consumidor final», la lista que quedaba abierta (se va al borrar
  el texto y con la venta bloqueada), «Buscando clientes…», el `aria-disabled`
  falso del botón mientras busca, el campo de solo lectura que parecía
  editable y `enterkeyhint="search"`. Queda como estaba (de todo el panel, no
  de esta ampliación): los campos de 14 px que hacen acercar la página en el
  iPhone.
- **`/code-review` (dos rondas):** corregidos la respuesta del correo que
  llegaba con la venta ya bloqueada (cambiaba el comprador con el QR a la
  vista) y la de la búsqueda por nombre en el mismo caso, las respuestas que
  estaban por llegar al cambiar el texto o al cortarse una búsqueda, la lista
  vieja al editar el texto, el largo de `usuario.busqueda` (cortado sin partir
  un carácter), el conteo de letras igual al del servidor (`letrasDe`), la
  constante `MINIMO_LETRAS_DE_COMPRADOR` en `core/modelos/venta.ts` y código
  muerto. **Descartados:** `httpResource` para la búsqueda (es una acción de
  quien vende al tocar «Buscar», como la búsqueda por correo de RF-4);
  ordenar por `busqueda` (la primera ronda lo propuso y la segunda mostró que
  contradice la duda 5 que decidió Fabio: se volvió a nombre, apellido y
  número de cuenta); bloquear las búsquedas repetidas (la última gana por el
  contador, y la revisión de interfaz pidió que el botón no diga
  «desactivado» si funciona; queda anotado: varias pulsaciones mandan varias
  consultas).
- **`/security-review`:** sin hallazgos. Revisado: la regla de
  `SecurityConfig` cubre el mapeo nuevo con `params = "nombre"`; la consulta
  usa parámetros y escapa los comodines; solo sale id, nombre y correo
  enmascarado; mínimo de 3 letras y 10 resultados sin páginas; sin HTML sin
  escapar.

### Distinto de lo planeado

- La lista de resultados no se muestra con la venta bloqueada (el plan decía
  botones desactivados): la revisión de interfaz mostró que empujaba «Cobro»
  fuera de la pantalla.
- `letrasDe` y `MINIMO_LETRAS_DE_COMPRADOR` en `core/modelos/venta.ts`, y no
  una constante dentro del componente.
- `VentaPage.elegirComprador` lleva el foco a «Volver a consumidor final»
  (no estaba en el plan).

### Prueba manual

*18/11/2026, Fabio:* la búsqueda por nombre funciona en el mostrador.
