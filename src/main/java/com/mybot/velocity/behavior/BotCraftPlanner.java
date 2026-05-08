package com.mybot.velocity.behavior;

import com.mybot.velocity.bot.BotInventoryState;
import com.mybot.velocity.bot.BotRecipeBook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

public final class BotCraftPlanner {
    public Step planStoneSword(BotInventoryState inventory, BotRecipeBook recipes, RecipeOverrides overrides) {
        InventoryCounts counts = InventoryCounts.from(inventory, recipes);
        if (counts.stoneSwords() > 0) {
            return Step.done("stone_sword");
        }
        if (counts.cobblestone() < 2) {
            if (counts.woodenPickaxes() <= 0) {
                return planWoodenPickaxe(counts, inventory, recipes, overrides);
            }
            return Step.gather(Resource.COBBLESTONE, 2 - counts.cobblestone(), "need_cobblestone_for_stone_sword",
                    "stone_sword", required("cobblestone", 2, counts.cobblestone()));
        }
        if (counts.sticks() < 1) {
            return planSticks(counts, recipes, overrides, 1, "stone_sword");
        }
        return craft3x3("stone_sword", inventory, counts, recipes, overrides,
                required("cobblestone", 2, counts.cobblestone(), "sticks", 1, counts.sticks()));
    }

    private Step planWoodenPickaxe(InventoryCounts counts, BotInventoryState inventory, BotRecipeBook recipes, RecipeOverrides overrides) {
        if (counts.craftingTables() <= 0) {
            return planCraftingTable(counts, recipes, overrides, "wooden_pickaxe");
        }
        if (counts.planks() < 3) {
            return planPlanks(counts, recipes, overrides, 3, "wooden_pickaxe");
        }
        if (counts.sticks() < 2) {
            return planSticks(counts, recipes, overrides, 2, "wooden_pickaxe");
        }
        return craft3x3("wooden_pickaxe", inventory, counts, recipes, overrides,
                required("planks", 3, counts.planks(), "sticks", 2, counts.sticks()));
    }

    private Step planSticks(InventoryCounts counts, BotRecipeBook recipes, RecipeOverrides overrides, int targetCount, String parentGoal) {
        if (counts.planks() < 2) {
            return planPlanks(counts, recipes, overrides, 2, "sticks_for_" + parentGoal);
        }
        return craft2x2("sticks", recipes, overrides,
                "need_sticks_for_" + parentGoal, parentGoal,
                required("planks", 2, counts.planks(), "sticks", targetCount, counts.sticks()));
    }

    private Step planCraftingTable(InventoryCounts counts, BotRecipeBook recipes, RecipeOverrides overrides, String parentGoal) {
        if (counts.planks() < 4) {
            return planPlanks(counts, recipes, overrides, 4, "crafting_table_for_" + parentGoal);
        }
        return craft2x2("crafting_table", recipes, overrides,
                "need_table_for_3x3", parentGoal,
                required("planks", 4, counts.planks()));
    }

    private Step planPlanks(InventoryCounts counts, BotRecipeBook recipes, RecipeOverrides overrides, int targetCount, String parentGoal) {
        if (counts.logs() < 1) {
            return Step.gather(Resource.LOG, 1, "need_logs_for_planks", parentGoal,
                    required("logs", 1, counts.logs(), "planks", targetCount, counts.planks()));
        }
        return craft2x2("planks", recipes, overrides,
                "need_planks_for_" + parentGoal, parentGoal,
                required("logs", 1, counts.logs(), "planks", targetCount, counts.planks()));
    }

    private Step craft3x3(String recipeName,
                          BotInventoryState inventory,
                          InventoryCounts counts,
                          BotRecipeBook recipes,
                          RecipeOverrides overrides,
                          Map<String, Object> requiredItems) {
        if (inventory.openContainerId() <= 0) {
            if (counts.craftingTables() <= 0) {
                return planCraftingTable(counts, recipes, overrides, recipeName);
            }
            return new Step("stone_sword", "open_crafting_table_for_" + recipeName, StepType.PLACE_OR_OPEN,
                    Resource.NONE, 0, recipeName, "need_table_for_3x3", requiredItems);
        }
        return craft2x2(recipeName, recipes, overrides, "craft_" + recipeName, "stone_sword", requiredItems).as3x3();
    }

    private Step craft2x2(String recipeName,
                          BotRecipeBook recipes,
                          RecipeOverrides overrides,
                          String subGoal,
                          String parentGoal,
                          Map<String, Object> requiredItems) {
        if (recipeId(recipeName, recipes, overrides) < 0) {
            return new Step(parentGoal, subGoal, StepType.WAIT_FOR_RECIPE, Resource.NONE, 0, recipeName,
                    "waiting_recipe_book:" + recipeName, requiredItems);
        }
        return new Step(parentGoal, subGoal, StepType.CRAFT_2X2, Resource.NONE, 0, recipeName, "", requiredItems);
    }

    public int recipeId(String recipeName, BotRecipeBook recipes, RecipeOverrides overrides) {
        return recipes.recipeId(recipeName).orElse(overrides.recipeId(recipeName));
    }

    private static Map<String, Object> required(Object... triples) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 2 < triples.length; i += 3) {
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("need", triples[i + 1]);
            counts.put("have", triples[i + 2]);
            map.put(triples[i].toString(), counts);
        }
        return map;
    }

    public enum Resource {
        NONE,
        LOG,
        COBBLESTONE
    }

    public enum StepType {
        DONE,
        GATHER,
        CRAFT_2X2,
        CRAFT_3X3,
        PLACE_OR_OPEN,
        WAIT_FOR_RECIPE
    }

    public record Step(String goal,
                       String subGoal,
                       StepType type,
                       Resource resource,
                       int count,
                       String recipeName,
                       String blocker,
                       Map<String, Object> requiredItems) {
        static Step done(String goal) {
            return new Step(goal, "complete", StepType.DONE, Resource.NONE, 0, "", "", Map.of());
        }

        static Step gather(Resource resource, int count, String blocker, String goal, Map<String, Object> requiredItems) {
            return new Step(goal, "gather_" + resource.name().toLowerCase(), StepType.GATHER, resource,
                    Math.max(1, count), "", blocker, requiredItems);
        }

        Step as3x3() {
            return new Step(goal, subGoal, StepType.CRAFT_3X3, resource, count, recipeName, blocker, requiredItems);
        }

        public String nextStep() {
            return switch (type) {
                case DONE -> "done";
                case GATHER -> "gather:" + resource.name().toLowerCase() + ":" + count;
                case CRAFT_2X2, CRAFT_3X3 -> "craft:" + recipeName;
                case PLACE_OR_OPEN -> "place_or_open:crafting_table";
                case WAIT_FOR_RECIPE -> "wait_recipe:" + recipeName;
            };
        }
    }

    public record RecipeOverrides(int planksRecipeId,
                                  int sticksRecipeId,
                                  int craftingTableRecipeId,
                                  int woodenPickaxeRecipeId,
                                  int stoneSwordRecipeId) {
        int recipeId(String recipeName) {
            return switch (recipeName) {
                case "planks" -> planksRecipeId;
                case "sticks" -> sticksRecipeId;
                case "crafting_table" -> craftingTableRecipeId;
                case "wooden_pickaxe" -> woodenPickaxeRecipeId;
                case "stone_sword" -> stoneSwordRecipeId;
                default -> -1;
            };
        }
    }

    public record InventoryCounts(int logs,
                                  int planks,
                                  int sticks,
                                  int craftingTables,
                                  int woodenPickaxes,
                                  int cobblestone,
                                  int stoneSwords) {
        static InventoryCounts from(BotInventoryState inventory, BotRecipeBook recipes) {
            return new InventoryCounts(
                    inventory.logCount(),
                    countKnownOrFallback(inventory, recipes.resultItemId("planks"), inventory.plankCount()),
                    countKnownOrFallback(inventory, recipes.resultItemId("sticks"), inventory.stickCount()),
                    countKnownOrFallback(inventory, recipes.resultItemId("crafting_table"), inventory.hasCraftingTable() ? 1 : 0),
                    countKnownOrFallback(inventory, recipes.resultItemId("wooden_pickaxe"), inventory.hasWoodenPickaxe() ? 1 : 0),
                    inventory.cobblestoneCount(),
                    countKnownOrFallback(inventory, recipes.resultItemId("stone_sword"), inventory.hasLikelyStoneSword() ? 1 : 0)
            );
        }

        private static int countKnownOrFallback(BotInventoryState inventory, OptionalInt itemId, int fallback) {
            return itemId.isPresent() ? inventory.countItemId(itemId.getAsInt()) : fallback;
        }
    }
}
