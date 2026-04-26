package com.mybot.velocity.bot;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BotPhysicsTest {
    @Test
    void excludesBotsAndChoosesNearestRealPlayer() {
        TrackedPlayer bot = new TrackedPlayer(1, UUID.randomUUID(), "Bot_Astra", new Vec3(1, 64, 0), 0, 0, Instant.now());
        TrackedPlayer far = new TrackedPlayer(2, UUID.randomUUID(), "RealFar", new Vec3(12, 64, 0), 0, 0, Instant.now());
        TrackedPlayer near = new TrackedPlayer(3, UUID.randomUUID(), "RealNear", new Vec3(4, 64, 0), 0, 0, Instant.now());

        Optional<TrackedPlayer> selected = BotPhysics.nearestRealPlayer(new Vec3(0, 64, 0), List.of(bot, far, near));

        assertThat(selected).contains(near);
    }

    @Test
    void computesLookAnglesTowardTarget() {
        BotPhysics.LookAngles angles = BotPhysics.lookAt(new Vec3(0, 65.62, 0), new Vec3(4, 65.62, 0));

        assertThat(angles.yaw()).isCloseTo(-90f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(angles.pitch()).isCloseTo(0f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void gravityFallsOntoSolidGround() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.setBlockForTesting(x, 63, z, 1);
            }
        }
        BotPhysics physics = new BotPhysics();
        physics.correctPosition(new Vec3(0, 66, 0), Vec3.ZERO, 0, 0);

        BotPhysics.PhysicsTick last = null;
        for (int i = 0; i < 80; i++) {
            last = physics.tick(blocks, List.of());
        }

        assertThat(last).isNotNull();
        assertThat(last.onGround()).isTrue();
        assertThat(last.position().y()).isCloseTo(64.0, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    void wallCollisionStopsHorizontalMovement() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.setBlockForTesting(x, 63, z, 1);
            }
        }
        for (int y = 64; y <= 66; y++) {
            blocks.setBlockForTesting(1, y, 0, 1);
        }
        BotPhysics physics = new BotPhysics();
        physics.correctPosition(new Vec3(0, 64, 0), Vec3.ZERO, 0, 0);
        TrackedPlayer target = new TrackedPlayer(2, UUID.randomUUID(), "Real", new Vec3(4, 64, 0), 0, 0, Instant.now());

        BotPhysics.PhysicsTick tick = null;
        for (int i = 0; i < 10; i++) {
            tick = physics.tick(blocks, List.of(target));
        }

        assertThat(tick).isNotNull();
        assertThat(tick.horizontalCollision()).isTrue();
        assertThat(tick.position().x()).isLessThan(0.8);
    }

    @Test
    void sprintMovesFurtherThanSneak() {
        WorldBlockCache blocks = flatGround();
        BotPhysics sprint = new BotPhysics();
        sprint.correctPosition(new Vec3(0, 64, 0), Vec3.ZERO, 0, 0);
        BotPhysics sneak = new BotPhysics();
        sneak.correctPosition(new Vec3(0, 64, 0), Vec3.ZERO, 0, 0);

        for (int i = 0; i < 5; i++) {
            sprint.tick(blocks, List.of(), new MovementInput(1, 0, false, true, false));
            sneak.tick(blocks, List.of(), new MovementInput(1, 0, false, false, true));
        }

        assertThat(sprint.position().z()).isGreaterThan(sneak.position().z());
    }

    @Test
    void knockbackChangesTrajectory() {
        WorldBlockCache blocks = flatGround();
        BotPhysics physics = new BotPhysics();
        physics.correctPosition(new Vec3(0, 64, 0), Vec3.ZERO, 0, 0);
        physics.applyKnockback(new Vec3(-0.8, 0.25, 0));

        physics.tick(blocks, List.of(), MovementInput.NONE);

        assertThat(physics.position().x()).isLessThan(0);
    }

    @Test
    void jumpClimbsOneBlockStep() {
        WorldBlockCache blocks = flatGround();
        blocks.setBlockForTesting(0, 64, 2, 1);
        BotPhysics physics = new BotPhysics();
        physics.correctPosition(new Vec3(0.5, 64, 0.5), Vec3.ZERO, 0, 0);
        boolean climbed = false;

        for (int i = 0; i < 35; i++) {
            physics.tick(blocks, List.of(), new MovementInput(1, 0, true, false, false));
            climbed |= physics.position().y() > 64.45 && physics.position().z() > 1.6;
        }

        assertThat(climbed).isTrue();
        assertThat(physics.position().z()).isGreaterThan(2.0);
    }

    private WorldBlockCache flatGround() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                blocks.setBlockForTesting(x, 63, z, 1);
            }
        }
        return blocks;
    }
}
