package com.mybot.velocity.demo;

import com.velocitypowered.api.util.GameProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BotSkinRegistryTest {
    @Test
    void appliesRegisteredSkinByUsername() {
        BotSkinRegistry registry = new BotSkinRegistry();
        registry.register("Bot_Nova", new HgBotSkin("texture-value", "texture-signature"));
        GameProfile profile = new GameProfile(UUID.randomUUID(), "Bot_Nova", List.of());

        GameProfile skinned = registry.applySkin(profile);

        assertThat(skinned.getProperties()).singleElement().satisfies(property -> {
            assertThat(property.getName()).isEqualTo("textures");
            assertThat(property.getValue()).isEqualTo("texture-value");
            assertThat(property.getSignature()).isEqualTo("texture-signature");
        });
    }

    @Test
    void leavesUnknownProfilesUntouched() {
        BotSkinRegistry registry = new BotSkinRegistry();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "RealPlayer", List.of());

        assertThat(registry.applySkin(profile)).isSameAs(profile);
    }

    @Test
    void ignoresIncompleteSkins() {
        BotSkinRegistry registry = new BotSkinRegistry();
        registry.register("Bot_Nova", new HgBotSkin("texture-value", ""));

        assertThat(registry.skin("Bot_Nova")).isEmpty();
    }
}
