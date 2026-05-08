package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import static org.assertj.core.api.Assertions.assertThat;

class PathfinderTest {
    private static final int TALL_GRASS_BOTTOM = 12721;
    private static final int LARGE_FERN_BOTTOM = 12723;

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

    @Test
    void treatsTallGrassAndLargeFernAsWalkableSpace() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        blocks.setBlockForTesting(0, 63, 0, 1);
        blocks.setBlockForTesting(0, 64, 0, TALL_GRASS_BOTTOM);
        blocks.setBlockForTesting(0, 65, 0, LARGE_FERN_BOTTOM);

        assertThat(blocks.isSolid(0, 64, 0)).isFalse();
        assertThat(blocks.isSolid(0, 65, 0)).isFalse();
        assertThat(new Pathfinder().walkable(blocks, new PathNode(0, 64, 0))).isTrue();
    }

    @Test
    void findsPathThroughTallGrassInsteadOfTreatingItAsAWall() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        for (int x = -2; x <= 4; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.setBlockForTesting(x, 63, z, 1);
            }
        }
        for (int z = -2; z <= 2; z++) {
            blocks.setBlockForTesting(1, 64, z, TALL_GRASS_BOTTOM);
        }

        var path = new Pathfinder().findPath(blocks, new Vec3(0, 64, 0), new Vec3(3, 64, 0));

        assertThat(path).isPresent();
        assertThat(path.get()).anyMatch(node -> node.x() == 1);
    }

    @Test
    void plansToAttackRangeInsteadOfExactPlayerBlock() {
        WorldBlockCache blocks = flatGround(-2, 8, -2, 2);

        var path = new Pathfinder().findPath(blocks, new Vec3(0.5, 64, 0.5), new Vec3(6.5, 64, 0.5), 2.25);

        assertThat(path).isPresent();
        PathNode end = path.get().getLast();
        assertThat(end.center().horizontalDistanceTo(new Vec3(6.5, 64, 0.5))).isLessThanOrEqualTo(2.25);
        assertThat(end.x()).isLessThan(6);
    }

    @Test
    void doesNotCutDiagonalCornersThroughWalls() {
        WorldBlockCache blocks = flatGround(-1, 3, -1, 3);
        blocks.setBlockForTesting(1, 64, 0, 1);
        blocks.setBlockForTesting(1, 65, 0, 1);
        blocks.setBlockForTesting(0, 64, 1, 1);
        blocks.setBlockForTesting(0, 65, 1, 1);

        var path = new Pathfinder().findPath(blocks, new Vec3(0.5, 64, 0.5), new Vec3(2.5, 64, 2.5), 0.25);

        assertThat(path).isPresent();
        assertThat(path.get()).noneMatch(node -> node.x() == 1 && node.z() == 1);
    }

    @Test
    void prefersWaterSurfaceNodesWhenCrossingWater() {
        WorldBlockCache blocks = flatGround(-2, 8, -2, 2);
        for (int x = 2; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                blocks.setBlockForTesting(x, 63, z, 50);
                blocks.setBlockForTesting(x, 64, z, 50);
            }
        }

        var path = new Pathfinder().findPath(blocks, new Vec3(0.5, 64, 0.5), new Vec3(6.5, 64, 0.5), 0.5);

        assertThat(path).isPresent();
        assertThat(path.get()).anyMatch(node -> node.y() == 64 && blocks.isWaterSurface(node.x(), node.y(), node.z()));
        assertThat(path.get()).noneMatch(node -> node.y() < 64 && blocks.isLiquidBlock(node.x(), node.y(), node.z()));
    }

    @Test
    void swimsUpToSurfaceBeforePathingAcrossWater() {
        WorldBlockCache blocks = flatGround(-1, 5, -1, 1);
        for (int y = 61; y <= 64; y++) {
            blocks.setBlockForTesting(0, y, 0, 29);
            blocks.setBlockForTesting(1, y, 0, 29);
        }

        var path = new Pathfinder().findPath(blocks, new Vec3(0.5, 61.2, 0.5), new Vec3(4.5, 64, 0.5), 0.5);

        assertThat(path).isPresent();
        assertThat(path.get().get(1)).satisfies(node -> {
            assertThat(node.x()).isEqualTo(0);
            assertThat(node.z()).isEqualTo(0);
            assertThat(node.y()).isEqualTo(62);
        });
        assertThat(path.get()).anyMatch(node -> node.y() == 64 && blocks.isWaterSurface(node.x(), node.y(), node.z()));
    }

    private WorldBlockCache flatGround(int minX, int maxX, int minZ, int maxZ) {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                blocks.setBlockForTesting(x, 63, z, 1);
            }
        }
        return blocks;
    }
}
