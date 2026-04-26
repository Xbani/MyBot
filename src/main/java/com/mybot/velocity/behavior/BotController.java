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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class BotController {
    private static final Duration REPATH_INTERVAL = Duration.ofMillis(750);
    private static final double BOT_AVOIDANCE_RADIUS = 1.35;
    private static final double BOT_AVOIDANCE_STRENGTH = 0.65;
    private static final double BOT_AVOIDANCE_HEIGHT = 2.2;

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
            return new ControlPlan(avoidBots(MovementInput.NONE, physics, state.trackedPlayers()), target);
        }
        if (target.isEmpty()) {
            return new ControlPlan(avoidBots(MovementInput.NONE, physics, state.trackedPlayers()), Optional.empty());
        }
        Vec3 targetPosition = target.get().position();
        BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), BotPhysics.bodyAimPosition(targetPosition));
        physics.setLook(smooth(physics.yaw(), look.yaw(), config.aimAccuracy()), smooth(physics.pitch(), look.pitch(), config.aimAccuracy()));
        if (intent == BotIntent.FLEE) {
            return new ControlPlan(avoidBots(fleeInput(physics.position(), targetPosition), physics, state.trackedPlayers()), target);
        }
        if (targetPosition.y() > physics.position().y() + 0.45) {
            return new ControlPlan(avoidBots(pathInput(state, physics.position(), targetPosition), physics, state.trackedPlayers()), target);
        }
        if (brain.combat().canMelee(physics.position(), target.get(), config)) {
            actions.enqueue(new BotAction.SwingMainHand());
            actions.enqueue(new BotAction.LeftClickEntity(target.get().entityId()));
            return new ControlPlan(avoidBots(new MovementInput(0, strafeOscillation(), false, true, false), physics, state.trackedPlayers()), target);
        }
        MovementInput followInput = pathInput(state, physics.position(), targetPosition);
        return new ControlPlan(avoidBots(followInput, physics, state.trackedPlayers()), target);
    }

    public BotIntent intent() {
        return intent;
    }

    public List<PathNode> path() {
        return List.copyOf(path);
    }

    public Vec3 pathTarget() {
        return pathTarget;
    }

    public boolean pathStuck() {
        return follower.stuck();
    }

    private MovementInput pathInput(BotWorldState state, Vec3 position, Vec3 target) {
        if (target.y() > position.y() + 0.45 && position.horizontalDistanceTo(target) < 2.25) {
            path = List.of();
            follower.reset();
            pathTarget = target;
            return directInput(position, target);
        }
        Instant now = Instant.now();
        if (path.isEmpty() || follower.stuck() || target.horizontalDistanceTo(pathTarget) > 2.0 || Duration.between(lastPathAt, now).compareTo(REPATH_INTERVAL) > 0) {
            double goalRadius = target.y() > position.y() + 0.45 ? 0.8 : Math.max(1.4, config.meleeRange() - 0.7);
            path = pathfinder.findPath(state.blocks(), position, target, goalRadius).orElse(List.of());
            pathTarget = target;
            lastPathAt = now;
            follower.reset();
        }
        if (path.isEmpty()) {
            return directInput(position, target);
        }
        return follower.follow(position, BotPhysics.lookAt(BotPhysics.eyePosition(position), BotPhysics.bodyAimPosition(target)).yaw(),
                path, position.horizontalDistanceTo(target) > 6);
    }

    private MovementInput directInput(Vec3 position, Vec3 target) {
        double distance = position.horizontalDistanceTo(target);
        boolean jump = target.y() > position.y() + 0.45 && distance < 4.5;
        if (distance <= 3.25 && !jump) {
            return MovementInput.NONE;
        }
        return new MovementInput(1, 0, jump, distance > 7, false);
    }

    private MovementInput fleeInput(Vec3 position, Vec3 threat) {
        double distance = position.horizontalDistanceTo(threat);
        boolean sneak = distance < 2.5;
        return new MovementInput(-1, strafeOscillation(), false, !sneak, sneak);
    }

    private MovementInput avoidBots(MovementInput input, BotPhysics physics, Collection<TrackedPlayer> players) {
        Vec3 position = physics.position();
        double awayX = 0;
        double awayZ = 0;
        for (TrackedPlayer player : players) {
            if (!player.isBot() || Math.abs(player.position().y() - position.y()) > BOT_AVOIDANCE_HEIGHT) {
                continue;
            }
            double dx = position.x() - player.position().x();
            double dz = position.z() - player.position().z();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared >= BOT_AVOIDANCE_RADIUS * BOT_AVOIDANCE_RADIUS) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            if (distance < 1.0E-4) {
                double fallbackAngle = Math.toRadians(physics.yaw() + player.entityId() * 37.0);
                dx = Math.cos(fallbackAngle);
                dz = Math.sin(fallbackAngle);
                distance = 1.0;
            }
            double pressure = (BOT_AVOIDANCE_RADIUS - distance) / BOT_AVOIDANCE_RADIUS;
            awayX += (dx / distance) * pressure;
            awayZ += (dz / distance) * pressure;
        }
        double pressure = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (pressure < 1.0E-6) {
            return input;
        }
        double yawRad = Math.toRadians(physics.yaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = Math.cos(yawRad);
        double strafeZ = Math.sin(yawRad);
        double forwardAvoidance = awayX * forwardX + awayZ * forwardZ;
        double strafeAvoidance = awayX * strafeX + awayZ * strafeZ;
        boolean sprint = input.sprint() && pressure < 0.35;
        return new MovementInput(
                input.forward() + forwardAvoidance * BOT_AVOIDANCE_STRENGTH,
                input.strafe() + strafeAvoidance * BOT_AVOIDANCE_STRENGTH,
                input.jump(),
                sprint,
                input.sneak()
        ).clamp();
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
