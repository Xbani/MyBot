package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BotDecisionSystemTest {
    @Test
    void choosesKnownLootTargetWhenPoorlyEquipped() {
        WorldBlockCache blocks = new WorldBlockCache(NOPLogger.NOP_LOGGER);
        blocks.setBlockForTesting(3, 64, 0, 920);
        BotWorldState state = new BotWorldState(blocks);
        BotMemory memory = new BotMemory();
        HgBehaviorConfig config = HgBehaviorConfig.defaults();
        BotPersonality personality = BotPersonality.fromTraits(config.traits());
        BotSkillProfile skill = BotSkillProfile.fromTraits(config.traits(), personality.skillLevel());
        WorldFacts facts = WorldFacts.from(state, new Vec3(0, 64, 0), memory, personality, config, Instant.now());
        BotBlackboard board = BotBlackboard.from(Instant.now(), "hg0", EBotLifecycleState.ActiveHG,
                BotGamePlan.Phase.EarlyGame, new Vec3(0, 64, 0), state, memory, personality, config, facts);

        BotDecisionSystem.Decision decision = new BotDecisionSystem(7).decide(board, personality, skill, memory, config);

        assertThat(decision.intent()).isEqualTo(BotIntent.LOOT);
        assertThat(decision.strategy()).isEqualTo(BotStrategy.GearUp);
        assertThat(decision.reason()).contains("known loot");
    }

    @Test
    void contestFeastWhenGearedAndFeastIsSoon() {
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        state.scoreboard().setSnapshotForTesting(new com.mybot.velocity.bot.BotScoreboardState.Snapshot(java.util.List.of("Feast: 30"), "", 30, 8));
        state.inventory().setSlot(0, 1, 36, new org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack(850, 1, null));
        BotMemory memory = new BotMemory();
        HgBehaviorConfig config = HgBehaviorConfig.defaults();
        BotPersonality personality = BotPersonality.fromTraits(config.traits());
        BotSkillProfile skill = BotSkillProfile.fromTraits(config.traits(), personality.skillLevel());
        WorldFacts facts = WorldFacts.from(state, new Vec3(0, 64, 0), memory, personality, config, Instant.now());
        BotBlackboard board = BotBlackboard.from(Instant.now(), "hg0", EBotLifecycleState.ActiveHG,
                BotGamePlan.Phase.FeastPhase, new Vec3(0, 64, 0), state, memory, personality, config, facts);

        BotDecisionSystem.Decision decision = new BotDecisionSystem(9).decide(board, personality, skill, memory, config);

        assertThat(decision.intent()).isEqualTo(BotIntent.LOOT);
        assertThat(decision.strategy()).isEqualTo(BotStrategy.ContestFeast);
    }

    @Test
    void recentAttackerIncreasesThreatMemory() {
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        TrackedPlayer attacker = new TrackedPlayer(12, UUID.randomUUID(), "RealAttacker", new Vec3(2, 64, 0), 0, 0, Instant.now());
        state.putPlayer(attacker);
        state.markDamage(12);
        BotMemory memory = new BotMemory();

        memory.update(state, new Vec3(0, 64, 0), Instant.now());

        assertThat(memory.reputation().get(attacker.uuid())).isPresent();
        assertThat(memory.reputation().get(attacker.uuid()).get().attackedMe()).isTrue();
    }
}
