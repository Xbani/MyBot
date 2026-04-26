package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class Pathfinder {
    private static final int MAX_EXPANSIONS = 1800;

    public Optional<List<PathNode>> findPath(WorldBlockCache blocks, Vec3 from, Vec3 to) {
        PathNode start = new PathNode(floor(from.x()), floor(from.y()), floor(from.z()));
        PathNode goal = new PathNode(floor(to.x()), floor(to.y()), floor(to.z()));
        Queue<PathNode> open = new ArrayDeque<>();
        Map<PathNode, PathNode> cameFrom = new HashMap<>();
        Set<PathNode> closed = new HashSet<>();
        open.add(start);
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < MAX_EXPANSIONS) {
            PathNode current = open.poll();
            if (current.distanceSquared(goal) <= 2) {
                return Optional.of(reconstruct(cameFrom, current));
            }
            if (!closed.add(current)) {
                continue;
            }
            for (PathNode neighbor : neighbors(blocks, current)) {
                if (!closed.contains(neighbor) && !cameFrom.containsKey(neighbor) && !neighbor.equals(start)) {
                    cameFrom.put(neighbor, current);
                    open.add(neighbor);
                }
            }
        }
        return Optional.empty();
    }

    private List<PathNode> neighbors(WorldBlockCache blocks, PathNode node) {
        List<PathNode> result = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] dir : dirs) {
            for (int dy : new int[]{0, 1, -1}) {
                PathNode candidate = new PathNode(node.x() + dir[0], node.y() + dy, node.z() + dir[1]);
                if (walkable(blocks, candidate)) {
                    result.add(candidate);
                    break;
                }
            }
        }
        return result;
    }

    public boolean walkable(WorldBlockCache blocks, PathNode node) {
        return blocks.isSolid(node.x(), node.y() - 1, node.z())
                && !blocks.isSolid(node.x(), node.y(), node.z())
                && !blocks.isSolid(node.x(), node.y() + 1, node.z());
    }

    private List<PathNode> reconstruct(Map<PathNode, PathNode> cameFrom, PathNode end) {
        ArrayList<PathNode> path = new ArrayList<>();
        PathNode current = end;
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }
}
