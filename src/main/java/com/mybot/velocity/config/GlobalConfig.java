package com.mybot.velocity.config;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
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
        DataFolderConfig dataFolders,
        DashboardConfig dashboard
) {

    public GlobalConfig {
        Objects.requireNonNull(velocityEndpoint, "velocityEndpoint");
        Objects.requireNonNull(authConfig, "authConfig");
        Objects.requireNonNull(defaultGraph, "defaultGraph");
        Objects.requireNonNull(logging, "logging");
        Objects.requireNonNull(schedulers, "schedulers");
        Objects.requireNonNull(dataFolders, "dataFolders");
        Objects.requireNonNull(dashboard, "dashboard");
    }

    public record AuthConfig(String mode, String mojangEmail, String mojangPassword) {
        public boolean onlineMode() {
            return "online".equalsIgnoreCase(mode);
        }
    }

    public record LoggingConfig(String level, boolean commandAudits) { }

    public record SchedulerConfig(Duration botTick, Duration graphTick) { }

    public record DataFolderConfig(String botsDir, String graphsDir, String schematicsDir) { }

    public record DashboardConfig(
            boolean enabled,
            String host,
            int port,
            String publicUrl,
            String authToken,
            List<DashboardSource> sources
    ) {
        public DashboardConfig {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(publicUrl, "publicUrl");
            Objects.requireNonNull(authToken, "authToken");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        }
    }

    public record DashboardSource(String id, String name, String baseUrl, String token) {
        public DashboardSource {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(baseUrl, "baseUrl");
            Objects.requireNonNull(token, "token");
        }
    }
}
