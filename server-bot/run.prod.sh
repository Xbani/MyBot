#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
ENV_FILE="$ROOT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "[MyBot] Fichier .env manquant dans $ROOT_DIR. Copiez .env et adaptez les variables avant de lancer le proxy." >&2
  exit 1
fi

COMPOSE="docker compose"
PROJECT_NAME=mybot-velocity-prod

${COMPOSE} --project-name ${PROJECT_NAME} --file "$ROOT_DIR/docker-compose.yml" pull
${COMPOSE} --project-name ${PROJECT_NAME} --file "$ROOT_DIR/docker-compose.yml" up -d
