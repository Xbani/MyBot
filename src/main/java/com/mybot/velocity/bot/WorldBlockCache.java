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
            return explicit != 0;
        }
        if (overrideChunks.contains(chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)))) {
            return false;
        }
        if (section == null) {
            return false;
        }
        return section.getBlock(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16)) != 0;
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
