package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReputationMemory {
    private static final Duration FORGET_AFTER = Duration.ofMinutes(12);

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void observe(TrackedPlayer player, Vec3 botPosition, Instant now) {
        Entry entry = entries.computeIfAbsent(player.uuid(), ignored -> new Entry(player.uuid(), player.username()));
        entry.name = player.username();
        entry.lastKnownPosition = player.position();
        entry.lastSeenAt = now;
        double distance = botPosition.horizontalDistanceTo(player.position());
        if (distance < 5.5) {
            entry.lookedStrong += 0.08;
        }
        entry.lookedStrong = clamp(entry.lookedStrong * 0.995);
        entry.lookedWeak = clamp(entry.lookedWeak * 0.995);
    }

    public void rememberAttacker(TrackedPlayer player, Instant now) {
        Entry entry = entries.computeIfAbsent(player.uuid(), ignored -> new Entry(player.uuid(), player.username()));
        entry.name = player.username();
        entry.attackedMe = true;
        entry.lastHostileAt = now;
        entry.lookedStrong = clamp(entry.lookedStrong + 0.20);
    }

    public void rememberWeak(TrackedPlayer player) {
        Entry entry = entries.computeIfAbsent(player.uuid(), ignored -> new Entry(player.uuid(), player.username()));
        entry.name = player.username();
        entry.lookedWeak = clamp(entry.lookedWeak + 0.25);
    }

    public void rememberTeamed(TrackedPlayer player, Instant now) {
        Entry entry = entries.computeIfAbsent(player.uuid(), ignored -> new Entry(player.uuid(), player.username()));
        entry.name = player.username();
        entry.teamedBefore = true;
        entry.helpedMe = true;
        entry.lastSocialAt = now;
    }

    public void rememberBetrayal(TrackedPlayer player, Instant now) {
        Entry entry = entries.computeIfAbsent(player.uuid(), ignored -> new Entry(player.uuid(), player.username()));
        entry.name = player.username();
        entry.betrayedMe = true;
        entry.lastHostileAt = now;
    }

    public Optional<Entry> get(UUID uuid) {
        return Optional.ofNullable(entries.get(uuid));
    }

    public void prune(Instant now) {
        entries.values().removeIf(entry -> Duration.between(entry.lastSeenAt, now).compareTo(FORGET_AFTER) > 0);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static final class Entry {
        private final UUID uuid;
        private String name;
        private boolean attackedMe;
        private boolean helpedMe;
        private boolean teamedBefore;
        private boolean betrayedMe;
        private boolean killedMyTeammate;
        private double lookedStrong;
        private double lookedWeak;
        private boolean ranAwayFromMe;
        private Vec3 lastKnownPosition = Vec3.ZERO;
        private Instant lastSeenAt = Instant.EPOCH;
        private Instant lastHostileAt = Instant.EPOCH;
        private Instant lastSocialAt = Instant.EPOCH;

        private Entry(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public UUID uuid() { return uuid; }
        public String name() { return name; }
        public boolean attackedMe() { return attackedMe; }
        public boolean helpedMe() { return helpedMe; }
        public boolean teamedBefore() { return teamedBefore; }
        public boolean betrayedMe() { return betrayedMe; }
        public boolean killedMyTeammate() { return killedMyTeammate; }
        public double lookedStrong() { return lookedStrong; }
        public double lookedWeak() { return lookedWeak; }
        public boolean ranAwayFromMe() { return ranAwayFromMe; }
        public Vec3 lastKnownPosition() { return lastKnownPosition; }
        public Instant lastSeenAt() { return lastSeenAt; }
        public Instant lastHostileAt() { return lastHostileAt; }
        public Instant lastSocialAt() { return lastSocialAt; }
    }
}
