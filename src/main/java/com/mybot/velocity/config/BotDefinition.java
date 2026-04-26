package com.mybot.velocity.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a bot profile loaded from bots/*.yml.
 */
public final class BotDefinition {

    private final String id;
    private final String username;
    private final UUID uuid;
    private final String initialServer;
    private final String assignedGraph;
    private final List<String> inventoryPreset;
    private final List<String> commandWhitelist;
    private final Map<String, Object> traits;

    public BotDefinition(String id,
                         String username,
                         UUID uuid,
                         String initialServer,
                         String assignedGraph,
                         List<String> inventoryPreset,
                         List<String> commandWhitelist,
                         Map<String, Object> traits) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = Objects.requireNonNull(username, "username");
        this.uuid = uuid;
        this.initialServer = Objects.requireNonNullElse(initialServer, "lobby");
        this.assignedGraph = Objects.requireNonNullElse(assignedGraph, "builder");
        this.inventoryPreset = List.copyOf(inventoryPreset == null ? List.of() : inventoryPreset);
        this.commandWhitelist = List.copyOf(commandWhitelist == null ? List.of() : commandWhitelist);
        this.traits = traits == null ? Map.of() : Map.copyOf(traits);
    }

    public String id() {
        return id;
    }

    public String username() {
        return username;
    }

    public UUID uuid() {
        return uuid;
    }

    public String initialServer() {
        return initialServer;
    }

    public String assignedGraph() {
        return assignedGraph;
    }

    public List<String> inventoryPreset() {
        return inventoryPreset;
    }

    public List<String> commandWhitelist() {
        return commandWhitelist;
    }

    public Map<String, Object> traits() {
        return traits;
    }
}
