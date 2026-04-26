package com.mybot.velocity.demo;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record HgBotSkin(String value, String signature) {
    public HgBotSkin {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(signature, "signature");
    }

    public boolean isComplete() {
        return !value.isBlank() && !signature.isBlank();
    }

    public static Optional<HgBotSkin> fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        String value = Objects.toString(map.get("value"), "");
        String signature = Objects.toString(map.get("signature"), "");
        HgBotSkin skin = new HgBotSkin(value, signature);
        return skin.isComplete() ? Optional.of(skin) : Optional.empty();
    }
}
