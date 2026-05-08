package com.mybot.velocity.action;

import com.mybot.velocity.bot.BotSession;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;

public interface BotAction {

    String key();

    long cooldownMillis();

    void execute(BotSession session);

    record SetHotbarSlot(int slot) implements BotAction {
        public String key() { return "slot"; }
        public long cooldownMillis() { return 80; }
        public void execute(BotSession session) { session.setHotbarSlot(slot); }
    }

    record MoveInventorySlotToHotbar(int sourceSlot, int hotbarSlot) implements BotAction {
        public String key() { return "move-hotbar"; }
        public long cooldownMillis() { return 220; }
        public void execute(BotSession session) { session.moveInventorySlotToHotbar(sourceSlot, hotbarSlot); }
    }

    record StartSprint() implements BotAction {
        public String key() { return "sprint"; }
        public long cooldownMillis() { return 120; }
        public void execute(BotSession session) { session.setSprinting(true); }
    }

    record StopSprint() implements BotAction {
        public String key() { return "sprint"; }
        public long cooldownMillis() { return 120; }
        public void execute(BotSession session) { session.setSprinting(false); }
    }

    record StartSneak() implements BotAction {
        public String key() { return "sneak"; }
        public long cooldownMillis() { return 120; }
        public void execute(BotSession session) { session.setSneaking(true); }
    }

    record StopSneak() implements BotAction {
        public String key() { return "sneak"; }
        public long cooldownMillis() { return 120; }
        public void execute(BotSession session) { session.setSneaking(false); }
    }

    record SetFlying(boolean flying) implements BotAction {
        public String key() { return "flying"; }
        public long cooldownMillis() { return 250; }
        public void execute(BotSession session) { session.setFlying(flying); }
    }

    record SwingMainHand() implements BotAction {
        public String key() { return "swing"; }
        public long cooldownMillis() { return 450; }
        public void execute(BotSession session) { session.swingMainHand(); }
    }

    record LeftClickEntity(int entityId) implements BotAction {
        public String key() { return "attack"; }
        public long cooldownMillis() { return 625; }
        public void execute(BotSession session) { session.attackEntity(entityId); }
    }

    record RightClickItem() implements BotAction {
        public String key() { return "use-item"; }
        public long cooldownMillis() { return 300; }
        public void execute(BotSession session) { session.useItem(); }
    }

    record RightClickBlock(Vector3i position, Direction face) implements BotAction {
        public String key() { return "use-block"; }
        public long cooldownMillis() { return 300; }
        public void execute(BotSession session) { session.useItemOn(position, face); }
    }

    record StartDigBlock(Vector3i position, Direction face) implements BotAction {
        public String key() { return "dig"; }
        public long cooldownMillis() { return 180; }
        public void execute(BotSession session) { session.startDigging(position, face); }
    }

    record FinishDigBlock(Vector3i position, Direction face) implements BotAction {
        public String key() { return "dig"; }
        public long cooldownMillis() { return 180; }
        public void execute(BotSession session) { session.finishDigging(position, face); }
    }

    record CraftRecipe(int recipeId, String fallbackName) implements BotAction {
        public String key() { return "craft"; }
        public long cooldownMillis() { return 650; }
        public void execute(BotSession session) { session.craftRecipe(recipeId, fallbackName); }
    }

    record DropItem() implements BotAction {
        public String key() { return "drop"; }
        public long cooldownMillis() { return 250; }
        public void execute(BotSession session) { session.dropSelectedItem(); }
    }

    record Say(String message) implements BotAction {
        public String key() { return "say"; }
        public long cooldownMillis() { return 5000; }
        public void execute(BotSession session) { session.sendCommand("say " + message); }
    }
}
