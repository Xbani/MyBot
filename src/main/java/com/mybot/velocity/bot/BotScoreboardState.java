package com.mybot.velocity.bot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundResetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BotScoreboardState {
    private static final Pattern FEAST_SECONDS = Pattern.compile("(?i)feast\\D+(\\d+)");
    private static final Pattern PHASE = Pattern.compile("(?i)phase\\s*[:\\-]?\\s*([a-zA-Z]+)");
    private static final Pattern ALIVE = Pattern.compile("(?i)(?:alive|players?|remaining)\\D+(\\d+)");

    private final ConcurrentMap<String, ScoreLine> scores = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TeamLine> teams = new ConcurrentHashMap<>();
    private volatile Snapshot testSnapshot;

    public void setScore(ClientboundSetScorePacket packet) {
        String display = plain(packet.getDisplay());
        scores.put(packet.getOwner(), new ScoreLine(packet.getOwner(), display, packet.getValue()));
    }

    public void resetScore(ClientboundResetScorePacket packet) {
        scores.remove(packet.getOwner());
    }

    public void setTeam(ClientboundSetPlayerTeamPacket packet) {
        TeamAction action = packet.getAction();
        if (action == TeamAction.REMOVE) {
            teams.remove(packet.getTeamName());
            return;
        }
        TeamLine current = teams.getOrDefault(packet.getTeamName(), new TeamLine("", "", List.of()));
        String prefix = packet.getPrefix() == null ? current.prefix() : plain(packet.getPrefix());
        String suffix = packet.getSuffix() == null ? current.suffix() : plain(packet.getSuffix());
        List<String> players = new ArrayList<>(current.players());
        if (action == TeamAction.CREATE || action == TeamAction.ADD_PLAYER) {
            for (String player : safePlayers(packet)) {
                if (!players.contains(player)) {
                    players.add(player);
                }
            }
        } else if (action == TeamAction.REMOVE_PLAYER) {
            players.removeAll(safePlayers(packet));
        } else if (action == TeamAction.UPDATE && packet.getPlayers() != null) {
            players = new ArrayList<>(safePlayers(packet));
        }
        teams.put(packet.getTeamName(), new TeamLine(prefix, suffix, List.copyOf(players)));
    }

    public Snapshot snapshot() {
        if (testSnapshot != null) {
            return testSnapshot;
        }
        List<String> lines = scores.values().stream()
                .sorted(Comparator.comparingInt(ScoreLine::score).reversed())
                .map(this::renderLine)
                .filter(line -> !line.isBlank())
                .toList();
        String joined = String.join(" ", lines);
        Matcher feast = FEAST_SECONDS.matcher(joined);
        Matcher phase = PHASE.matcher(joined);
        Matcher alive = ALIVE.matcher(joined);
        return new Snapshot(
                lines,
                phase.find() ? phase.group(1) : "",
                feast.find() ? Integer.parseInt(feast.group(1)) : -1,
                alive.find() ? Integer.parseInt(alive.group(1)) : -1
        );
    }

    public void setSnapshotForTesting(Snapshot snapshot) {
        this.testSnapshot = snapshot;
    }

    private String renderLine(ScoreLine score) {
        String base = score.display().isBlank() ? score.owner() : score.display();
        TeamLine team = teamFor(score.owner());
        if (team == null) {
            return clean(base);
        }
        return clean(team.prefix() + base + team.suffix());
    }

    private TeamLine teamFor(String owner) {
        for (TeamLine team : teams.values()) {
            if (team.players().contains(owner)) {
                return team;
            }
        }
        return null;
    }

    private List<String> safePlayers(ClientboundSetPlayerTeamPacket packet) {
        String[] players = packet.getPlayers();
        return players == null ? List.of() : List.of(players);
    }

    private String plain(Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("§.", "").trim();
    }

    private record ScoreLine(String owner, String display, int score) { }

    private record TeamLine(String prefix, String suffix, List<String> players) { }

    public record Snapshot(List<String> lines, String phase, int feastSeconds, int alivePlayers) {
        public Snapshot(List<String> lines, String phase, int feastSeconds) {
            this(lines, phase, feastSeconds, -1);
        }
    }
}
