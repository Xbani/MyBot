package com.mybot.velocity.bot;

import com.mybot.velocity.config.BotDefinition;
import com.mybot.velocity.config.ConfigService;
import com.mybot.velocity.config.GraphDefinition;
import com.mybot.velocity.config.GlobalConfig;
import com.mybot.velocity.dashboard.JsonWriter;
import com.mybot.velocity.graph.GraphNodeExecutors;
import com.mybot.velocity.graph.GraphRuntime;
import com.mybot.velocity.graph.node.GraphRuntimeContext;
import com.mybot.velocity.metrics.BotMetrics;
import com.mybot.velocity.navigation.NavigationService;
import com.mybot.velocity.schematic.SchematicService;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates bot lifecycle and ties configuration to runtime state.
 */
public final class BotManager implements AutoCloseable {

    private final ConfigService configService;
    private final NavigationService navigationService;
    private final SchematicService schematicService;
    private final BotMetrics metrics;
    private final Path recordingsDirectory;
    private final Logger logger;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "mybot-scheduler");
                t.setDaemon(true);
                return t;
            });
    private final ConcurrentMap<String, BotHandle> activeBots = new ConcurrentHashMap<>();
    private final GraphNodeExecutors executors;
    private final Instant startedAt = Instant.now();
    private final ConcurrentMap<Long, DashboardEvent> recentEvents = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong eventSequence = new java.util.concurrent.atomic.AtomicLong();

    public BotManager(ConfigService configService,
                      NavigationService navigationService,
                      SchematicService schematicService,
                      BotMetrics metrics,
                      Path recordingsDirectory,
                      Logger logger) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.schematicService = Objects.requireNonNull(schematicService, "schematicService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.recordingsDirectory = Objects.requireNonNull(recordingsDirectory, "recordingsDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executors = new GraphNodeExecutors(logger);
        this.configService.addReloadListener(this::reloadFromConfig);
        scheduleGraphTicks();
        scheduleBotTicks();
    }

    private void scheduleGraphTicks() {
        long graphTickMillis = configService.globalConfig().schedulers().graphTick().toMillis();
        executor.scheduleAtFixedRate(this::tickBots, graphTickMillis, graphTickMillis, TimeUnit.MILLISECONDS);
    }

    private void scheduleBotTicks() {
        long botTickMillis = configService.globalConfig().schedulers().botTick().toMillis();
        executor.scheduleAtFixedRate(this::tickPhysics, botTickMillis, botTickMillis, TimeUnit.MILLISECONDS);
    }

    private void tickBots() {
        for (BotHandle handle : activeBots.values()) {
            try {
                handle.tick(configService.globalConfig().schedulers().graphTick());
            } catch (Exception ex) {
                logger.error("Bot {} tick failure", handle.definition.id(), ex);
            }
        }
        metrics.setActiveBots(activeBots.size());
    }

    private void tickPhysics() {
        for (BotHandle handle : activeBots.values()) {
            try {
                handle.tickPhysics();
            } catch (Exception ex) {
                logger.error("Bot {} physics tick failure", handle.definition.id(), ex);
            }
        }
    }

    public CompletableFuture<Boolean> spawnBot(String botId) {
        BotDefinition definition = configService.bots().get(botId);
        if (definition == null) {
            return CompletableFuture.completedFuture(false);
        }
        return spawnBot(definition);
    }

    public CompletableFuture<Boolean> spawnBot(BotDefinition definition) {
        String botId = definition.id();
        if (activeBots.size() >= configService.globalConfig().maxBots()) {
            logger.warn("Max bot count reached, cannot spawn {}", botId);
            return CompletableFuture.completedFuture(false);
        }
        GraphDefinition graphDefinition = Optional.ofNullable(configService.graphs().get(definition.assignedGraph()))
                .orElseGet(() -> configService.graphs().get(configService.globalConfig().defaultGraph()));
        if (graphDefinition == null) {
            logger.error("Missing graph {} for bot {}", definition.assignedGraph(), botId);
            return CompletableFuture.completedFuture(false);
        }
        BotHandle handle = new BotHandle(definition, graphDefinition);
        activeBots.put(botId, handle);
        recordEvent("spawn", botId, "Spawned " + definition.username());
        logger.debug("Spawned bot {} using graph {}", botId, graphDefinition.id());
        return handle.connect().handle((v, throwable) -> {
            if (throwable != null) {
                logger.error("Bot {} failed to connect", botId, throwable);
                activeBots.remove(botId);
                handle.shutdown("connect-error");
                recordEvent("connect-error", botId, "Connection failed for " + definition.username());
                return false;
            }
            handle.session().disconnectedFuture().thenAccept(reason -> {
                handle.dumpRecording("disconnect-" + reason);
                activeBots.remove(botId, handle);
                recordEvent("disconnect", botId, definition.username() + " disconnected: " + reason);
                logger.debug("Removed disconnected bot {} from active registry: {}", botId, reason);
            });
            return true;
        });
    }

    public void despawnBot(String botId, String reason) {
        BotHandle handle = activeBots.remove(botId);
        if (handle != null) {
            handle.shutdown(reason);
            recordEvent("despawn", botId, "Despawned " + handle.definition.username() + ": " + reason);
        }
    }

    public void despawnAll(String reason) {
        activeBots.forEach((id, handle) -> handle.shutdown(reason));
        activeBots.clear();
    }

    public Map<String, BotDefinition> configuredBots() {
        return configService.bots();
    }

    public Map<String, GraphDefinition> configuredGraphs() {
        return configService.graphs();
    }

    public int dumpRecordings(Path directory, String reason) throws IOException {
        int snapshots = 0;
        for (BotHandle handle : activeBots.values()) {
            snapshots += handle.session().recorder().dump(directory, reason);
        }
        logger.debug("Dumped {} bot recorder snapshots to {} ({})", snapshots, directory, reason);
        return snapshots;
    }

    public String dashboardStateJson(String proxyId, String proxyName, String publicUrl) {
        JsonWriter json = new JsonWriter();
        List<BotHandle> handles = activeBots.values().stream()
                .sorted(Comparator.comparing(handle -> handle.definition.id()))
                .toList();
        Map<String, ObservedPlayer> observedPlayers = observedPlayers(handles);
        json.beginObject();
        json.name("proxy").beginObject()
                .name("id").string(proxyId).comma()
                .name("name").string(proxyName).comma()
                .name("publicUrl").string(publicUrl).comma()
                .name("startedAt").string(startedAt.toString()).comma()
                .name("uptimeSeconds").value(Duration.between(startedAt, Instant.now()).toSeconds()).comma()
                .name("activeBots").value(metrics.activeBots()).comma()
                .name("configuredBots").value(configuredBots().size()).comma()
                .name("graphTransitions").value(metrics.graphTransitions()).comma()
                .name("commandsExecuted").value(metrics.commandsExecuted()).comma()
                .name("lastReload").string(metrics.lastReload().toString())
                .endObject().comma();
        json.name("bots").beginArray();
        boolean firstBot = true;
        for (BotHandle handle : handles) {
            if (!firstBot) json.comma();
            firstBot = false;
            appendBot(json, handle);
        }
        json.endArray().comma();
        json.name("players").beginArray();
        boolean firstPlayer = true;
        for (ObservedPlayer player : observedPlayers.values()) {
            if (!firstPlayer) json.comma();
            firstPlayer = false;
            appendPlayer(json, player);
        }
        json.endArray().comma();
        json.name("map").beginObject();
        json.name("chunks").beginArray();
        Map<String, ObservedChunk> chunks = observedChunks(handles);
        boolean firstChunk = true;
        for (ObservedChunk chunk : chunks.values()) {
            if (!firstChunk) json.comma();
            firstChunk = false;
            json.beginObject()
                    .name("chunkX").value(chunk.chunkX()).comma()
                    .name("chunkZ").value(chunk.chunkZ()).comma()
                    .name("seenBy").value(chunk.seenBy()).comma()
                    .name("updatedAt").value(chunk.updatedAtMillis()).comma()
                    .name("minY").value(chunk.minY()).comma()
                    .name("maxY").value(chunk.maxY()).comma()
                    .name("colors").string(chunk.colors()).comma()
                    .name("heights").value(chunk.heights())
                    .endObject();
        }
        json.endArray().comma();
        json.name("limits").beginObject()
                .name("centerX").value(0).comma()
                .name("centerZ").value(0).comma()
                .name("initialRadius").value(1000).comma()
                .name("currentRadius").value(375).comma()
                .name("finalRadius").value(125)
                .endObject();
        json.endObject().comma();
        appendGame(json.name("game"), handles).comma();
        json.name("events").beginArray();
        List<DashboardEvent> events = new ArrayList<>(recentEvents.values());
        events.sort(Comparator.comparing(DashboardEvent::at).reversed());
        boolean firstEvent = true;
        for (DashboardEvent event : events.stream().limit(40).toList()) {
            if (!firstEvent) json.comma();
            firstEvent = false;
            json.beginObject()
                    .name("at").string(event.at().toString()).comma()
                    .name("type").string(event.type()).comma()
                    .name("botId").string(event.botId()).comma()
                    .name("message").string(event.message())
                    .endObject();
        }
        json.endArray();
        json.endObject();
        return json.json();
    }

    private void appendBot(JsonWriter json, BotHandle handle) {
        BotSession session = handle.session();
        Vec3 position = session.physics().position();
        Vec3 velocity = session.physics().velocity();
        var debug = session.debugSnapshot();
        json.beginObject()
                .name("id").string(handle.definition.id()).comma()
                .name("username").string(handle.definition.username()).comma()
                .name("uuid").string(handle.definition.uuid() == null ? "" : handle.definition.uuid().toString()).comma()
                .name("server").string(session.currentServer()).comma()
                .name("connected").value(session.isConnected()).comma()
                .name("lifecycle").string(session.lifecycleState().name()).comma()
                .name("intent").string(session.intent().name()).comma()
                .name("health").value(session.worldState().health()).comma()
                .name("food").value(session.worldState().food()).comma()
                .name("saturation").value(round(session.worldState().saturation())).comma()
                .name("xpLevel").value(session.worldState().experienceLevel()).comma()
                .name("xpProgress").value(round(session.worldState().experienceProgress())).comma()
                .name("totalXp").value(session.worldState().totalExperience()).comma();
        appendVec(json.name("position"), position).comma();
        appendVec(json.name("velocity"), velocity).comma();
        json.name("yaw").value(session.yaw()).comma()
                .name("pitch").value(session.pitch()).comma()
                .name("target").string(session.worldState().trackedPlayers().stream()
                        .filter(player -> !player.isBot())
                        .min(Comparator.comparingDouble(player -> player.position().distanceSquaredTo(position)))
                        .map(TrackedPlayer::username)
                        .orElse("")).comma()
                .name("pathStuck").value(session.pathStuck()).comma();
        appendVec(json.name("pathTarget"), session.pathTarget()).comma();
        json.name("pathLength").value(session.path().size()).comma()
                .name("inventory");
        appendInventory(json, session.worldState().inventory().snapshot()).comma()
                .name("ai").beginObject()
                .name("lifecycle").string(session.lifecycleState().name()).comma()
                .name("intent").string(session.intent().name()).comma()
                .name("target").string(debug.targetName()).comma()
                .name("confidence").value(round(debug.confidence())).comma()
                .name("panic").value(round(debug.panic())).comma()
                .name("threat").value(round(debug.threatScore())).comma()
                .name("fight").value(round(debug.fightScore())).comma()
                .name("flee").value(round(debug.fleeScore())).comma()
                .name("loot").value(round(debug.lootScore())).comma()
                .name("team").value(round(debug.teamScore())).comma()
                .name("teammate").string(debug.teammateName()).comma()
                .name("reason").string(debug.reason()).comma()
                .name("invincibilityStage").string(debug.invincibilityStage()).comma()
                .name("lastCraft").string(debug.lastCraft()).comma()
                .name("craftFailure").string(debug.craftFailure()).comma()
                .name("lastMine").string(debug.lastMine()).comma()
                .name("openContainerId").value(debug.openContainerId()).comma()
                .name("goal").string(debug.goal()).comma()
                .name("subGoal").string(debug.subGoal()).comma()
                .name("nextStep").string(debug.nextStep()).comma()
                .name("blocker").string(debug.blocker()).comma()
                .name("requiredItems").value(debug.requiredItems()).comma()
                .name("lastResourceTarget").string(debug.lastResourceTarget()).comma()
                .name("recipes").value(session.worldState().recipeBook().snapshot()).comma()
                .name("pathStuck").value(session.pathStuck()).comma()
                .name("pathLength").value(session.path().size())
                .endObject().comma()
                .name("lastActivity").string(session.lastActivity().toString())
                .endObject();
    }

    private JsonWriter appendInventory(JsonWriter json, List<BotInventoryState.ItemSnapshot> items) {
        json.beginArray();
        boolean first = true;
        for (BotInventoryState.ItemSnapshot item : items) {
            if (!first) json.comma();
            first = false;
            json.beginObject()
                    .name("slot").value(item.slot()).comma()
                    .name("id").value(item.id()).comma()
                    .name("amount").value(item.amount()).comma()
                    .name("name").string(item.name())
                    .endObject();
        }
        return json.endArray();
    }

    private JsonWriter appendGame(JsonWriter json, List<BotHandle> handles) {
        BotScoreboardState.Snapshot scoreboard = handles.stream()
                .map(handle -> handle.session().worldState().scoreboard().snapshot())
                .filter(snapshot -> !snapshot.lines().isEmpty())
                .findFirst()
                .orElse(new BotScoreboardState.Snapshot(List.of(), "", -1));
        return json.beginObject()
                .name("phase").string(scoreboard.phase()).comma()
                .name("feastSeconds").value(scoreboard.feastSeconds()).comma()
                .name("scoreboard").value(scoreboard.lines())
                .endObject();
    }

    private void appendPlayer(JsonWriter json, ObservedPlayer player) {
        json.beginObject()
                .name("entityId").value(player.entityId()).comma()
                .name("uuid").string(player.uuid()).comma()
                .name("username").string(player.username()).comma()
                .name("bot").value(player.bot()).comma()
                .name("seenBy").value(player.seenBy()).comma();
        appendVec(json.name("position"), player.position()).comma();
        json.name("updatedAt").string(player.updatedAt().toString())
                .endObject();
    }

    private Map<String, ObservedPlayer> observedPlayers(List<BotHandle> handles) {
        Map<String, ObservedPlayer> players = new LinkedHashMap<>();
        for (BotHandle handle : handles) {
            for (TrackedPlayer player : handle.session().worldState().trackedPlayers()) {
                String key = playerKey(player);
                players.compute(key, (ignored, current) -> mergePlayer(current, player, handle.definition.id()));
            }
        }
        return players;
    }

    private Map<String, ObservedChunk> observedChunks(List<BotHandle> handles) {
        Map<String, ObservedChunk> chunks = new LinkedHashMap<>();
        for (BotHandle handle : handles) {
            for (WorldBlockCache.ChunkSnapshot chunk : handle.session().worldState().blocks().chunkSnapshots()) {
                String key = chunk.chunkX() + ":" + chunk.chunkZ();
                chunks.compute(key, (ignored, current) -> mergeChunk(current, chunk, handle.definition.id()));
            }
        }
        return chunks;
    }

    private ObservedChunk mergeChunk(ObservedChunk current, WorldBlockCache.ChunkSnapshot next, String seenBy) {
        LinkedHashSet<String> observers = new LinkedHashSet<>();
        if (current != null) {
            observers.addAll(current.seenBy());
        }
        observers.add(seenBy);
        if (current == null || next.updatedAtMillis() >= current.updatedAtMillis()) {
            return new ObservedChunk(
                    next.chunkX(),
                    next.chunkZ(),
                    next.updatedAtMillis(),
                    next.minY(),
                    next.maxY(),
                    next.colors(),
                    next.heights(),
                    List.copyOf(observers)
            );
        }
        return new ObservedChunk(
                current.chunkX(),
                current.chunkZ(),
                current.updatedAtMillis(),
                current.minY(),
                current.maxY(),
                current.colors(),
                current.heights(),
                List.copyOf(observers)
        );
    }

    private ObservedPlayer mergePlayer(ObservedPlayer current, TrackedPlayer next, String seenBy) {
        LinkedHashSet<String> observers = new LinkedHashSet<>();
        if (current != null) {
            observers.addAll(current.seenBy());
        }
        observers.add(seenBy);
        if (current == null || next.updatedAt().isAfter(current.updatedAt())) {
            return new ObservedPlayer(
                    next.entityId(),
                    next.uuid() == null ? "" : next.uuid().toString(),
                    next.username(),
                    next.isBot(),
                    next.position(),
                    next.updatedAt(),
                    List.copyOf(observers)
            );
        }
        return new ObservedPlayer(
                current.entityId(),
                current.uuid(),
                current.username(),
                current.bot(),
                current.position(),
                current.updatedAt(),
                List.copyOf(observers)
        );
    }

    private String playerKey(TrackedPlayer player) {
        if (player.uuid() != null) {
            return "uuid:" + player.uuid();
        }
        if (player.username() != null && !player.username().isBlank()) {
            return "name:" + player.username().toLowerCase(java.util.Locale.ROOT);
        }
        return "entity:" + player.entityId();
    }

    private JsonWriter appendVec(JsonWriter json, Vec3 value) {
        return json.beginObject()
                .name("x").value(round(value.x())).comma()
                .name("y").value(round(value.y())).comma()
                .name("z").value(round(value.z()))
                .endObject();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void recordEvent(String type, String botId, String message) {
        long sequence = eventSequence.incrementAndGet();
        recentEvents.put(sequence, new DashboardEvent(Instant.now(), type, botId, message));
        if (recentEvents.size() > 100) {
            recentEvents.keySet().stream().sorted().limit(recentEvents.size() - 100L).forEach(recentEvents::remove);
        }
    }

    private void reloadFromConfig() {
        metrics.markReload();
        logger.debug("Reload triggered: restarting {} bots", activeBots.size());
        despawnAll("reload");
        schematicService.invalidateAll();
    }

    private final class BotHandle implements GraphRuntimeContext {
        private final BotDefinition definition;
        private final GraphDefinition graphDefinition;
        private final BotSession session;
        private final BotPacketBridge packetBridge;
        private final GraphRuntime runtime;
        private final AtomicBoolean recordingDumped = new AtomicBoolean(false);

        private BotHandle(BotDefinition definition, GraphDefinition graphDefinition) {
            this.definition = definition;
            this.graphDefinition = graphDefinition;
            GlobalConfig config = configService.globalConfig();
            this.session = new BotSession(definition, config, logger);
            this.packetBridge = new BotPacketBridge(logger);
            this.runtime = new GraphRuntime(graphDefinition, this, executors, logger);
        }

        private CompletableFuture<Void> connect() {
            return session.connect();
        }

        private void tick(Duration delta) {
            if (!session.isConnected()) {
                return;
            }
            runtime.tick(delta);
        }

        private void tickPhysics() {
            session.tickPhysics();
        }

        private void shutdown(String reason) {
            dumpRecording(reason);
            session.disconnect(reason);
        }

        private void dumpRecording(String reason) {
            if (!recordingDumped.compareAndSet(false, true)) {
                return;
            }
            try {
                int snapshots = session.recorder().dump(recordingsDirectory, reason);
                logger.debug("Dumped {} recorder snapshots for bot {} ({})", snapshots, definition.id(), reason);
            } catch (IOException ex) {
                logger.warn("Failed to dump recorder snapshots for bot {}", definition.id(), ex);
            }
        }

        @Override
        public BotDefinition definition() {
            return definition;
        }

        @Override
        public BotSession session() {
            return session;
        }

        @Override
        public BotPacketBridge packets() {
            return packetBridge;
        }

        @Override
        public NavigationService navigation() {
            return navigationService;
        }

        @Override
        public SchematicService schematics() {
            return schematicService;
        }

        @Override
        public WorldSnapshot snapshot() {
            return packetBridge.snapshot();
        }

        @Override
        public void onNodeTransition(String from, String to, String reason) {
            logger.debug("Bot {} transitioned {} -> {} via {}", definition.id(), from, to, reason);
            metrics.incrementTransitions();
            recordEvent("transition", definition.id(), definition.username() + ": " + from + " -> " + to);
        }
    }

    private record DashboardEvent(Instant at, String type, String botId, String message) { }

    private record ObservedPlayer(
            int entityId,
            String uuid,
            String username,
            boolean bot,
            Vec3 position,
            Instant updatedAt,
            List<String> seenBy
    ) { }

    private record ObservedChunk(
            int chunkX,
            int chunkZ,
            long updatedAtMillis,
            int minY,
            int maxY,
            String colors,
            List<Integer> heights,
            List<String> seenBy
    ) { }

    @Override
    public void close() {
        despawnAll("shutdown");
        executor.shutdownNow();
    }
}
