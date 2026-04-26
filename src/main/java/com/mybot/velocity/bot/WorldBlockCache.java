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
            chunks.put(chunkKey(packet.getX(), packet.getZ()), column);
        } catch (RuntimeException ex) {
            chunks.remove(chunkKey(packet.getX(), packet.getZ()));
            logger.debug("Unable to decode chunk {},{} for collision cache", packet.getX(), packet.getZ(), ex);
        }
    }

    public void handleForget(ClientboundForgetLevelChunkPacket packet) {
        chunks.remove(chunkKey(packet.getX(), packet.getZ()));
        explicitBlocks.keySet().removeIf(key -> chunkKey(blockX(key) >> 4, blockZ(key) >> 4) == chunkKey(packet.getX(), packet.getZ()));
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

    public boolean isSolid(int x, int y, int z) {
        ChunkSection section = section(x, y, z);
        Integer explicit = explicitBlocks.get(blockKey(x, y, z));
        if (explicit != null) {
            return isCollisionBlocking(explicit);
        }
        if (overrideChunks.contains(chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)))) {
            return false;
        }
        if (section == null) {
            return false;
        }
        return isCollisionBlocking(section.getBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16)));
    }

    public void setBlockForTesting(int x, int y, int z, int blockState) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        long chunkKey = chunkKey(chunkX, chunkZ);
        overrideChunks.add(chunkKey);
        ChunkColumn column = chunks.computeIfAbsent(chunkKey, ignored -> new ChunkColumn());
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

    private void setBlock(BlockChangeEntry entry) {
        int x = entry.getPosition().getX();
        int y = entry.getPosition().getY();
        int z = entry.getPosition().getZ();
        ChunkSection section = section(x, y, z);
        if (section != null) {
            explicitBlocks.put(blockKey(x, y, z), entry.getBlock());
            section.setBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16), entry.getBlock());
        }
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

    private static final class ChunkColumn {
        private final Map<Integer, ChunkSection> sections = new ConcurrentHashMap<>();
    }
}
