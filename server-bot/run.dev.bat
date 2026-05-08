@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "ENV_FILE=%ROOT_DIR%.env"
set "PROJECT_NAME=mybot-velocity-dev"
set "COMPOSE_FILE=%ROOT_DIR%docker-compose.yml"
set "DATA_DIR=%ROOT_DIR%data"

if not exist "%ENV_FILE%" (
  echo [MyBot] Fichier .env manquant dans %ROOT_DIR%. Copiez .env et adaptez les variables avant de lancer le proxy. 1>&2
  exit /b 1
)

echo [MyBot] Stopping existing dev proxy if it is running...
docker compose --project-name "%PROJECT_NAME%" --file "%COMPOSE_FILE%" down
if errorlevel 1 exit /b %ERRORLEVEL%

if exist "%DATA_DIR%" (
  echo [MyBot] Resetting dev server data: %DATA_DIR%
  rmdir /s /q "%DATA_DIR%"
  if errorlevel 1 exit /b %ERRORLEVEL%
)

mkdir "%DATA_DIR%"
if errorlevel 1 exit /b %ERRORLEVEL%

docker compose --project-name "%PROJECT_NAME%" --file "%COMPOSE_FILE%" up -d --build
exit /b %ERRORLEVEL%
