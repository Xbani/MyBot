package com.mybot.velocity.graph;

import com.mybot.velocity.graph.node.GraphNodeExecutor;
import com.mybot.velocity.graph.node.GraphRuntimeContext;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;

/**
 * Simple registry of executors per {@link GraphNodeType}. Each executor logs what it would do
 * in a production environment so functional tests can assert behavior without a live server.
 */
public final class GraphNodeExecutors {

    private final Map<GraphNodeType, GraphNodeExecutor> registry = new EnumMap<>(GraphNodeType.class);

    public GraphNodeExecutors(Logger logger) {
        registry.put(GraphNodeType.Command, (node, ctx, ticks) -> {
            String command = String.valueOf(node.params().getOrDefault("command", "say ready"));
            ctx.session().sendCommand(command);
            logger.debug("Bot {} executed command {}", ctx.definition().id(), command);
            return GraphNodeExecutor.transition("default");
        });
        registry.put(GraphNodeType.Wait, (node, ctx, ticks) -> {
            long required = longValue(node.params().get("ticks"), 20L);
            if (ticks + 1 >= required) {
                return GraphNodeExecutor.transition("default");
            }
            return GraphNodeExecutor.stay();
        });
        registry.put(GraphNodeType.MoveTo, (node, ctx, ticks) -> {
            Object coords = node.params().get("target");
            double[] vector = parseVector(coords);
            ctx.navigation().navigateAsync(ctx.definition().id(), vector, ctx.snapshot());
            ctx.snapshot().put("lastTarget", vector);
            return GraphNodeExecutor.transition("arrived");
        });
        registry.put(GraphNodeType.ServerHop, (node, ctx, ticks) -> {
            long required = longValue(node.params().get("delay-ticks"), 0L);
            if (ticks + 1 < required) {
                return GraphNodeExecutor.stay();
            }
            Object servers = node.params().get("servers");
            String next = ctx.session().currentServer();
            if (servers instanceof Iterable<?> iterable) {
                for (Object s : iterable) {
                    String candidate = String.valueOf(s);
                    if (!candidate.equalsIgnoreCase(next)) {
                        next = candidate;
                        break;
                    }
                }
            }
            ctx.session().hopServer(next);
            return GraphNodeExecutor.transition("hopped");
        });
        registry.put(GraphNodeType.BuildSchematic, (node, ctx, ticks) -> {
            String schematic = String.valueOf(node.params().getOrDefault("schematic", "demo_bridge.schem"));
            ctx.schematics().metadata(schematic)
                    .orElseThrow(() -> new IllegalStateException("Missing schematic " + schematic));
            logger.info("Bot {} queued schematic {}", ctx.definition().id(), schematic);
            return GraphNodeExecutor.transition("success");
        });
        registry.put(GraphNodeType.Conditional, (node, ctx, ticks) -> {
            String condition = String.valueOf(node.params().getOrDefault("condition", "default"));
            boolean flag = ctx.snapshot().flag(condition);
            return GraphNodeExecutor.transition(flag ? "true" : "false");
        });
        registry.put(GraphNodeType.MineBlock, logAndAdvance(logger, "Mining"));
        registry.put(GraphNodeType.PlaceBlock, logAndAdvance(logger, "Placing"));
        registry.put(GraphNodeType.Craft, logAndAdvance(logger, "Crafting"));
        registry.put(GraphNodeType.InteractEntity, logAndAdvance(logger, "Interacting"));
        registry.put(GraphNodeType.Combat, logAndAdvance(logger, "Fighting"));
        registry.put(GraphNodeType.PathSequence, logAndAdvance(logger, "Following path"));
        registry.put(GraphNodeType.Loop, (node, ctx, ticks) -> GraphNodeExecutor.transition("loop"));
    }

    private GraphNodeExecutor logAndAdvance(Logger logger, String verb) {
        return (node, ctx, ticks) -> {
            logger.info("{} bot {} via node {}", verb, ctx.definition().id(), node);
            return GraphNodeExecutor.transition("success");
        };
    }

    public GraphNodeExecutor executor(GraphNodeType type) {
        return registry.getOrDefault(type, (node, ctx, ticks) -> GraphNodeExecutor.stay());
    }

    private static double[] parseVector(Object coords) {
        if (coords instanceof Iterable<?> iterable) {
            double[] vector = new double[3];
            int i = 0;
            for (Object value : iterable) {
                if (i >= 3) break;
                vector[i++] = Double.parseDouble(String.valueOf(value));
            }
            return vector;
        }
        return new double[]{0, 0, 0};
    }

    private static long longValue(Object value, long def) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
