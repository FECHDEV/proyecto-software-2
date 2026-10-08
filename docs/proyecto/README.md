# Documentación del proyecto

La escribe el equipo, en Markdown, y entra por PR desde una rama `docs/…`.
Sale solo de los datos reales del repositorio: historias, decisiones, issues,
PR y código. Las reglas completas y el alcance de cada archivo están en
`AGENTS.md` → «Rol: documentación», que opencode lee solo.

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

Al final se exporta al Word con el formato de la universidad (pandoc o un
generador).

## Primeros pasos (para quien documenta)

Una sola vez:

1. Aceptar la invitación al repositorio y al Project «Tienda Montero».
2. Clonar: `git clone git@github.com:FECHDEV/proyecto-software-2.git`.
3. Instalar [GitHub CLI](https://cli.github.com/) e iniciar sesión:
   `gh auth login`.
4. Abrir opencode en la carpeta del repositorio. Lee `AGENTS.md` solo.

Para documentar no hace falta Java, Node ni el `.env`: solo para levantar la
aplicación (`README.md` de la raíz).

Cada vez que se documenta algo:

1. Actualizar `main` y crear una rama:
   `git checkout main`, `git pull`, `git checkout -b docs/<tema>`.
2. Pedirle a opencode lo que toca, por ejemplo:
   - `/acta 1` al cerrar el sprint 1;
   - `/requisitos` cuando hay historias nuevas en `docs/historias/`;
   - «escribe `arquitectura.md` a partir del código».
3. **Leer lo que escribió.** Revisar que no invente nada y completar los
   «[FALTA DATO]».
4. Pedirle el commit y el PR (o hacerlos a mano). Con el CI en verde, se
   mergea.

Si algo del repositorio no coincide (una historia contra el código, una regla
contra otra), no se corrige desde acá: se avisa a quien programa.
