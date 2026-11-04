#!/usr/bin/env bash
# Respaldo de la tienda: la base (mysqldump) y las imágenes subidas, en un solo archivo
# tienda-AAAA-MM-DD_HHMMSS.tar en /opt/respaldos-tienda. Borra los de más de 14 días, solo después de que el nuevo
# salió bien (docs/decisiones.md → «Respaldos»). En el servidor lo corre cron todos los días (lo instala
# instalar-servidor.sh completar); a mano, como root desde la carpeta del proyecto:
#   bash scripts/respaldar.sh
# Variables opcionales: TIENDA_RESPALDOS (la carpeta) y TIENDA_RESPALDOS_DIAS (cuántos días se guardan).
# Para bajarlos a la PC (PowerShell): scp "root@<ip>:/opt/respaldos-tienda/tienda-*.tar" .\respaldos\
set -euo pipefail
# Los respaldos tienen los datos de los clientes: solo para root
umask 077
cd "$(dirname "$0")/.."

CARPETA=${TIENDA_RESPALDOS:-/opt/respaldos-tienda}
DIAS=${TIENDA_RESPALDOS_DIAS:-14}
# Sin los avisos de progreso de Docker: el registro de cron queda con una línea por día
COMPOSE=(docker compose --progress quiet -f docker-compose.yml)
# En el servidor, con Caddy: el mismo proyecto que levantó instalar-servidor.sh
grep -q '^TIENDA_DOMINIO=.' .env 2>/dev/null && COMPOSE+=(-f docker-compose.nube.yml)

# Con los segundos: el respaldo que hace restaurar.sh justo antes nunca pisa al que se va a restaurar
NOMBRE=tienda-$(date +%Y-%m-%d_%H%M%S)
mkdir -p "$CARPETA"
TRABAJO=$(mktemp -d "$CARPETA/.armando-XXXXXX")
trap 'rm -rf "$TRABAJO"' EXIT

# La base: --single-transaction lee una foto fija sin bloquear la tienda. Con MYSQL_PWD y no -p, la contraseña no
# queda a la vista en la lista de procesos. --databases deja el CREATE DATABASE y el USE para restaurar
"${COMPOSE[@]}" exec -T base sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -u root --single-transaction \
  --quick --routines --triggers --no-tablespaces --databases tienda_db' < /dev/null | gzip > "$TRABAJO/base.sql.gz"
# mysqldump escribe esta línea al final solo si terminó entero
gunzip -c "$TRABAJO/base.sql.gz" | tail -1 | grep -q 'Dump completed' \
  || { echo "$(date '+%F %T') ERROR: el volcado de la base quedó incompleto" >&2; exit 1; }

# Las imágenes: con la imagen de la API, que monta el volumen aunque el contenedor esté parado
"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint tar backend -C /datos -czf - imagenes < /dev/null > "$TRABAJO/imagenes.tar.gz"
gzip -t "$TRABAJO/imagenes.tar.gz"

# Un solo archivo; el nombre final recién al terminar, así un respaldo cortado nunca parece bueno
tar -C "$TRABAJO" -cf "$TRABAJO/$NOMBRE.tar" base.sql.gz imagenes.tar.gz
[ ! -e "$CARPETA/$NOMBRE.tar" ] || { echo "$(date '+%F %T') ERROR: ya existe $NOMBRE.tar" >&2; exit 1; }
mv "$TRABAJO/$NOMBRE.tar" "$CARPETA/$NOMBRE.tar"

find "$CARPETA" -maxdepth 1 -name 'tienda-*.tar' -mtime +"$((DIAS - 1))" -delete
echo "$(date '+%F %T') Respaldo $CARPETA/$NOMBRE.tar ($(du -h "$CARPETA/$NOMBRE.tar" | cut -f1));" \
  "quedan $(find "$CARPETA" -maxdepth 1 -name 'tienda-*.tar' | wc -l)"
