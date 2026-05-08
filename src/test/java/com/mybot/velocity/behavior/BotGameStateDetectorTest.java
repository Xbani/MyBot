package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.BotScoreboardState;
import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.bot.WorldBlockCache;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.time.Instant;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class BotGameStateDetectorTest {
    @Test
    void keepsPregameWaitingWhileKitSelectorIsPresentEvenAfterOldMatchSignal() {
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        DataComponents components = new DataComponents(new HashMap<>());
        components.put(DataComponentTypes.CUSTOM_NAME, Component.text("Kit Selector"));
        state.inventory().setSlot(0, 1, 36, new ItemStack(1, 1, components));
        state.markMatchStarted();

        EBotLifecycleState detected = new BotGameStateDetector()
                .detect("hg0", state, new Vec3(0, 64, 0), Instant.now());

        assertThat(detected).isEqualTo(EBotLifecycleState.PregameWaiting);
        assertThat(state.matchStartedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void usesScoreboardAliveCountForFinalFight() {
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        state.markMatchStarted();

        EBotLifecycleState detected = new BotGameStateDetector()
                .detect("hg0", state, new Vec3(0, 64, 0), Instant.now(),
                        new BotScoreboardState.Snapshot(java.util.List.of("Alive: 2"), "", -1, 2));

        assertThat(detected).isEqualTo(EBotLifecycleState.FinalFight);
    }

    @Test
    void usesScoreboardPhaseForLateGame() {
        BotWorldState state = new BotWorldState(new WorldBlockCache(NOPLogger.NOP_LOGGER));
        state.markMatchStarted();

        EBotLifecycleState detected = new BotGameStateDetector()
                .detect("hg0", state, new Vec3(0, 64, 0), Instant.now(),
                        new BotScoreboardState.Snapshot(java.util.List.of("Phase: Late"), "Late", -1, -1));

        assertThat(detected).isEqualTo(EBotLifecycleState.LateGame);
    }
}
