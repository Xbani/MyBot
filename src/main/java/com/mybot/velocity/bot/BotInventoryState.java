package com.mybot.velocity.bot;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

public final class BotInventoryState {
    private final ItemStack[] slots = new ItemStack[46];
    private volatile ItemRegistryConfig registry = ItemRegistryConfig.defaults();
    private int selectedHotbarSlot;
    private int openContainerId;
    private int stateId;

    public void setContent(int containerId, int stateId, ItemStack[] items) {
        this.openContainerId = containerId;
        this.stateId = stateId;
        int length = Math.min(slots.length, items.length);
        System.arraycopy(items, 0, slots, 0, length);
        if (length < slots.length) {
            Arrays.fill(slots, length, slots.length, null);
        }
    }

    public void setSlot(int containerId, int stateId, int slot, ItemStack item) {
        this.openContainerId = containerId;
        this.stateId = stateId;
        if (slot >= 0 && slot < slots.length) {
            slots[slot] = item;
        }
    }

    public int selectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public void setSelectedHotbarSlot(int selectedHotbarSlot) {
        this.selectedHotbarSlot = Math.floorMod(selectedHotbarSlot, 9);
    }

    public int openContainerId() {
        return openContainerId;
    }

    public int stateId() {
        return stateId;
    }

    public OptionalInt bestWeaponHotbarSlot() {
        return java.util.stream.IntStream.range(0, 9)
                .filter(slot -> slots[36 + slot] != null)
                .boxed()
                .max(Comparator.comparingInt(slot -> weaponScore(slots[36 + slot])))
                .filter(slot -> weaponScore(slots[36 + slot]) > 0)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    public void setRegistry(ItemRegistryConfig registry) {
        if (registry != null) {
            this.registry = registry;
        }
    }

    public boolean hasUsableWeaponSelected() {
        ItemStack item = selectedHotbarItem();
        return item != null && weaponScore(item) > 0;
    }

    public ItemStack selectedHotbarItem() {
        return slots[36 + Math.floorMod(selectedHotbarSlot, 9)];
    }

    public boolean hasLikelyFoodOrHeal() {
        return Arrays.stream(slots).anyMatch(item -> item != null && registry.foodScore(item) >= 40);
    }

    public boolean hasLikelyArmor() {
        return Arrays.stream(slots).anyMatch(item -> item != null && registry.armorScore(item) > 0);
    }

    public boolean hasUsefulBlocks() {
        return Arrays.stream(slots).anyMatch(item -> item != null && registry.blockScore(item) > 0);
    }

    public boolean hasUsefulTools() {
        return Arrays.stream(slots).anyMatch(item -> item != null && registry.toolScore(item) > 0);
    }

    public boolean hasLikelyStoneSword() {
        return Arrays.stream(slots).anyMatch(item -> item != null && registry.weaponScore(item) >= 70);
    }

    public boolean hasKitFeather() {
        return Arrays.stream(slots).anyMatch(this::isKitSelector);
    }

    public List<ItemSnapshot> snapshot() {
        return java.util.stream.IntStream.range(0, slots.length)
                .filter(slot -> slots[slot] != null)
                .mapToObj(slot -> new ItemSnapshot(slot, slots[slot].getId(), slots[slot].getAmount(), itemLabel(slots[slot])))
                .toList();
    }

    public int itemScore(ItemStack item) {
        if (item == null) {
            return 0;
        }
        return Math.max(Math.max(weaponScore(item), registry.foodScore(item)), registry.utilityScore(item));
    }

    public int weaponScore(ItemStack item) {
        return registry.weaponScore(item);
    }

    private boolean isKitSelector(ItemStack item) {
        if (item == null || item.getDataComponentsPatch() == null) {
            return false;
        }
        String customName = plainName(item, true);
        String itemName = plainName(item, false);
        return customName.contains("kit") || itemName.contains("kit");
    }

    private String plainName(ItemStack item, boolean custom) {
        try {
            var component = item.getDataComponentsPatch().get(custom ? DataComponentTypes.CUSTOM_NAME : DataComponentTypes.ITEM_NAME);
            if (component == null) {
                return "";
            }
            return PlainTextComponentSerializer.plainText().serialize(component).toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String itemLabel(ItemStack item) {
        String customName = plainName(item, true);
        if (!customName.isBlank()) {
            return customName;
        }
        String itemName = plainName(item, false);
        return itemName.isBlank() ? "item:" + item.getId() : itemName;
    }

    public record ItemSnapshot(int slot, int id, int amount, String name) { }
}
