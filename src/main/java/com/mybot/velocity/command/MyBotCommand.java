package com.mybot.velocity.command;

import com.mybot.velocity.bot.BotManager;
import com.mybot.velocity.config.ConfigService;
import com.mybot.velocity.demo.DemoScenarioLoader;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class MyBotCommand implements SimpleCommand {

    private final BotManager botManager;
    private final ConfigService configService;
    private final DemoScenarioLoader demoScenarioLoader;
    private final Logger logger;

    public MyBotCommand(BotManager botManager,
                        ConfigService configService,
                        DemoScenarioLoader demoScenarioLoader,
                        Logger logger) {
        this.botManager = Objects.requireNonNull(botManager, "botManager");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.demoScenarioLoader = Objects.requireNonNull(demoScenarioLoader, "demoScenarioLoader");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            help(invocation);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn" -> spawn(invocation, args);
            case "kill" -> kill(invocation, args);
            case "list" -> list(invocation);
            case "reload" -> reload(invocation);
            case "demo" -> demo(invocation, args);
            default -> help(invocation);
        }
    }

    private void spawn(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /mybot spawn <botId>", NamedTextColor.RED));
            return;
        }
        String botId = args[1];
        botManager.spawnBot(botId).thenAccept(success -> {
            if (success) {
                invocation.source().sendMessage(Component.text("Spawned bot " + botId, NamedTextColor.GREEN));
            } else {
                invocation.source().sendMessage(Component.text("Failed to spawn bot " + botId, NamedTextColor.RED));
            }
        });
    }

    private void kill(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /mybot kill <botId>", NamedTextColor.RED));
            return;
        }
        botManager.despawnBot(args[1], "command");
        invocation.source().sendMessage(Component.text("Despawn requested for " + args[1], NamedTextColor.YELLOW));
    }

    private void list(Invocation invocation) {
        Component header = Component.text("Configured bots: " + configService.bots().keySet(), NamedTextColor.AQUA);
        invocation.source().sendMessage(header);
    }

    private void reload(Invocation invocation) {
        try {
            configService.reloadAll();
            invocation.source().sendMessage(Component.text("Configuration reloaded", NamedTextColor.GREEN));
        } catch (Exception ex) {
            invocation.source().sendMessage(Component.text("Reload failed: " + ex.getMessage(), NamedTextColor.RED));
            logger.error("Reload command failed", ex);
        }
    }

    private void demo(Invocation invocation, String[] args) {
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /mybot demo <start|stop>", NamedTextColor.RED));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> demoScenarioLoader.startDemo();
            case "stop" -> demoScenarioLoader.stopDemo();
            default -> invocation.source().sendMessage(Component.text("Unknown demo action", NamedTextColor.RED));
        }
        invocation.source().sendMessage(Component.text("Demo command processed", NamedTextColor.YELLOW));
    }

    private void help(Invocation invocation) {
        invocation.source().sendMessage(Component.text("/mybot <spawn|kill|list|reload|demo>", NamedTextColor.GRAY));
        invocation.source().sendMessage(Component.text("Args: " + Arrays.toString(invocation.arguments()), NamedTextColor.DARK_GRAY));
    }
}
