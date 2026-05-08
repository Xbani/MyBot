package com.mybot.velocity.behavior;

import java.util.Map;
import java.util.Objects;

public record BotSkillProfile(
        long reactionDelayMinMillis,
        long reactionDelayMaxMillis,
        double aimErrorDegrees,
        double lootMistakeChance,
        double inventoryDelayChance,
        double missedHitChance,
        double strafeQuality,
        double critAttemptChance
) {
    public static BotSkillProfile fromTraits(Map<String, Object> traits, BotSkillLevel level) {
        BotSkillProfile defaults = defaults(level);
        long reaction = number(first(traits, "reaction-millis", "reactionMillis"), -1L).longValue();
        return new BotSkillProfile(
                number(first(traits, "reaction-delay-min-millis", "reactionDelayMinMillis"), reaction > 0 ? reaction : defaults.reactionDelayMinMillis()).longValue(),
                number(first(traits, "reaction-delay-max-millis", "reactionDelayMaxMillis"), reaction > 0 ? Math.max(reaction + 180L, defaults.reactionDelayMaxMillis()) : defaults.reactionDelayMaxMillis()).longValue(),
                number(first(traits, "aim-error", "aimErrorDegrees"), defaults.aimErrorDegrees()).doubleValue(),
                bounded(number(first(traits, "loot-mistake-chance", "lootMistakeChance"), defaults.lootMistakeChance()).doubleValue()),
                bounded(number(first(traits, "inventory-delay-chance", "inventoryDelayChance"), defaults.inventoryDelayChance()).doubleValue()),
                bounded(number(first(traits, "missed-hit-chance", "missedHitChance"), defaults.missedHitChance()).doubleValue()),
                bounded(number(first(traits, "strafe-quality", "strafeQuality"), defaults.strafeQuality()).doubleValue()),
                bounded(number(first(traits, "crit-attempt-chance", "critAttemptChance"), defaults.critAttemptChance()).doubleValue())
        );
    }

    public static BotSkillProfile defaults(BotSkillLevel level) {
        return switch (level) {
            case NOOB -> new BotSkillProfile(380, 950, 9.0, 0.24, 0.34, 0.28, 0.35, 0.08);
            case TRYHARD -> new BotSkillProfile(120, 280, 2.2, 0.05, 0.08, 0.08, 0.82, 0.42);
            default -> new BotSkillProfile(220, 520, 4.8, 0.12, 0.18, 0.16, 0.58, 0.22);
        };
    }

    public long randomReactionDelay(java.util.Random random) {
        long min = Math.min(reactionDelayMinMillis, reactionDelayMaxMillis);
        long max = Math.max(reactionDelayMinMillis, reactionDelayMaxMillis);
        if (max <= min) {
            return min;
        }
        return min + Math.floorMod(random.nextLong(), max - min + 1);
    }

    private static Object first(Map<String, Object> traits, String... keys) {
        for (String key : keys) {
            if (traits.containsKey(key)) {
                return traits.get(key);
            }
        }
        return null;
    }

    private static Number number(Object value, Number fallback) {
        if (value instanceof Number number) {
            return number;
        }
        if (value != null) {
            try {
                return Double.parseDouble(Objects.toString(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double bounded(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
