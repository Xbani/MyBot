package com.mybot.velocity.bot;

import java.time.Instant;
import java.util.UUID;

public record TrackedPlayer(int entityId, UUID uuid, String username, Vec3 position, float yaw, float pitch, Instant updatedAt) {
    public boolean isBot() {
        return username != null && username.startsWith("Bot_");
    }

    public TrackedPlayer withPosition(Vec3 nextPosition, float nextYaw, float nextPitch) {
        return new TrackedPlayer(entityId, uuid, username, nextPosition, nextYaw, nextPitch, Instant.now());
    }

    public TrackedPlayer withUsername(String nextUsername) {
        return new TrackedPlayer(entityId, uuid, nextUsername, position, yaw, pitch, updatedAt);
    }
}
