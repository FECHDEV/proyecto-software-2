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
| Base de datos | MySQL 8.0 (H2 en las pruebas) |
| Correo | Gmail por SMTP, con simulador por defecto |
| Pago | Interfaz `IPago`, con simulador por defecto |
| Despliegue | Docker Compose; Caddy con HTTPS en la nube |
| CI | GitHub Actions en cada PR y en cada push a `main` |

## Estructura

```
.
├── AGENTS.md  CLAUDE.md  README.md  .env.example
├── .github/   workflows/pruebas.yml  CODEOWNERS  pull_request_template.md
├── .claude/commands/sdd/   historia.md  analizar.md  cerrar.md
├── docs/      flujo-sdd.md  decisiones.md  historias/  planes/  proyecto/
├── backend/   Spring Boot: JWT, roles, ManejadorErrores, correo SMTP, IPago + simulador
├── frontend/  Angular: features/core/shared, interceptores, guards, tema claro/oscuro, tokens
├── docker-compose.yml  docker-compose.nube.yml  nube/Caddyfile
└── scripts/instalar-servidor.sh
```

## Requisitos

- JDK 21
- Node.js 22 y npm
- MySQL Server 8.0 en `localhost:3306` (solo para levantar la API en local; las
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
tienda.admin.correo=${TIENDA_ADMIN_CORREO:admin@mail.com}
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

## Correo

Por defecto, `TIENDA_CORREO_PROVEEDOR=simulador`: los correos (el de
recuperación de contraseña, con su enlace) solo se escriben en los registros de
la API. Para enviarlos de verdad, con Gmail:

1. Una cuenta de Gmail del proyecto, con la verificación en dos pasos activa.
2. Una **contraseña de aplicación** en
   <https://myaccount.google.com/apppasswords> (16 letras).
3. En el `.env`: `TIENDA_CORREO_PROVEEDOR=smtp`, `TIENDA_CORREO_USUARIO`
   (la cuenta) y `TIENDA_CORREO_CONTRASENA` (la contraseña de aplicación).

Las credenciales van **solo en el `.env`**, que no se versiona.

## Despliegue con Docker

Tres contenedores: MySQL 8.0, la API (perfil `prod`) y el cliente web servido
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
- **Registros de la API:** `docker compose logs -f backend`.

### En un servidor, con HTTPS

`docker-compose.nube.yml` suma **Caddy**, que publica los puertos 80 y 443 y saca
solo el certificado de Let's Encrypt: el sitio queda en
`https://TIENDA_DOMINIO`. Sin dominio propio sirve uno de sslip.io armado con
la IP (`64-176-27-4.sslip.io`), que no necesita DNS.

En un Ubuntu nuevo, con el repositorio clonado y como root:

```bash
bash scripts/instalar-servidor.sh preparar   # sin preguntas; compila las imágenes
bash scripts/instalar-servidor.sh completar  # pide la contraseña del Administrador
```

Crea swap, instala Docker, activa el firewall (22, 80 y 443), arma el `.env` con
claves generadas y levanta los contenedores. Las credenciales de Gmail se
completan a mano en el `.env`. Se puede volver a correr sin perder nada.

## Documentación

| Archivo | Contenido |
|---|---|
| `docs/flujo-sdd.md` | Cómo se implementa una historia: Scrum + SDD, Definition of Done |
| `docs/decisiones.md` | Las decisiones técnicas tomadas, con su motivo |
| `docs/historias/` | Una historia por archivo (`/sdd:historia HU-XX`) |
| `docs/planes/` | El plan corto de cada historia |
| `docs/proyecto/` | Documentación del proyecto: perfil, metodología, actas de sprint, requisitos |
