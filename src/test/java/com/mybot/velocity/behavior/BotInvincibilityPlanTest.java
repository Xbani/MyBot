package com.mybot.velocity.behavior;

import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplayEntry;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapedCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.EmptySlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemSlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class BotInvincibilityPlanTest {
    @Test
    void missingStoneSwordDependenciesReplanToLogsInsteadOfFailingRecipe() throws Exception {
        BotInvincibilityPlan plan = new BotInvincibilityPlan(new BotActionQueue(), Map.of(), 3);
        set(plan, "stage", BotInvincibilityPlan.Stage.CRAFT_STONE_SWORD);
        set(plan, "virtualCobble", 3);
        set(plan, "virtualSticks", 1);
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        BotBlackboard board = board(state);

        BotInvincibilityPlan.Plan result = plan.tick(board, new BotPhysics());

        assertThat(result.stage()).isEqualTo(BotInvincibilityPlan.Stage.FIND_WOOD);
        assertThat(plan.stoneSwordReady()).isFalse();
        assertThat(plan.blocker()).isEqualTo("need_logs_for_planks");
    }

    @Test
    void invalidatesFallbackStoneSwordWhenRecipeResultIsKnown() throws Exception {
        BotInvincibilityPlan plan = new BotInvincibilityPlan(new BotActionQueue(), Map.of(), 3);
        set(plan, "stage", BotInvincibilityPlan.Stage.SCOUT_WITH_SWORD);
        set(plan, "virtualStoneSword", true);
        set(plan, "virtualCobble", 3);
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        addStoneSwordRecipe(state, 12, 798);
        state.inventory().setSlot(0, 1, 36, new ItemStack(799, 1, null));
        state.inventory().setSlot(0, 1, 10, new ItemStack(1, 3, null));
        state.inventory().setSlot(0, 1, 11, new ItemStack(550, 1, null));
        state.inventory().setSlot(0, 1, 12, new ItemStack(890, 1, null));
        state.inventory().setSlot(0, 1, 13, new ItemStack(501, 1, null));
        state.inventory().setOpenContainerId(2);

        BotInvincibilityPlan.Plan result = plan.tick(board(state), new BotPhysics());

        assertThat(result.stage()).isEqualTo(BotInvincibilityPlan.Stage.CRAFT_STONE_SWORD);
        assertThat(plan.stoneSwordReady()).isFalse();
    }

    @Test
    void confirmsPendingMineWhenInventoryCountIncreases() throws Exception {
        BotInvincibilityPlan plan = new BotInvincibilityPlan(new BotActionQueue(), Map.of(), 3);
        set(plan, "stage", BotInvincibilityPlan.Stage.FIND_STONE);
        set(plan, "pendingMineTarget", Vector3i.from(1, 64, 1));
        set(plan, "pendingMineWood", false);
        set(plan, "pendingMineBaseline", 1);
        set(plan, "pendingMineUntil", Instant.now().plusSeconds(1));
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        state.inventory().setSlot(0, 1, 10, new ItemStack(1, 2, null));

        plan.tick(board(state), new BotPhysics());

        assertThat(plan.lastMine()).contains("inventory 1->2");
        assertThat(plan.craftFailure()).isBlank();
    }

    @Test
    void unavailableCraftingTableBecomesResourceSubgoal() throws Exception {
        BotInvincibilityPlan plan = new BotInvincibilityPlan(new BotActionQueue(), Map.of(
                "recipe-wooden-pickaxe-id", 42
        ), 3);
        set(plan, "stage", BotInvincibilityPlan.Stage.CRAFT_WOOD_KIT);
        set(plan, "virtualCraftingTable", true);
        set(plan, "virtualSticks", 2);
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));

        BotInvincibilityPlan.Plan result = plan.tick(board(state), new BotPhysics());

        assertThat(result.stage()).isEqualTo(BotInvincibilityPlan.Stage.FIND_WOOD);
        assertThat(plan.blocker()).isEqualTo("need_logs_for_planks");
    }

    private void addStoneSwordRecipe(BotWorldState state, int recipeId, int resultId) {
        RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId,
                new ShapedCraftingRecipeDisplay(1, 3,
                        List.of(new ItemSlotDisplay(1), new ItemSlotDisplay(1), new ItemSlotDisplay(550)),
                        new ItemSlotDisplay(resultId), EmptySlotDisplay.INSTANCE),
                OptionalInt.empty(), 0, List.of());
        state.recipeBook().add(new ClientboundRecipeBookAddPacket(List.of(new ClientboundRecipeBookAddPacket.Entry(entry, false, false)), false));
    }

    private BotBlackboard board(BotWorldState state) {
        HgBehaviorConfig config = HgBehaviorConfig.defaults();
        BotMemory memory = new BotMemory();
        BotPersonality personality = BotPersonality.fromTraits(config.traits());
        Vec3 position = new Vec3(0, 64, 0);
        WorldFacts facts = WorldFacts.from(state, position, memory, personality, config, Instant.now());
        return BotBlackboard.from(Instant.now(), "hg0", EBotLifecycleState.InvincibilityStart,
                BotGamePlan.Phase.Invincibility, position, state, memory, personality, config, facts);
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
