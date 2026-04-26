package com.mybot.velocity.bot;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.OptionalInt;

public final class BotInventoryState {
    private final ItemStack[] slots = new ItemStack[46];
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

    public boolean hasLikelyFoodOrHeal() {
        return Arrays.stream(slots).anyMatch(item -> item != null && itemScore(item) >= 40);
    }

    public int itemScore(ItemStack item) {
        if (item == null) {
            return 0;
        }
        return Math.max(weaponScore(item), foodScore(item));
    }

    public int weaponScore(ItemStack item) {
        if (item == null) {
            return 0;
        }
        int id = item.getId();
        // Registry ids vary by version; this is deliberately broad and only provides a local heuristic.
        if (id <= 0) return 0;
        if (id >= 800 && id <= 900) return 90;
        if (id >= 700 && id < 800) return 70;
        if (id >= 600 && id < 700) return 55;
        return 10;
    }

    private int foodScore(ItemStack item) {
        int id = item.getId();
        if (id <= 0) return 0;
        if (id >= 900 && id <= 1050) return 50;
        return 0;
    }
}
