package com.mybot.velocity.action;

import com.mybot.velocity.bot.BotSession;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public final class BotActionQueue {
    private final Queue<BotAction> actions = new ArrayDeque<>();
    private final Map<String, Long> nextAllowedAt = new HashMap<>();
    private final Clock clock;
    private final int maxActionsPerTick;

    public BotActionQueue() {
        this(Clock.systemUTC(), 2);
    }

    public BotActionQueue(Clock clock, int maxActionsPerTick) {
        this.clock = clock;
        this.maxActionsPerTick = maxActionsPerTick;
    }

    public synchronized void enqueue(BotAction action) {
        actions.add(action);
    }

    public synchronized void enqueueIfAbsent(Class<? extends BotAction> actionType, BotAction action) {
        boolean exists = actions.stream().anyMatch(actionType::isInstance);
        if (!exists) {
            actions.add(action);
        }
    }

    public synchronized int tick(BotSession session) {
        long now = clock.millis();
        int executed = 0;
        while (executed < maxActionsPerTick && !actions.isEmpty()) {
            BotAction action = actions.peek();
            long allowedAt = nextAllowedAt.getOrDefault(action.key(), 0L);
            if (allowedAt > now) {
                break;
            }
            actions.remove();
            action.execute(session);
            nextAllowedAt.put(action.key(), now + action.cooldownMillis());
            executed++;
        }
        return executed;
    }

    public synchronized int size() {
        return actions.size();
    }
}
