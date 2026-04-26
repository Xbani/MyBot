package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotInventoryState;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class CombatController {
    public Optional<TrackedPlayer> selectTarget(Vec3 botPosition, Collection<TrackedPlayer> players, HgBehaviorConfig config) {
        return players.stream()
                .filter(player -> !player.isBot())
                .filter(player -> player.position().distanceSquaredTo(botPosition) <= config.detectionRadius() * config.detectionRadius())
                .max(Comparator.comparingDouble(player -> score(botPosition, player)));
    }

    public BotIntent chooseIntent(float health, BotInventoryState inventory, Optional<TrackedPlayer> target, Vec3 botPosition, HgBehaviorConfig config) {
        if (health <= config.fleeHealth()) {
            return BotIntent.FLEE;
        }
        if (health <= config.healHealth() && inventory.hasLikelyFoodOrHeal()) {
            return BotIntent.HEAL;
        }
        if (target.isEmpty()) {
            return BotIntent.IDLE;
        }
        double distance = botPosition.horizontalDistanceTo(target.get().position());
        return distance <= config.detectionRadius() ? BotIntent.FIGHT : BotIntent.FOLLOW;
    }

    public boolean canMelee(Vec3 botPosition, TrackedPlayer target, HgBehaviorConfig config) {
        return botPosition.distanceSquaredTo(target.position()) <= config.meleeRange() * config.meleeRange();
    }

    private double score(Vec3 botPosition, TrackedPlayer player) {
        double distance = Math.max(0.1, botPosition.horizontalDistanceTo(player.position()));
        return 100.0 / distance;
    }
}
