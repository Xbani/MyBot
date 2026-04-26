package com.mybot.velocity.config;

import com.mybot.velocity.graph.GraphNodeType;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class GraphDefinition {

    private final String id;
    private final String entryNode;
    private final Map<String, GraphNodeDefinition> nodes;
    private final Map<String, Object> variables;

    public GraphDefinition(String id,
                           String entryNode,
                           Map<String, GraphNodeDefinition> nodes,
                           Map<String, Object> variables) {
        this.id = Objects.requireNonNull(id, "id");
        this.entryNode = Objects.requireNonNull(entryNode, "entryNode");
        this.nodes = Collections.unmodifiableMap(nodes);
        this.variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public String id() {
        return id;
    }

    public String entryNode() {
        return entryNode;
    }

    public Map<String, GraphNodeDefinition> nodes() {
        return nodes;
    }

    public Map<String, Object> variables() {
        return variables;
    }

    public GraphNodeDefinition node(String name) {
        return nodes.get(name);
    }

    public record GraphNodeDefinition(GraphNodeType type,
                                      Map<String, Object> params,
                                      Map<String, String> transitions,
                                      long timeoutTicks) {
        public GraphNodeDefinition {
            Objects.requireNonNull(type, "type");
        }

        public Map<String, Object> params() {
            return params == null ? Map.of() : params;
        }

        public Map<String, String> transitions() {
            return transitions == null ? Map.of() : transitions;
        }
    }
}
