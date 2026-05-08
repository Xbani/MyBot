package com.mybot.velocity.bot;

import io.netty.buffer.Unpooled;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.slf4j.Logger;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorldBlockCache {
    private static final int MIN_SECTION_Y = -4;
    private static final int SECTION_COUNT = 24;
    private static final int BLOCK_GLOBAL_PALETTE_BITS = 15;
    private static final int BIOME_GLOBAL_PALETTE_BITS = 6;
    private static final int[][] EMPTY_COLLISION_STATE_RANGES = {
            {0, 0},
            {29, 84},
            {86, 117},
            {1987, 2034},
            {2047, 2056},
            {2109, 2136},
            {3169, 3686},
            {3810, 5105},
            {5110, 5117},
            {5134, 5453},
            {5526, 5545},
            {5626, 6473},
            {6570, 6595},
            {6660, 6679},
            {6684, 6725},
            {6744, 6744},
            {6746, 6761},
            {6805, 6814},
            {6816, 6817},
            {8133, 8444},
            {9246, 9249},
            {9267, 9267},
            {9382, 9525},
            {10457, 10712},
            {11029, 11060},
            {11206, 11229},
            {12333, 12364},
            {12713, 13044},
            {14595, 14596},
            {14607, 14612},
            {14614, 14614},
            {14649, 14649},
            {14860, 14886},
            {14945, 15064},
            {15076, 15076},
            {15090, 15093},
            {20739, 20742},
            {20756, 20756},
            {20758, 20759},
            {20773, 20773},
            {20775, 20829},
            {20844, 20847},
            {21264, 21311},
            {21440, 21519},
            {22541, 22566},
            {24487, 24487},
            {24969, 25096},
            {27554, 27608},
            {27612, 27659},
            {27693, 27718},
            {29389, 29389},
            {29664, 29667},
            {29670, 29670}
    };

    private final ConcurrentMap<Long, ChunkColumn> chunks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Integer> explicitBlocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> chunkUpdates = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ChunkSnapshot> surfaceSnapshots = new ConcurrentHashMap<>();
    private final java.util.Set<Long> overrideChunks = ConcurrentHashMap.newKeySet();
    private final Logger logger;

    public WorldBlockCache(Logger logger) {
        this.logger = logger;
    }

    public void handleChunk(ClientboundLevelChunkWithLightPacket packet) {
        ChunkColumn column = new ChunkColumn();
        var buffer = Unpooled.wrappedBuffer(packet.getChunkData());
        try {
            for (int i = 0; i < SECTION_COUNT && buffer.isReadable(); i++) {
                column.sections.put(MIN_SECTION_Y + i,
                        MinecraftTypes.readChunkSection(buffer, BLOCK_GLOBAL_PALETTE_BITS, BIOME_GLOBAL_PALETTE_BITS));
            }
            long key = chunkKey(packet.getX(), packet.getZ());
            chunks.put(key, column);
            chunkUpdates.put(key, System.currentTimeMillis());
            surfaceSnapshots.remove(key);
        } catch (RuntimeException ex) {
            long key = chunkKey(packet.getX(), packet.getZ());
            chunks.remove(key);
            chunkUpdates.remove(key);
            surfaceSnapshots.remove(key);
            logger.debug("Unable to decode chunk {},{} for collision cache", packet.getX(), packet.getZ(), ex);
        }
    }

    public void handleForget(ClientboundForgetLevelChunkPacket packet) {
        long removedChunkKey = chunkKey(packet.getX(), packet.getZ());
        chunks.remove(removedChunkKey);
        chunkUpdates.remove(removedChunkKey);
        surfaceSnapshots.remove(removedChunkKey);
        explicitBlocks.keySet().removeIf(key -> chunkKey(blockX(key) >> 4, blockZ(key) >> 4) == removedChunkKey);
    }

    public void handleBlockUpdate(ClientboundBlockUpdatePacket packet) {
        setBlock(packet.getEntry());
    }

    public void handleSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        for (BlockChangeEntry entry : packet.getEntries()) {
            setBlock(entry);
        }
    }

    public boolean hasChunkAt(double x, double z) {
        int chunkX = Math.floorDiv(floor(x), 16);
        int chunkZ = Math.floorDiv(floor(z), 16);
        return chunks.containsKey(chunkKey(chunkX, chunkZ));
    }

    public boolean collides(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int startX = floor(minX);
        int endX = floor(maxX - 1.0E-7);
        int startY = floor(minY);
        int endY = floor(maxY - 1.0E-7);
        int startZ = floor(minZ);
        int endZ = floor(maxZ - 1.0E-7);
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (isSolid(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean hasLineOfSight(Vec3 from, Vec3 to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-6) {
            return true;
        }
        int steps = Math.max(2, (int) Math.ceil(distance / 0.25));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = floor(from.x() + dx * t);
            int y = floor(from.y() + dy * t);
            int z = floor(from.z() + dz * t);
            if (isSolid(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    public boolean isSolid(int x, int y, int z) {
        int blockState = blockStateAt(x, y, z);
        return blockState != -1 && isCollisionBlocking(blockState);
    }

    public boolean isLiquidLike(Vec3 position) {
        return isLiquidBlock(floor(position.x()), floor(position.y() + 0.1), floor(position.z()))
                || isLiquidBlock(floor(position.x()), floor(position.y() + 0.9), floor(position.z()));
    }

    public boolean isLiquidBlock(int x, int y, int z) {
        int blockState = blockStateAt(x, y, z);
        return isLikelyWaterOrLava(blockState)
                || (blockState > 0 && !isCollisionBlocking(blockState) && !isLikelyVegetation(blockState));
    }

    public boolean isAirBlock(int x, int y, int z) {
        return blockStateAt(x, y, z) == 0;
    }

    public boolean isWaterSurface(int x, int y, int z) {
        return isLiquidBlock(x, y, z)
                && !isSolid(x, y + 1, z)
                && isAirBlock(x, y + 1, z);
    }

    public int blockStateAt(int x, int y, int z) {
        ChunkSection section = section(x, y, z);
        Integer explicit = explicitBlocks.get(blockKey(x, y, z));
        if (explicit != null) {
            return explicit;
        }
        if (overrideChunks.contains(chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)))) {
            return 0;
        }
        if (section == null) {
            return -1;
        }
        return section.getBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16));
    }

    public void setBlockForTesting(int x, int y, int z, int blockState) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        long chunkKey = chunkKey(chunkX, chunkZ);
        overrideChunks.add(chunkKey);
        ChunkColumn column = chunks.computeIfAbsent(chunkKey, ignored -> new ChunkColumn());
        chunkUpdates.put(chunkKey, System.currentTimeMillis());
        surfaceSnapshots.remove(chunkKey);
        int sectionY = Math.floorDiv(y, 16);
        ChunkSection section = column.sections.computeIfAbsent(sectionY, ignored -> emptySection());
        explicitBlocks.put(blockKey(x, y, z), blockState);
        section.setBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16), blockState);
    }

    private ChunkSection emptySection() {
        return new ChunkSection(0,
                DataPalette.createForBlockState(BLOCK_GLOBAL_PALETTE_BITS, 0),
                DataPalette.createForBiome(BIOME_GLOBAL_PALETTE_BITS, 0));
    }

    private static boolean isCollisionBlocking(int blockState) {
        for (int[] range : EMPTY_COLLISION_STATE_RANGES) {
            if (blockState < range[0]) {
                return true;
            }
            if (blockState <= range[1]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLikelyWaterOrLava(int blockState) {
        return blockState >= 34 && blockState <= 84;
    }

    private void setBlock(BlockChangeEntry entry) {
        int x = entry.getPosition().getX();
        int y = entry.getPosition().getY();
        int z = entry.getPosition().getZ();
        ChunkSection section = section(x, y, z);
        if (section != null) {
            explicitBlocks.put(blockKey(x, y, z), entry.getBlock());
            section.setBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16), entry.getBlock());
            long chunkKey = chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            chunkUpdates.put(chunkKey, System.currentTimeMillis());
            surfaceSnapshots.remove(chunkKey);
        }
    }

    public List<ChunkSnapshot> chunkSnapshots() {
        return chunks.keySet().stream()
                .map(this::cachedSurfaceSnapshot)
                .toList();
    }

    private ChunkSnapshot cachedSurfaceSnapshot(long key) {
        long updatedAt = chunkUpdates.getOrDefault(key, 0L);
        ChunkSnapshot cached = surfaceSnapshots.get(key);
        if (cached != null && cached.updatedAtMillis() == updatedAt) {
            return cached;
        }
        ChunkSnapshot next = surfaceSnapshot(key);
        surfaceSnapshots.put(key, next);
        return next;
    }

    private ChunkSnapshot surfaceSnapshot(long key) {
        int chunkX = chunkX(key);
        int chunkZ = chunkZ(key);
        int[] colors = new int[16 * 16];
        int[] heights = new int[16 * 16];
        java.util.Arrays.fill(heights, Integer.MIN_VALUE);
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkX * 16 + localX;
                int blockZ = chunkZ * 16 + localZ;
                int index = localZ * 16 + localX;
                SurfaceBlock top = highestVisibleBlock(blockX, blockZ);
                if (top != null) {
                    int color = DynmapBlockColors.shaded(surfaceColor(top.blockState()), top.y());
                    colors[index] = color;
                    heights[index] = top.y();
                    minHeight = Math.min(minHeight, top.y());
                    maxHeight = Math.max(maxHeight, top.y());
                }
            }
        }
        boolean hasSurface = maxHeight != Integer.MIN_VALUE;
        return new ChunkSnapshot(
                chunkX,
                chunkZ,
                chunkUpdates.getOrDefault(key, 0L),
                hasSurface ? minHeight : 0,
                hasSurface ? maxHeight : 0,
                encodeColors(colors),
                encodeHeights(heights)
        );
    }

    private SurfaceBlock highestVisibleBlock(int x, int z) {
        for (int sectionY = MIN_SECTION_Y + SECTION_COUNT - 1; sectionY >= MIN_SECTION_Y; sectionY--) {
            ChunkSection section = section(x, sectionY * 16, z);
            if (section == null) {
                continue;
            }
            for (int localY = 15; localY >= 0; localY--) {
                int y = sectionY * 16 + localY;
                int blockState = blockStateAt(x, y, z);
                if (isVisibleSurfaceBlock(blockState)) {
                    return new SurfaceBlock(y, blockState);
                }
            }
        }
        return null;
    }

    private boolean isVisibleSurfaceBlock(int blockState) {
        return blockState > 0;
    }

    private int surfaceColor(int blockState) {
        if (!isCollisionBlocking(blockState) && !isLikelyVegetation(blockState)) {
            return DynmapBlockColors.WATER;
        }
        return DynmapBlockColors.topColor(blockState);
    }

    private boolean isLikelyVegetation(int blockState) {
        return (blockState >= 20 && blockState <= 28)
                || (blockState >= 118 && blockState <= 197)
                || (blockState >= 1987 && blockState <= 2034)
                || (blockState >= 8133 && blockState <= 8444)
                || (blockState >= 12713 && blockState <= 13044);
    }

    private String encodeColors(int[] colors) {
        StringBuilder builder = new StringBuilder(colors.length * 6);
        for (int color : colors) {
            builder.append(hex((color >> 20) & 0xf));
            builder.append(hex((color >> 16) & 0xf));
            builder.append(hex((color >> 12) & 0xf));
            builder.append(hex((color >> 8) & 0xf));
            builder.append(hex((color >> 4) & 0xf));
            builder.append(hex(color & 0xf));
        }
        return builder.toString();
    }

    private List<Integer> encodeHeights(int[] heights) {
        List<Integer> encoded = new java.util.ArrayList<>(heights.length);
        for (int height : heights) {
            encoded.add(height == Integer.MIN_VALUE ? -999 : height);
        }
        return encoded;
    }

    private char hex(int value) {
        return (char) (value < 10 ? '0' + value : 'a' + value - 10);
    }

    private ChunkSection section(int x, int y, int z) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        ChunkColumn column = chunks.get(chunkKey(chunkX, chunkZ));
        if (column == null) {
            return null;
        }
        return column.sections.get(Math.floorDiv(y, 16));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static long blockKey(int x, int y, int z) {
        return (((long) (x & 0x3ffffff)) << 38) | (((long) (z & 0x3ffffff)) << 12) | (y & 0xfffL);
    }

    private static int blockX(long key) {
        int value = (int) (key >> 38);
        return value >= 0x2000000 ? value - 0x4000000 : value;
    }

    private static int blockZ(long key) {
        int value = (int) ((key >> 12) & 0x3ffffff);
        return value >= 0x2000000 ? value - 0x4000000 : value;
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }

    public record ChunkSnapshot(
            int chunkX,
            int chunkZ,
            long updatedAtMillis,
            int minY,
            int maxY,
            String colors,
            List<Integer> heights
    ) { }

    private record SurfaceBlock(int y, int blockState) { }

    private static final class ChunkColumn {
        private final Map<Integer, ChunkSection> sections = new ConcurrentHashMap<>();
    }
}
