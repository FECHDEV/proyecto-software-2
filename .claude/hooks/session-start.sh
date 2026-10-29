#!/bin/bash
# Prepara una sesión de Claude Code en la nube como la PC del proyecto (CLAUDE.md → Uso de skills): las skills de
# skills-lock.json, playwright-cli con su skill, las dependencias del frontend y las del backend. En una PC no hace
# nada. Se puede correr varias veces: lo que ya está no se vuelve a instalar
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
	exit 0
fi

PROYECTO="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$PROYECTO"

# --- Node 26, el de Docker y el CI (29/10/2026): la imagen trae la 22 -------------------------------------------
# Con nvm, que ya viene en la imagen; la ruta queda para toda la sesión por CLAUDE_ENV_FILE
export NVM_DIR="${NVM_DIR:-/opt/nvm}"
set +u
# shellcheck disable=SC1091
. "$NVM_DIR/nvm.sh"
nvm install 26 >/dev/null 2>&1
nvm use 26 >/dev/null
set -u
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
	echo "export PATH=\"$(dirname "$(nvm which 26)"):\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi

# Las apagadas en skillOverrides de .claude/settings.json (CLAUDE.md → Uso de skills): acá no se enlazan
APAGADAS=" $(python3 -c 'import json, sys
print(" ".join(n for n, v in json.load(open(sys.argv[1]))["skillOverrides"].items() if v == "off"))' .claude/settings.json) "

# --- Skills de skills-lock.json ---------------------------------------------------------------------------------
# La CLI instala la última versión de cada skill y reescribe el lock con sus hashes: se instala en una carpeta aparte,
# para no tocar el lock versionado, y se avisa qué skills quedaron distintas de las de la PC
if [ ! -d .agents/skills ] || [ skills-lock.json -nt .agents/skills ]; then
	TEMPORAL="$(mktemp -d)"
	cp skills-lock.json "$TEMPORAL/"
	(cd "$TEMPORAL" && npx -y skills experimental_install </dev/null >/dev/null 2>&1)
	rm -rf .agents/skills
	mkdir -p .agents
	cp -r "$TEMPORAL/.agents/skills" .agents/skills
	python3 - skills-lock.json "$TEMPORAL/skills-lock.json" >&2 <<'PY' || true
import json, sys
pc = json.load(open(sys.argv[1]))["skills"]
nube = json.load(open(sys.argv[2]))["skills"]
distintas = sorted(n for n in pc if n in nube and pc[n]["computedHash"] != nube[n]["computedHash"])
faltan = sorted(n for n in pc if n not in nube)
if distintas:
	print("Skills con una versión distinta de skills-lock.json: " + ", ".join(distintas))
if faltan:
	print("Skills que no se pudieron instalar: " + ", ".join(faltan))
PY
	rm -rf "$TEMPORAL"
fi

mkdir -p .claude/skills
for carpeta in .agents/skills/*/; do
	nombre="$(basename "$carpeta")"
	case "$APAGADAS" in
		*[[:space:]]"$nombre"[[:space:]]*) rm -f ".claude/skills/$nombre" ;;
		*) [ -e ".claude/skills/$nombre" ] || ln -s "../../.agents/skills/$nombre" ".claude/skills/$nombre" ;;
	esac
done

# --- playwright-cli, con su skill (CLAUDE.md → Verificación en el navegador) -------------------------------------
# El Chromium ya viene en la imagen de la nube (PLAYWRIGHT_BROWSERS_PATH)
if ! command -v playwright-cli >/dev/null 2>&1; then
	npm install -g @playwright/cli@0.1.22 --no-audit --no-fund >/dev/null
fi
if [ ! -e .claude/skills/playwright-cli ]; then
	playwright-cli install --skills >/dev/null
fi
# Sin esto busca Google Chrome, que la imagen no trae; .playwright/ está ignorada
if [ ! -f .playwright/cli.config.json ]; then
	CHROMIUM="$(ls -d "${PLAYWRIGHT_BROWSERS_PATH:-/opt/pw-browsers}"/chromium-*/chrome-linux*/chrome 2>/dev/null | sort -V | tail -1)"
	if [ -n "$CHROMIUM" ]; then
		mkdir -p .playwright
		printf '{"browser":{"browserName":"chromium","launchOptions":{"executablePath":"%s"}}}\n' "$CHROMIUM" \
			> .playwright/cli.config.json
	fi
fi

# --- Dependencias del frontend y del backend ------------------------------------------------------------------
if [ ! -d frontend/node_modules ]; then
	(cd frontend && npm ci --no-audit --no-fund >/dev/null)
fi
(cd backend && ./mvnw -q dependency:go-offline >/dev/null)
