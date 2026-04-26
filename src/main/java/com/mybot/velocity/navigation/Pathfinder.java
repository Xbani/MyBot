package com.mybot.velocity.navigation;

import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class Pathfinder {
    private static final int MAX_EXPANSIONS = 5000;
    private static final double DEFAULT_GOAL_RADIUS = 1.5;

    public Optional<List<PathNode>> findPath(WorldBlockCache blocks, Vec3 from, Vec3 to) {
        return findPath(blocks, from, to, DEFAULT_GOAL_RADIUS);
    }

    public Optional<List<PathNode>> findPath(WorldBlockCache blocks, Vec3 from, Vec3 to, double goalRadius) {
        PathNode start = new PathNode(floor(from.x()), floor(from.y()), floor(from.z()));
        PathNode goal = new PathNode(floor(to.x()), floor(to.y()), floor(to.z()));
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::score));
        Map<PathNode, PathNode> cameFrom = new HashMap<>();
        Map<PathNode, Double> costSoFar = new HashMap<>();
        Set<PathNode> closed = new HashSet<>();
        open.add(new SearchNode(start, heuristic(start, goal)));
        costSoFar.put(start, 0.0);
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < MAX_EXPANSIONS) {
            PathNode current = open.poll().node();
            if (!closed.add(current)) {
                continue;
            }
            if (reached(current, goal, goalRadius)) {
                return Optional.of(reconstruct(cameFrom, current));
            }
            for (PathNode neighbor : neighbors(blocks, current)) {
                if (closed.contains(neighbor)) {
                    continue;
                }
                double nextCost = costSoFar.get(current) + movementCost(current, neighbor);
                if (nextCost < costSoFar.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    costSoFar.put(neighbor, nextCost);
                    cameFrom.put(neighbor, current);
                    open.add(new SearchNode(neighbor, nextCost + heuristic(neighbor, goal)));
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
                if (walkable(blocks, candidate) && !cutsCorner(blocks, node, candidate)) {
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

    private boolean cutsCorner(WorldBlockCache blocks, PathNode from, PathNode to) {
        int dx = Integer.compare(to.x(), from.x());
        int dz = Integer.compare(to.z(), from.z());
        if (dx == 0 || dz == 0) {
            return false;
        }
        return !walkable(blocks, new PathNode(from.x() + dx, to.y(), from.z()))
                || !walkable(blocks, new PathNode(from.x(), to.y(), from.z() + dz));
    }

    private boolean reached(PathNode current, PathNode goal, double goalRadius) {
        double dx = current.x() - goal.x();
        double dz = current.z() - goal.z();
        return Math.sqrt(dx * dx + dz * dz) <= goalRadius && Math.abs(current.y() - goal.y()) <= 1;
    }

    private double movementCost(PathNode from, PathNode to) {
        int dx = Math.abs(to.x() - from.x());
        int dz = Math.abs(to.z() - from.z());
        int dy = Math.abs(to.y() - from.y());
        double horizontal = dx + dz == 2 ? 1.414 : 1.0;
        return horizontal + dy * 0.35;
    }

    private double heuristic(PathNode node, PathNode goal) {
        int dx = Math.abs(node.x() - goal.x());
        int dz = Math.abs(node.z() - goal.z());
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return min * 1.414 + (max - min) + Math.abs(node.y() - goal.y()) * 0.35;
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }

    private record SearchNode(PathNode node, double score) { }
}
