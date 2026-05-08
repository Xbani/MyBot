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
        double aimAccuracy,
        double lobbyRoamRadius,
        double pregameRoamRadius,
        boolean debugLogging,
        Map<String, Object> traits
) {
    public HgBehaviorConfig(double detectionRadius,
                            double meleeRange,
                            float fleeHealth,
                            float healHealth,
                            double walkSpeed,
                            double sprintSpeed,
                            double sneakSpeed,
                            long reactionMillis,
                            double aimAccuracy) {
        this(detectionRadius, meleeRange, fleeHealth, healHealth, walkSpeed, sprintSpeed, sneakSpeed,
                reactionMillis, aimAccuracy, 12.0, 18.0, false,
                Map.of("aim-error", Math.max(0.0, (1.0 - aimAccuracy) * 30.0),
                        "reaction-delay-min-millis", reactionMillis,
                        "reaction-delay-max-millis", reactionMillis));
    }

    public static HgBehaviorConfig defaults() {
        return new HgBehaviorConfig(24.0, 3.0, 6.0f, 10.0f, 0.10, 0.16, 0.04, 180L, 0.86,
                12.0, 18.0, false, Map.of());
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
                number(traits.get("aim-accuracy"), defaults.aimAccuracy()).doubleValue(),
                number(traits.get("lobby-roam-radius"), defaults.lobbyRoamRadius()).doubleValue(),
                number(traits.get("pregame-roam-radius"), defaults.pregameRoamRadius()).doubleValue(),
                bool(traits.get("debug-logging"), defaults.debugLogging()),
                Map.copyOf(traits)
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

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }
}
