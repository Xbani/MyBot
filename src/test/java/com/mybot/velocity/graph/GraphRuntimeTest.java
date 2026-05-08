package com.mybot.velocity.graph;

import com.mybot.velocity.bot.BotPacketBridge;
import com.mybot.velocity.bot.BotSession;
import com.mybot.velocity.bot.WorldSnapshot;
import com.mybot.velocity.config.BotDefinition;
import com.mybot.velocity.config.GraphDefinition;
import com.mybot.velocity.config.GlobalConfig;
import com.mybot.velocity.graph.node.GraphRuntimeContext;
import com.mybot.velocity.navigation.NavigationService;
import com.mybot.velocity.schematic.SchematicService;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphRuntimeTest {

    @Test
    void waitNodeTransitionsIntoCommand() {
        GraphDefinition graph = new GraphDefinition(
                "test",
                "wait",
                Map.of(
                        "wait", new GraphDefinition.GraphNodeDefinition(
                                GraphNodeType.Wait,
                                Map.of("ticks", 2),
                                Map.of("default", "command"),
                                0
                        ),
                        "command", new GraphDefinition.GraphNodeDefinition(
                                GraphNodeType.Command,
                                Map.of("command", "say hi"),
                                Map.of("default", "wait"),
                                0
                        )
                ),
                Map.of()
        );
        TestContext context = new TestContext();
        GraphRuntime runtime = new GraphRuntime(graph, context, new GraphNodeExecutors(NOPLogger.NOP_LOGGER), NOPLogger.NOP_LOGGER);
        runtime.tick(Duration.ofMillis(50));
        assertThat(runtime.activeNode()).isEqualTo("wait");
        runtime.tick(Duration.ofMillis(50));
        assertThat(runtime.activeNode()).isEqualTo("command");
        runtime.tick(Duration.ofMillis(50));
        assertThat(context.commandsIssued).isEqualTo(1);
    }

    private static final class TestContext implements GraphRuntimeContext {
        private final BotDefinition definition = new BotDefinition(
                "demo",
                "Demo",
                null,
                "lobby",
                "test",
                List.of(),
                List.of(),
                Map.of()
        );
        private final GlobalConfig globalConfig = new GlobalConfig(
                1,
                new InetSocketAddress("localhost", 25577),
                new GlobalConfig.AuthConfig("offline", "", ""),
                "test",
                false,
                new GlobalConfig.LoggingConfig("INFO", true),
                new GlobalConfig.SchedulerConfig(Duration.ofMillis(50), Duration.ofMillis(50)),
                new GlobalConfig.DataFolderConfig("bots", "graphs", "schematics"),
                new GlobalConfig.DashboardConfig(false, "127.0.0.1", 8080, "", "", List.of())
        );
        private final TestSession session = new TestSession();
        private final BotPacketBridge bridge = new BotPacketBridge(NOPLogger.NOP_LOGGER);
        private final NavigationService navigation = new NavigationService(NOPLogger.NOP_LOGGER);
        private final SchematicService schematics = new SchematicService(Paths.get("schematics"), NOPLogger.NOP_LOGGER);
        private int commandsIssued = 0;

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
            return bridge;
        }

        @Override
        public NavigationService navigation() {
            return navigation;
        }

        @Override
        public SchematicService schematics() {
            return schematics;
        }

        @Override
        public WorldSnapshot snapshot() {
            return bridge.snapshot();
        }

        @Override
        public void onNodeTransition(String from, String to, String reason) {
        }

        private final class TestSession extends BotSession {
            private TestSession() {
                super(definition, globalConfig, NOPLogger.NOP_LOGGER);
            }

            @Override
            public void sendCommand(String commandLine) {
                commandsIssued++;
            }
        }
    }
}
