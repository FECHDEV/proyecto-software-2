# Documentación del proyecto

La escribe Fabio con Claude Code (*21/10/2026:* David se retiró del proyecto),
en Markdown, y entra por PR desde una rama `docs/…`. Sale solo de los datos
reales del repositorio: historias, decisiones, issues, PR y código. Las reglas
completas y el alcance de cada archivo están en `AGENTS.md` → «Rol:
documentación».

Archivos previstos:

| Archivo | Contenido |
|---|---|
| `perfil.md` | Perfil del proyecto: problema, objetivos, alcance, equipo |
| `metodologia.md` | Scrum en este proyecto: roles, eventos, artefactos, Definition of Done |
| `requisitos.md` | Requisitos funcionales (desde las historias) y no funcionales |
| `arquitectura.md` | Stack, capas, módulos, datos, API y despliegue, descritos desde el código |
| `backlog.md` | Historias por sprint y su estado |
| `sprints/sprint-N.md` | Acta de cada sprint: planning, review y retro |
| `diagramas/` | Diagramas en Mermaid |
| `demo/` | El catálogo de demostración (abajo) |

Al final se exporta al Word con el formato de la universidad (pandoc o un
generador).

## Cómo se documenta

1. Rama desde `main` actualizado: `docs/<tema>`.
2. En Claude Code:
   - `/proyecto:acta N` al cerrar el sprint N;
   - `/proyecto:requisitos` cuando hay historias nuevas en `docs/historias/`;
   - «escribí `arquitectura.md` a partir del código».
3. Leer lo que escribió: que no invente nada y completar los «[FALTA DATO]».
4. Commit y PR; con el CI en verde, se mergea.

## Catálogo de demostración (issue #25)

El catálogo con el que se muestra la tienda en las reviews y en la defensa. El
código que lo carga ya existe (`decisiones.md` → «Catálogo de demostración»);
falta el contenido.

- **Entrega:** `docs/proyecto/demo/catalogo.csv` y
  `docs/proyecto/demo/README.md`, por PR con `Closes #25`.
- **Rubro: ferretería** (herramientas, hogar y jardín). Los productos con
  modelo 3D salen de [Poly Haven](https://polyhaven.com/models) (licencia CC0:
  martillos, llaves, taladros, destornilladores, alicates, escaleras,
  regaderas…); el resto puede ir solo con fotos.
- `catalogo.csv`, una fila por producto:
  `categoria,nombre,descripcion,precio_bs,stock_inicial,imagenes,modelo_3d,destacado,unidad`.
  `imagenes`, los archivos separados por `|`; `modelo_3d`, el `.glb` o vacío;
  `destacado`, `si` o vacío (la portada muestra hasta 12); `unidad`, vacía o
  `metro`, `kilo`, `litro`, `caja`, `par` o `rollo`. **Ningún campo lleva
  comas**; los decimales van con punto.
- Entre 15 y 25 productos, en 4 a 6 categorías, con precios creíbles en
  bolivianos y descripciones cortas y propias.
- **Imágenes:** propias o con licencia libre (Unsplash, Pexels), JPG, PNG o
  WebP de **2 MB como máximo**. Van en una carpeta fuera del repositorio
  (`TIENDA_DEMO_DIRECTORIO`); el `README.md` lista cada archivo con su fuente y
  su licencia.
- Sin marcas registradas en los nombres ni datos de personas reales.
