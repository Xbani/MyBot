package com.mybot.velocity.bot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BotWorldState {
    private final WorldBlockCache blocks;
    private final BotInventoryState inventory = new BotInventoryState();
    private final ConcurrentMap<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, TrackedPlayer> playersByEntity = new ConcurrentHashMap<>();
    private volatile float health = 20.0f;
    private volatile int food = 20;
    private volatile float saturation = 5.0f;
    private volatile int lastAttackerEntityId = -1;
    private volatile Instant lastDamageAt = Instant.EPOCH;

    public BotWorldState(WorldBlockCache blocks) {
        this.blocks = blocks;
    }

    public WorldBlockCache blocks() {
        return blocks;
    }

    public BotInventoryState inventory() {
        return inventory;
    }

    public Collection<TrackedPlayer> trackedPlayers() {
        return List.copyOf(playersByEntity.values());
    }

    public void rememberPlayerName(UUID uuid, String username) {
        playerNames.put(uuid, username);
        playersByEntity.replaceAll((id, tracked) -> uuid.equals(tracked.uuid()) ? tracked.withUsername(username) : tracked);
    }

    public void removePlayerName(UUID uuid) {
        playerNames.remove(uuid);
    }

    public void putPlayer(TrackedPlayer player) {
        playersByEntity.put(player.entityId(), player.withUsername(playerNames.getOrDefault(player.uuid(), player.username())));
    }

    public void movePlayer(int entityId, Vec3 delta, Float yaw, Float pitch) {
        playersByEntity.computeIfPresent(entityId, (id, player) -> player.withPosition(
                player.position().add(delta),
                yaw == null ? player.yaw() : yaw,
                pitch == null ? player.pitch() : pitch));
    }

    public void teleportPlayer(int entityId, Vec3 position, float yaw, float pitch) {
        playersByEntity.computeIfPresent(entityId, (id, player) -> player.withPosition(position, yaw, pitch));
    }

    public void removeEntity(int entityId) {
        playersByEntity.remove(entityId);
    }

    public float health() {
        return health;
    }

    public int food() {
        return food;
    }

    public float saturation() {
        return saturation;
    }

    public void updateHealth(float health, int food, float saturation) {
        this.health = health;
        this.food = food;
        this.saturation = saturation;
    }

    public int lastAttackerEntityId() {
        return lastAttackerEntityId;
    }

    public Instant lastDamageAt() {
        return lastDamageAt;
    }

    public void markDamage(int attackerEntityId) {
        this.lastAttackerEntityId = attackerEntityId;
        this.lastDamageAt = Instant.now();
    }
}
