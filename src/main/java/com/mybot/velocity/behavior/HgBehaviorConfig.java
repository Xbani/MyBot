package com.mybot.velocity.behavior;

import java.util.Map;

public record HgBehaviorConfig(
        double detectionRadius,
        double meleeRange,
        float fleeHealth,
        float healHealth,
        double walkSpeed,
        double sprintSpeed,
        double sneakSpeed,
        long reactionMillis,
        double aimAccuracy
) {
    public static HgBehaviorConfig defaults() {
        return new HgBehaviorConfig(24.0, 3.0, 6.0f, 10.0f, 0.10, 0.16, 0.04, 180L, 0.86);
    }

    public static HgBehaviorConfig fromTraits(Map<String, Object> traits) {
        HgBehaviorConfig defaults = defaults();
        return new HgBehaviorConfig(
                number(traits.get("detection-radius"), defaults.detectionRadius()).doubleValue(),
                number(traits.get("melee-range"), defaults.meleeRange()).doubleValue(),
                number(traits.get("flee-health"), defaults.fleeHealth()).floatValue(),
                number(traits.get("heal-health"), defaults.healHealth()).floatValue(),
                number(traits.get("walk-speed"), defaults.walkSpeed()).doubleValue(),
                number(traits.get("sprint-speed"), defaults.sprintSpeed()).doubleValue(),
                number(traits.get("sneak-speed"), defaults.sneakSpeed()).doubleValue(),
                number(traits.get("reaction-millis"), defaults.reactionMillis()).longValue(),
                number(traits.get("aim-accuracy"), defaults.aimAccuracy()).doubleValue()
        );
    }

    private static Number number(Object value, Number fallback) {
        if (value instanceof Number number) {
            return number;
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
