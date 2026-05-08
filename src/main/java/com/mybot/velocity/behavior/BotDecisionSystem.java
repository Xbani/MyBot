package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotInventoryState;
import com.mybot.velocity.bot.TrackedPlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;

public final class BotDecisionSystem {
    private static final Duration TARGET_SWITCH_DELAY = Duration.ofMillis(900);

    private final Random random;
    private Decision committed = Decision.idle("boot");
    private Instant nextDecisionAt = Instant.EPOCH;
    private Instant lastTargetSwitchAt = Instant.EPOCH;

    public BotDecisionSystem(long seed) {
        this.random = new Random(seed);
    }

    public Decision decide(BotBlackboard board,
                           BotPersonality personality,
                           BotSkillProfile skill,
                           BotMemory memory,
                           HgBehaviorConfig config) {
        if (canKeepCommitted(board)) {
            return committed;
        }
        if (!combatAllowed(board.lifecycle())) {
            committed = nonCombatDecision(board, personality, config);
            scheduleNext(board.now(), skill, 0.5);
            return committed;
        }

        Optional<TrackedPlayer> target = selectTarget(board, personality, memory, config);
        double threat = target.map(player -> threatScore(board, player, memory)).orElse(0.0);
        double confidence = target.map(player -> fightConfidence(board, player, personality, memory)).orElse(0.0);
        double fightScore = target.map(player -> confidence * 100.0 + personality.aggression() * 25.0 - board.panic() * 55.0).orElse(0.0);
        double fleeScore = threat * 92.0 + board.panic() * 70.0 + (board.health() < config.fleeHealth() ? 85.0 : 0.0);
        double healScore = board.health() < config.healHealth() && board.state().inventory().hasLikelyFoodOrHeal() ? 68.0 + board.panic() * 20.0 : 0.0;
        double lootScore = lootScore(board, personality);
        double teamScore = teamScore(board, personality, memory);

        fightScore = noisy(fightScore, personality, skill);
        fleeScore = noisy(fleeScore, personality, skill);
        healScore = noisy(healScore, personality, skill);
        lootScore = noisy(lootScore, personality, skill);
        teamScore = noisy(teamScore, personality, skill);

        BotIntent intent = BotIntent.IDLE;
        String reason = "idle";
        double best = 8.0;
        if (healScore > best) {
            best = healScore;
            intent = BotIntent.HEAL;
            reason = "low health and has food/heal";
        }
        if (fleeScore > best) {
            best = fleeScore;
            intent = BotIntent.FLEE;
            reason = "threat/panic too high";
        }
        if (lootScore > best && board.lifecycle() != EBotLifecycleState.LateGame && board.lifecycle() != EBotLifecycleState.FinalFight) {
            best = lootScore;
            intent = BotIntent.LOOT;
            reason = board.facts().hasLootTarget() ? "known loot/resource target" : "needs gear or early route";
        }
        if (teamScore > best) {
            best = teamScore;
            intent = BotIntent.FOLLOW;
            reason = "temporary team/proximity";
        }
        if (target.isPresent() && fightScore > best && confidence > 0.32) {
            intent = BotIntent.FIGHT;
            reason = confidence > 0.62 ? "confident fight" : "risky human commit";
        }
        if (target.isPresent() && intent == BotIntent.IDLE) {
            intent = BotIntent.FOLLOW;
            reason = "stalking visible player";
        }

        committed = new Decision(intent, strategyFor(intent, board, confidence), target, confidence, board.panic(), threat, fightScore, fleeScore, lootScore, teamScore, reason);
        scheduleNext(board.now(), skill, board.panic());
        return committed;
    }

    private boolean canKeepCommitted(BotBlackboard board) {
        if (board.now().isAfter(nextDecisionAt)) {
            return false;
        }
        if ((committed.intent() == BotIntent.FIGHT || committed.intent() == BotIntent.FOLLOW)
                && committed.target().stream().noneMatch(target -> board.visiblePlayers().stream().anyMatch(player -> player.entityId() == target.entityId()))) {
            return false;
        }
        return board.panic() < 0.74 || committed.intent() == BotIntent.FLEE || committed.intent() == BotIntent.HEAL;
    }

    private Decision nonCombatDecision(BotBlackboard board, BotPersonality personality, HgBehaviorConfig config) {
        if (board.lifecycle() == EBotLifecycleState.InvincibilityStart && board.state().inventory().bestWeaponHotbarSlot().isEmpty()) {
            return new Decision(BotIntent.LOOT, BotStrategy.GearUp, Optional.empty(), 0, board.panic(), 0, 0, 0, 75, 0, "invincibility loot route");
        }
        if (board.lifecycle() == EBotLifecycleState.PregameWaiting || board.lifecycle() == EBotLifecycleState.LobbyRoaming) {
            return new Decision(BotIntent.IDLE, BotStrategy.Survive, board.nearestPlayer().filter(player -> personality.curiosity() > 0.35), 0, board.panic(), 0, 0, 0, 0, 0, "waiting/roaming");
        }
        return Decision.idle("non-combat lifecycle");
    }

    private Optional<TrackedPlayer> selectTarget(BotBlackboard board, BotPersonality personality, BotMemory memory, HgBehaviorConfig config) {
        Optional<TrackedPlayer> previous = committed.target().flatMap(old -> board.visiblePlayers().stream()
                .filter(player -> player.entityId() == old.entityId())
                .findFirst());
        if (previous.isPresent() && Duration.between(lastTargetSwitchAt, board.now()).compareTo(TARGET_SWITCH_DELAY) < 0) {
            return previous;
        }
        Optional<TrackedPlayer> selected = board.visiblePlayers().stream()
                .filter(player -> board.position().distanceSquaredTo(player.position()) <= config.detectionRadius() * config.detectionRadius())
                .filter(player -> board.teammate().isEmpty() || !board.teammate().get().uuid().equals(player.uuid()))
                .max(Comparator.comparingDouble(player -> targetScore(board, player, personality, memory)));
        if (selected.isPresent() && previous.map(player -> player.entityId() != selected.get().entityId()).orElse(true)) {
            lastTargetSwitchAt = board.now();
        }
        return selected;
    }

    private double targetScore(BotBlackboard board, TrackedPlayer player, BotPersonality personality, BotMemory memory) {
        double distance = Math.max(0.1, board.position().horizontalDistanceTo(player.position()));
        double score = 80.0 / distance;
        score += memory.reputation().get(player.uuid()).map(rep -> rep.lookedWeak() * 30.0 - rep.lookedStrong() * 25.0).orElse(0.0);
        score += player.isBot() ? personality.betrayalLikelihood() * 8.0 : 0.0;
        return score;
    }

    private double threatScore(BotBlackboard board, TrackedPlayer player, BotMemory memory) {
        double distance = Math.max(1.0, board.position().horizontalDistanceTo(player.position()));
        double score = (12.0 / distance) * 0.45;
        score += memory.reputation().get(player.uuid()).map(rep -> rep.attackedMe() ? 0.35 : 0.0).orElse(0.0);
        score += board.health() < 9.0 ? 0.28 : 0.0;
        return Math.max(0.0, Math.min(1.0, score));
    }

    private double fightConfidence(BotBlackboard board, TrackedPlayer player, BotPersonality personality, BotMemory memory) {
        BotInventoryState inventory = board.state().inventory();
        double gear = inventory.bestWeaponHotbarSlot().isPresent() ? 0.22 : -0.12;
        double health = (board.health() - 8.0) / 20.0;
        double distance = board.position().horizontalDistanceTo(player.position()) < 7.0 ? 0.08 : -0.06;
        double reputation = memory.reputation().get(player.uuid()).map(rep -> rep.lookedWeak() * 0.18 - rep.lookedStrong() * 0.20).orElse(0.0);
        double team = board.teammate().filter(t -> board.position().horizontalDistanceTo(t.position()) < 9.0).map(t -> 0.14).orElse(0.0);
        double phase = switch (board.lifecycle()) {
            case LateGame -> -0.08;
            case FinalFight -> 0.16;
            default -> 0.0;
        };
        return Math.max(0.0, Math.min(1.0, 0.46 + gear + health + distance + reputation + team + phase + personality.fightConfidenceBias()));
    }

    private double lootScore(BotBlackboard board, BotPersonality personality) {
        double base = switch (board.phase()) {
            case Invincibility -> 72.0;
            case EarlyGame -> 58.0;
            case MidGame -> 35.0;
            case FeastPhase -> board.facts().needsWeapon() || board.facts().needsArmor() ? 42.0 : 18.0;
            default -> 8.0;
        };
        if (board.state().inventory().bestWeaponHotbarSlot().isEmpty()) {
            base += 28.0;
        }
        if (board.facts().hasLootTarget()) {
            base += 20.0;
        }
        if (board.facts().feastSoon() && !board.facts().needsWeapon() && board.health() >= 14.0f) {
            base += 22.0;
        }
        return base * (0.65 + personality.greed() * 0.7) - board.panic() * 60.0;
    }

    private double teamScore(BotBlackboard board, BotPersonality personality, BotMemory memory) {
        if (board.teammate().isPresent()) {
            return 42.0;
        }
        return board.nearestPlayer()
                .filter(TrackedPlayer::isBot)
                .filter(player -> board.position().horizontalDistanceTo(player.position()) < 6.0)
                .filter(player -> memory.reputation().get(player.uuid()).map(rep -> !rep.betrayedMe()).orElse(true))
                .map(player -> 38.0 * personality.teamingLikelihood() - board.panic() * 20.0)
                .orElse(0.0);
    }

    private boolean combatAllowed(EBotLifecycleState lifecycle) {
        return lifecycle == EBotLifecycleState.ActiveHG || lifecycle == EBotLifecycleState.LateGame || lifecycle == EBotLifecycleState.FinalFight;
    }

    private double noisy(double score, BotPersonality personality, BotSkillProfile skill) {
        double noobFactor = switch (personality.skillLevel()) {
            case NOOB -> 15.0;
            case AVERAGE -> 9.0;
            case TRYHARD -> 4.5;
        };
        return score + (random.nextDouble() - 0.5) * noobFactor + skill.lootMistakeChance() * (random.nextDouble() - 0.5) * 12.0;
    }

    private BotStrategy strategyFor(BotIntent intent, BotBlackboard board, double confidence) {
        if (intent == BotIntent.HEAL) {
            return BotStrategy.Recover;
        }
        if (intent == BotIntent.FLEE) {
            return BotStrategy.Disengage;
        }
        if (intent == BotIntent.FOLLOW) {
            return BotStrategy.Regroup;
        }
        if (intent == BotIntent.LOOT) {
            return board.facts().feastSoon() && !board.facts().needsWeapon() ? BotStrategy.ContestFeast : BotStrategy.GearUp;
        }
        if (intent == BotIntent.FIGHT) {
            return confidence > 0.62 ? BotStrategy.HuntWeakTarget : BotStrategy.Survive;
        }
        return BotStrategy.Survive;
    }

    private void scheduleNext(Instant now, BotSkillProfile skill, double panic) {
        long delay = skill.randomReactionDelay(random);
        delay = (long) Math.max(110L, delay * (1.0 - panic * 0.35));
        nextDecisionAt = now.plusMillis(delay);
    }

    public record Decision(BotIntent intent,
                           BotStrategy strategy,
                           Optional<TrackedPlayer> target,
                           double confidence,
                           double panic,
                           double threatScore,
                           double fightScore,
                           double fleeScore,
                           double lootScore,
                           double teamScore,
                           String reason) {
        static Decision idle(String reason) {
            return new Decision(BotIntent.IDLE, BotStrategy.Survive, Optional.empty(), 0, 0, 0, 0, 0, 0, 0, reason);
        }
    }
}
