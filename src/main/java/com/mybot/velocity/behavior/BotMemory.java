package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class BotMemory {
    private final ReputationMemory reputation = new ReputationMemory();
    private int lastDamageAttackerEntityId = -1;
    private Instant lastSocialAt = Instant.EPOCH;
    private TrackedPlayer teammate;

    public void update(BotWorldState state, Vec3 botPosition, Instant now) {
        for (TrackedPlayer player : state.trackedPlayers()) {
            reputation.observe(player, botPosition, now);
        }
        if (state.lastAttackerEntityId() != -1 && state.lastAttackerEntityId() != lastDamageAttackerEntityId) {
            state.trackedPlayers().stream()
                    .filter(player -> player.entityId() == state.lastAttackerEntityId())
                    .findFirst()
                    .ifPresent(player -> reputation.rememberAttacker(player, now));
            lastDamageAttackerEntityId = state.lastAttackerEntityId();
        }
        if (teammate != null && state.trackedPlayers().stream().noneMatch(player -> player.uuid().equals(teammate.uuid()))) {
            teammate = null;
        }
        reputation.prune(now);
    }

    public ReputationMemory reputation() {
        return reputation;
    }

    public Optional<TrackedPlayer> teammate() {
        return Optional.ofNullable(teammate);
    }

    public void setTeammate(TrackedPlayer player, Instant now) {
        this.teammate = player;
        this.lastSocialAt = now;
        reputation.rememberTeamed(player, now);
    }

    public boolean socialReady(Instant now, Duration cooldown) {
        return Duration.between(lastSocialAt, now).compareTo(cooldown) >= 0;
    }

    public void markSocial(Instant now) {
        this.lastSocialAt = now;
    }

    public void resetVolatile() {
        teammate = null;
        lastDamageAttackerEntityId = -1;
    }
}
