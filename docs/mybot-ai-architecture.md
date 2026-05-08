# MyBot HungerGames AI

The bot AI is intentionally split into small systems under `com.mybot.velocity.behavior`:

- `BotController` coordinates one physics tick and gates behavior by lifecycle state.
- `BotGameStateDetector` detects lobby, spectator, pregame, invincibility, active HG, late game, and final fight from proxy server name, game mode, inventory, player count, and local match timing hints.
- `BotPersonality` and `BotSkillProfile` load stable per-bot traits from config.
- `BotMemory` and `ReputationMemory` keep time-limited player memory for attackers, teammates, betrayers, weak/strong impressions, and last known positions.
- `BotGamePlan` tracks the broad HG phase.
- `BotInvincibilityPlan` runs a concrete starter route during invincibility: leave spawn, find/mine wood, craft starter tools, find/mine stone, craft a stone sword, then scout. It uses `BotResourceScanner` so block-state ranges can be tuned from config when the server/version mapping differs.
- `BotDecisionSystem` scores fight, flee, heal, loot, team, follow, and idle choices, adds skill/personality noise, and commits to a decision for a short reaction window.
- `HumanInputLayer` turns decisions into imperfect player-like inputs: delayed slot switches, smooth rotations, aim error, missed swings, strafing quality, jump timing, and plausible melee checks.

Server hooks that are not available from the Velocity protocol client yet should be wired into `BotWorldState` and consumed through `BotGameStateDetector`. Good future hook points are accurate HG match time, feast time, border radius, alive player count, death/kill events, chest locations, dropped item entities, and exact item registry ids.

Combat is deliberately conservative. `HumanInputLayer.tryAttack` validates target range, selected/best weapon presence, line of sight, and per-skill attack timing before sending an attack packet. Low-skill bots can swing late, miss, hesitate, panic, or delay inventory management; high-skill bots reduce those mistakes without bypassing normal reach or visibility checks.

Crafting support is adapter-based. Mining uses normal `START_DIGGING` / `FINISH_DIGGING` packets. Recipe crafting can use `ServerboundPlaceRecipePacket` when exact recipe ids are configured; otherwise the invincibility plan still advances its high-level intent and logs the missing recipe id at debug level. The HG plugin should eventually provide exact block/item/recipe ids for the active server version.
