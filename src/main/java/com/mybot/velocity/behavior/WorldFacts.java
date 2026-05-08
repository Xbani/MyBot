package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotScoreboardState;
import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;
import org.cloudburstmc.math.vector.Vector3i;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record WorldFacts(
        String scoreboardPhase,
        int feastSeconds,
        int alivePlayers,
        boolean feastSoon,
        boolean finalFightLikely,
        List<TrackedPlayer> visiblePlayers,
        Optional<TrackedPlayer> nearestPlayer,
        Optional<TrackedPlayer> nearestThreat,
        Optional<Vec3> bestLootTarget,
        boolean needsWeapon,
        boolean needsFood,
        boolean needsArmor,
        boolean hasUsefulBlocks
) {
    public static WorldFacts from(BotWorldState state,
                                  Vec3 position,
                                  BotMemory memory,
                                  BotPersonality personality,
                                  HgBehaviorConfig config,
                                  Instant now) {
        BotScoreboardState.Snapshot scoreboard = state.scoreboard().snapshot();
        List<TrackedPlayer> visible = state.trackedPlayers().stream()
                .filter(player -> player.position().distanceSquaredTo(position) < 48.0 * 48.0)
                .toList();
        Optional<TrackedPlayer> nearest = visible.stream()
                .min(Comparator.comparingDouble(player -> player.position().distanceSquaredTo(position)));
        Optional<TrackedPlayer> nearestThreat = visible.stream()
                .filter(player -> position.distanceSquaredTo(player.position()) <= config.detectionRadius() * config.detectionRadius())
                .filter(player -> memory.teammate().isEmpty() || !memory.teammate().get().uuid().equals(player.uuid()))
                .max(Comparator.comparingDouble(player -> threatScore(position, player, memory)));
        boolean needsWeapon = state.inventory().bestWeaponHotbarSlot().isEmpty();
        boolean needsFood = state.health() < config.healHealth() && !state.inventory().hasLikelyFoodOrHeal();
        boolean needsArmor = !state.inventory().hasLikelyArmor();
        boolean hasUsefulBlocks = state.inventory().hasUsefulBlocks();
        Optional<Vec3> lootTarget = bestLootTarget(state, position, config, needsWeapon || needsFood || needsArmor);
        int alive = scoreboard.alivePlayers();
        boolean feastSoon = scoreboard.feastSeconds() >= 0 && scoreboard.feastSeconds() <= 90;
        boolean finalFightLikely = alive > 0 && alive <= 2;
        return new WorldFacts(scoreboard.phase(), scoreboard.feastSeconds(), alive, feastSoon, finalFightLikely,
                visible, nearest, nearestThreat, lootTarget, needsWeapon, needsFood, needsArmor, hasUsefulBlocks);
    }

    public boolean hasLootTarget() {
        return bestLootTarget.isPresent();
    }

    public boolean underImmediateThreat(Vec3 position) {
        return nearestThreat.map(player -> position.horizontalDistanceTo(player.position()) < 5.0).orElse(false);
    }

    private static double threatScore(Vec3 position, TrackedPlayer player, BotMemory memory) {
        double distance = Math.max(1.0, position.horizontalDistanceTo(player.position()));
        double score = 16.0 / distance;
        score += memory.reputation().get(player.uuid()).map(rep -> rep.attackedMe() ? 8.0 : 0.0).orElse(0.0);
        return score;
    }

    private static Optional<Vec3> bestLootTarget(BotWorldState state, Vec3 position, HgBehaviorConfig config, boolean needsGear) {
        if (!needsGear && state.inventory().hasLikelyFoodOrHeal() && state.inventory().bestWeaponHotbarSlot().isPresent()) {
            return Optional.empty();
        }
        BotResourceScanner scanner = new BotResourceScanner(config.traits());
        Optional<Vector3i> table = scanner.nearestCraftingTable(state.blocks(), position, 18);
        Optional<Vector3i> chest = scanner.nearestChest(state.blocks(), position, 22);
        return chest.or(() -> table).map(WorldFacts::center);
    }

    private static Vec3 center(Vector3i pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
