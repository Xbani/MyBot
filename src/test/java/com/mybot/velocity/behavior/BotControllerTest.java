package com.mybot.velocity.behavior;

import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.BotWorldState;
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
}
