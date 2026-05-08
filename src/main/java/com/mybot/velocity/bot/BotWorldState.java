package com.mybot.velocity.bot;

import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BotWorldState {
    private final WorldBlockCache blocks;
    private final BotInventoryState inventory = new BotInventoryState();
    private final BotRecipeBook recipeBook = new BotRecipeBook();
    private final BotScoreboardState scoreboard = new BotScoreboardState();
    private final ConcurrentMap<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, TrackedPlayer> playersByEntity = new ConcurrentHashMap<>();
    private volatile float health = 20.0f;
    private volatile int food = 20;
    private volatile float saturation = 5.0f;
    private volatile float experienceProgress;
    private volatile int experienceLevel;
    private volatile int totalExperience;
    private volatile int lastAttackerEntityId = -1;
    private volatile Instant lastDamageAt = Instant.EPOCH;
    private volatile Instant lastTeleportAt = Instant.EPOCH;
    private volatile Instant matchStartedAt = Instant.EPOCH;
    private volatile GameMode gameMode = GameMode.SURVIVAL;
    private volatile String serverName = "";

    public BotWorldState(WorldBlockCache blocks) {
        this.blocks = blocks;
    }

    public WorldBlockCache blocks() {
        return blocks;
    }

    public BotInventoryState inventory() {
        return inventory;
    }

    public BotRecipeBook recipeBook() {
        return recipeBook;
    }

    public BotScoreboardState scoreboard() {
        return scoreboard;
    }

    public Collection<TrackedPlayer> trackedPlayers() {
        return List.copyOf(playersByEntity.values());
    }

    public void clearTrackedPlayers() {
        playersByEntity.clear();
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

    public String serverName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName == null ? "" : serverName;
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        if (gameMode != null) {
            this.gameMode = gameMode;
        }
    }

    public boolean spectator() {
        return gameMode == GameMode.SPECTATOR;
    }

    public Instant lastTeleportAt() {
        return lastTeleportAt;
    }

    public void markTeleport() {
        lastTeleportAt = Instant.now();
    }

    public Instant matchStartedAt() {
        return matchStartedAt;
    }

    public void markMatchStarted() {
        if (matchStartedAt.equals(Instant.EPOCH)) {
            matchStartedAt = Instant.now();
        }
    }

    public void resetMatchTiming() {
        matchStartedAt = Instant.EPOCH;
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

    public float experienceProgress() {
        return experienceProgress;
    }

    public int experienceLevel() {
        return experienceLevel;
    }

    public int totalExperience() {
        return totalExperience;
    }

    public void updateExperience(float progress, int level, int total) {
        this.experienceProgress = progress;
        this.experienceLevel = level;
        this.totalExperience = total;
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
