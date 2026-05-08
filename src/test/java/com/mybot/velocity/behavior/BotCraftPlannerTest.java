package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotInventoryState;
import com.mybot.velocity.bot.BotRecipeBook;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotCraftPlannerTest {
    private final BotCraftPlanner planner = new BotCraftPlanner();
    private final BotCraftPlanner.RecipeOverrides recipes = new BotCraftPlanner.RecipeOverrides(10, 11, 12, 13, 14);

    @Test
    void emptyInventoryStartsByGatheringLogsForPlanks() {
        BotCraftPlanner.Step step = planner.planStoneSword(new BotInventoryState(), new BotRecipeBook(), recipes);

        assertThat(step.type()).isEqualTo(BotCraftPlanner.StepType.GATHER);
        assertThat(step.resource()).isEqualTo(BotCraftPlanner.Resource.LOG);
        assertThat(step.blocker()).isEqualTo("need_logs_for_planks");
    }

    @Test
    void logAllowsPlanksCraftBeforeTableOrTools() {
        BotInventoryState inventory = new BotInventoryState();
        inventory.setSlot(0, 1, 10, new ItemStack(84, 1, null));

        BotCraftPlanner.Step step = planner.planStoneSword(inventory, new BotRecipeBook(), recipes);

        assertThat(step.type()).isEqualTo(BotCraftPlanner.StepType.CRAFT_2X2);
        assertThat(step.recipeName()).isEqualTo("planks");
    }

    @Test
    void planksCraftTableWhenThreeByThreeCraftIsBlocked() {
        BotInventoryState inventory = new BotInventoryState();
        inventory.setSlot(0, 1, 10, new ItemStack(14, 4, null));

        BotCraftPlanner.Step step = planner.planStoneSword(inventory, new BotRecipeBook(), recipes);

        assertThat(step.type()).isEqualTo(BotCraftPlanner.StepType.CRAFT_2X2);
        assertThat(step.recipeName()).isEqualTo("crafting_table");
        assertThat(step.blocker()).isEmpty();
    }

    @Test
    void craftingTableInventoryTriggersPlaceOrOpenForThreeByThree() {
        BotInventoryState inventory = new BotInventoryState();
        inventory.setSlot(0, 1, 10, new ItemStack(14, 3, null));
        inventory.setSlot(0, 1, 11, new ItemStack(550, 2, null));
        inventory.setSlot(0, 1, 12, new ItemStack(890, 1, null));

        BotCraftPlanner.Step step = planner.planStoneSword(inventory, new BotRecipeBook(), recipes);

        assertThat(step.type()).isEqualTo(BotCraftPlanner.StepType.PLACE_OR_OPEN);
        assertThat(step.recipeName()).isEqualTo("wooden_pickaxe");
        assertThat(step.blocker()).isEqualTo("need_table_for_3x3");
    }

    @Test
    void openTableWithMaterialsCraftsStoneSword() {
        BotInventoryState inventory = new BotInventoryState();
        inventory.setOpenContainerId(2);
        inventory.setSlot(2, 1, 10, new ItemStack(1, 2, null));
        inventory.setSlot(2, 1, 11, new ItemStack(550, 1, null));
        inventory.setSlot(2, 1, 12, new ItemStack(890, 1, null));
        inventory.setSlot(2, 1, 13, new ItemStack(501, 1, null));

        BotCraftPlanner.Step step = planner.planStoneSword(inventory, new BotRecipeBook(), recipes);

        assertThat(step.type()).isEqualTo(BotCraftPlanner.StepType.CRAFT_3X3);
        assertThat(step.recipeName()).isEqualTo("stone_sword");
    }
}
