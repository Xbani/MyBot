package com.mybot.velocity.bot;

import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplayEntry;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapedCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.EmptySlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemSlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class BotRecipeBookTest {
    @Test
    void resolvesRecipeIdFromRecipeBookResultItem() {
        BotRecipeBook recipes = new BotRecipeBook();
        recipes.setRegistry(ItemRegistryConfig.fromTraits(Map.of("stone-sword-items", List.of("742"))));
        RecipeDisplayEntry entry = new RecipeDisplayEntry(123,
                new ShapedCraftingRecipeDisplay(1, 1, List.of(EmptySlotDisplay.INSTANCE),
                        new ItemSlotDisplay(742), EmptySlotDisplay.INSTANCE),
                OptionalInt.empty(), 0, List.of());

        recipes.add(new ClientboundRecipeBookAddPacket(List.of(new ClientboundRecipeBookAddPacket.Entry(entry, false, false)), false));

        assertThat(recipes.recipeId("stone_sword")).hasValue(123);
        assertThat(recipes.resultItemId("stone_sword")).hasValue(742);
    }

    @Test
    void resolvesStoneSwordFromShapedIngredients() {
        BotRecipeBook recipes = new BotRecipeBook();
        recipes.setRegistry(ItemRegistryConfig.fromTraits(Map.of(
                "cobblestone-items", List.of("1"),
                "stick-items", List.of("2")
        )));
        RecipeDisplayEntry entry = new RecipeDisplayEntry(456,
                new ShapedCraftingRecipeDisplay(1, 3,
                        List.of(new ItemSlotDisplay(1), new ItemSlotDisplay(1), new ItemSlotDisplay(2)),
                        new ItemSlotDisplay(901), EmptySlotDisplay.INSTANCE),
                OptionalInt.empty(), 0, List.of());

        recipes.add(new ClientboundRecipeBookAddPacket(List.of(new ClientboundRecipeBookAddPacket.Entry(entry, false, false)), false));

        assertThat(recipes.recipeId("stone_sword")).hasValue(456);
        assertThat(recipes.resultItemId("stone_sword")).hasValue(901);
    }

    @Test
    void resolvesWoodenPickaxeFromShapedIngredients() {
        BotRecipeBook recipes = new BotRecipeBook();
        recipes.setRegistry(ItemRegistryConfig.fromTraits(Map.of(
                "block-items", List.of("10"),
                "stick-items", List.of("2")
        )));
        RecipeDisplayEntry entry = new RecipeDisplayEntry(789,
                new ShapedCraftingRecipeDisplay(3, 3,
                        List.of(new ItemSlotDisplay(10), new ItemSlotDisplay(10), new ItemSlotDisplay(10),
                                EmptySlotDisplay.INSTANCE, new ItemSlotDisplay(2), EmptySlotDisplay.INSTANCE,
                                EmptySlotDisplay.INSTANCE, new ItemSlotDisplay(2), EmptySlotDisplay.INSTANCE),
                        new ItemSlotDisplay(333), EmptySlotDisplay.INSTANCE),
                OptionalInt.empty(), 0, List.of());

        recipes.add(new ClientboundRecipeBookAddPacket(List.of(new ClientboundRecipeBookAddPacket.Entry(entry, false, false)), false));

        assertThat(recipes.recipeId("wooden_pickaxe")).hasValue(789);
        assertThat(recipes.resultItemId("wooden_pickaxe")).hasValue(333);
    }

    @Test
    void returnsEmptyWhenRecipeIsAbsent() {
        BotRecipeBook recipes = new BotRecipeBook();

        assertThat(recipes.recipeId("stone_sword")).isEmpty();
    }
}
