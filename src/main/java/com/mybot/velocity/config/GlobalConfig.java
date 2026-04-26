package com.mybot.velocity.config;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable representation of the root config.yml contents.
 */
public record GlobalConfig(
        int maxBots,
        InetSocketAddress velocityEndpoint,
        AuthConfig authConfig,
        String defaultGraph,
        boolean demoEnabled,
        LoggingConfig logging,
        SchedulerConfig schedulers,
        DataFolderConfig dataFolders
) {

    public GlobalConfig {
        Objects.requireNonNull(velocityEndpoint, "velocityEndpoint");
        Objects.requireNonNull(authConfig, "authConfig");
        Objects.requireNonNull(defaultGraph, "defaultGraph");
        Objects.requireNonNull(logging, "logging");
        Objects.requireNonNull(schedulers, "schedulers");
        Objects.requireNonNull(dataFolders, "dataFolders");
    }

    public record AuthConfig(String mode, String mojangEmail, String mojangPassword) {
        public boolean onlineMode() {
            return "online".equalsIgnoreCase(mode);
        }
    }

    public record LoggingConfig(String level, boolean commandAudits) { }

    public record SchedulerConfig(Duration botTick, Duration graphTick) { }

    public record DataFolderConfig(String botsDir, String graphsDir, String schematicsDir) { }
}
