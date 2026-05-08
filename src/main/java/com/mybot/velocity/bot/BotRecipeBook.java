package com.mybot.velocity.bot;

import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplayEntry;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapedCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapelessCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.CompositeSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemStackSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.SlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.TagSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.WithRemainderSlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookRemovePacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BotRecipeBook {
    private final ConcurrentMap<String, RecipeInfo> recipesByName = new ConcurrentHashMap<>();
    private volatile ItemRegistryConfig registry = ItemRegistryConfig.defaults();

    public void setRegistry(ItemRegistryConfig registry) {
        if (registry != null) {
            this.registry = registry;
        }
    }

    public void add(ClientboundRecipeBookAddPacket packet) {
        if (packet.isReplace()) {
            recipesByName.clear();
        }
        for (ClientboundRecipeBookAddPacket.Entry entry : packet.getEntries()) {
            RecipeDisplayEntry contents = entry.contents();
            RecipeDisplay display = contents.display();
            int resultItemId = resultItemId(display);
            classify(display).ifPresent(target -> recipesByName.put(target, new RecipeInfo(contents.id(), resultItemId)));
        }
    }

    public void remove(ClientboundRecipeBookRemovePacket packet) {
        java.util.Set<Integer> removed = java.util.Arrays.stream(packet.getRecipes()).boxed().collect(java.util.stream.Collectors.toSet());
        recipesByName.entrySet().removeIf(entry -> removed.contains(entry.getValue().id()));
    }

    public OptionalInt recipeId(String fallbackName) {
        if (fallbackName == null || fallbackName.isBlank()) {
            return OptionalInt.empty();
        }
        RecipeInfo info = recipesByName.get(normalize(fallbackName));
        return info == null ? OptionalInt.empty() : OptionalInt.of(info.id());
    }

    public OptionalInt resultItemId(String fallbackName) {
        if (fallbackName == null || fallbackName.isBlank()) {
            return OptionalInt.empty();
        }
        RecipeInfo info = recipesByName.get(normalize(fallbackName));
        return info == null || info.resultItemId() <= 0 ? OptionalInt.empty() : OptionalInt.of(info.resultItemId());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        recipesByName.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().snapshot()));
        return snapshot;
    }

    private java.util.Optional<String> classify(RecipeDisplay display) {
        if (isStoneSwordRecipe(display)) {
            return java.util.Optional.of("stone_sword");
        }
        if (isWoodenPickaxeRecipe(display)) {
            return java.util.Optional.of("wooden_pickaxe");
        }
        if (isPlanksRecipe(display)) {
            return java.util.Optional.of("planks");
        }
        SlotDisplay result = null;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            result = shaped.result();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            result = shapeless.result();
        }
        int itemId = resultItemId(result);
        if (itemId <= 0) {
            return java.util.Optional.empty();
        }
        return registry.craftTarget(itemId);
    }

    private int resultItemId(RecipeDisplay display) {
        SlotDisplay result = null;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            result = shaped.result();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            result = shapeless.result();
        }
        return resultItemId(result);
    }

    private boolean isStoneSwordRecipe(RecipeDisplay display) {
        if (!(display instanceof ShapedCraftingRecipeDisplay shaped) || shaped.width() != 1 || shaped.height() != 3) {
            return false;
        }
        List<SlotDisplay> ingredients = shaped.ingredients();
        if (ingredients.size() != 3) {
            return false;
        }
        long stones = ingredients.stream().filter(this::isStoneIngredient).count();
        long sticks = ingredients.stream().filter(this::isStickIngredient).count();
        return stones >= 2 && sticks >= 1;
    }

    private boolean isWoodenPickaxeRecipe(RecipeDisplay display) {
        if (!(display instanceof ShapedCraftingRecipeDisplay shaped) || shaped.width() != 3 || shaped.height() != 3) {
            return false;
        }
        List<SlotDisplay> ingredients = shaped.ingredients();
        if (ingredients.size() != 9) {
            return false;
        }
        return isPlankIngredient(ingredients.get(0))
                && isPlankIngredient(ingredients.get(1))
                && isPlankIngredient(ingredients.get(2))
                && isEmpty(ingredients.get(3))
                && isStickIngredient(ingredients.get(4))
                && isEmpty(ingredients.get(5))
                && isEmpty(ingredients.get(6))
                && isStickIngredient(ingredients.get(7))
                && isEmpty(ingredients.get(8));
    }

    private boolean isPlanksRecipe(RecipeDisplay display) {
        if (!(display instanceof ShapelessCraftingRecipeDisplay shapeless)) {
            return false;
        }
        List<SlotDisplay> ingredients = shapeless.ingredients();
        return ingredients.size() == 1 && isLogIngredient(ingredients.getFirst());
    }

    private boolean isStoneIngredient(SlotDisplay slot) {
        return slotMatches(slot, "stone", registry::isCobblestone);
    }

    private boolean isLogIngredient(SlotDisplay slot) {
        return slotMatches(slot, "log", registry::isLog);
    }

    private boolean isPlankIngredient(SlotDisplay slot) {
        return slotMatches(slot, "plank", item -> registry.isLog(item) || registry.blockScore(item) > 0);
    }

    private boolean isStickIngredient(SlotDisplay slot) {
        return slotMatches(slot, "stick", registry::isStick);
    }

    private boolean isEmpty(SlotDisplay slot) {
        return slot == null || slot.getClass().getSimpleName().contains("Empty");
    }

    private boolean slotMatches(SlotDisplay slot,
                                String tagNeedle,
                                java.util.function.Predicate<org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack> itemPredicate) {
        if (slot instanceof ItemSlotDisplay item) {
            return itemPredicate.test(new org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack(item.item(), 1));
        }
        if (slot instanceof ItemStackSlotDisplay itemStack) {
            return itemPredicate.test(itemStack.itemStack());
        }
        if (slot instanceof TagSlotDisplay tag) {
            return tag.tag().asString().toLowerCase(Locale.ROOT).contains(tagNeedle);
        }
        if (slot instanceof CompositeSlotDisplay composite) {
            return composite.contents().stream().anyMatch(child -> slotMatches(child, tagNeedle, itemPredicate));
        }
        if (slot instanceof WithRemainderSlotDisplay remainder) {
            return slotMatches(remainder.input(), tagNeedle, itemPredicate);
        }
        return false;
    }

    private int resultItemId(SlotDisplay result) {
        if (result instanceof ItemStackSlotDisplay itemStack) {
            return itemStack.itemStack().getId();
        }
        if (result instanceof ItemSlotDisplay item) {
            return item.item();
        }
        return -1;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_').trim();
    }

    public record RecipeInfo(int id, int resultItemId) {
        Map<String, Object> snapshot() {
            return Map.of("id", id, "resultItemId", resultItemId);
        }
    }
}
