#!/usr/bin/env bash
# Vuelve la tienda al estado de un respaldo de scripts/respaldar.sh: reemplaza la base y las imágenes subidas por
# las del archivo (docs/decisiones.md → «Respaldos»). Como root desde la carpeta del proyecto:
#   bash scripts/restaurar.sh /opt/respaldos-tienda/tienda-AAAA-MM-DD_HHMMSS.tar
# Antes respalda lo que hay, por si fue un error. Pide escribir «restaurar» para seguir. Mientras dura, la tienda
# queda fuera de línea (unos segundos con una base chica).
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."

ARCHIVO=$(realpath "${1:?Uso: $0 <respaldo .tar>}")
COMPOSE=(docker compose -f docker-compose.yml)
grep -q '^TIENDA_DOMINIO=.' .env 2>/dev/null && COMPOSE+=(-f docker-compose.nube.yml)

TRABAJO=$(mktemp -d)
trap 'rm -rf "$TRABAJO"' EXIT
tar -C "$TRABAJO" -xf "$ARCHIVO" base.sql.gz imagenes.tar.gz
gunzip -c "$TRABAJO/base.sql.gz" | tail -1 | grep -q 'Dump completed' || { echo "El volcado de la base está incompleto"; exit 1; }
gzip -t "$TRABAJO/imagenes.tar.gz"

echo "Se reemplazan la base y las imágenes por las de $(basename "$ARCHIVO")."
echo "Lo que se cargó después (pedidos, usuarios, productos, fotos) se pierde; antes queda un respaldo de lo actual."
read -rp "Escriba «restaurar» para seguir: " RESPUESTA
[ "$RESPUESTA" = restaurar ] || { echo "No se restauró nada."; exit 1; }

# La base tiene que estar en marcha para respaldar lo actual y para cargar el volcado
"${COMPOSE[@]}" up -d --wait base
bash scripts/respaldar.sh

# Sin la API ni el sitio: nadie escribe mientras se reemplaza
"${COMPOSE[@]}" stop frontend backend

# La base entera de nuevo: sin tablas o filas que no estaban en el respaldo. Los permisos de tienda_app no se
# borran con la base, y el volcado la vuelve a crear
"${COMPOSE[@]}" exec -T base sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -e "DROP DATABASE IF EXISTS tienda_db"'
gunzip -c "$TRABAJO/base.sql.gz" | "${COMPOSE[@]}" exec -T base sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root'

# Las imágenes: se vacía la carpeta y se descomprime el respaldo, con el usuario de la API (dueño de los archivos)
"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint sh backend \
  -c 'find /datos/imagenes -mindepth 1 -delete && tar -C /datos -xzf -' < "$TRABAJO/imagenes.tar.gz"

"${COMPOSE[@]}" up -d
echo "Listo: la tienda volvió a $(basename "$ARCHIVO"). La API tarda un poco en arrancar: docker compose ps"
