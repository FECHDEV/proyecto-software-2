#!/usr/bin/env bash
# Deja la aplicación funcionando en un servidor Ubuntu nuevo, con HTTPS. Se corre como root desde la
# carpeta del proyecto, en dos partes o de una vez:
#   bash scripts/instalar-servidor.sh preparar    sin preguntas: swap, Docker, firewall (22, 80, 443),
#                                                 el .env con claves generadas y la compilación
#                                                 de las imágenes (la parte larga)
#   bash scripts/instalar-servidor.sh completar   interactiva: la contraseña del Administrador y los
#                                                 contenedores
#   bash scripts/instalar-servidor.sh             las dos
# Variables opcionales: TIENDA_DOMINIO (por defecto, la IP en sslip.io) y ADMIN_CORREO
# (admin@mail.com). Se puede volver a correr. Las credenciales de Gmail no las pide: se ponen a mano en
# el .env (TIENDA_CORREO_*), y mientras falten los correos van al simulador.
set -euo pipefail
cd "$(dirname "$0")/.."

PARTE=${1:-todo}
paso() { printf '\n\033[1;33m== %s\033[0m\n' "$1"; }
poner() { grep -v "^$1=" .env > .env.tmp || true; printf "%s='%s'\n" "$1" "$2" >> .env.tmp; mv .env.tmp .env; }
valor() { grep "^$1=" .env | cut -d= -f2- | tr -d "'"; }
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.nube.yml)
# Una consulta a la base de la aplicación; sin salida si la base o la tabla todavía no están
consulta() {
  "${COMPOSE[@]}" exec -T base sh -c "mysql -N -u root -p\"\$MYSQL_ROOT_PASSWORD\" tienda_db -e \"$1\"" \
    2>/dev/null || true
}

preparar() {
  # Los archivos pueden venir de Windows con CRLF
  find . -maxdepth 3 -type f \( -name '*.sh' -o -name '.env.example' -o -name 'Caddyfile' \) \
    -not -path './frontend/node_modules/*' -exec sed -i 's/\r$//' {} +

  paso "Memoria de intercambio (swap) de 2 GB"
  if ! swapon --show | grep -q .; then
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
  swapon --show

  paso "Docker"
  command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh
  docker --version

  paso "Firewall: solo SSH, HTTP y HTTPS"
  ufw allow OpenSSH >/dev/null && ufw allow 80/tcp >/dev/null && ufw allow 443/tcp >/dev/null
  ufw --force enable >/dev/null && ufw status | head -8

  paso "Configuración (.env)"
  if [ ! -f .env ]; then
    cp .env.example .env
    IP=$(curl -fsS https://api.ipify.org)
    poner TIENDA_DB_PASSWORD "$(openssl rand -hex 24)"
    poner MYSQL_ROOT_PASSWORD "$(openssl rand -hex 24)"
    poner TIENDA_JWT_SECRETO "$(openssl rand -base64 48 | tr -d '\n')"
    poner TIENDA_DOMINIO "${TIENDA_DOMINIO:-${IP//./-}.sslip.io}"
    poner TIENDA_ADMIN_CORREO "${ADMIN_CORREO:-admin@mail.com}"
    chmod 600 .env
    echo "Listo: .env creado con claves nuevas. Falta la contraseña del Administrador (parte completar)."
  else
    echo ".env ya existe: se deja como está."
  fi

  paso "Imágenes (la primera vez tarda 10-15 minutos: compila el backend y el frontend)"
  # docker-compose.yml exige la contraseña del Administrador hasta para compilar. Todavía no está (la pone
  # la parte completar): un valor provisional solo para este comando, que no se guarda en el .env
  TIENDA_ADMIN_PASSWORD=provisional "${COMPOSE[@]}" build
  TIENDA_ADMIN_PASSWORD=provisional "${COMPOSE[@]}" pull --quiet --ignore-buildable
}

completar() {
  paso "Contraseña del Administrador inicial ($(valor TIENDA_ADMIN_CORREO))"
  if [ -z "$(valor TIENDA_ADMIN_PASSWORD)" ]; then
    while true; do
      read -rsp "Contraseña (mínimo 8, sin comillas simples): " CLAVE; echo
      read -rsp "Repítela: " CLAVE2; echo
      [ "$CLAVE" = "$CLAVE2" ] && [ ${#CLAVE} -ge 8 ] && [[ "$CLAVE" != *"'"* ]] && break
      echo "No coinciden, son menos de 8 caracteres o tienen comillas simples. De nuevo."
    done
    poner TIENDA_ADMIN_PASSWORD "$CLAVE"
    unset CLAVE CLAVE2
  else
    echo "Ya está en el .env."
  fi

  paso "Contenedores"
  "${COMPOSE[@]}" up -d --build

  paso "Esperando a que la API arranque y exista el Administrador"
  # El backend crea al Administrador después de levantar el servidor web: cuando aparece en la base, la API
  # ya responde. Hasta 10 minutos, porque la primera vez Hibernate crea todas las tablas
  LISTO=no
  for _ in $(seq 1 120); do
    if [ "$(consulta "SELECT COUNT(*) FROM usuario WHERE rol = 'ADMINISTRADOR'")" -gt 0 ] 2>/dev/null; then
      LISTO=si; break
    fi
    sleep 5
  done
  if [ "$LISTO" != si ]; then
    echo "El Administrador no apareció en 10 minutos. Revisa: ${COMPOSE[*]} logs backend"
    exit 1
  fi
  echo "API lista y Administrador creado"

  paso "Listo"
  echo "Sitio:   https://$(valor TIENDA_DOMINIO)"
  echo "El certificado HTTPS se pide la primera vez que se abre la dirección (unos segundos)."
  [ "$(valor TIENDA_CORREO_PROVEEDOR)" = smtp ] || echo "Correo: simulador. Para Gmail, completar TIENDA_CORREO_* en el .env y volver a levantar."
}

case "$PARTE" in
  preparar) preparar ;;
  completar) completar ;;
  todo) preparar; completar ;;
  *) echo "Uso: $0 [preparar|completar]"; exit 1 ;;
esac
