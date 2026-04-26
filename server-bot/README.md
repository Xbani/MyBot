# Velocity Test Proxy Image

Ce dossier fournit une image Docker de test pour Velocity, inspirée des fichiers de configuration existants (`velocity.toml` et `hg-pvp-ipforwarding.secret`). Les templates sont rendus via `envsubst` à partir des variables définies dans `.env`.

## Arborescence

- `server-bot/.env` : variables partagées entre la construction et l\'exécution (port, URLs backend, secret, etc.).
- `server-bot/docker-compose.yml` : exemple prêt à l\'emploi pour builder et lancer le proxy.
- `server-bot/image/Dockerfile` : construit une image basée sur Temurin 21, télécharge le jar Velocity et copie les scripts.
- `server-bot/image/server/start.sh` : script d\'entrée qui rend les templates puis lance `velocity-proxy.jar`.
- `server-bot/image/templates/*.tpl` : templates des fichiers de configuration (velocity.toml + secret).

## Pré-requis

1. Choisir une URL valide pour le jar Velocity et la renseigner dans `.env` via `VELOCITY_JAR_URL` (voir https://papermc.io/downloads).
2. Vérifier/adapter les IP backend (`SERVER_IP_*`) et le secret `VELOCITY_SECRET`.

## Construction & exécution

```bash
cd server-bot
docker compose build
docker compose up -d
```

Sous Windows, vous pouvez utiliser les wrappers fournis :

```powershell
.\run.dev.ps1
```

```bat
run.dev.bat
```

Les wrappers Windows lancent le conteneur en arrière-plan avec `up -d --build`.

Les fichiers générés (config + secret + logs) sont stockés dans le volume Docker `velocity-data`.

Pour régénérer la configuration après modification de `.env` :

```bash
docker compose down
docker compose up -d --build
```

Pour le mode prod :

```bash
./run.prod.sh
```

```powershell
.\run.prod.ps1
```

```bat
run.prod.bat
```

## Personnalisation

- Ajuster le MOTD (`MOTD`) ou les flags Velocity directement depuis `.env`.
- Ajouter d'autres templates dans `image/templates/*.tpl` (ils seront automatiquement rendus dans `/data`).
- Si vous souhaitez injecter d'autres fichiers non template (ressources, plugins...), copiez-les dans `image/server` et adaptez `Dockerfile`.
