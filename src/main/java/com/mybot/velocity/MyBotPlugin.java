package com.mybot.velocity;

import com.mybot.velocity.bot.BotManager;
import com.mybot.velocity.command.MyBotCommand;
import com.mybot.velocity.config.ConfigService;
import com.mybot.velocity.config.GlobalConfig;
import com.mybot.velocity.demo.BotSkinRegistry;
import com.mybot.velocity.demo.DemoScenarioLoader;
import com.mybot.velocity.metrics.BotMetrics;
import com.mybot.velocity.metrics.MetricsEndpoint;
import com.mybot.velocity.navigation.NavigationService;
import com.mybot.velocity.schematic.SchematicService;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ListenerBoundEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(id = "mybot", name = "MyBot", version = "0.1.0", authors = "MyBot Team")
public final class MyBotPlugin {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigService configService;
    private BotManager botManager;
    private DemoScenarioLoader demoScenarioLoader;
    private MetricsEndpoint metricsEndpoint;
    private BotSkinRegistry skinRegistry;
    private final AtomicBoolean demoStarted = new AtomicBoolean(false);

    @Inject
    public MyBotPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            configService = new ConfigService(dataDirectory, logger);
            configService.initialize();
            GlobalConfig globalConfig = configService.globalConfig();
            NavigationService navigationService = new NavigationService(logger);
            SchematicService schematicService = new SchematicService(
                    configService.resolve(globalConfig.dataFolders().schematicsDir()), logger);
            BotMetrics botMetrics = new BotMetrics();
            skinRegistry = new BotSkinRegistry();
            botManager = new BotManager(configService, navigationService, schematicService, botMetrics,
                    dataDirectory.resolve("recordings"), logger);
            demoScenarioLoader = new DemoScenarioLoader(botManager, configService, logger, skinRegistry);
            CommandMeta meta = proxyServer.getCommandManager().metaBuilder("mybot")
                    .plugin(this)
                    .aliases("mbot")
                    .build();
            proxyServer.getCommandManager().register(meta,
                    new MyBotCommand(botManager, configService, demoScenarioLoader, dataDirectory.resolve("recordings"), logger));
            metricsEndpoint = new MetricsEndpoint(botMetrics,
                    new java.net.InetSocketAddress(globalConfig.velocityEndpoint().getHostString(), 0),
                    logger);
            logger.info("MyBot initialized with {} bots, {} graphs", configService.bots().size(), configService.graphs().size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize MyBot", e);
        }
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        if (skinRegistry == null) {
            return;
        }
        event.setGameProfile(skinRegistry.applySkin(event.getGameProfile()));
    }

    @Subscribe
    public void onListenerBound(ListenerBoundEvent event) {
        if (configService == null || demoScenarioLoader == null || !configService.globalConfig().demoEnabled()) {
            return;
        }
        if (demoStarted.compareAndSet(false, true)) {
            CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                    .execute(() -> demoScenarioLoader.startDemo());
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (botManager != null) {
            try {
                botManager.dumpRecordings(dataDirectory.resolve("recordings"), "proxy-shutdown");
            } catch (IOException e) {
                logger.warn("Failed to dump bot recordings on shutdown", e);
            }
            botManager.close();
            botManager = null;
        }
        if (skinRegistry != null) {
            skinRegistry.clear();
        }
        if (metricsEndpoint != null) {
            metricsEndpoint.close();
        }
        if (configService != null) {
            try {
                configService.close();
            } catch (IOException e) {
                logger.warn("Failed to close config service", e);
            }
        }
    }
}
