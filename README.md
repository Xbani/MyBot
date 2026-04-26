# MyBot Velocity Plugin

MyBot est un plugin Velocity 3.x (Java 21) qui héberge des bots Minecraft simulés côté proxy. Les bots chargent des profils YAML, exécutent des graphes de comportements configurables et peuvent être utilisés comme scénarios de démo pour montrer la navigation, les commandes, la construction de schématiques et le combat.

## Construction

```bash
mvn package
```

Le jar final se trouve sous `target/MyBot-0.1.0-SNAPSHOT.jar`.

## Configuration

Lors du premier lancement, les fichiers suivants sont copiés dans le dossier de données du plugin :

- `config.yml` – paramètres globaux (maxBots, endpoint Velocity, graphes par défaut, etc.).
- `bots/*.yml` – profils individuels des bots.
- `graphs/*.yml` – description des graphes de comportement.
- `schematics/*.schem` – exemples utilisables par les nœuds `BuildSchematic`.

Modifiez/dupliquez ces fichiers puis utilisez `/mybot reload` pour recharger à chaud.

## Commandes

- `/mybot list` – affiche les bots disponibles.
- `/mybot spawn <botId>` – démarre un bot.
- `/mybot kill <botId>` – arrête un bot.
- `/mybot demo <start|stop>` – lance ou arrête le scénario de démo (builder, miner, scout).
- `/mybot reload` – recharge la configuration et réaligne les bots.

## Tests & Démos

`mvn test` exécute les tests unitaires de parsing et du moteur de graphes. Des graphes de démonstration (resource_gatherer, builder, scout) mettent en scène plusieurs bots accomplissant des tâches complémentaires.
