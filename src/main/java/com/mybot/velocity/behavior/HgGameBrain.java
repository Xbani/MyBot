package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotInventoryState;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;

import java.util.Optional;

public final class HgGameBrain {
    private final CombatController combat = new CombatController();

    public Decision decide(Vec3 botPosition,
                           float health,
                           BotInventoryState inventory,
                           java.util.Collection<TrackedPlayer> players,
                           HgBehaviorConfig config) {
        Optional<TrackedPlayer> target = combat.selectTarget(botPosition, players, config);
        BotIntent intent = combat.chooseIntent(health, inventory, target, botPosition, config);
        return new Decision(intent, target);
    }

    public CombatController combat() {
        return combat;
    }

    public record Decision(BotIntent intent, Optional<TrackedPlayer> target) { }
}
