package com.mybot.velocity.demo;

import com.velocitypowered.api.util.GameProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BotSkinRegistry {
    private static final String TEXTURES_PROPERTY = "textures";

    private final ConcurrentMap<String, HgBotSkin> skinsByUsername = new ConcurrentHashMap<>();

    public void register(String username, HgBotSkin skin) {
        if (username == null || username.isBlank() || skin == null || !skin.isComplete()) {
            return;
        }
        skinsByUsername.put(key(username), skin);
    }

    public void unregister(String username) {
        if (username != null) {
            skinsByUsername.remove(key(username));
        }
    }

    public Optional<HgBotSkin> skin(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(skinsByUsername.get(key(username)));
    }

    public GameProfile applySkin(GameProfile profile) {
        return skin(profile.getName())
                .map(skin -> profile.withProperties(replaceTextures(profile.getProperties(), skin)))
                .orElse(profile);
    }

    public void clear() {
        skinsByUsername.clear();
    }

    private String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private List<GameProfile.Property> replaceTextures(List<GameProfile.Property> properties, HgBotSkin skin) {
        List<GameProfile.Property> next = new ArrayList<>();
        for (GameProfile.Property property : properties) {
            if (!TEXTURES_PROPERTY.equals(property.getName())) {
                next.add(property);
            }
        }
        next.add(new GameProfile.Property(TEXTURES_PROPERTY, skin.value(), skin.signature()));
        return List.copyOf(next);
    }
}
