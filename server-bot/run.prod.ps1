Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $RootDir ".env"

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    Write-Error "[MyBot] Fichier .env manquant dans $RootDir. Copiez .env et adaptez les variables avant de lancer le proxy."
    exit 1
}

$ProjectName = "mybot-velocity-prod"
$ComposeFile = Join-Path $RootDir "docker-compose.yml"

docker compose --project-name $ProjectName --file $ComposeFile pull
docker compose --project-name $ProjectName --file $ComposeFile up -d
