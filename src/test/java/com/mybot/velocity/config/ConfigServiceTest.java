package com.mybot.velocity.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsBundledSamples() throws Exception {
        ConfigService service = new ConfigService(tempDir, NOPLogger.NOP_LOGGER);
        service.initialize();
        assertThat(service.bots()).isEmpty();
        assertThat(service.graphs()).isNotEmpty();
        assertThat(tempDir.resolve("hg-bots.yml")).exists();
        service.close();
    }
}
