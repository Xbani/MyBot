package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BotResourceScanner {
    private final ResourceRanges wood;
    private final ResourceRanges stone;
    private final ResourceRanges craftingTable;
    private final ResourceRanges chest;

    public BotResourceScanner(Map<String, Object> traits) {
        this.wood = ranges(traits, "wood-block-states", List.of(new Range(84, 117), new Range(6570, 6725)));
        this.stone = ranges(traits, "stone-block-states", List.of(new Range(1, 28)));
        this.craftingTable = ranges(traits, "crafting-table-block-states", List.of(new Range(850, 900), new Range(1450, 1500)));
        this.chest = ranges(traits, "chest-block-states", List.of(new Range(900, 980), new Range(1501, 1560)));
    }

    public Optional<Vector3i> nearestWood(WorldBlockCache blocks, Vec3 origin, int radius) {
        return nearest(blocks, origin, radius, wood);
    }

    public Optional<Vector3i> nearestStone(WorldBlockCache blocks, Vec3 origin, int radius) {
        return nearest(blocks, origin, radius, stone);
    }

    public Optional<Vector3i> nearestCraftingTable(WorldBlockCache blocks, Vec3 origin, int radius) {
        return nearest(blocks, origin, radius, craftingTable);
    }

    public Optional<Vector3i> nearestChest(WorldBlockCache blocks, Vec3 origin, int radius) {
        return nearest(blocks, origin, radius, chest);
    }

    private Optional<Vector3i> nearest(WorldBlockCache blocks, Vec3 origin, int radius, ResourceRanges ranges) {
        int ox = floor(origin.x());
        int oy = floor(origin.y());
        int oz = floor(origin.z());
        return java.util.stream.IntStream.rangeClosed(-radius, radius)
                .boxed()
                .flatMap(dx -> java.util.stream.IntStream.rangeClosed(-3, 4)
                        .boxed()
                        .flatMap(dy -> java.util.stream.IntStream.rangeClosed(-radius, radius)
                                .mapToObj(dz -> Vector3i.from(ox + dx, oy + dy, oz + dz))))
                .filter(pos -> Math.abs(pos.getX() - ox) + Math.abs(pos.getZ() - oz) <= radius * 2)
                .filter(pos -> ranges.matches(blocks.blockStateAt(pos.getX(), pos.getY(), pos.getZ())))
                .min(Comparator.comparingDouble(pos -> distanceSquared(origin, pos)));
    }

    private static ResourceRanges ranges(Map<String, Object> traits, String key, List<Range> fallback) {
        Object value = traits.get(key);
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<Range> parsed = list.stream()
                    .map(BotResourceScanner::parseRange)
                    .flatMap(Optional::stream)
                    .toList();
            if (!parsed.isEmpty()) {
                return new ResourceRanges(parsed);
            }
        }
        return new ResourceRanges(fallback);
    }

    private static Optional<Range> parseRange(Object raw) {
        if (raw instanceof Number number) {
            int value = number.intValue();
            return Optional.of(new Range(value, value));
        }
        if (raw instanceof List<?> list && list.size() >= 2 && list.get(0) instanceof Number min && list.get(1) instanceof Number max) {
            return Optional.of(new Range(min.intValue(), max.intValue()));
        }
        if (raw != null) {
            String text = raw.toString().trim();
            int separator = text.indexOf('-');
            try {
                if (separator > 0) {
                    return Optional.of(new Range(Integer.parseInt(text.substring(0, separator).trim()),
                            Integer.parseInt(text.substring(separator + 1).trim())));
                }
                int value = Integer.parseInt(text);
                return Optional.of(new Range(value, value));
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static double distanceSquared(Vec3 origin, Vector3i pos) {
        double dx = origin.x() - (pos.getX() + 0.5);
        double dy = origin.y() - pos.getY();
        double dz = origin.z() - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private record ResourceRanges(List<Range> ranges) {
        boolean matches(int blockState) {
            return ranges.stream().anyMatch(range -> range.matches(blockState));
        }
    }

    private record Range(int min, int max) {
        boolean matches(int value) {
            return value >= Math.min(min, max) && value <= Math.max(min, max);
        }
    }
}
