package com.mybot.velocity.config;

import com.mybot.velocity.graph.GraphNodeType;
import com.mybot.velocity.util.FileWatcher;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.yaml.snakeyaml.Yaml;

/**
 * Centralized loader for config.yml, bot definitions, graph definitions, and schematics metadata.
 */
public final class ConfigService implements AutoCloseable {

    private static final String[] SAMPLE_BOT_FILES = {};
    private static final String[] SAMPLE_GRAPH_FILES = {
            "graphs/hg_bot.yml"
    };
    private static final String[] SAMPLE_SCHEMATICS = {
            "schematics/demo_bridge.schem",
            "schematics/demo_tower.schem"
    };

    private final Path dataDirectory;
    private final Logger logger;
    private final Yaml yaml = new Yaml();
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();
    private FileWatcher watcher;

    private volatile GlobalConfig globalConfig;
    private volatile Map<String, BotDefinition> bots = Map.of();
    private volatile Map<String, GraphDefinition> graphs = Map.of();

    public ConfigService(Path dataDirectory, Logger logger) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void initialize() throws IOException {
        Files.createDirectories(dataDirectory);
        copyDefaults();
        reloadAll();
        initWatcher();
    }

    private void initWatcher() {
        try {
            watcher = new FileWatcher(logger);
            watcher.register(resolveDataPath(configFolder()));
            watcher.register(resolveDataPath(globalConfig().dataFolders().botsDir()));
            watcher.register(resolveDataPath(globalConfig().dataFolders().graphsDir()));
            watcher.addListener(path -> {
                logger.info("Detected config change at {}", path);
                try {
                    reloadAll();
                    reloadListeners.forEach(Runnable::run);
                } catch (IOException e) {
                    logger.error("Failed to reload configuration", e);
                }
            });
            watcher.start();
        } catch (IOException ex) {
            logger.warn("Unable to start file watcher, hot reload disabled", ex);
        }
    }

    private void copyDefaults() throws IOException {
        copyResource("config.yml", resolveDataPath("config.yml"));
        copyResource("hg-bots.yml", resolveDataPath("hg-bots.yml"));
        copyResource("hg-behavior.yml", resolveDataPath("hg-behavior.yml"));
        mergeBundledDefaults("hg-behavior.yml", resolveDataPath("hg-behavior.yml"));
        for (String sample : SAMPLE_BOT_FILES) {
            copyResource(sample, resolveDataPath(sample));
        }
        for (String sample : SAMPLE_GRAPH_FILES) {
            copyResource(sample, resolveDataPath(sample));
        }
        for (String sample : SAMPLE_SCHEMATICS) {
            copyResource(sample, resolveDataPath(sample));
        }
    }

    private void mergeBundledDefaults(String resource, Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return;
            }
            Map<String, Object> defaults = asMap(yaml.load(stream));
            Map<String, Object> current;
            try (Reader reader = Files.newBufferedReader(target)) {
                current = asMap(yaml.load(reader));
            }
            boolean changed = false;
            for (Map.Entry<String, Object> entry : defaults.entrySet()) {
                if (!current.containsKey(entry.getKey())) {
                    current.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            if (changed) {
                try (var writer = Files.newBufferedWriter(target)) {
                    yaml.dump(current, writer);
                }
                logger.info("Merged missing default keys into {}", target);
            }
        }
    }

    private void copyResource(String resource, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                logger.warn("Missing bundled resource {}", resource);
                return;
            }
            Files.copy(stream, target);
        }
    }

    private Path resolveDataPath(String relative) {
        return dataDirectory.resolve(relative).normalize();
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public Path resolve(String relative) {
        return resolveDataPath(relative);
    }

    private String configFolder() {
        return ".";
    }

    public synchronized void reloadAll() throws IOException {
        this.globalConfig = loadGlobalConfig(resolveDataPath("config.yml"));
        this.bots = loadBots();
        this.graphs = loadGraphs();
        logger.info("Loaded config: {} bots, {} graphs", bots.size(), graphs.size());
    }

    private GlobalConfig loadGlobalConfig(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> map = asMap(yaml.load(reader));
            int maxBots = number(map.get("max-bots"), 10).intValue();
            Map<String, Object> endpoint = asMap(map.get("velocity-endpoint"));
            String host = endpoint.getOrDefault("host", "127.0.0.1").toString();
            int port = number(endpoint.get("port"), 25577).intValue();
            Map<String, Object> authMap = asMap(map.get("auth"));
            String mode = Optional.ofNullable(authMap.get("mode")).map(Object::toString).orElse("offline");
            String email = Optional.ofNullable(authMap.get("mojang-email")).map(Object::toString).orElse("");
            String password = Optional.ofNullable(authMap.get("mojang-password")).map(Object::toString).orElse("");
            Map<String, Object> logging = asMap(map.get("logging"));
            String level = Optional.ofNullable(logging.get("level")).map(Object::toString).orElse("INFO");
            boolean audits = bool(logging.get("command-audits"), true);
            Map<String, Object> schedulers = asMap(map.get("schedulers"));
            Duration botTick = Duration.ofMillis(number(schedulers.get("bot-tick-millis"), 50L).longValue());
            Duration graphTick = Duration.ofMillis(number(schedulers.get("graph-tick-millis"), 200L).longValue());
            Map<String, Object> folders = asMap(map.get("data-folders"));
            String botsDir = folders.getOrDefault("bots", "bots").toString();
            String graphsDir = folders.getOrDefault("graphs", "graphs").toString();
            String schematicsDir = folders.getOrDefault("schematics", "schematics").toString();
            Map<String, Object> dashboard = asMap(map.get("dashboard"));
            boolean dashboardEnabled = bool(dashboard.get("enabled"), true);
            String dashboardHost = Optional.ofNullable(dashboard.get("host")).map(Object::toString).orElse("127.0.0.1");
            int dashboardPort = number(dashboard.get("port"), 8080).intValue();
            String dashboardPublicUrl = Optional.ofNullable(dashboard.get("public-url")).map(Object::toString).orElse("");
            String dashboardToken = Optional.ofNullable(dashboard.get("auth-token")).map(Object::toString).orElse("");
            List<GlobalConfig.DashboardSource> dashboardSources = dashboardSources(dashboard.get("sources"));

            return new GlobalConfig(
                    maxBots,
                    new java.net.InetSocketAddress(host, port),
                    new GlobalConfig.AuthConfig(mode, email, password),
                    Optional.ofNullable(map.get("default-graph")).map(Object::toString).orElse("builder"),
                    bool(map.get("demo-enabled"), true),
                    new GlobalConfig.LoggingConfig(level, audits),
                    new GlobalConfig.SchedulerConfig(botTick, graphTick),
                    new GlobalConfig.DataFolderConfig(botsDir, graphsDir, schematicsDir),
                    new GlobalConfig.DashboardConfig(dashboardEnabled, dashboardHost, dashboardPort,
                            dashboardPublicUrl, dashboardToken, dashboardSources)
            );
        }
    }

    private List<GlobalConfig.DashboardSource> dashboardSources(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::asMap)
                .map(source -> new GlobalConfig.DashboardSource(
                        Optional.ofNullable(source.get("id")).map(Object::toString).orElse("proxy"),
                        Optional.ofNullable(source.get("name")).map(Object::toString).orElse("Proxy"),
                        Optional.ofNullable(source.get("base-url")).map(Object::toString).orElse(""),
                        Optional.ofNullable(source.get("token")).map(Object::toString).orElse("")
                ))
                .filter(source -> !source.id().isBlank() && !source.baseUrl().isBlank())
                .toList();
    }

    private Map<String, BotDefinition> loadBots() throws IOException {
        Map<String, BotDefinition> loaded = new LinkedHashMap<>();
        Path dir = resolveDataPath(globalConfig.dataFolders().botsDir());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        try (var files = Files.list(dir)) {
            files.filter(path -> path.toString().endsWith(".yml")).forEach(path -> {
                try {
                    BotDefinition def = parseBot(path);
                    loaded.put(def.id(), def);
                } catch (Exception ex) {
                    logger.error("Failed to parse bot definition {}", path, ex);
                }
            });
        }
        return Collections.unmodifiableMap(loaded);
    }

    private BotDefinition parseBot(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> map = asMap(yaml.load(reader));
            String id = Optional.ofNullable(map.get("id")).map(Object::toString)
                    .orElseGet(() -> file.getFileName().toString().replace(".yml", ""));
            String username = Optional.ofNullable(map.get("username")).map(Object::toString).orElse(id);
            UUID uuid = Optional.ofNullable(map.get("uuid")).map(Object::toString).map(UUID::fromString).orElse(null);
            String initialServer = Optional.ofNullable(map.get("initial-server")).map(Object::toString).orElse("lobby");
            String assignedGraph = Optional.ofNullable(map.get("assigned-graph")).map(Object::toString)
                    .orElse(globalConfig.defaultGraph());
            List<String> inventory = stringList(map.get("inventory-preset"));
            List<String> commands = stringList(map.get("command-whitelist"));
            Map<String, Object> traits = asMap(map.get("traits"));
            return new BotDefinition(id, username, uuid, initialServer, assignedGraph, inventory, commands, traits);
        }
    }

    private Map<String, GraphDefinition> loadGraphs() throws IOException {
        Map<String, GraphDefinition> loaded = new HashMap<>();
        Path dir = resolveDataPath(globalConfig.dataFolders().graphsDir());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        try (var files = Files.list(dir)) {
            files.filter(path -> path.toString().endsWith(".yml")).forEach(path -> {
                try {
                    GraphDefinition graph = parseGraph(path);
                    loaded.put(graph.id(), graph);
                } catch (Exception ex) {
                    logger.error("Failed to parse graph {}", path, ex);
                }
            });
        }
        return Collections.unmodifiableMap(loaded);
    }

    private GraphDefinition parseGraph(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> map = asMap(yaml.load(reader));
            String id = Optional.ofNullable(map.get("id")).map(Object::toString)
                    .orElseGet(() -> file.getFileName().toString().replace(".yml", ""));
            String entry = Optional.ofNullable(map.get("entry")).map(Object::toString).orElse("start");
            Map<String, Object> variables = asMap(map.get("variables"));
            Map<String, GraphDefinition.GraphNodeDefinition> nodes = new LinkedHashMap<>();
            Map<String, Object> nodeSection = asMap(map.get("nodes"));
            for (Map.Entry<String, Object> entrySet : nodeSection.entrySet()) {
                Map<String, Object> nodeMap = asMap(entrySet.getValue());
                GraphNodeType type = GraphNodeType.valueOf(nodeMap.get("type").toString());
                Map<String, Object> params = asMap(nodeMap.get("params"));
                Map<String, String> transitions = stringMap(nodeMap.get("next"));
                long timeout = number(nodeMap.get("timeout"), 0L).longValue();
                nodes.put(entrySet.getKey(), new GraphDefinition.GraphNodeDefinition(type, params, transitions, timeout));
            }
            return new GraphDefinition(id, entry, nodes, variables);
        }
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of(value.toString());
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, Object> raw = asMap(value);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            result.put(entry.getKey(), Objects.toString(entry.getValue(), ""));
        }
        return result;
    }

    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) continue;
                normalized.put(entry.getKey().toString(), entry.getValue());
            }
            return normalized;
        }
        return new LinkedHashMap<>();
    }

    private Number number(Object value, Number defaultNumber) {
        if (value instanceof Number number) {
            return number;
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultNumber;
    }

    private boolean bool(Object value, boolean def) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return def;
    }

    public GlobalConfig globalConfig() {
        return globalConfig;
    }

    public Map<String, BotDefinition> bots() {
        return bots;
    }

    public Map<String, GraphDefinition> graphs() {
        return graphs;
    }

    public void addReloadListener(Runnable runnable) {
        reloadListeners.add(runnable);
    }

    @Override
    public void close() throws IOException {
        if (watcher != null) {
            watcher.close();
        }
    }
}
