package com.mybot.velocity.bot;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BotInventoryStateTest {
    @Test
    void usesConfiguredItemRegistryRangesForWeaponSelection() {
        BotInventoryState inventory = new BotInventoryState();
        inventory.setRegistry(ItemRegistryConfig.fromTraits(Map.of(
                "weapon-items", List.of("42:100", "50-55:20")
        )));
        inventory.setSlot(0, 1, 36, new ItemStack(50, 1, null));
        inventory.setSlot(0, 1, 37, new ItemStack(42, 1, null));

        assertThat(inventory.bestWeaponHotbarSlot()).hasValue(1);
        assertThat(inventory.weaponScore(inventory.selectedHotbarItem())).isEqualTo(20);
        inventory.setSelectedHotbarSlot(1);
        assertThat(inventory.weaponScore(inventory.selectedHotbarItem())).isEqualTo(100);
        assertThat(inventory.hasUsableWeaponSelected()).isTrue();
    }

    @Test
    void stoneSwordConfirmationRequiresDedicatedRegistryRange() {
        BotInventoryState inventory = new BotInventoryState();
        inventory.setRegistry(ItemRegistryConfig.fromTraits(Map.of(
                "weapon-items", List.of("700-799:70"),
                "stone-sword-items", List.of("742")
        )));
        inventory.setSlot(0, 1, 36, new ItemStack(706, 1, null));

        assertThat(inventory.weaponScore(inventory.selectedHotbarItem())).isEqualTo(70);
        assertThat(inventory.hasLikelyStoneSword()).isFalse();

        inventory.setSlot(0, 1, 37, new ItemStack(742, 1, null));

        assertThat(inventory.hasLikelyStoneSword()).isTrue();
    }
}
