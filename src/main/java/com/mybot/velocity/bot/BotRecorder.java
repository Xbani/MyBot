package com.mybot.velocity.bot;

import com.mybot.velocity.behavior.BotIntent;
import com.mybot.velocity.navigation.PathNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class BotRecorder {
    private static final int DEFAULT_TICKS_PER_SECOND = 20;
    private static final int DEFAULT_SECONDS = 30;
    private static final int DEFAULT_CAPACITY = DEFAULT_TICKS_PER_SECOND * DEFAULT_SECONDS;

    private final String botId;
    private final String username;
    private final Snapshot[] snapshots;
    private int next;
    private int size;

    public BotRecorder(String botId, String username) {
        this(botId, username, DEFAULT_CAPACITY);
    }

    BotRecorder(String botId, String username, int capacity) {
        this.botId = botId;
        this.username = username;
        this.snapshots = new Snapshot[capacity];
    }

    public synchronized void record(Snapshot snapshot) {
        snapshots[next] = snapshot;
        next = (next + 1) % snapshots.length;
        if (size < snapshots.length) {
            size++;
        }
    }

    public synchronized int dump(Path directory, String reason) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(safe(botId) + "-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-') + ".jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("{\"type\":\"metadata\",\"botId\":\"");
            writer.write(escape(botId));
            writer.write("\",\"username\":\"");
            writer.write(escape(username));
            writer.write("\",\"reason\":\"");
            writer.write(escape(reason));
            writer.write("\",\"snapshots\":");
            writer.write(Integer.toString(size));
            writer.write("}");
            writer.newLine();
            for (Snapshot snapshot : orderedSnapshots()) {
                writer.write(snapshot.toJson());
                writer.newLine();
            }
        }
        return size;
    }

    private List<Snapshot> orderedSnapshots() {
        List<Snapshot> ordered = new ArrayList<>(size);
        int start = (next - size + snapshots.length) % snapshots.length;
        for (int i = 0; i < size; i++) {
            ordered.add(snapshots[(start + i) % snapshots.length]);
        }
        return ordered;
    }

    private static String safe(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record Snapshot(
            Instant at,
            BotIntent intent,
            Vec3 position,
            Vec3 velocity,
            float yaw,
            float pitch,
            boolean onGround,
            boolean horizontalCollision,
            boolean chunksLoaded,
            MovementInput input,
            float health,
            int food,
            Optional<TargetSnapshot> target,
            List<PlayerSnapshot> players,
            List<PathNode> path,
            Vec3 pathTarget,
            boolean pathStuck
    ) {
        public static Snapshot from(BotIntent intent,
                                    Vec3 position,
                                    Vec3 velocity,
                                    float yaw,
                                    float pitch,
                                    BotPhysics.PhysicsTick tick,
                                    float health,
                                    int food,
                                    Optional<TrackedPlayer> target,
                                    Collection<TrackedPlayer> players,
                                    List<PathNode> path,
                                    Vec3 pathTarget,
                                    boolean pathStuck) {
            return new Snapshot(
                    Instant.now(),
                    intent,
                    position,
                    velocity,
                    yaw,
                    pitch,
                    tick.onGround(),
                    tick.horizontalCollision(),
                    tick.chunksLoaded(),
                    tick.input(),
                    health,
                    food,
                    target.map(TargetSnapshot::from),
                    players.stream().map(PlayerSnapshot::from).toList(),
                    List.copyOf(path),
                    pathTarget,
                    pathStuck
            );
        }

        private String toJson() {
            StringBuilder json = new StringBuilder(512);
            json.append('{');
            field(json, "type", "snapshot").append(',');
            field(json, "at", at.toString()).append(',');
            field(json, "intent", intent.name()).append(',');
            vec(json, "position", position).append(',');
            vec(json, "velocity", velocity).append(',');
            json.append("\"yaw\":").append(yaw).append(',');
            json.append("\"pitch\":").append(pitch).append(',');
            json.append("\"onGround\":").append(onGround).append(',');
            json.append("\"horizontalCollision\":").append(horizontalCollision).append(',');
            json.append("\"chunksLoaded\":").append(chunksLoaded).append(',');
            input(json, input).append(',');
            json.append("\"health\":").append(health).append(',');
            json.append("\"food\":").append(food).append(',');
            json.append("\"pathStuck\":").append(pathStuck).append(',');
            vec(json, "pathTarget", pathTarget).append(',');
            json.append("\"target\":");
            if (target.isPresent()) {
                target.get().appendJson(json);
            } else {
                json.append("null");
            }
            json.append(',');
            json.append("\"players\":[");
            for (int i = 0; i < players.size(); i++) {
                if (i > 0) json.append(',');
                players.get(i).appendJson(json);
            }
            json.append("],\"path\":[");
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) json.append(',');
                PathNode node = path.get(i);
                json.append("{\"x\":").append(node.x()).append(",\"y\":").append(node.y()).append(",\"z\":").append(node.z()).append('}');
            }
            json.append("]}");
            return json.toString();
        }

        private static StringBuilder input(StringBuilder json, MovementInput input) {
            json.append("\"input\":{\"forward\":").append(input.forward())
                    .append(",\"strafe\":").append(input.strafe())
                    .append(",\"jump\":").append(input.jump())
                    .append(",\"sprint\":").append(input.sprint())
                    .append(",\"sneak\":").append(input.sneak())
                    .append('}');
            return json;
        }

        private static StringBuilder vec(StringBuilder json, String name, Vec3 value) {
            json.append('"').append(name).append("\":{\"x\":").append(value.x())
                    .append(",\"y\":").append(value.y())
                    .append(",\"z\":").append(value.z())
                    .append('}');
            return json;
        }

        private static StringBuilder field(StringBuilder json, String name, String value) {
            return json.append('"').append(name).append("\":\"").append(escape(value)).append('"');
        }
    }

    private record TargetSnapshot(int entityId, String username, Vec3 position, boolean bot) {
        private static TargetSnapshot from(TrackedPlayer player) {
            return new TargetSnapshot(player.entityId(), player.username(), player.position(), player.isBot());
        }

        private void appendJson(StringBuilder json) {
            json.append("{\"entityId\":").append(entityId).append(',');
            Snapshot.field(json, "username", username).append(',');
            Snapshot.vec(json, "position", position).append(',');
            json.append("\"bot\":").append(bot).append('}');
        }
    }

    private record PlayerSnapshot(int entityId, String username, Vec3 position, boolean bot) {
        private static PlayerSnapshot from(TrackedPlayer player) {
            return new PlayerSnapshot(player.entityId(), player.username(), player.position(), player.isBot());
        }

        private void appendJson(StringBuilder json) {
            json.append("{\"entityId\":").append(entityId).append(',');
            Snapshot.field(json, "username", username).append(',');
            Snapshot.vec(json, "position", position).append(',');
            json.append("\"bot\":").append(bot).append('}');
        }
    }
}
