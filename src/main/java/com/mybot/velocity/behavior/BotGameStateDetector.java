package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.BotScoreboardState;
import com.mybot.velocity.bot.Vec3;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class BotGameStateDetector {
    private static final Duration INVINCIBILITY_DURATION = Duration.ofSeconds(120);
    private static final Duration LATE_GAME_AFTER = Duration.ofMinutes(23);
    private static final Duration FINAL_FIGHT_AFTER = Duration.ofMinutes(28);
    private static final double PREGAME_TO_GAME_TELEPORT_DISTANCE_SQUARED = 64.0 * 64.0;

    private EBotLifecycleState current = EBotLifecycleState.Unknown;
    private Instant activeHgSince = Instant.EPOCH;
    private String lastServer = "";
    private Vec3 lastPosition = Vec3.ZERO;
    private Vec3 pregameAnchor = null;

    public EBotLifecycleState detect(String serverName, BotWorldState state, Vec3 position, Instant now) {
        return detect(serverName, state, position, now, state.scoreboard().snapshot());
    }

    public EBotLifecycleState detect(String serverName, BotWorldState state, Vec3 position, Instant now, BotScoreboardState.Snapshot scoreboard) {
        String server = serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
        if (!server.equals(lastServer)) {
            activeHgSince = Instant.EPOCH;
            pregameAnchor = null;
            lastServer = server;
        }
        if (server.equals("lobby0") || server.equals("lobby")) {
            activeHgSince = Instant.EPOCH;
            pregameAnchor = null;
            lastPosition = position;
            return transition(EBotLifecycleState.LobbyRoaming);
        }
        if (state.spectator()) {
            lastPosition = position;
            return transition(EBotLifecycleState.SpectatorDead);
        }
        if (!server.matches("hg[0-4]")) {
            lastPosition = position;
            return transition(EBotLifecycleState.Unknown);
        }
        boolean hasKitFeather = state.inventory().hasKitFeather();
        if (hasKitFeather) {
            if (pregameAnchor == null) {
                pregameAnchor = position;
            }
            activeHgSince = Instant.EPOCH;
            state.resetMatchTiming();
            lastPosition = position;
            return transition(EBotLifecycleState.PregameWaiting);
        }
        if (activeHgSince.equals(Instant.EPOCH)) {
            Instant packetHint = state.matchStartedAt();
            activeHgSince = packetHint.equals(Instant.EPOCH) ? now : packetHint;
            state.markMatchStarted();
        }
        Duration matchAge = Duration.between(activeHgSince, now);
        lastPosition = position;
        if (scoreboard != null) {
            String phase = scoreboard.phase() == null ? "" : scoreboard.phase().toLowerCase(Locale.ROOT);
            if (scoreboard.alivePlayers() > 0 && scoreboard.alivePlayers() <= 2) {
                return transition(EBotLifecycleState.FinalFight);
            }
            if (scoreboard.alivePlayers() > 0 && scoreboard.alivePlayers() <= 5) {
                return transition(EBotLifecycleState.LateGame);
            }
            if (phase.contains("final") || phase.contains("deathmatch")) {
                return transition(EBotLifecycleState.FinalFight);
            }
            if (phase.contains("late")) {
                return transition(EBotLifecycleState.LateGame);
            }
        }
        if (matchAge.compareTo(INVINCIBILITY_DURATION) < 0) {
            return transition(EBotLifecycleState.InvincibilityStart);
        }
        if (matchAge.compareTo(FINAL_FIGHT_AFTER) >= 0) {
            return transition(EBotLifecycleState.FinalFight);
        }
        if (matchAge.compareTo(LATE_GAME_AFTER) >= 0) {
            return transition(EBotLifecycleState.LateGame);
        }
        return transition(EBotLifecycleState.ActiveHG);
    }

    public boolean serverOrPositionResetNeeded(String serverName, Vec3 position) {
        String server = serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
        return !server.equals(lastServer) || position.distanceSquaredTo(lastPosition) > 64.0 * 64.0;
    }

    public EBotLifecycleState current() {
        return current;
    }

    private EBotLifecycleState transition(EBotLifecycleState next) {
        current = next;
        return next;
    }

}
