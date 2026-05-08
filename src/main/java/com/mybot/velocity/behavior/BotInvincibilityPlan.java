package com.mybot.velocity.behavior;

import com.mybot.velocity.action.BotAction;
import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.MovementInput;
import com.mybot.velocity.bot.Vec3;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class BotInvincibilityPlan {
    private static final Duration DIG_TIME = Duration.ofMillis(850);
    private static final double RESOURCE_REACH = 3.2;

    private final BotActionQueue actions;
    private final BotResourceScanner resources;
    private final Random random;
    private final int sticksRecipeId;
    private final int craftingTableRecipeId;
    private final int woodenPickaxeRecipeId;
    private final int stoneSwordRecipeId;

    private Stage stage = Stage.LEAVE_SPAWN;
    private Vec3 spawnAnchor;
    private Vec3 routeTarget = Vec3.ZERO;
    private Vector3i blockTarget;
    private Instant nextActionAt = Instant.EPOCH;
    private Instant startedDiggingAt = Instant.EPOCH;
    private int virtualLogs;
    private int virtualSticks;
    private int virtualCobble;
    private boolean virtualCraftingTable;
    private boolean virtualWoodenPickaxe;
    private boolean virtualStoneSword;

    public BotInvincibilityPlan(BotActionQueue actions, Map<String, Object> traits, long seed) {
        this.actions = actions;
        this.resources = new BotResourceScanner(traits);
        this.random = new Random(seed ^ 0x51a7e5eedL);
        this.craftingTableRecipeId = intTrait(traits, "recipe-crafting-table-id", -1);
        this.sticksRecipeId = intTrait(traits, "recipe-sticks-id", -1);
        this.woodenPickaxeRecipeId = intTrait(traits, "recipe-wooden-pickaxe-id", -1);
        this.stoneSwordRecipeId = intTrait(traits, "recipe-stone-sword-id", -1);
    }

    public Plan tick(BotBlackboard board, BotPhysics physics) {
        reconcileInventory(board);
        if (spawnAnchor == null || board.position().horizontalDistanceTo(spawnAnchor) > 96.0) {
            spawnAnchor = board.position();
            chooseRouteTarget(board.position(), 22.0, 36.0);
        }
        advanceStage(board);
        return switch (stage) {
            case LEAVE_SPAWN -> leaveSpawn(board, physics);
            case FIND_WOOD -> findAndMine(board, physics, true);
            case CRAFT_WOOD_KIT -> craftWoodKit(board);
            case FIND_STONE -> findAndMine(board, physics, false);
            case CRAFT_STONE_SWORD -> craftStoneSword(board);
            case SCOUT_WITH_SWORD -> scout(board, physics);
        };
    }

    public void reset() {
        stage = Stage.LEAVE_SPAWN;
        spawnAnchor = null;
        routeTarget = Vec3.ZERO;
        blockTarget = null;
        nextActionAt = Instant.EPOCH;
        startedDiggingAt = Instant.EPOCH;
        virtualLogs = 0;
        virtualSticks = 0;
        virtualCobble = 0;
        virtualCraftingTable = false;
        virtualWoodenPickaxe = false;
        virtualStoneSword = false;
    }

    public boolean stoneSwordReady() {
        return virtualStoneSword;
    }

    private Plan leaveSpawn(BotBlackboard board, BotPhysics physics) {
        if (board.position().horizontalDistanceTo(routeTarget) < 3.5 || board.position().horizontalDistanceTo(spawnAnchor) > 24.0) {
            stage = Stage.FIND_WOOD;
            blockTarget = null;
        }
        lookAt(physics, routeTarget, 0.25);
        return new Plan(new MovementInput(0.85, strafeNoise(), false, true, false), "leaving spawn for starter resources", stage);
    }

    private Plan findAndMine(BotBlackboard board, BotPhysics physics, boolean wood) {
        Optional<Vector3i> found = wood
                ? resources.nearestWood(board.state().blocks(), board.position(), 14)
                : resources.nearestStone(board.state().blocks(), board.position(), 14);
        if (found.isPresent()) {
            blockTarget = found.get();
        }
        if (blockTarget == null) {
            if (board.position().horizontalDistanceTo(routeTarget) < 4.0 || board.now().isAfter(nextActionAt)) {
                chooseRouteTarget(board.position(), wood ? 10.0 : 6.0, wood ? 24.0 : 18.0);
                nextActionAt = board.now().plusSeconds(4 + random.nextInt(5));
            }
            lookAt(physics, routeTarget, 0.22);
            return new Plan(new MovementInput(0.72, strafeNoise(), false, true, false),
                    wood ? "searching for wood" : "searching for exposed stone", stage);
        }
        Vec3 target = center(blockTarget);
        lookAt(physics, target, 0.38);
        if (board.position().distanceTo(target) > RESOURCE_REACH) {
            return new Plan(new MovementInput(0.68, strafeNoise(), target.y() > board.position().y() + 0.6, false, false),
                    wood ? "moving to wood" : "moving to stone", stage);
        }
        if (board.now().isAfter(nextActionAt)) {
            if (startedDiggingAt.equals(Instant.EPOCH)) {
                actions.enqueue(new BotAction.StartDigBlock(blockTarget, Direction.UP));
                actions.enqueue(new BotAction.SwingMainHand());
                startedDiggingAt = board.now();
                nextActionAt = board.now().plus(DIG_TIME);
            } else {
                actions.enqueue(new BotAction.FinishDigBlock(blockTarget, Direction.UP));
                if (wood) {
                    virtualLogs++;
                } else {
                    virtualCobble++;
                }
                startedDiggingAt = Instant.EPOCH;
                blockTarget = null;
                nextActionAt = board.now().plusMillis(250 + random.nextInt(450));
            }
        }
        return new Plan(MovementInput.NONE, wood ? "mining wood for crafting chain" : "mining cobblestone for stone sword", stage);
    }

    private Plan craftWoodKit(BotBlackboard board) {
        if (board.now().isBefore(nextActionAt)) {
            return new Plan(MovementInput.NONE, "hesitating before crafting starter tools", stage);
        }
        if (!virtualCraftingTable) {
            actions.enqueue(new BotAction.CraftRecipe(craftingTableRecipeId, "crafting_table"));
            virtualCraftingTable = true;
            nextActionAt = board.now().plusMillis(650 + random.nextInt(600));
            return new Plan(MovementInput.NONE, "crafting a crafting table", stage);
        }
        if (virtualSticks < 2) {
            actions.enqueue(new BotAction.CraftRecipe(sticksRecipeId, "sticks"));
            virtualSticks = 4;
            nextActionAt = board.now().plusMillis(550 + random.nextInt(600));
            return new Plan(MovementInput.NONE, "crafting sticks", stage);
        }
        if (!virtualWoodenPickaxe) {
            actions.enqueue(new BotAction.CraftRecipe(woodenPickaxeRecipeId, "wooden_pickaxe"));
            virtualWoodenPickaxe = true;
            nextActionAt = board.now().plusMillis(900 + random.nextInt(700));
            return new Plan(MovementInput.NONE, "crafting a wooden pickaxe", stage);
        }
        stage = Stage.FIND_STONE;
        blockTarget = null;
        return new Plan(MovementInput.NONE, "starter tools ready, looking for stone", stage);
    }

    private Plan craftStoneSword(BotBlackboard board) {
        if (board.now().isAfter(nextActionAt)) {
            if (virtualSticks < 1) {
                actions.enqueue(new BotAction.CraftRecipe(sticksRecipeId, "sticks"));
                virtualSticks = 4;
                nextActionAt = board.now().plusMillis(600);
                return new Plan(MovementInput.NONE, "crafting extra sticks for sword", stage);
            }
            actions.enqueue(new BotAction.CraftRecipe(stoneSwordRecipeId, "stone_sword"));
            virtualStoneSword = true;
            nextActionAt = board.now().plusMillis(900);
            stage = Stage.SCOUT_WITH_SWORD;
        }
        return new Plan(MovementInput.NONE, "crafting stone sword", stage);
    }

    private Plan scout(BotBlackboard board, BotPhysics physics) {
        if (board.position().horizontalDistanceTo(routeTarget) < 4.0 || board.now().isAfter(nextActionAt)) {
            chooseRouteTarget(board.position(), 18.0, 32.0);
            nextActionAt = board.now().plusSeconds(4 + random.nextInt(4));
        }
        lookAt(physics, routeTarget, 0.22);
        return new Plan(new MovementInput(0.65, strafeNoise(), false, false, false), "stone sword plan complete, scouting", stage);
    }

    private void advanceStage(BotBlackboard board) {
        reconcileInventory(board);
        if (stage == Stage.LEAVE_SPAWN) {
            return;
        }
        if (stage == Stage.FIND_WOOD && virtualLogs >= 2) {
            stage = Stage.CRAFT_WOOD_KIT;
            blockTarget = null;
            nextActionAt = board.now().plusMillis(300 + random.nextInt(700));
        } else if (stage == Stage.FIND_STONE && virtualCobble >= 3) {
            stage = Stage.CRAFT_STONE_SWORD;
            blockTarget = null;
            nextActionAt = board.now().plusMillis(300 + random.nextInt(700));
        }
    }

    private void reconcileInventory(BotBlackboard board) {
        if (board.state().inventory().hasUsefulTools()) {
            virtualWoodenPickaxe = true;
        }
        if (board.state().inventory().hasLikelyStoneSword()) {
            virtualStoneSword = true;
            if (stage != Stage.SCOUT_WITH_SWORD) {
                stage = Stage.SCOUT_WITH_SWORD;
                blockTarget = null;
                nextActionAt = board.now().plusMillis(250);
            }
        }
    }

    private void chooseRouteTarget(Vec3 origin, double minDistance, double maxDistance) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
        routeTarget = origin.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    private void lookAt(BotPhysics physics, Vec3 target, double speed) {
        BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), target.add(0, 0.8, 0));
        physics.setLook(smooth(physics.yaw(), look.yaw(), speed), smooth(physics.pitch(), look.pitch(), speed * 0.7));
    }

    private float smooth(float current, float target, double factor) {
        return (float) (current + wrapDegrees(target - current) * Math.max(0.04, Math.min(1.0, factor)));
    }

    private float wrapDegrees(float degrees) {
        float value = degrees % 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private double strafeNoise() {
        return (random.nextDouble() - 0.5) * 0.22;
    }

    private Vec3 center(Vector3i position) {
        return new Vec3(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
    }

    private static int intTrait(Map<String, Object> traits, String key, int fallback) {
        Object value = traits.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public enum Stage {
        LEAVE_SPAWN,
        FIND_WOOD,
        CRAFT_WOOD_KIT,
        FIND_STONE,
        CRAFT_STONE_SWORD,
        SCOUT_WITH_SWORD
    }

    public record Plan(MovementInput movement, String reason, Stage stage) { }
}
