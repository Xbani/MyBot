package com.mybot.velocity.behavior;

import com.mybot.velocity.action.BotAction;
import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.MovementInput;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.navigation.PathFollower;
import com.mybot.velocity.navigation.PathNode;
import com.mybot.velocity.navigation.Pathfinder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class BotController {
    private static final Duration REPATH_INTERVAL = Duration.ofMillis(750);

    private final HgBehaviorConfig config;
    private final BotActionQueue actions;
    private final HgGameBrain brain = new HgGameBrain();
    private final Pathfinder pathfinder = new Pathfinder();
    private final PathFollower follower = new PathFollower();

    private BotIntent intent = BotIntent.IDLE;
    private List<PathNode> path = List.of();
    private Vec3 pathTarget = Vec3.ZERO;
    private Instant lastPathAt = Instant.EPOCH;

    public BotController(HgBehaviorConfig config, BotActionQueue actions) {
        this.config = config;
        this.actions = actions;
    }

    public ControlPlan tick(BotWorldState state, BotPhysics physics) {
        HgGameBrain.Decision decision = brain.decide(physics.position(), state.health(), state.inventory(), state.trackedPlayers(), config);
        intent = decision.intent();
        Optional<TrackedPlayer> target = decision.target();
        state.inventory().bestWeaponHotbarSlot().ifPresent(slot -> {
            if (state.inventory().selectedHotbarSlot() != slot) {
                actions.enqueueIfAbsent(BotAction.SetHotbarSlot.class, new BotAction.SetHotbarSlot(slot));
            }
        });
        if (intent == BotIntent.HEAL) {
            actions.enqueueIfAbsent(BotAction.RightClickItem.class, new BotAction.RightClickItem());
            return new ControlPlan(MovementInput.NONE, target);
        }
        if (target.isEmpty()) {
            return new ControlPlan(MovementInput.NONE, Optional.empty());
        }
        Vec3 targetPosition = target.get().position();
        BotPhysics.LookAngles look = BotPhysics.lookAt(physics.position().add(0, 1.62, 0), targetPosition.add(0, 1.62, 0));
        physics.setLook(smooth(physics.yaw(), look.yaw(), config.aimAccuracy()), smooth(physics.pitch(), look.pitch(), config.aimAccuracy()));
        if (intent == BotIntent.FLEE) {
            return new ControlPlan(fleeInput(physics.position(), targetPosition), target);
        }
        if (brain.combat().canMelee(physics.position(), target.get(), config)) {
            actions.enqueue(new BotAction.SwingMainHand());
            actions.enqueue(new BotAction.LeftClickEntity(target.get().entityId()));
            return new ControlPlan(new MovementInput(0, strafeOscillation(), false, true, false), target);
        }
        MovementInput followInput = pathInput(state, physics.position(), targetPosition);
        return new ControlPlan(followInput, target);
    }

    public BotIntent intent() {
        return intent;
    }

    private MovementInput pathInput(BotWorldState state, Vec3 position, Vec3 target) {
        Instant now = Instant.now();
        if (path.isEmpty() || follower.stuck() || target.horizontalDistanceTo(pathTarget) > 2.0 || Duration.between(lastPathAt, now).compareTo(REPATH_INTERVAL) > 0) {
            path = pathfinder.findPath(state.blocks(), position, target).orElse(List.of());
            pathTarget = target;
            lastPathAt = now;
            follower.reset();
        }
        if (path.isEmpty()) {
            return directInput(position, target);
        }
        return follower.follow(position, path, position.horizontalDistanceTo(target) > 6);
    }

    private MovementInput directInput(Vec3 position, Vec3 target) {
        double distance = position.horizontalDistanceTo(target);
        if (distance <= 3.25) {
            return MovementInput.NONE;
        }
        return new MovementInput(1, 0, false, distance > 7, false);
    }

    private MovementInput fleeInput(Vec3 position, Vec3 threat) {
        double distance = position.horizontalDistanceTo(threat);
        boolean sneak = distance < 2.5;
        return new MovementInput(-1, strafeOscillation(), false, !sneak, sneak);
    }

    private double strafeOscillation() {
        return (System.currentTimeMillis() / 700L) % 2 == 0 ? 0.45 : -0.45;
    }

    private float smooth(float current, float target, double accuracy) {
        double factor = Math.max(0.05, Math.min(1.0, accuracy));
        return (float) (current + wrapDegrees(target - current) * factor);
    }

    private float wrapDegrees(float degrees) {
        float value = degrees % 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    public record ControlPlan(MovementInput movement, Optional<TrackedPlayer> target) { }
}
