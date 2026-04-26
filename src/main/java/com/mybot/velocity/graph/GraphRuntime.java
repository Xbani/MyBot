package com.mybot.velocity.graph;

import com.mybot.velocity.config.GraphDefinition;
import com.mybot.velocity.graph.node.GraphNodeExecutor;
import com.mybot.velocity.graph.node.GraphRuntimeContext;
import com.mybot.velocity.graph.node.GraphStepResult;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Executes graph nodes for a single bot.
 */
public final class GraphRuntime {

    private final GraphDefinition definition;
    private final GraphRuntimeContext context;
    private final GraphNodeExecutors executors;
    private final Logger logger;

    private String activeNode;
    private long ticksInNode;
    private Instant lastTick = Instant.now();

    public GraphRuntime(GraphDefinition definition,
                        GraphRuntimeContext context,
                        GraphNodeExecutors executors,
                        Logger logger) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.context = Objects.requireNonNull(context, "context");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.activeNode = definition.entryNode();
        this.ticksInNode = 0;
    }

    public void tick(Duration delta) {
        GraphDefinition.GraphNodeDefinition node = definition.node(activeNode);
        if (node == null) {
            logger.warn("Bot {} referencing missing node {}", context.definition().id(), activeNode);
            return;
        }
        GraphNodeExecutor executor = executors.executor(node.type());
        GraphStepResult result;
        try {
            result = executor.execute(node, context, ticksInNode);
        } catch (Exception ex) {
            logger.error("Graph node {} failed for bot {}", activeNode, context.definition().id(), ex);
            result = GraphNodeExecutor.transition("failure");
        }
        ticksInNode++;
        if (node.timeoutTicks() > 0 && ticksInNode >= node.timeoutTicks()) {
            result = GraphNodeExecutor.transition("timeout");
        }
        if (result.completed()) {
            String next = node.transitions().getOrDefault(result.outcomeKey(),
                    node.transitions().getOrDefault("default", activeNode));
            if (next != null && !next.equals(activeNode) && definition.nodes().containsKey(next)) {
                context.onNodeTransition(activeNode, next, result.outcomeKey());
                activeNode = next;
                ticksInNode = 0;
            }
        }
        lastTick = Instant.now();
    }

    public String activeNode() {
        return activeNode;
    }

    public long ticksInNode() {
        return ticksInNode;
    }

    public Instant lastTick() {
        return lastTick;
    }
}
