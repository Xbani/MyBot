package com.mybot.velocity.behavior;

import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.MovementInput;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BotControllerTest {
    @Test
    void steersAwayFromNearbyBotsEvenWhenIdle() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        BotWorldState state = new BotWorldState(blocks);
        state.putPlayer(new TrackedPlayer(4, UUID.randomUUID(), "Bot_Nova", new Vec3(0.45, 64, 0.2), 0, 0, Instant.now()));
        BotPhysics physics = new BotPhysics();
        physics.correctPosition(new Vec3(0, 64, 0), Vec3.ZERO, 0, 0);
        BotController controller = new BotController(HgBehaviorConfig.defaults(), new BotActionQueue());

        BotController.ControlPlan plan = controller.tick(state, physics);

        assertThat(plan.movement().moving()).isTrue();
        assertThat(plan.movement().strafe()).isLessThan(0);
        assertThat(plan.movement().sprint()).isFalse();
    }

    @Test
    void jumpsWhenDirectTargetIsOneBlockHigher() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        BotWorldState state = new BotWorldState(blocks);
        state.putPlayer(new TrackedPlayer(7, UUID.randomUUID(), "RealTarget", new Vec3(4, 65, 0), 0, 0, Instant.now()));
        BotPhysics physics = new BotPhysics();
        physics.correctPosition(new Vec3(0, 64, 0), Vec3.ZERO, 0, 0);
        BotController controller = new BotController(HgBehaviorConfig.defaults(), new BotActionQueue());

        BotController.ControlPlan plan = controller.tick(state, physics);

        assertThat(plan.movement().jump()).isTrue();
        assertThat(plan.movement().forward()).isGreaterThan(0);
    }

    @Test
    void keepsMovingWhenOneBlockHigherTargetIsInsideMeleeStopRange() {
        WorldBlockCache blocks = flatGround();
        blocks.setBlockForTesting(0, 64, 2, 1);
        BotWorldState state = new BotWorldState(blocks);
        state.putPlayer(new TrackedPlayer(8, UUID.randomUUID(), "RealTarget", new Vec3(0.5, 65, 3), 0, 0, Instant.now()));
        BotPhysics physics = new BotPhysics();
        physics.correctPosition(new Vec3(0.5, 64, 0.5), Vec3.ZERO, 0, 0);
        BotController controller = new BotController(HgBehaviorConfig.defaults(), new BotActionQueue());
        boolean climbed = false;

        for (int i = 0; i < 35; i++) {
            MovementInput movement = controller.tick(state, physics).movement();
            physics.tick(blocks, state.trackedPlayers(), movement);
            climbed |= physics.position().y() > 64.45 && physics.position().z() > 1.6;
        }

        assertThat(climbed).isTrue();
        assertThat(physics.position().z()).isGreaterThan(2.0);
    }

    private WorldBlockCache flatGround() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 6; z++) {
                blocks.setBlockForTesting(x, 63, z, 1);
            }
        }
        return blocks;
    }
}
