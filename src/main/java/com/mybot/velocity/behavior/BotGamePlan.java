package com.mybot.velocity.behavior;

import java.time.Duration;
import java.time.Instant;

public final class BotGamePlan {
    public enum Phase {
        Lobby,
        Pregame,
        Invincibility,
        EarlyGame,
        MidGame,
        FeastPhase,
        LateGame,
        FinalDuel
    }

    private Phase phase = Phase.Lobby;
    private Instant lastChangedAt = Instant.EPOCH;

    public Phase update(EBotLifecycleState lifecycle, BotBlackboard previous, Instant now) {
        return update(lifecycle, previous, null, now);
    }

    public Phase update(EBotLifecycleState lifecycle, BotBlackboard previous, WorldFacts facts, Instant now) {
        Phase next = switch (lifecycle) {
            case LobbyRoaming, SpectatorDead, Unknown -> Phase.Lobby;
            case PregameWaiting -> Phase.Pregame;
            case InvincibilityStart -> Phase.Invincibility;
            case LateGame -> Phase.LateGame;
            case FinalFight -> Phase.FinalDuel;
            case ActiveHG -> activePhase(previous, facts, now);
        };
        if (next != phase) {
            phase = next;
            lastChangedAt = now;
        }
        return phase;
    }

    public Phase phase() {
        return phase;
    }

    public Instant lastChangedAt() {
        return lastChangedAt;
    }

    private Phase activePhase(BotBlackboard previous, WorldFacts facts, Instant now) {
        if (facts != null) {
            String scoreboardPhase = facts.scoreboardPhase() == null ? "" : facts.scoreboardPhase().toLowerCase(java.util.Locale.ROOT);
            if (facts.finalFightLikely() || scoreboardPhase.contains("final") || scoreboardPhase.contains("deathmatch")) {
                return Phase.FinalDuel;
            }
            if ((facts.alivePlayers() > 0 && facts.alivePlayers() <= 5) || scoreboardPhase.contains("late")) {
                return Phase.LateGame;
            }
            if (facts.feastSoon() || scoreboardPhase.contains("feast")) {
                return Phase.FeastPhase;
            }
        }
        if (previous == null || previous.phase() == Phase.Invincibility || previous.phase() == Phase.Pregame) {
            return Phase.EarlyGame;
        }
        if (previous.visiblePlayers().size() <= 5) {
            return Phase.LateGame;
        }
        if (Duration.between(lastChangedAt, now).compareTo(Duration.ofMinutes(10)) > 0) {
            return Phase.FeastPhase;
        }
        return previous.phase() == Phase.EarlyGame ? Phase.EarlyGame : Phase.MidGame;
    }
}
