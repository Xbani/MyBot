package com.mybot.velocity.bot;

public record MovementInput(double forward, double strafe, boolean jump, boolean sprint, boolean sneak) {
    public static final MovementInput NONE = new MovementInput(0, 0, false, false, false);

    public MovementInput clamp() {
        return new MovementInput(clampUnit(forward), clampUnit(strafe), jump, sprint, sneak);
    }

    public boolean moving() {
        return Math.abs(forward) > 1.0E-6 || Math.abs(strafe) > 1.0E-6;
    }

    private static double clampUnit(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
