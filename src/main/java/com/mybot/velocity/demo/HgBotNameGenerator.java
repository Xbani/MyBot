package com.mybot.velocity.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class HgBotNameGenerator {
    public static final List<String> DEFAULT_NAMES = List.of("Astra", "Nova", "Kiro", "Milo", "Rune", "Sora", "Vex", "Nox", "Lynx", "Echo");

    private final Random random;

    public HgBotNameGenerator(Random random) {
        this.random = random;
    }

    public List<String> generate(int count, List<String> names) {
        List<String> pool = new ArrayList<>(names == null || names.isEmpty() ? DEFAULT_NAMES : names);
        Collections.shuffle(pool, random);
        List<String> generated = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String base = pool.get(i % pool.size());
            generated.add("Bot_" + base + (i >= pool.size() ? i + 1 : ""));
        }
        return generated;
    }
}
