package com.mybot.velocity.bot;

import com.mybot.velocity.behavior.BotIntent;
import com.mybot.velocity.navigation.PathNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BotRecorderTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsCircularHistoryAndDumpsJsonLines() throws Exception {
        BotRecorder recorder = new BotRecorder("bot-id", "Bot_Test", 2);
        recorder.record(snapshot(0));
        recorder.record(snapshot(1));
        recorder.record(snapshot(2));

        int dumped = recorder.dump(tempDir, "test");

        Path file = Files.list(tempDir).findFirst().orElseThrow();
        List<String> lines = Files.readAllLines(file);
        assertThat(dumped).isEqualTo(2);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("\"type\":\"metadata\"");
        assertThat(lines.get(1)).contains("\"x\":1.0");
        assertThat(lines.get(2)).contains("\"x\":2.0");
        assertThat(lines).allMatch(line -> line.startsWith("{") && line.endsWith("}"));
    }

    private BotRecorder.Snapshot snapshot(double x) {
        return new BotRecorder.Snapshot(
                Instant.now(),
                BotIntent.FIGHT,
                new Vec3(x, 64, 0),
                Vec3.ZERO,
                0,
                0,
                true,
                false,
                true,
                new MovementInput(1, 0, false, true, false),
                20,
                20,
                Optional.empty(),
                List.of(),
                List.of(new PathNode((int) x, 64, 0)),
                new Vec3(5, 64, 0),
                false
        );
    }
}
