package com.mybot.velocity.behavior;

public enum BotSkillLevel {
    NOOB,
    AVERAGE,
    TRYHARD;

    public static BotSkillLevel parse(Object value) {
        if (value == null) {
            return AVERAGE;
        }
        String text = value.toString().trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        return switch (text) {
            case "NOOB", "BAD", "BEGINNER" -> NOOB;
            case "TRYHARD", "STRONG", "GOOD" -> TRYHARD;
            default -> AVERAGE;
        };
    }
}
