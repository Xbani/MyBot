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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class BotInvincibilityPlan {
    private static final Duration DIG_TIME = Duration.ofMillis(850);
    private static final Duration CRAFT_CONFIRM_TIMEOUT = Duration.ofMillis(2400);
    private static final Duration MINE_CONFIRM_TIMEOUT = Duration.ofMillis(1800);
    private static final double RESOURCE_REACH = 3.2;

    private final BotActionQueue actions;
    private final BotResourceScanner resources;
    private final BotCraftPlanner craftPlanner = new BotCraftPlanner();
    private final BotCraftPlanner.RecipeOverrides recipeOverrides;
    private final Random random;
    private final int planksRecipeId;
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
    private Vector3i pendingMineTarget;
    private boolean pendingMineWood;
    private Instant pendingMineUntil = Instant.EPOCH;
    private int pendingMineBaseline = -1;
    private String pendingCraft = "";
    private Instant pendingCraftUntil = Instant.EPOCH;
    private String waitingForTableCraft = "";
    private Instant pendingTableUntil = Instant.EPOCH;
    private String lastCraft = "";
    private String lastMine = "";
    private String craftFailure = "";
    private BotCraftPlanner.Step currentCraftStep = BotCraftPlanner.Step.done("stone_sword");
    private final Map<Vector3i, Instant> failedMineTargets = new HashMap<>();
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
        this.planksRecipeId = intTrait(traits, "recipe-planks-id", -1);
        this.craftingTableRecipeId = intTrait(traits, "recipe-crafting-table-id", -1);
        this.sticksRecipeId = intTrait(traits, "recipe-sticks-id", -1);
        this.woodenPickaxeRecipeId = intTrait(traits, "recipe-wooden-pickaxe-id", -1);
        this.stoneSwordRecipeId = intTrait(traits, "recipe-stone-sword-id", -1);
        this.recipeOverrides = new BotCraftPlanner.RecipeOverrides(planksRecipeId, sticksRecipeId,
                craftingTableRecipeId, woodenPickaxeRecipeId, stoneSwordRecipeId);
    }

    public Plan tick(BotBlackboard board, BotPhysics physics) {
        reconcileInventory(board);
        if (spawnAnchor == null || board.position().horizontalDistanceTo(spawnAnchor) > 96.0) {
            spawnAnchor = board.position();
            chooseRouteTarget(board.position(), 22.0, 36.0);
        }
        if (stage == Stage.LEAVE_SPAWN) {
            return leaveSpawn(board, physics);
        }
        return executeCraftPlanner(board, physics);
    }

    public void reset() {
        stage = Stage.LEAVE_SPAWN;
        spawnAnchor = null;
        routeTarget = Vec3.ZERO;
        blockTarget = null;
        nextActionAt = Instant.EPOCH;
        startedDiggingAt = Instant.EPOCH;
        pendingMineTarget = null;
        pendingMineUntil = Instant.EPOCH;
        pendingMineBaseline = -1;
        pendingCraft = "";
        pendingCraftUntil = Instant.EPOCH;
        waitingForTableCraft = "";
        pendingTableUntil = Instant.EPOCH;
        lastCraft = "";
        lastMine = "";
        craftFailure = "";
        currentCraftStep = BotCraftPlanner.Step.done("stone_sword");
        failedMineTargets.clear();
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

    public Stage stage() {
        return stage;
    }

    public String lastCraft() {
        return lastCraft;
    }

    public String lastMine() {
        return lastMine;
    }

    public String craftFailure() {
        return craftFailure;
    }

    public String goal() {
        return currentCraftStep.goal();
    }

    public String subGoal() {
        return currentCraftStep.subGoal();
    }

    public String nextStep() {
        return currentCraftStep.nextStep();
    }

    public String blocker() {
        return currentCraftStep.blocker();
    }

    public Map<String, Object> requiredItems() {
        return currentCraftStep.requiredItems();
    }

    public String lastResourceTarget() {
        return lastMine;
    }

    private Plan executeCraftPlanner(BotBlackboard board, BotPhysics physics) {
        confirmPendingMine(board);
        reconcileInventory(board);
        if (!pendingCraft.isBlank()) {
            return waitForCraftConfirmation(board);
        }
        if (!waitingForTableCraft.isBlank()) {
            return waitForCraftingTable(board);
        }
        currentCraftStep = craftPlanner.planStoneSword(board.state().inventory(), board.state().recipeBook(), recipeOverrides);
        return switch (currentCraftStep.type()) {
            case DONE -> {
                stage = Stage.SCOUT_WITH_SWORD;
                virtualStoneSword = true;
                yield scout(board, physics);
            }
            case GATHER -> {
                boolean wood = currentCraftStep.resource() == BotCraftPlanner.Resource.LOG;
                stage = wood ? Stage.FIND_WOOD : Stage.FIND_STONE;
                yield findAndMine(board, physics, wood);
            }
            case CRAFT_2X2 -> {
                stage = Stage.CRAFT_WOOD_KIT;
                yield requestCraft(board, recipeOverride(currentCraftStep.recipeName()), currentCraftStep.recipeName(),
                        "craft planner:" + currentCraftStep.subGoal());
            }
            case CRAFT_3X3 -> {
                stage = currentCraftStep.recipeName().equals("stone_sword") ? Stage.CRAFT_STONE_SWORD : Stage.CRAFT_WOOD_KIT;
                yield requestCraft(board, recipeOverride(currentCraftStep.recipeName()), currentCraftStep.recipeName(),
                        "craft planner:" + currentCraftStep.subGoal(), true);
            }
            case PLACE_OR_OPEN -> {
                stage = currentCraftStep.recipeName().equals("stone_sword") ? Stage.CRAFT_STONE_SWORD : Stage.CRAFT_WOOD_KIT;
                yield requestCraftingTable(board, currentCraftStep.recipeName());
            }
            case WAIT_FOR_RECIPE -> {
                craftFailure = currentCraftStep.blocker();
                nextActionAt = board.now().plusMillis(550);
                yield new Plan(MovementInput.NONE, craftFailure, stage);
            }
        };
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
        confirmPendingMine(board);
        Optional<Vector3i> found = wood
                ? resources.nearestWood(board.state().blocks(), board.position(), 14)
                : resources.nearestStone(board.state().blocks(), board.position(), 14);
        if (found.isPresent() && !isFailedMineTarget(found.get(), board.now())) {
            blockTarget = found.get();
        } else if (blockTarget != null && isFailedMineTarget(blockTarget, board.now())) {
            blockTarget = null;
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
                pendingMineTarget = blockTarget;
                pendingMineWood = wood;
                pendingMineBaseline = wood ? board.state().inventory().logCount() : board.state().inventory().cobblestoneCount();
                pendingMineUntil = board.now().plus(MINE_CONFIRM_TIMEOUT);
                lastMine = (wood ? "wood" : "stone") + " " + describe(blockTarget)
                        + " baseline=" + pendingMineBaseline;
                startedDiggingAt = Instant.EPOCH;
                blockTarget = null;
                nextActionAt = board.now().plusMillis(250 + random.nextInt(450));
            }
        }
        return new Plan(MovementInput.NONE, wood ? "mining wood for crafting chain" : "mining cobblestone for stone sword", stage);
    }

    private Plan craftWoodKit(BotBlackboard board) {
        reconcileInventory(board);
        if (!pendingCraft.isBlank()) {
            return waitForCraftConfirmation(board);
        }
        if (!waitingForTableCraft.isBlank()) {
            return waitForCraftingTable(board);
        }
        if (board.now().isBefore(nextActionAt)) {
            return new Plan(MovementInput.NONE, "hesitating before crafting starter tools", stage);
        }
        if (!virtualCraftingTable) {
            return requestCraft(board, craftingTableRecipeId, "crafting_table", "crafting a crafting table");
        }
        if (virtualSticks < 2) {
            return requestCraft(board, sticksRecipeId, "sticks", "crafting sticks");
        }
        if (!virtualWoodenPickaxe) {
            return requestCraft(board, woodenPickaxeRecipeId, "wooden_pickaxe", "crafting a wooden pickaxe", true);
        }
        stage = Stage.FIND_STONE;
        blockTarget = null;
        return new Plan(MovementInput.NONE, "starter tools ready, looking for stone", stage);
    }

    private Plan craftStoneSword(BotBlackboard board) {
        reconcileInventory(board);
        if (!pendingCraft.isBlank()) {
            return waitForCraftConfirmation(board);
        }
        if (!waitingForTableCraft.isBlank()) {
            return waitForCraftingTable(board);
        }
        if (board.now().isAfter(nextActionAt)) {
            if (virtualSticks < 1) {
                return requestCraft(board, sticksRecipeId, "sticks", "crafting extra sticks for sword");
            }
            return requestCraft(board, stoneSwordRecipeId, "stone_sword", "crafting stone sword", true);
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
        virtualLogs = board.state().inventory().logCount();
        virtualCobble = board.state().inventory().cobblestoneCount();
        virtualSticks = Math.max(virtualSticks, board.state().inventory().stickCount());
        if (board.state().inventory().hasCraftingTable()) {
            virtualCraftingTable = true;
            confirmCraft("crafting_table");
        }
        int plankCount = board.state().recipeBook().resultItemId("planks")
                .stream()
                .map(board.state().inventory()::countItemId)
                .findFirst()
                .orElse(board.state().inventory().plankCount());
        if (plankCount > 0) {
            confirmCraft("planks");
        }
        boolean hasWoodenPickaxe = board.state().recipeBook().resultItemId("wooden_pickaxe")
                .stream()
                .anyMatch(board.state().inventory()::hasItemId);
        if (!hasWoodenPickaxe && board.state().recipeBook().resultItemId("wooden_pickaxe").isEmpty()) {
            hasWoodenPickaxe = board.state().inventory().hasWoodenPickaxe();
        }
        if (hasWoodenPickaxe) {
            virtualWoodenPickaxe = true;
            confirmCraft("wooden_pickaxe");
        }
        if (virtualSticks >= 2) {
            confirmCraft("sticks");
        }
        boolean hasStoneSword = board.state().recipeBook().resultItemId("stone_sword")
                .stream()
                .anyMatch(board.state().inventory()::hasItemId);
        if (!hasStoneSword && board.state().recipeBook().resultItemId("stone_sword").isEmpty()) {
            hasStoneSword = board.state().inventory().hasLikelyStoneSword();
        }
        if (hasStoneSword) {
            virtualStoneSword = true;
            confirmCraft("stone_sword");
            if (stage != Stage.SCOUT_WITH_SWORD) {
                stage = Stage.SCOUT_WITH_SWORD;
                blockTarget = null;
                nextActionAt = board.now().plusMillis(250);
            }
        } else if (virtualStoneSword && board.state().recipeBook().resultItemId("stone_sword").isPresent()) {
            virtualStoneSword = false;
            if (stage == Stage.SCOUT_WITH_SWORD) {
                stage = virtualCobble >= 3 ? Stage.CRAFT_STONE_SWORD : Stage.FIND_STONE;
                blockTarget = null;
                craftFailure = "stone sword not in inventory after recipe resolved";
            }
        }
    }

    private Plan requestCraft(BotBlackboard board, int configuredRecipeId, String recipeName, String reason) {
        return requestCraft(board, configuredRecipeId, recipeName, reason, false);
    }

    private Plan requestCraft(BotBlackboard board, int configuredRecipeId, String recipeName, String reason, boolean needsCraftingTable) {
        if (configuredRecipeId < 0 && board.state().recipeBook().recipeId(recipeName).isEmpty()) {
            craftFailure = "missing recipe id for " + recipeName;
            nextActionAt = board.now().plusMillis(600);
            return new Plan(MovementInput.NONE, craftFailure, stage);
        }
        if (needsCraftingTable && board.state().inventory().openContainerId() <= 0) {
            return requestCraftingTable(board, recipeName);
        }
        actions.enqueue(new BotAction.CraftRecipe(configuredRecipeId, recipeName));
        pendingCraft = recipeName;
        pendingCraftUntil = board.now().plus(CRAFT_CONFIRM_TIMEOUT);
        lastCraft = recipeName;
        craftFailure = "";
        nextActionAt = board.now().plusMillis(650 + random.nextInt(500));
        return new Plan(MovementInput.NONE, reason, stage);
    }

    private Plan requestCraftingTable(BotBlackboard board, String recipeName) {
        Optional<Vector3i> nearbyTable = resources.nearestCraftingTable(board.state().blocks(), board.position(), 4);
        if (nearbyTable.isPresent()) {
            Vector3i table = nearbyTable.get();
            actions.enqueue(new BotAction.RightClickBlock(table, Direction.UP));
            waitingForTableCraft = recipeName;
            pendingTableUntil = board.now().plus(CRAFT_CONFIRM_TIMEOUT);
            craftFailure = "";
            nextActionAt = board.now().plusMillis(450);
            return new Plan(MovementInput.NONE, "opening crafting table for " + recipeName, stage);
        }
        Optional<Vector3i> support = placementSupport(board);
        java.util.OptionalInt hotbarSlot = board.state().inventory().craftingTableHotbarSlot();
        if (support.isPresent() && hotbarSlot.isPresent()) {
            int slot = hotbarSlot.getAsInt();
            actions.enqueue(new BotAction.SetHotbarSlot(slot));
            actions.enqueue(new BotAction.RightClickBlock(support.get(), Direction.UP));
            waitingForTableCraft = recipeName;
            pendingTableUntil = board.now().plus(CRAFT_CONFIRM_TIMEOUT);
            craftFailure = "";
            nextActionAt = board.now().plusMillis(650);
            return new Plan(MovementInput.NONE, "placing crafting table for " + recipeName, stage);
        }
        java.util.OptionalInt tableSlot = board.state().inventory().craftingTableSlot();
        if (tableSlot.isPresent()) {
            actions.enqueue(new BotAction.MoveInventorySlotToHotbar(tableSlot.getAsInt(), 0));
            craftFailure = "";
            nextActionAt = board.now().plusMillis(450);
            return new Plan(MovementInput.NONE, "moving crafting table to hotbar for " + recipeName, stage);
        }
        craftFailure = board.state().inventory().hasCraftingTable()
                ? "crafting table not on hotbar for " + recipeName
                : "crafting table unavailable for " + recipeName;
        nextActionAt = board.now().plusMillis(700);
        return new Plan(MovementInput.NONE, craftFailure, stage);
    }

    private Plan waitForCraftingTable(BotBlackboard board) {
        if (board.state().inventory().openContainerId() > 0) {
            String recipeName = waitingForTableCraft;
            waitingForTableCraft = "";
            pendingTableUntil = Instant.EPOCH;
            return requestCraft(board, recipeName.equals("wooden_pickaxe") ? woodenPickaxeRecipeId : stoneSwordRecipeId,
                    recipeName, "crafting " + recipeName, false);
        }
        if (board.now().isAfter(pendingTableUntil)) {
            craftFailure = "crafting table container not open for " + waitingForTableCraft;
            waitingForTableCraft = "";
            pendingTableUntil = Instant.EPOCH;
            nextActionAt = board.now().plusMillis(800);
            return new Plan(MovementInput.NONE, craftFailure, stage);
        }
        return new Plan(MovementInput.NONE, "waiting for crafting table container: " + waitingForTableCraft, stage);
    }

    private Plan waitForCraftConfirmation(BotBlackboard board) {
        reconcileInventory(board);
        if (pendingCraft.isBlank()) {
            return new Plan(MovementInput.NONE, "craft confirmed", stage);
        }
        if (board.now().isAfter(pendingCraftUntil)) {
            craftFailure = "craft not confirmed: " + pendingCraft;
            pendingCraft = "";
            pendingCraftUntil = Instant.EPOCH;
            nextActionAt = board.now().plusMillis(650 + random.nextInt(700));
            return new Plan(MovementInput.NONE, craftFailure, stage);
        }
        return new Plan(MovementInput.NONE, "waiting for craft confirmation: " + pendingCraft, stage);
    }

    private void confirmCraft(String recipeName) {
        if (pendingCraft.equals(recipeName)) {
            pendingCraft = "";
            pendingCraftUntil = Instant.EPOCH;
            craftFailure = "";
        }
    }

    private void confirmPendingMine(BotBlackboard board) {
        if (pendingMineTarget == null) {
            return;
        }
        int currentCount = pendingMineWood ? board.state().inventory().logCount() : board.state().inventory().cobblestoneCount();
        if (pendingMineBaseline >= 0 && currentCount > pendingMineBaseline) {
            if (pendingMineWood) {
                virtualLogs = Math.max(virtualLogs, currentCount);
            } else {
                virtualCobble = Math.max(virtualCobble, currentCount);
            }
            lastMine = (pendingMineWood ? "wood" : "stone") + " " + describe(pendingMineTarget)
                    + " inventory " + pendingMineBaseline + "->" + currentCount;
            pendingMineTarget = null;
            pendingMineUntil = Instant.EPOCH;
            pendingMineBaseline = -1;
            craftFailure = "";
            return;
        }
        boolean stillResource = pendingMineWood
                ? resources.isWood(board.state().blocks(), pendingMineTarget)
                : resources.isStone(board.state().blocks(), pendingMineTarget);
        if (!stillResource) {
            if (pendingMineWood) {
                virtualLogs++;
            } else {
                virtualCobble++;
            }
            lastMine = (pendingMineWood ? "wood" : "stone") + " " + describe(pendingMineTarget)
                    + " block update";
            pendingMineTarget = null;
            pendingMineUntil = Instant.EPOCH;
            pendingMineBaseline = -1;
            craftFailure = "";
            return;
        }
        if (board.now().isAfter(pendingMineUntil)) {
            craftFailure = pendingMineWood ? "wood mine not confirmed" : "stone mine not confirmed";
            lastMine = (pendingMineWood ? "wood" : "stone") + " " + describe(pendingMineTarget)
                    + " failed baseline=" + pendingMineBaseline + " current=" + currentCount;
            failedMineTargets.put(pendingMineTarget, board.now().plusSeconds(8));
            pendingMineTarget = null;
            pendingMineUntil = Instant.EPOCH;
            pendingMineBaseline = -1;
        }
    }

    private Optional<Vector3i> placementSupport(BotBlackboard board) {
        int x = (int) Math.floor(board.position().x());
        int y = (int) Math.floor(board.position().y());
        int z = (int) Math.floor(board.position().z());
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] offset : offsets) {
            int px = x + offset[0];
            int pz = z + offset[1];
            if (board.state().blocks().isSolid(px, y - 1, pz) && board.state().blocks().isAirBlock(px, y, pz)) {
                return Optional.of(Vector3i.from(px, y - 1, pz));
            }
        }
        return Optional.empty();
    }

    private boolean isFailedMineTarget(Vector3i target, Instant now) {
        Instant until = failedMineTargets.get(target);
        if (until == null) {
            return false;
        }
        if (now.isAfter(until)) {
            failedMineTargets.remove(target);
            return false;
        }
        return true;
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

    private String describe(Vector3i position) {
        return position == null ? "" : position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private int recipeOverride(String recipeName) {
        return switch (recipeName) {
            case "planks" -> planksRecipeId;
            case "sticks" -> sticksRecipeId;
            case "crafting_table" -> craftingTableRecipeId;
            case "wooden_pickaxe" -> woodenPickaxeRecipeId;
            case "stone_sword" -> stoneSwordRecipeId;
            default -> -1;
        };
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
