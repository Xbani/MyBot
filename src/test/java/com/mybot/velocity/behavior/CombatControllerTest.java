package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotInventoryState;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CombatControllerTest {
    @Test
    void ignoresBotsAndSelectsNearestThreat() {
        CombatController controller = new CombatController();
        TrackedPlayer bot = player("Bot_Nova", 1);
        TrackedPlayer near = player("RealNear", 4);
        TrackedPlayer far = player("RealFar", 12);

        Optional<TrackedPlayer> selected = controller.selectTarget(new Vec3(0, 64, 0), List.of(bot, near, far), HgBehaviorConfig.defaults());

        assertThat(selected).contains(near);
    }

    @Test
    void fleesBeforeFightingWhenHealthIsLow() {
        BotIntent intent = new CombatController().chooseIntent(
                4,
                new BotInventoryState(),
                Optional.of(player("Real", 2)),
                new Vec3(0, 64, 0),
                HgBehaviorConfig.defaults());

        assertThat(intent).isEqualTo(BotIntent.FLEE);
    }

    private TrackedPlayer player(String name, double x) {
        return new TrackedPlayer((int) x, UUID.randomUUID(), name, new Vec3(x, 64, 0), 0, 0, Instant.now());
    }
}
