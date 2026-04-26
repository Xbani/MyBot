#!/bin/sh
set -eu

DATA_DIR=${DATA_DIR:-/data}
TEMPLATE_DIR=${TEMPLATE_DIR:-/opt/templates}
PLUGIN_DIR=${PLUGIN_DIR:-/opt/plugins}
VELOCITY_JAR=/opt/velocity/velocity-proxy.jar
JAVA_OPTS=${JAVA_OPTS:-"-Xms512M -Xmx512M"}

mkdir -p "$DATA_DIR" "$DATA_DIR/plugins"

if ls "$TEMPLATE_DIR"/*.tpl >/dev/null 2>&1; then
  for template in "$TEMPLATE_DIR"/*.tpl; do
    filename=$(basename "$template" .tpl)
    target="$DATA_DIR/$filename"
    echo "[MyBot] Rendering template $filename"
    envsubst < "$template" > "$target"
  done
fi

if ls "$PLUGIN_DIR"/*.jar >/dev/null 2>&1; then
  for plugin in "$PLUGIN_DIR"/*.jar; do
    echo "[MyBot] Installing plugin $(basename "$plugin")"
    cp -f "$plugin" "$DATA_DIR/plugins/"
  done
fi

cd "$DATA_DIR"
exec java $JAVA_OPTS -jar "$VELOCITY_JAR"
