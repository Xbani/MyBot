package com.mybot.velocity.behavior;

import java.util.Map;
import java.util.Objects;

public record BotPersonality(
        BotSkillLevel skillLevel,
        double aggression,
        double courage,
        double greed,
        double curiosity,
        double panicThreshold,
        double teamingLikelihood,
        double betrayalLikelihood,
        double tauntLikelihood,
        double chasePersistence,
        double fightConfidenceBias
) {
    public static BotPersonality fromTraits(Map<String, Object> traits) {
        BotSkillLevel skill = BotSkillLevel.parse(first(traits, "skill-level", "skillLevel", "skill"));
        BotPersonality defaults = defaults(skill);
        return new BotPersonality(
                skill,
                bounded(number(first(traits, "aggression"), defaults.aggression())),
                bounded(number(first(traits, "courage"), defaults.courage())),
                bounded(number(first(traits, "greed"), defaults.greed())),
                bounded(number(first(traits, "curiosity"), defaults.curiosity())),
                bounded(number(first(traits, "panic-threshold", "panicThreshold"), defaults.panicThreshold())),
                bounded(number(first(traits, "teaming-likelihood", "teamingLikelihood"), defaults.teamingLikelihood())),
                bounded(number(first(traits, "betrayal-likelihood", "betrayalLikelihood"), defaults.betrayalLikelihood())),
                bounded(number(first(traits, "taunt-likelihood", "tauntLikelihood"), defaults.tauntLikelihood())),
                bounded(number(first(traits, "chase-persistence", "chasePersistence"), defaults.chasePersistence())),
                clamp(number(first(traits, "fight-confidence-bias", "fightConfidenceBias"), defaults.fightConfidenceBias()), -1.0, 1.0)
        );
    }

    public static BotPersonality defaults(BotSkillLevel skill) {
        return switch (skill) {
            case NOOB -> new BotPersonality(skill, 0.42, 0.35, 0.68, 0.55, 0.35, 0.38, 0.08, 0.06, 0.35, -0.18);
            case TRYHARD -> new BotPersonality(skill, 0.72, 0.68, 0.48, 0.42, 0.18, 0.22, 0.16, 0.04, 0.72, 0.18);
            default -> new BotPersonality(skill, 0.56, 0.52, 0.52, 0.45, 0.25, 0.30, 0.10, 0.04, 0.55, 0.0);
        };
    }

    private static Object first(Map<String, Object> traits, String... keys) {
        for (String key : keys) {
            if (traits.containsKey(key)) {
                return traits.get(key);
            }
        }
        return null;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
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
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
