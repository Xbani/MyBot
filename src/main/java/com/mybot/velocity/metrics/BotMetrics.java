package com.mybot.velocity.metrics;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class BotMetrics {

    private final AtomicInteger activeBots = new AtomicInteger();
    private final AtomicLong commandsExecuted = new AtomicLong();
    private final AtomicLong graphTransitions = new AtomicLong();
    private volatile Instant lastReload = Instant.now();

    public void setActiveBots(int count) {
        activeBots.set(count);
    }

    public void incrementCommands() {
        commandsExecuted.incrementAndGet();
    }

    public void incrementTransitions() {
        graphTransitions.incrementAndGet();
    }

    public void markReload() {
        lastReload = Instant.now();
    }

    public Map<String, Object> snapshot() {
        return Map.of(
                "activeBots", activeBots.get(),
                "commandsExecuted", commandsExecuted.get(),
                "graphTransitions", graphTransitions.get(),
                "lastReload", lastReload.toString()
        );
    }

    public int activeBots() {
        return activeBots.get();
    }

    public long commandsExecuted() {
        return commandsExecuted.get();
    }

    public long graphTransitions() {
        return graphTransitions.get();
    }

    public Instant lastReload() {
        return lastReload;
    }
}
