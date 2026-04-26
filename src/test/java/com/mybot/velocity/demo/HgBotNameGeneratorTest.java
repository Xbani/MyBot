package com.mybot.velocity.demo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class HgBotNameGeneratorTest {
    @Test
    void generatesDistinctValidBotUsernames() {
        HgBotNameGenerator generator = new HgBotNameGenerator(new Random(1));

        List<String> names = generator.generate(2, HgBotNameGenerator.DEFAULT_NAMES);

        assertThat(names).hasSize(2).doesNotHaveDuplicates();
        assertThat(names).allMatch(name -> name.startsWith("Bot_"));
        assertThat(names).noneMatch(name -> name.contains("#"));
    }
}
