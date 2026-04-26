package com.mybot.velocity.bot;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class BotPhysics {
    private static final double WIDTH = 0.6;
    private static final double HALF_WIDTH = WIDTH / 2.0;
    private static final double HEIGHT = 1.8;
    private static final double GRAVITY = 0.08;
    private static final double TERMINAL_VELOCITY = -3.92;
    private static final double FOLLOW_RADIUS = 16.0;
    private static final double WALK_SPEED = 0.10;
    private static final double SPRINT_SPEED = 0.16;
    private static final double SNEAK_SPEED = 0.04;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double GROUND_FRICTION = 0.55;
    private static final double AIR_FRICTION = 0.91;

    private Vec3 position = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private float yaw;
    private float pitch;
    private boolean onGround;
    private boolean horizontalCollision;
    private boolean initialized;
    private int knockbackTicks;

    public PhysicsTick tick(WorldBlockCache blocks, Collection<TrackedPlayer> players) {
        if (!initialized) {
            return new PhysicsTick(position, yaw, pitch, onGround, false, false, Optional.empty(), MovementInput.NONE);
        }

        Optional<TrackedPlayer> target = nearestRealPlayer(players);
        if (target.isPresent()) {
            Vec3 targetPosition = target.get().position();
            LookAngles look = lookAt(position.add(0, 1.62, 0), targetPosition.add(0, 1.62, 0));
            yaw = look.yaw();
            pitch = look.pitch();
            return tick(blocks, players, targetInput(targetPosition, blocks.hasChunkAt(position.x(), position.z())));
        }
        return tick(blocks, players, MovementInput.NONE);
    }

    public PhysicsTick tick(WorldBlockCache blocks, Collection<TrackedPlayer> players, MovementInput input) {
        if (!initialized) {
            return new PhysicsTick(position, yaw, pitch, onGround, false, false, Optional.empty(), MovementInput.NONE);
        }
        MovementInput clamped = input.clamp();
        Optional<TrackedPlayer> target = nearestRealPlayer(players);
        if (knockbackTicks > 0) {
            clamped = new MovementInput(clamped.forward() * 0.25, clamped.strafe() * 0.25, clamped.jump(), false, clamped.sneak());
            knockbackTicks--;
        }
        Vec3 horizontal = horizontalVelocity(clamped);
        double vertical = velocity.y();
        if (clamped.jump() && onGround) {
            vertical = JUMP_VELOCITY;
        }
        vertical = Math.max(TERMINAL_VELOCITY, vertical - GRAVITY);
        double friction = onGround ? GROUND_FRICTION : AIR_FRICTION;
        velocity = new Vec3(horizontal.x() + velocity.x() * friction, vertical, horizontal.z() + velocity.z() * friction);
        CollisionResult resolved = moveWithCollisions(blocks, position, velocity);
        position = resolved.position();
        velocity = new Vec3(resolved.blockedX() ? 0 : velocity.x(), resolved.blockedY() ? 0 : velocity.y(), resolved.blockedZ() ? 0 : velocity.z());
        onGround = resolved.grounded();
        horizontalCollision = resolved.blockedX() || resolved.blockedZ();
        return new PhysicsTick(position, yaw, pitch, onGround, horizontalCollision, blocks.hasChunkAt(position.x(), position.z()), target, clamped);
    }

    public void correctPosition(Vec3 nextPosition, Vec3 nextVelocity, float nextYaw, float nextPitch) {
        position = nextPosition;
        velocity = nextVelocity;
        yaw = nextYaw;
        pitch = nextPitch;
        onGround = false;
        initialized = true;
    }

    public Vec3 position() {
        return position;
    }

    public Vec3 velocity() {
        return velocity;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean onGround() {
        return onGround;
    }

    public void setLook(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Math.max(-90f, Math.min(90f, pitch));
    }

    public void applyKnockback(Vec3 knockback) {
        velocity = knockback;
        knockbackTicks = 8;
        initialized = true;
    }

    public static Optional<TrackedPlayer> nearestRealPlayer(Vec3 origin, Collection<TrackedPlayer> players) {
        return players.stream()
                .filter(player -> !player.isBot())
                .filter(player -> player.position().distanceSquaredTo(origin) <= FOLLOW_RADIUS * FOLLOW_RADIUS)
                .min(Comparator.comparingDouble(player -> player.position().distanceSquaredTo(origin)));
    }

    public static LookAngles lookAt(Vec3 from, Vec3 to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontal));
        return new LookAngles(yaw, pitch);
    }

    private Optional<TrackedPlayer> nearestRealPlayer(Collection<TrackedPlayer> players) {
        return nearestRealPlayer(position, players);
    }

    private MovementInput targetInput(Vec3 targetPosition, boolean chunksLoaded) {
        double distance = position.horizontalDistanceTo(targetPosition);
        if (distance < 3.25 || !chunksLoaded) {
            return MovementInput.NONE;
        }
        return new MovementInput(1, 0, false, distance > 7, false);
    }

    private Vec3 horizontalVelocity(MovementInput input) {
        double speed = input.sneak() ? SNEAK_SPEED : input.sprint() ? SPRINT_SPEED : WALK_SPEED;
        if (!input.moving()) {
            return Vec3.ZERO;
        }
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = Math.cos(yawRad);
        double strafeZ = Math.sin(yawRad);
        double x = forwardX * input.forward() + strafeX * input.strafe();
        double z = forwardZ * input.forward() + strafeZ * input.strafe();
        Vec3 normalized = new Vec3(x, 0, z).horizontalNormalize();
        return normalized.multiply(speed);
    }

    private static CollisionResult moveWithCollisions(WorldBlockCache blocks, Vec3 start, Vec3 delta) {
        MoveAxis x = moveAxis(blocks, start, delta.x(), Axis.X);
        MoveAxis y = moveAxis(blocks, x.position(), delta.y(), Axis.Y);
        MoveAxis z = moveAxis(blocks, y.position(), delta.z(), Axis.Z);
        boolean grounded = delta.y() < 0 && y.blocked();
        return new CollisionResult(z.position(), x.blocked(), y.blocked(), z.blocked(), grounded);
    }

    private static MoveAxis moveAxis(WorldBlockCache blocks, Vec3 position, double delta, Axis axis) {
        if (Math.abs(delta) < 1.0E-7) {
            return new MoveAxis(position, false);
        }
        Vec3 next = switch (axis) {
            case X -> position.add(delta, 0, 0);
            case Y -> position.add(0, delta, 0);
            case Z -> position.add(0, 0, delta);
        };
        if (!intersects(blocks, next)) {
            return new MoveAxis(next, false);
        }
        double step = Math.signum(delta) * 0.01;
        Vec3 current = position;
        double moved = 0;
        while (Math.abs(moved + step) <= Math.abs(delta)) {
            Vec3 candidate = switch (axis) {
                case X -> current.add(step, 0, 0);
                case Y -> current.add(0, step, 0);
                case Z -> current.add(0, 0, step);
            };
            if (intersects(blocks, candidate)) {
                break;
            }
            current = candidate;
            moved += step;
        }
        return new MoveAxis(current, true);
    }

    private static boolean intersects(WorldBlockCache blocks, Vec3 position) {
        return blocks.collides(position.x() - HALF_WIDTH, position.y(), position.z() - HALF_WIDTH,
                position.x() + HALF_WIDTH, position.y() + HEIGHT, position.z() + HALF_WIDTH);
    }

    public record LookAngles(float yaw, float pitch) { }

    public record PhysicsTick(Vec3 position, float yaw, float pitch, boolean onGround, boolean horizontalCollision,
                              boolean chunksLoaded, Optional<TrackedPlayer> target, MovementInput input) { }

    private record MoveAxis(Vec3 position, boolean blocked) { }

    private record CollisionResult(Vec3 position, boolean blockedX, boolean blockedY, boolean blockedZ, boolean grounded) { }

    private enum Axis {
        X, Y, Z
    }
}
