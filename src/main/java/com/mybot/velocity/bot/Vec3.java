package com.mybot.velocity.bot;

public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 multiply(double scalar) {
        return new Vec3(x * scalar, y * scalar, z * scalar);
    }

    public double horizontalDistanceTo(Vec3 other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double distanceSquaredTo(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vec3 horizontalNormalize() {
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0E-6) {
            return ZERO;
        }
        return new Vec3(x / length, 0, z / length);
    }
}
