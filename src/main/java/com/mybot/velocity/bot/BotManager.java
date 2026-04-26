package com.mybot.velocity.bot;

import com.mybot.velocity.config.BotDefinition;
import com.mybot.velocity.config.ConfigService;
import com.mybot.velocity.config.GraphDefinition;
import com.mybot.velocity.config.GlobalConfig;
import com.mybot.velocity.graph.GraphNodeExecutors;
import com.mybot.velocity.graph.GraphRuntime;
import com.mybot.velocity.graph.node.GraphRuntimeContext;
import com.mybot.velocity.metrics.BotMetrics;
import com.mybot.velocity.navigation.NavigationService;
import com.mybot.velocity.schematic.SchematicService;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates bot lifecycle and ties configuration to runtime state.
 */
public final class BotManager implements AutoCloseable {

    private final ConfigService configService;
    private final NavigationService navigationService;
    private final SchematicService schematicService;
    private final BotMetrics metrics;
    private final Logger logger;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "mybot-scheduler");
                t.setDaemon(true);
                return t;
            });
    private final ConcurrentMap<String, BotHandle> activeBots = new ConcurrentHashMap<>();
    private final GraphNodeExecutors executors;

    public BotManager(ConfigService configService,
                      NavigationService navigationService,
                      SchematicService schematicService,
                      BotMetrics metrics,
                      Logger logger) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.schematicService = Objects.requireNonNull(schematicService, "schematicService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
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
        logger.info("Spawned bot {} using graph {}", botId, graphDefinition.id());
        return handle.connect().handle((v, throwable) -> {
            if (throwable != null) {
                logger.error("Bot {} failed to connect", botId, throwable);
                activeBots.remove(botId);
                handle.shutdown("connect-error");
                return false;
            }
            handle.session().disconnectedFuture().thenAccept(reason -> {
                activeBots.remove(botId, handle);
                logger.info("Removed disconnected bot {} from active registry: {}", botId, reason);
            });
            return true;
        });
    }

    public void despawnBot(String botId, String reason) {
        BotHandle handle = activeBots.remove(botId);
        if (handle != null) {
            handle.shutdown(reason);
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

    private void reloadFromConfig() {
        metrics.markReload();
        logger.info("Reload triggered: restarting {} bots", activeBots.size());
        despawnAll("reload");
        schematicService.invalidateAll();
    }

    private final class BotHandle implements GraphRuntimeContext {
        private final BotDefinition definition;
        private final GraphDefinition graphDefinition;
        private final BotSession session;
        private final BotPacketBridge packetBridge;
        private final GraphRuntime runtime;

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
            session.disconnect(reason);
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
        }
    }

    @Override
    public void close() {
        despawnAll("shutdown");
        executor.shutdownNow();
    }
}
