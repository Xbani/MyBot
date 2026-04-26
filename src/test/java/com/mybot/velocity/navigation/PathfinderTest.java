package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import static org.assertj.core.api.Assertions.assertThat;

class PathfinderTest {
    @Test
    void findsPathAroundWallGap() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                blocks.setBlockForTesting(x, 63, z, 1);
            }
        }
        for (int z = -2; z <= 4; z++) {
            if (z != 2) {
                blocks.setBlockForTesting(2, 64, z, 1);
                blocks.setBlockForTesting(2, 65, z, 1);
            }
        }

        var path = new Pathfinder().findPath(blocks, new Vec3(0, 64, 0), new Vec3(5, 64, 0));

        assertThat(path).isPresent();
        assertThat(path.get()).anyMatch(node -> node.x() == 2 && node.z() == 2);
    }

    @Test
    void refusesUnsafeAirWithoutFloor() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);

        assertThat(new Pathfinder().walkable(blocks, new PathNode(0, 64, 0))).isFalse();
    }
}
