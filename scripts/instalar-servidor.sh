#!/usr/bin/env bash
# Deja la aplicación funcionando en un servidor Ubuntu nuevo, con HTTPS. Se corre como root desde la
# carpeta del proyecto, en dos partes o de una vez:
#   bash scripts/instalar-servidor.sh preparar    sin preguntas: swap, Docker, firewall (22, 80, 443),
#                                                 el .env con claves generadas y la compilación
#                                                 de las imágenes (la parte larga)
#   bash scripts/instalar-servidor.sh completar   interactiva: la contraseña del Administrador y los
#                                                 contenedores
#   bash scripts/instalar-servidor.sh             las dos
# Variable opcional: TIENDA_DOMINIO (por defecto, la IP en sslip.io). El correo, el nombre y el apellido del
# Administrador los pide la parte completar. Se puede volver a correr. Las credenciales de Gmail y de Libélula no las pide: se
# ponen en el .env a mano o desde la PC con scripts/copiar-env-al-servidor.sh. Sin ellas, completar no levanta la
# tienda.
set -euo pipefail
# Todo lo que crea el script (el .env y su .env.tmp, con los secretos) nace solo para root
umask 077
cd "$(dirname "$0")/.."

PARTE=${1:-todo}
paso() { printf '\n\033[1;33m== %s\033[0m\n' "$1"; }
poner() { grep -v "^$1=" .env > .env.tmp || true; printf "%s='%s'\n" "$1" "$2" >> .env.tmp; mv .env.tmp .env; }
valor() { grep "^$1=" .env | cut -d= -f2- | tr -d "'\""; }
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.nube.yml)
# Una consulta a la base de la aplicación; sin salida si la base o la tabla todavía no están
consulta() {
  # Con MYSQL_PWD y no -p: la contraseña no queda a la vista en la lista de procesos del contenedor
  "${COMPOSE[@]}" exec -T base sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -N -u root tienda_db -e \"$1\"" \
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
  [ -f .env ] || cp .env.example .env
  # Solo lo que falta: si una corrida anterior se cortó a la mitad (sin red para pedir la IP), la siguiente completa
  # sin cambiar las claves ya puestas, que la base ya usa (revisión total, 26/10/2026)
  [ -n "$(valor TIENDA_DB_PASSWORD)" ] || poner TIENDA_DB_PASSWORD "$(openssl rand -hex 24)"
  [ -n "$(valor MYSQL_ROOT_PASSWORD)" ] || poner MYSQL_ROOT_PASSWORD "$(openssl rand -hex 24)"
  [ -n "$(valor TIENDA_JWT_SECRETO)" ] || poner TIENDA_JWT_SECRETO "$(openssl rand -base64 48 | tr -d '\n')"
  if [ -z "$(valor TIENDA_DOMINIO)" ]; then
    if [ -z "${TIENDA_DOMINIO:-}" ]; then
      IP=$(curl -fsS https://api.ipify.org)
      TIENDA_DOMINIO="${IP//./-}.sslip.io"
    fi
    poner TIENDA_DOMINIO "$TIENDA_DOMINIO"
  fi
  chmod 600 .env
  echo "Listo: .env con sus claves. Faltan los datos del Administrador (parte completar)."

  paso "Imágenes (la primera vez tarda 10-15 minutos: compila el backend y el frontend)"
  # docker-compose.yml exige el correo y la contraseña del Administrador hasta para compilar. Todavía no
  # están (los pone la parte completar): valores provisionales solo para estos comandos, que no se guardan
  export TIENDA_ADMIN_CORREO=provisional@localhost TIENDA_ADMIN_PASSWORD=provisional
  "${COMPOSE[@]}" build
  "${COMPOSE[@]}" pull --quiet --ignore-buildable
  unset TIENDA_ADMIN_CORREO TIENDA_ADMIN_PASSWORD
}

completar() {
  paso "Administrador inicial"
  if [ -z "$(valor TIENDA_ADMIN_CORREO)" ]; then
    while true; do
      read -rp "Correo: " CORREO
      [[ "$CORREO" == ?*@?*.?* && "$CORREO" != *[[:space:]\']* ]] && break
      echo "No parece un correo (sin espacios ni comillas simples). De nuevo."
    done
    read -rp "Nombre (vacío: Administrador): " NOMBRE
    read -rp "Apellido (vacío: Inicial): " APELLIDO
    poner TIENDA_ADMIN_CORREO "$CORREO"
    # Sin comillas simples: poner las usa para encerrar el valor
    poner TIENDA_ADMIN_NOMBRE "${NOMBRE//\'/}"
    poner TIENDA_ADMIN_APELLIDO "${APELLIDO//\'/}"
  else
    echo "Correo: $(valor TIENDA_ADMIN_CORREO) (ya está en el .env)"
  fi

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

  # Con Libélula (el proveedor por defecto) y sin su appkey o su correo, la API no arranca
  if [ "$(valor TIENDA_PAGO_PROVEEDOR)" != simulador ] &&     { [ -z "$(valor TIENDA_PAGO_LIBELULA_APPKEY)" ] || [ -z "$(valor TIENDA_PAGO_LIBELULA_EMAIL)" ]; }; then
    echo "Faltan TIENDA_PAGO_LIBELULA_APPKEY o TIENDA_PAGO_LIBELULA_EMAIL en el .env: completarlos y volver a"
    echo "correr '$0 completar'. Solo para probar, sin cobrar: TIENDA_PAGO_PROVEEDOR=simulador."
    exit 1
  fi

  # Con Gmail (el correo por defecto) y sin su usuario o su contraseña, la API tampoco arranca
  if [ "$(valor TIENDA_CORREO_PROVEEDOR)" != simulador ] && \
    { [ -z "$(valor TIENDA_CORREO_USUARIO)" ] || [ -z "$(valor TIENDA_CORREO_CONTRASENA)" ]; }; then
    echo "Faltan TIENDA_CORREO_USUARIO o TIENDA_CORREO_CONTRASENA en el .env: completarlos y volver a correr"
    echo "'$0 completar'. Solo para probar: TIENDA_CORREO_PROVEEDOR=simulador (escribe en el log los enlaces de"
    echo "recuperación: nunca en un servidor público)."
    exit 1
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
  [ "$(valor TIENDA_CORREO_PROVEEDOR)" != simulador ] || echo "Correo: SIMULADOR. Los enlaces de recuperación quedan en el log: solo para pruebas."
  [ "$(valor TIENDA_PAGO_PROVEEDOR)" != simulador ] || echo "Pago: SIMULADOR. Cualquier cliente puede marcar pagado su pedido: solo para pruebas."
}

case "$PARTE" in
  preparar) preparar ;;
  completar) completar ;;
  todo) preparar; completar ;;
  *) echo "Uso: $0 [preparar|completar]"; exit 1 ;;
esac
