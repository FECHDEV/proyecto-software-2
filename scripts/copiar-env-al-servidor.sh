#!/usr/bin/env bash
# Copia al .env del servidor los datos del .env de esta PC y levanta la tienda (instalar-servidor.sh completar). Se
# corre desde la PC, en la carpeta del proyecto (en Windows, con Git Bash), después de la parte preparar:
#   bash scripts/copiar-env-al-servidor.sh root@<ip>
# Copia: Libélula, Gmail, la marca, el retiro y el correo, nombre y apellido del Administrador. No copia la contraseña
# del Administrador (la pide completar la primera vez) ni las claves de la base y del JWT (el servidor tiene las
# suyas). Si en el servidor está /opt/demo-tienda, apunta ahí el catálogo de demostración. Se puede volver a correr
set -euo pipefail
cd "$(dirname "$0")/.."

SERVIDOR=${1:?Uso: $0 root@<ip>}
VARIABLES=(TIENDA_PAGO_PROVEEDOR TIENDA_PAGO_LIBELULA_APPKEY TIENDA_PAGO_LIBELULA_EMAIL
  TIENDA_CORREO_PROVEEDOR TIENDA_CORREO_USUARIO TIENDA_CORREO_CONTRASENA
  TIENDA_MARCA TIENDA_RETIRO_DIRECCION TIENDA_RETIRO_HORARIO
  TIENDA_ADMIN_CORREO TIENDA_ADMIN_NOMBRE TIENDA_ADMIN_APELLIDO)

# La última línea de cada variable, sin los \r de Windows; las vacías no se copian
LINEAS=$(for v in "${VARIABLES[@]}"; do grep "^$v=" .env | tail -1 | tr -d '\r' | grep -v "^$v=$" || true; done)
echo "Se copian: $(cut -d= -f1 <<< "$LINEAS" | tr '\n' ' ')"

# Cada variable reemplaza a la que hubiera en el servidor; el .env queda solo para root
printf '%s\n' "$LINEAS" | ssh "$SERVIDOR" '
  set -e
  cd /opt/tienda
  poner() {
    grep -v "^${1%%=*}=" .env > .env.tmp || true
    printf "%s\n" "$1" >> .env.tmp
    mv .env.tmp .env
  }
  while IFS= read -r linea; do poner "$linea"; done
  [ -d /opt/demo-tienda ] && poner TIENDA_DEMO_DIRECTORIO=/opt/demo-tienda
  chmod 600 .env
  echo "Listo: .env del servidor completado"'

# La primera vez pide la contraseña del Administrador; después solo vuelve a levantar los contenedores
ssh -t "$SERVIDOR" 'cd /opt/tienda && bash scripts/instalar-servidor.sh completar'
