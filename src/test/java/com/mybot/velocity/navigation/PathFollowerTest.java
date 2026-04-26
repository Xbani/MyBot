package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.MovementInput;
import com.mybot.velocity.bot.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathFollowerTest {
    @Test
    void jumpsWhenNextPathNodeIsOneBlockHigher() {
        PathFollower follower = new PathFollower();
        List<PathNode> path = List.of(
                new PathNode(0, 64, 0),
                new PathNode(1, 65, 0)
        );

        MovementInput input = follower.follow(new Vec3(0.5, 64, 0.5), path, false);

        assertThat(input.jump()).isTrue();
        assertThat(input.moving()).isTrue();
    }
}
