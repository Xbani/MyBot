package com.mybot.velocity.bot;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemRegistryConfig {
    private final List<ScoredRange> weapons;
    private final List<ScoredRange> food;
    private final List<ScoredRange> armor;
    private final List<ScoredRange> blocks;
    private final List<ScoredRange> tools;

    private ItemRegistryConfig(List<ScoredRange> weapons,
                               List<ScoredRange> food,
                               List<ScoredRange> armor,
                               List<ScoredRange> blocks,
                               List<ScoredRange> tools) {
        this.weapons = List.copyOf(weapons);
        this.food = List.copyOf(food);
        this.armor = List.copyOf(armor);
        this.blocks = List.copyOf(blocks);
        this.tools = List.copyOf(tools);
    }

    public static ItemRegistryConfig defaults() {
        return new ItemRegistryConfig(
                List.of(new ScoredRange(800, 900, 90), new ScoredRange(700, 799, 70), new ScoredRange(600, 699, 55), new ScoredRange(1, 599, 10)),
                List.of(new ScoredRange(900, 1050, 50)),
                List.of(new ScoredRange(1051, 1250, 65)),
                List.of(new ScoredRange(1, 28, 25)),
                List.of(new ScoredRange(500, 599, 45))
        );
    }

    public static ItemRegistryConfig fromTraits(Map<String, Object> traits) {
        ItemRegistryConfig defaults = defaults();
        return new ItemRegistryConfig(
                ranges(traits.get("weapon-items"), defaults.weapons),
                ranges(traits.get("food-items"), defaults.food),
                ranges(traits.get("armor-items"), defaults.armor),
                ranges(traits.get("block-items"), defaults.blocks),
                ranges(traits.get("tool-items"), defaults.tools)
        );
    }

    public int weaponScore(ItemStack item) {
        return score(item, weapons);
    }

    public int foodScore(ItemStack item) {
        return score(item, food);
    }

    public int armorScore(ItemStack item) {
        return score(item, armor);
    }

    public int blockScore(ItemStack item) {
        return score(item, blocks);
    }

    public int toolScore(ItemStack item) {
        return score(item, tools);
    }

    public int utilityScore(ItemStack item) {
        return Math.max(Math.max(armorScore(item), blockScore(item)), toolScore(item));
    }

    private int score(ItemStack item, List<ScoredRange> ranges) {
        if (item == null || item.getId() <= 0) {
            return 0;
        }
        return ranges.stream()
                .filter(range -> range.matches(item.getId()))
                .mapToInt(ScoredRange::score)
                .max()
                .orElse(0);
    }

    private static List<ScoredRange> ranges(Object raw, List<ScoredRange> fallback) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return fallback;
        }
        List<ScoredRange> parsed = new ArrayList<>();
        for (Object value : list) {
            parseRange(value).ifPresent(parsed::add);
        }
        return parsed.isEmpty() ? fallback : parsed;
    }

    private static Optional<ScoredRange> parseRange(Object raw) {
        if (raw instanceof Number number) {
            int value = number.intValue();
            return Optional.of(new ScoredRange(value, value, 50));
        }
        if (raw instanceof Map<?, ?> map) {
            Object ids = map.get("ids");
            int score = intValue(map.get("score"), 50);
            return parseRange(ids).map(range -> new ScoredRange(range.min(), range.max(), score));
        }
        if (raw instanceof List<?> list && list.size() >= 2) {
            return Optional.of(new ScoredRange(intValue(list.get(0), 0), intValue(list.get(1), 0),
                    list.size() >= 3 ? intValue(list.get(2), 50) : 50));
        }
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.toString().trim();
        if (text.isBlank()) {
            return Optional.empty();
        }
        String[] scoreSplit = text.split(":", 2);
        int score = scoreSplit.length == 2 ? intValue(scoreSplit[1], 50) : 50;
        String ids = scoreSplit[0].trim();
        int separator = ids.indexOf('-');
        try {
            if (separator > 0) {
                return Optional.of(new ScoredRange(Integer.parseInt(ids.substring(0, separator).trim()),
                        Integer.parseInt(ids.substring(separator + 1).trim()), score));
            }
            int id = Integer.parseInt(ids);
            return Optional.of(new ScoredRange(id, id, score));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private record ScoredRange(int min, int max, int score) {
        boolean matches(int value) {
            return value >= Math.min(min, max) && value <= Math.max(min, max);
        }
    }
}
