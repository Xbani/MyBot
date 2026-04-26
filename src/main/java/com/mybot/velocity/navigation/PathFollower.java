package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.MovementInput;
import com.mybot.velocity.bot.Vec3;

import java.util.List;

public final class PathFollower {
    private int index;
    private Vec3 lastPosition = Vec3.ZERO;
    private int stuckTicks;

    public MovementInput follow(Vec3 position, List<PathNode> path, boolean sprint) {
        if (path == null || path.isEmpty()) {
            return MovementInput.NONE;
        }
        while (index < path.size() - 1 && position.horizontalDistanceTo(path.get(index).center()) < 0.7) {
            index++;
        }
        Vec3 target = path.get(Math.min(index, path.size() - 1)).center();
        BotPhysics.LookAngles look = BotPhysics.lookAt(position.add(0, 1.62, 0), target.add(0, 1.0, 0));
        double yawRad = Math.toRadians(look.yaw());
        Vec3 delta = target.subtract(position).horizontalNormalize();
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double forward = delta.x() * forwardX + delta.z() * forwardZ;
        double strafe = delta.x() * forwardZ - delta.z() * forwardX;
        boolean jump = target.y() > position.y() + 0.45;
        updateStuck(position);
        return stuckTicks > 20 ? new MovementInput(0, strafe, jump, false, false) : new MovementInput(forward, strafe, jump, sprint, false).clamp();
    }

    public boolean stuck() {
        return stuckTicks > 40;
    }

    public void reset() {
        index = 0;
        stuckTicks = 0;
    }

    private void updateStuck(Vec3 position) {
        if (position.horizontalDistanceTo(lastPosition) < 0.03) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPosition = position;
        }
    }
}
