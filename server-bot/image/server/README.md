# Contenu du dossier `image/server`

- `start.sh` : script d\'entrée utilisé par l\'image.
- `velocity-proxy.jar` : jar Velocity téléchargé automatiquement pendant la construction (via `VELOCITY_JAR_URL`).

Si vous préférez embarquer un jar personnalisé ou hors ligne, téléchargez-le manuellement puis placez-le ici avant le build en le nommant `velocity-proxy.jar`. Modifiez ensuite le `Dockerfile` pour commenter l\'étape de téléchargement si nécessaire.
