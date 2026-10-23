# Catálogo de demostración (issue #25)

El catálogo con el que se muestra la tienda: **22 productos en 5 categorías**,
todos con fotos y modelo 3D. Lo carga `CatalogoDemo` al arrancar, una sola vez
y con la base sin productos (`decisiones.md` → «Catálogo de demostración»).

- `catalogo.csv` está en esta carpeta y, junto con las fotos y los modelos, en
  la carpeta de la demostración, **fuera del repositorio**. Esa carpeta se
  indica en `.env` con `TIENDA_DEMO_DIRECTORIO`, que Docker monta de solo
  lectura.
- Hay 6 destacados y uno agotado (la llave Stillson, con stock 0). Los
  precios están en bolivianos y las descripciones son propias.

## Fuente y licencia

**Todo sale de [Poly Haven](https://polyhaven.com/models), con licencia
[CC0](https://polyhaven.com/license)** (dominio público, sin atribución
obligatoria).

- **Modelos:** la versión glTF de 1k de cada modelo, pasada a `.glb` con las
  texturas recomprimidas en JPEG (calidad 75), como dice «Visor 3D y modelos»:

  ```
  npx @gltf-transform/cli copy modelo_1k.gltf modelo.glb
  npx @gltf-transform/cli jpeg modelo.glb final.glb --quality 75
  ```

  El generador lleva además las texturas a 512 px (`resize`). Ocupan entre
  0,39 y 1,36 MB. Los de más de 1 MB (generador, escalera y escoba) entran con
  el aviso de peso. Todos están en su escala real, en metros.
- **Fotos:** sacadas de los mismos modelos con `<model-viewer>` sobre fondo
  blanco, en JPEG de 1200 × 900, desde dos o tres ángulos. Son obras
  derivadas de modelos CC0: también se pueden usar sin restricciones.

| Producto | Modelo | Fotos | Original en Poly Haven |
|---|---|---|---|
| Martillo de peña 500 g | `martillo-de-pena.glb` | `martillo-de-pena-1.jpg`, `martillo-de-pena-2.jpg` | [cross_pein_hammer](https://polyhaven.com/a/cross_pein_hammer) |
| Llave combinada 13 mm | `llave-combinada.glb` | `llave-combinada-1.jpg`, `llave-combinada-2.jpg`, `llave-combinada-3.jpg` | [combination_wrench](https://polyhaven.com/a/combination_wrench) |
| Llave inglesa de 10 pulgadas | `llave-inglesa.glb` | `llave-inglesa-1.jpg`, `llave-inglesa-2.jpg` | [adjustable_wrench](https://polyhaven.com/a/adjustable_wrench) |
| Alicate universal aislado de 8 pulgadas | `alicate-universal.glb` | `alicate-universal-1.jpg`, `alicate-universal-2.jpg` | [pliers](https://polyhaven.com/a/pliers) |
| Pinza pico de loro de 10 pulgadas | `pinza-pico-de-loro.glb` | `pinza-pico-de-loro-1.jpg`, `pinza-pico-de-loro-2.jpg`, `pinza-pico-de-loro-3.jpg` | [tongue_groove_pliers](https://polyhaven.com/a/tongue_groove_pliers) |
| Destornillador plano 6 x 150 mm | `destornillador-plano.glb` | `destornillador-plano-1.jpg`, `destornillador-plano-2.jpg` | [flathead_screwdriver](https://polyhaven.com/a/flathead_screwdriver) |
| Juego de 2 destornilladores | `juego-de-destornilladores.glb` | `juego-de-destornilladores-1.jpg`, `juego-de-destornilladores-2.jpg`, `juego-de-destornilladores-3.jpg` | [screwdrivers_02](https://polyhaven.com/a/screwdrivers_02) |
| Serrucho para madera de 20 pulgadas | `serrucho.glb` | `serrucho-1.jpg`, `serrucho-2.jpg` | [handsaw_wood](https://polyhaven.com/a/handsaw_wood) |
| Comba de 4 libras | `comba.glb` | `comba-1.jpg`, `comba-2.jpg` | [sledgehammer_01](https://polyhaven.com/a/sledgehammer_01) |
| Llave Stillson de 14 pulgadas | `llave-stillson.glb` | `llave-stillson-1.jpg`, `llave-stillson-2.jpg` | [pipe_wrench](https://polyhaven.com/a/pipe_wrench) |
| Taladro atornillador inalámbrico 18 V | `taladro-inalambrico.glb` | `taladro-inalambrico-1.jpg`, `taladro-inalambrico-2.jpg`, `taladro-inalambrico-3.jpg` | [Drill_01](https://polyhaven.com/a/Drill_01) |
| Wincha de 3 metros | `wincha.glb` | `wincha-1.jpg`, `wincha-2.jpg` | [measuring_tape_01](https://polyhaven.com/a/measuring_tape_01) |
| Generador a gasolina 2500 W | `generador.glb` | `generador-1.jpg`, `generador-2.jpg`, `generador-3.jpg` | [portable_generator](https://polyhaven.com/a/portable_generator) |
| Regadera galvanizada de 8 litros | `regadera.glb` | `regadera-1.jpg`, `regadera-2.jpg`, `regadera-3.jpg` | [watering_can_metal_01](https://polyhaven.com/a/watering_can_metal_01) |
| Aspersor giratorio con ruedas | `aspersor.glb` | `aspersor-1.jpg`, `aspersor-2.jpg`, `aspersor-3.jpg` | [garden_sprinkler_01](https://polyhaven.com/a/garden_sprinkler_01) |
| Maceta de barro de 26 cm | `maceta-de-barro.glb` | `maceta-de-barro-1.jpg`, `maceta-de-barro-2.jpg`, `maceta-de-barro-3.jpg` | [planter_pot_clay](https://polyhaven.com/a/planter_pot_clay) |
| Almaciguera de 9 cavidades | `almaciguera.glb` | `almaciguera-1.jpg`, `almaciguera-2.jpg`, `almaciguera-3.jpg` | [seeding_tray_01](https://polyhaven.com/a/seeding_tray_01) |
| Escoba de cerda dura | `escoba.glb` | `escoba-1.jpg`, `escoba-2.jpg` | [plastic_broom](https://polyhaven.com/a/plastic_broom) |
| Recogedor metálico | `recogedor.glb` | `recogedor-1.jpg`, `recogedor-2.jpg`, `recogedor-3.jpg` | [dustpan](https://polyhaven.com/a/dustpan) |
| Destapador de goma | `destapador.glb` | `destapador-1.jpg`, `destapador-2.jpg` | [plunger](https://polyhaven.com/a/plunger) |
| Caja de herramientas metálica de 40 cm | `caja-de-herramientas.glb` | `caja-de-herramientas-1.jpg`, `caja-de-herramientas-2.jpg`, `caja-de-herramientas-3.jpg` | [metal_toolbox](https://polyhaven.com/a/metal_toolbox) |
| Escalera de aluminio de tres tramos | `escalera.glb` | `escalera-1.jpg`, `escalera-2.jpg` | [ladder_sectioned_01](https://polyhaven.com/a/ladder_sectioned_01) |

## Cómo se carga

1. Copiar la carpeta de la demostración (con `catalogo.csv`, las fotos y los
   `.glb`) a la máquina que corre Docker.
2. En `.env`: `TIENDA_DEMO_DIRECTORIO=<ruta de la carpeta>`.
3. Arrancar con la base **sin productos**: `docker compose up -d --build`. Si
   una fila es inválida, el registro del backend dice cuál y por qué, y no se
   carga nada.
