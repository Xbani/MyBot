package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.Vec3;

public record PathNode(int x, int y, int z) {
    public Vec3 center() {
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    public double distanceSquared(PathNode other) {
        int dx = x - other.x;
        int dy = y - other.y;
        int dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
