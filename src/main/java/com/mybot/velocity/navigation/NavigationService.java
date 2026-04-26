package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.WorldSnapshot;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified pathfinding facade. The concrete navigation implementation can be swapped
 * later without changing call-sites.
 */
public final class NavigationService {

    private final Logger logger;

    public NavigationService(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<NavigationResult> navigateAsync(String botId,
                                                              double[] destination,
                                                              WorldSnapshot snapshot) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("Planning path for {} to {}", botId, destination);
            // Placeholder: pretend path always succeeds after a short delay.
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new NavigationResult(true, Duration.ofSeconds(1), "mock-path");
        });
    }

    public record NavigationResult(boolean success, Duration eta, String planner) { }
}
