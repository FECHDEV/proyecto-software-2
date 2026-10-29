# Tienda Montero

Tienda en línea propia: catálogo, carrito, pedidos, inventario y reportes, con
registro, inicio de sesión con JWT, recuperación de contraseña por correo y
roles fijos (Cliente, Administrador, Empleado y Contador). Se construye
historia por historia, con Scrum y el flujo SDD de `docs/flujo-sdd.md`.

## Stack

| Capa | Tecnología |
|---|---|
| Frontend | Angular 22 (Signal Forms, Vitest) |
| Backend | Java 21 / Spring Boot 4.1.1 |
| Comunicación | API REST |
| Base de datos | MySQL 8.4 (H2 en las pruebas) |
| Correo | Gmail por SMTP, con simulador por defecto |
| Pago | Interfaz `IPago`: Libélula (QR) por defecto en Docker; simulador en desarrollo y pruebas |
| Despliegue | Docker Compose; Caddy con HTTPS en la nube |
| CI | GitHub Actions en cada PR y en cada push a `main` |

## Estructura

```
.
├── AGENTS.md  CLAUDE.md  README.md  .env.example
├── DESIGN.md  PRODUCT.md   el sistema visual y a quién le habla la tienda
├── .github/   workflows/pruebas.yml  CODEOWNERS  pull_request_template.md
├── .claude/commands/   sdd/ (historia, analizar, cerrar)  proyecto/ (acta, requisitos)
├── docs/      flujo-sdd.md  decisiones.md  historias/  planes/  proyecto/
├── backend/   Spring Boot: JWT, roles, ManejadorErrores, correo SMTP, IPago (simulador y Libélula)
├── frontend/  Angular: features/core/shared, interceptores, guards, tema claro/oscuro, tokens
├── docker-compose.yml  docker-compose.nube.yml  nube/Caddyfile
└── scripts/   instalar-servidor.sh  copiar-env-al-servidor.sh
```

## Requisitos

- JDK 21
- Node.js 22 y npm
- MySQL Server 8.4 en `localhost:3306` (solo para levantar la API en local; las
  pruebas no lo necesitan)

Maven no hace falta: el backend usa el wrapper `mvnw` incluido.

## Preparar la base de datos

Una sola vez, con un usuario que tenga permisos de administración:

```sql
CREATE DATABASE tienda_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tienda_app'@'localhost' IDENTIFIED BY '<contrasena>';
GRANT ALL PRIVILEGES ON tienda_db.* TO 'tienda_app'@'localhost';
FLUSH PRIVILEGES;
```

Las tablas las genera Hibernate al arrancar (`ddl-auto=update`), sin migraciones.

## Configuración

`application.properties` lee las credenciales de variables de entorno con
prefijo `TIENDA_`, para no versionarlas (por qué el prefijo:
`docs/decisiones.md` → «Variables de entorno con prefijo propio»).

En desarrollo, sin perfil activo el backend carga el perfil `local`: si existe
`backend/src/main/resources/application-local.properties` (ignorado por git),
toma de ahí las credenciales. Las variables de entorno siguen teniendo prioridad:

```properties
spring.datasource.password=${TIENDA_DB_PASSWORD:<contrasena>}
tienda.jwt.secreto=${TIENDA_JWT_SECRETO:<clave base64 de al menos 256 bits>}
tienda.admin.correo=${TIENDA_ADMIN_CORREO:<correo>}
tienda.admin.contrasena=${TIENDA_ADMIN_PASSWORD:<contrasena>}
```

## Levantar el proyecto

**Backend** — `http://localhost:8080`

```bash
cd backend
./mvnw spring-boot:run
```

**Frontend** — `http://localhost:4200`

```bash
cd frontend
npm start
```

El frontend usa `proxy.conf.json` para redirigir `/api` al backend y evitar
CORS en desarrollo.

## Pruebas

```bash
cd backend  && ./mvnw test
cd frontend && npx ng test --watch=false && npx ng build
```

Las del backend corren contra H2 en memoria (perfil `test`); las del frontend,
con Vitest sobre jsdom. Ninguna necesita MySQL ni un navegador. Las mismas
corren en GitHub Actions en cada PR (`.github/workflows/pruebas.yml`).

### De extremo a extremo

```bash
cd frontend && npm run e2e
```

Recorren el camino principal en el navegador con Playwright (`frontend/e2e/`):
registro, catálogo, carrito, verificación del correo, pedido, pago simulado,
«Mis pedidos» y la entrega, en escritorio y en celular. Cada corrida levanta
desde cero una tienda aparte en Docker (proyecto `tienda-e2e`, puerto 8090), con
los simuladores de pago y de correo y el catálogo de prueba del backend; no usa
el `.env` ni la base de desarrollo. Sus claves se generan al azar en
`frontend/e2e/.env.e2e`, ignorado por git. En la PC usan Edge; en el CI,
Chromium. La tienda de prueba queda levantada para revisar un fallo;
`npm run e2e:bajar` la borra.

## Correo

Con Docker, los correos (verificación, recuperación de contraseña y avisos del
pedido) salen por Gmail (`TIENDA_CORREO_PROVEEDOR=smtp`); sin usuario ni
contraseña la API no arranca. Hace falta:

1. Una cuenta de Gmail del proyecto, con la verificación en dos pasos activa.
2. Una **contraseña de aplicación** en
   <https://myaccount.google.com/apppasswords> (16 letras).
3. En el `.env`: `TIENDA_CORREO_USUARIO` (la cuenta) y
   `TIENDA_CORREO_CONTRASENA` (la contraseña de aplicación). Otro servidor
   SMTP: `TIENDA_CORREO_HOST` y `TIENDA_CORREO_PUERTO`.

Para probar en la PC sin enviar nada, `TIENDA_CORREO_PROVEEDOR=simulador`: los
correos, con sus enlaces, solo se escriben en los registros de la API (nunca en
un servidor público). Fuera de Docker y en las pruebas, el simulador es el que
va por defecto.

Las credenciales van **solo en el `.env`**, que no se versiona.

## Pago

Con Docker, el pago es con Libélula por defecto
(`TIENDA_PAGO_PROVEEDOR=libelula`): en el `.env` van
`TIENDA_PAGO_LIBELULA_APPKEY` (la de la cuenta) y `TIENDA_PAGO_LIBELULA_EMAIL`
(el correo que exige en cada deuda); sin ellas la API no arranca. Para probar
en la PC sin cobrar, `TIENDA_PAGO_PROVEEDOR=simulador`: el pedido muestra un QR
de prueba y los botones «Simular pago» y «Simular error» (solo existen con el
simulador; nunca en un servidor público). Fuera de Docker y en las pruebas, el
simulador es el que va por defecto. `TIENDA_PAGO_LIBELULA_URL` solo cambia para su entorno
de pruebas. El aviso de pago de Libélula llega a `TIENDA_SITIO_URL`, así que
solo funciona con el sitio publicado; en `localhost` alcanzan la pantalla y la
revisión de cada minuto.

Otras variables opcionales del `.env.example`: `TIENDA_RETIRO_DIRECCION` y
`TIENDA_RETIRO_HORARIO` (dónde y cuándo se retiran los pedidos) y
`TIENDA_DEMO_DIRECTORIO` (el catálogo de demostración).

## Despliegue con Docker

Tres contenedores: MySQL 8.4, la API (perfil `prod`) y el cliente web servido
por nginx, que además reenvía `/api` a la API. Solo nginx publica un puerto.

```bash
cp .env.example .env          # completar contraseñas, clave JWT y administrador
docker compose up -d --build
```

En Windows, Docker Desktop necesita WSL 2: si su motor no arranca, ejecutar
`wsl --install --no-distribution` en una PowerShell de administrador y reiniciar.

El sitio queda en `http://localhost` (o el puerto de `TIENDA_PUERTO`). En el
primer arranque Hibernate crea las tablas y se crea el Administrador inicial con
los datos del `.env`.

- **Datos:** la base y las imágenes subidas viven en volúmenes (`datos-mysql`,
  `imagenes`): sobreviven a `docker compose down`. `docker compose down -v` los
  borra.
- **Actualizar:** `git pull` y `docker compose up -d --build`.
- **Memoria:** cada contenedor tiene su límite (`mem_limit`): unos 1,7 GB en
  total, así que alcanza un servidor de 2 GB con swap. Para ver el uso:
  `docker stats $(docker compose ps -q)`.
- **Registros de la API:** `docker compose logs -f backend`.
- **Salud de la API:** `docker compose ps` la muestra `(healthy)` cuando responde
  y llega a la base; `(unhealthy)` si dejó de hacerlo. El sitio arranca recién
  con la API sana.

### En un servidor, con HTTPS

`docker-compose.nube.yml` suma **Caddy**, que publica los puertos 80 y 443 y saca
solo el certificado de Let's Encrypt: el sitio queda en
`https://TIENDA_DOMINIO`. Sin dominio propio sirve uno de sslip.io armado con
la IP (por ejemplo `203-0-113-10.sslip.io`), que no necesita DNS.

En un Ubuntu nuevo, con el repositorio clonado en `/opt/tienda` y como root:

```bash
bash scripts/instalar-servidor.sh preparar   # sin preguntas; compila las imágenes
```

Crea swap, instala Docker, activa el firewall (22, 80 y 443), arma el `.env` con
claves generadas y compila las imágenes. Después, **desde la PC** (Git Bash en
Windows), con la llave SSH ya autorizada en el servidor:

```bash
bash scripts/copiar-env-al-servidor.sh root@<ip>
```

Copia del `.env` de la PC Libélula, Gmail, la marca, el retiro y los datos del
Administrador (no su contraseña ni las claves de la base y del JWT), apunta el
catálogo de demostración a `/opt/demo-tienda` si está, y corre `completar`, que
pide la contraseña del Administrador la primera vez y levanta los contenedores.
El catálogo se copia antes con `scp -r <carpeta> root@<ip>:/opt/demo-tienda`.
Sin la PC, `completar` también se puede correr en el servidor con el `.env`
completado a mano. Todo se puede volver a correr sin perder nada.

La tienda en la nube **no aparece en Google**: `nube/Caddyfile` manda
`X-Robots-Tag: noindex`. Con un dominio propio, quitar esa línea
(`docs/decisiones.md` → «Buscadores y enlaces compartidos»).

## Documentación

| Archivo | Contenido |
|---|---|
| `docs/flujo-sdd.md` | Cómo se implementa una historia: Scrum + SDD, Definition of Done |
| `docs/decisiones.md` | Las decisiones técnicas tomadas, con su motivo |
| `docs/historias/` | Una historia por archivo (`/sdd:historia HU-XX`) |
| `docs/planes/` | El plan corto de cada historia |
| `docs/proyecto/` | Documentación del proyecto: perfil, metodología, actas de sprint, requisitos |
