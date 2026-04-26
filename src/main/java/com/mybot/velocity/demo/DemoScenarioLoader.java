package com.mybot.velocity.demo;

import com.mybot.velocity.bot.BotManager;
import com.mybot.velocity.config.BotDefinition;
import com.mybot.velocity.config.ConfigService;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles curated demo scenarios to showcase MyBot abilities.
 */
public final class DemoScenarioLoader {

    private static final String CONFIG_FILE = "hg-bots.yml";
    private static final String BEHAVIOR_FILE = "hg-behavior.yml";

    private final BotManager botManager;
    private final ConfigService configService;
    private final Logger logger;
    private final BotSkinRegistry skinRegistry;
    private final HgBotNameGenerator nameGenerator = new HgBotNameGenerator(new Random());
    private final Yaml yaml = new Yaml();
    private final List<String> spawnedBotIds = new CopyOnWriteArrayList<>();
    private final List<String> spawnedUsernames = new CopyOnWriteArrayList<>();

    public DemoScenarioLoader(BotManager botManager, ConfigService configService, Logger logger, BotSkinRegistry skinRegistry) {
        this.botManager = botManager;
        this.configService = configService;
        this.logger = logger;
        this.skinRegistry = Objects.requireNonNull(skinRegistry, "skinRegistry");
    }

    public CompletableFuture<Void> startDemo() {
        if (!configService.globalConfig().demoEnabled()) {
            logger.warn("Demo scenario requested but demo-enabled=false");
            return CompletableFuture.completedFuture(null);
        }
        HgBotConfig config = loadConfig();
        List<String> usernames = nameGenerator.generate(config.count(), config.names());
        logger.info("Spawning HG-Bots {}", usernames);
        return CompletableFuture.allOf(usernames.stream()
                .map(username -> {
                    registerSkin(username, config);
                    BotDefinition definition = toDefinition(username, config);
                    spawnedBotIds.add(definition.id());
                    spawnedUsernames.add(username);
                    return botManager.spawnBot(definition);
                })
                .toArray(CompletableFuture[]::new));
    }

    public void stopDemo() {
        logger.info("Stopping HG-Bots");
        spawnedBotIds.forEach(bot -> botManager.despawnBot(bot, "demo-stop"));
        spawnedBotIds.clear();
        spawnedUsernames.forEach(skinRegistry::unregister);
        spawnedUsernames.clear();
    }

    private BotDefinition toDefinition(String username, HgBotConfig config) {
        Map<String, Object> traits = new java.util.LinkedHashMap<>(loadBehaviorTraits());
        traits.put("type", "HG-Bot");
        return new BotDefinition(
                "hg_bot_" + username.substring("Bot_".length()).toLowerCase(),
                username,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                config.initialServer(),
                config.assignedGraph(),
                List.of(),
                List.of("server", "say"),
                traits
        );
    }

    private HgBotConfig loadConfig() {
        try (Reader reader = Files.newBufferedReader(configService.resolve(CONFIG_FILE))) {
            Map<String, Object> map = asMap(yaml.load(reader));
            int count = number(map.get("count"), 2).intValue();
            String initialServer = Objects.toString(map.getOrDefault("initial-server", "hg2"));
            String assignedGraph = Objects.toString(map.getOrDefault("assigned-graph", "hg_bot"));
            List<String> names = stringList(map.get("names"));
            java.util.Optional<HgBotSkin> defaultSkin = parseSkin(asMap(map.get("skin")), "skin");
            Map<String, HgBotSkin> skins = parseSkins(asMap(map.get("skins")));
            return new HgBotConfig(count, initialServer, assignedGraph, names, defaultSkin.orElse(null), skins);
        } catch (IOException ex) {
            logger.warn("Unable to read {}, using defaults", CONFIG_FILE, ex);
            return new HgBotConfig(2, "hg2", "hg_bot", HgBotNameGenerator.DEFAULT_NAMES, null, Map.of());
        }
    }

    private void registerSkin(String username, HgBotConfig config) {
        String baseName = username.startsWith("Bot_") ? username.substring("Bot_".length()) : username;
        HgBotSkin skin = config.skins().getOrDefault(baseName, config.defaultSkin());
        if (skin != null && skin.isComplete()) {
            skinRegistry.register(username, skin);
        }
    }

    private Map<String, HgBotSkin> parseSkins(Map<String, Object> rawSkins) {
        Map<String, HgBotSkin> skins = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawSkins.entrySet()) {
            parseSkin(asMap(entry.getValue()), "skins." + entry.getKey())
                    .ifPresent(skin -> skins.put(entry.getKey(), skin));
        }
        return skins;
    }

    private java.util.Optional<HgBotSkin> parseSkin(Map<String, Object> rawSkin, String path) {
        java.util.Optional<HgBotSkin> skin = HgBotSkin.fromMap(rawSkin);
        if (skin.isEmpty() && !rawSkin.isEmpty()) {
            logger.warn("Ignoring incomplete HG-Bot skin config at {}: signed value and signature are required", path);
        }
        return skin;
    }

    private Map<String, Object> loadBehaviorTraits() {
        try (Reader reader = Files.newBufferedReader(configService.resolve(BEHAVIOR_FILE))) {
            return asMap(yaml.load(reader));
        } catch (IOException ex) {
            logger.warn("Unable to read {}, using behavior defaults", BEHAVIOR_FILE, ex);
            return Map.of();
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return HgBotNameGenerator.DEFAULT_NAMES;
    }

    private Number number(Object value, Number defaultValue) {
        if (value instanceof Number number) {
            return number;
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private record HgBotConfig(int count, String initialServer, String assignedGraph, List<String> names,
                               HgBotSkin defaultSkin, Map<String, HgBotSkin> skins) { }
}
