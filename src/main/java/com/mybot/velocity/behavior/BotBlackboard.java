package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record BotBlackboard(
        Instant now,
        String serverName,
        EBotLifecycleState lifecycle,
        BotGamePlan.Phase phase,
        Vec3 position,
        float health,
        int food,
        BotWorldState state,
        WorldFacts facts,
        List<TrackedPlayer> visiblePlayers,
        Optional<TrackedPlayer> nearestPlayer,
        Optional<TrackedPlayer> teammate,
        double panic
) {
    public static BotBlackboard from(Instant now,
                                     String serverName,
                                     EBotLifecycleState lifecycle,
                                     BotGamePlan.Phase phase,
                                     Vec3 position,
                                     BotWorldState state,
                                     BotMemory memory,
                                     BotPersonality personality) {
        WorldFacts facts = WorldFacts.from(state, position, memory, personality, HgBehaviorConfig.defaults(), now);
        return from(now, serverName, lifecycle, phase, position, state, memory, personality, HgBehaviorConfig.defaults(), facts);
    }

    public static BotBlackboard from(Instant now,
                                     String serverName,
                                     EBotLifecycleState lifecycle,
                                     BotGamePlan.Phase phase,
                                     Vec3 position,
                                     BotWorldState state,
                                     BotMemory memory,
                                     BotPersonality personality,
                                     HgBehaviorConfig config,
                                     WorldFacts facts) {
        double lowHealthPanic = Math.max(0.0, 1.0 - state.health() / 20.0);
        double crowdPanic = Math.min(0.35, facts.visiblePlayers().stream().filter(player -> position.horizontalDistanceTo(player.position()) < 8.0).count() * 0.08);
        double damagePanic = java.time.Duration.between(state.lastDamageAt(), now).toMillis() < 3500 ? 0.25 : 0.0;
        double feastPanic = facts.feastSoon() && facts.nearestThreat().isPresent() ? 0.10 : 0.0;
        double panic = Math.max(0.0, Math.min(1.0, lowHealthPanic + crowdPanic + damagePanic - personality.courage() * 0.22));
        panic = Math.max(0.0, Math.min(1.0, panic + feastPanic));
        return new BotBlackboard(now, serverName, lifecycle, phase, position, state.health(), state.food(), state,
                facts, facts.visiblePlayers(), facts.nearestPlayer(), memory.teammate(), panic);
    }
}
