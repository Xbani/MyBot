@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "ENV_FILE=%ROOT_DIR%.env"
set "PROJECT_NAME=mybot-velocity-dev"
set "COMPOSE_FILE=%ROOT_DIR%docker-compose.yml"

if not exist "%ENV_FILE%" (
  echo [MyBot] Fichier .env manquant dans %ROOT_DIR%. Copiez .env et adaptez les variables avant de lancer le proxy. 1>&2
  exit /b 1
)

docker compose --project-name "%PROJECT_NAME%" --file "%COMPOSE_FILE%" up -d --build
exit /b %ERRORLEVEL%
