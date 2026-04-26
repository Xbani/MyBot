package com.mybot.velocity.bot;

import org.slf4j.Logger;

import java.util.Objects;

/**
 * Placeholder packet bridge. Real implementation should register MCProtocolLib
 * listeners to transform raw packets into a {@link WorldSnapshot}. For now we
 * expose minimal helpers so the behavior engine can store mock sensor values
 * during tests.
 */
public final class BotPacketBridge {

    private final Logger logger;
    private final WorldSnapshot snapshot = new WorldSnapshot();

    public BotPacketBridge(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public WorldSnapshot snapshot() {
        return snapshot;
    }

    public void debug(String message) {
        logger.debug("[packet-bridge] {}", message);
    }
}
