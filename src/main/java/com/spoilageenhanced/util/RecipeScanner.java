package com.spoilageenhanced.util;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

public class RecipeScanner {

    /**
     * Set when recipes are (re)loaded, consumed on the next server tick.
     *
     * <p>Scanning straight from {@code RecipeManager.apply} is too early: item components and item
     * tags are still unbound there, so {@code Item.components()} throws and every tag-based
     * ingredient blows up with "Trying to access unbound tag". One tick later both are bound and
     * the same scan sees the whole picture.</p>
     */
    private static volatile boolean scanPending = false;

    /** Called when recipes are loaded or reloaded; the scan itself happens on the next server tick. */
    public static void requestScan() {
        // Pass 97: take the same lock as runPendingScan so the request cannot be clobbered.
        synchronized (RecipeScanner.class) {
            scanPending = true;
        }
    }

    /** Runs a scan requested by {@link #requestScan()}, at most once per request. */
    public static void runPendingScan(RecipeManager manager) {
        // Pass 114 (Lens 8): this runs on EVERY server tick (20x/sec) via ServerTickScanMixin.
        // The Pass 97 synchronized block made the fast path (flag false, the overwhelmingly
        // common case) take a lock on every tick. Double-checked locking: the volatile read
        // below is lock-free; the synchronized block only runs when a scan is actually pending,
        // preserving the Pass 97 race fix (requestScan cannot be clobbered between check and
        // clear because both mutations happen under the same lock).
        if (manager == null || !scanPending) return;
        synchronized (RecipeScanner.class) {
            if (!scanPending) return;
            scanPending = false;
            scan(manager);
        }
    }

    public static void scan(RecipeManager manager) {
        SpoilageEnhancedLogger.log("Starting automatic recipe scan for food and storage blocks...");
        SpoilageConfig.getInstance().clearCache();

        // First server tick: registries, components and tags are all bound by now, so a lost
        // component registration would show up here.
        com.spoilageenhanced.platform.ForgeRegistryBridge.verifyRegistration(
                com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE);

        // Tag/component driven detection first, so recipes can build on what it found.
        AutoFoodDetector.scanTagsAndBlocks();

        Collection<RecipeHolder<?>> recipes = manager.getRecipes();
        int totalStorageFound = 0;
        int totalCompositeFoodsFound = 0;

        // A scan that read nothing and a scan that read everything and rejected it used to produce
        // the same log line. These counters are what tell the two apart.
        int skippedUnreadableResult = 0;
        int skippedThrown = 0;
        String firstUnreadableReason = null;
        String firstThrownReason = null;

        int pass = 0;
        int newlyDiscoveredThisPass;

        do {
            pass++;
            newlyDiscoveredThisPass = 0;

            for (RecipeHolder<?> recipeHolder : recipes) {
                try {
                    Recipe<?> recipe = recipeHolder.value();
                    RecipeOutput output = getRecipeOutput(recipe);
                    if (output == null) {
                        if (pass == 1) {
                            skippedUnreadableResult++;
                            if (firstUnreadableReason == null) {
                                firstUnreadableReason = recipeHolder.id().identifier() + " -> " + lastOutputFailure;
                            }
                        }
                        continue;
                    }

                    Item outputItem = output.item();
                    String outputId = BuiltInRegistries.ITEM.getKey(outputItem).toString();

                    if (recipe.placementInfo() == null) continue;
                    List<Ingredient> ingredients = recipe.placementInfo().ingredients();
                    if (ingredients == null || ingredients.isEmpty()) continue;

                    int ingredientCount = ingredients.size();

                    // 1. Storage Blocks (4x/9x compression)
                    if (output.count() == 1 && (ingredientCount == 4 || ingredientCount == 9)) {
                        Item uniqueInput = getUniqueIngredient(ingredients);
                        if (uniqueInput != null && uniqueInput != outputItem) {
                            String inputId = BuiltInRegistries.ITEM.getKey(uniqueInput).toString();
                            if (SpoilageConfig.getInstance().isSpoilable(uniqueInput)) {
                                if (!SpoilageConfig.getInstance().isSpoilable(outputItem)) {
                                    SpoilageConfig.getInstance().registerDynamicStorageItem(outputId, inputId);
                                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA, "Dynamic Registration (Storage): " + outputId + " acts as storage for " + inputId);
                                    totalStorageFound++;
                                    newlyDiscoveredThisPass++;
                                }
                            }
                        }
                    }

                    // 1b. Decompression (1 storage -> 4x/9x items)
                    if ((output.count() == 4 || output.count() == 9) && ingredientCount == 1) {
                        Item uniqueInput = getUniqueIngredient(ingredients);
                        if (uniqueInput != null && uniqueInput != outputItem) {
                            String inputId = BuiltInRegistries.ITEM.getKey(uniqueInput).toString();
                            if (SpoilageConfig.getInstance().isSpoilable(outputItem)) {
                                if (!SpoilageConfig.getInstance().isSpoilable(uniqueInput)) {
                                    SpoilageConfig.getInstance().registerDynamicStorageItem(inputId, outputId);
                                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA, "Dynamic Registration (Storage): " + inputId + " acts as storage for " + outputId);
                                    totalStorageFound++;
                                    newlyDiscoveredThisPass++;
                                }
                            }
                        }
                    }

                    // 2. Composite Food Recipes (meals, sushi, pies, etc.)
                    //
                    // The output must look edible ITSELF, not merely be made of things that
                    // were. isValidFoodCandidate only asks "is this obviously not a tool",
                    // which nearly everything passes: an apple sapling crafted from one apple
                    // scored 100% spoilable ingredients and was given a freshness timer.
                    // Being made of food is not being food.
                    if (AutoFoodDetector.looksEdibleItself(outputItem) && !SpoilageConfig.getInstance().isSpoilable(outputItem)) {
                        int totalIngredients = 0;
                        int spoilableCount = 0;
                        long sumFresh = 0;
                        long sumStale = 0;

                        for (Ingredient ingredient : ingredients) {
                            if (ingredient == null || ingredient.isEmpty()) continue;

                            boolean isContainer = false;
                            for (Holder<Item> holder : (Iterable<Holder<Item>>) ingredient.items()::iterator) {
                                if (AutoFoodDetector.isContainerItem(holder.value())) {
                                    isContainer = true;
                                    break;
                                }
                            }
                            if (isContainer) continue;

                            totalIngredients++;

                            Item spoilableMatchItem = null;
                            for (Holder<Item> holder : (Iterable<Holder<Item>>) ingredient.items()::iterator) {
                                Item stackItem = holder.value();
                                if (SpoilageConfig.getInstance().isSpoilable(stackItem) && AutoFoodDetector.isFoodOrMealItem(stackItem)) {
                                    spoilableMatchItem = stackItem;
                                    break;
                                }
                            }

                            if (spoilableMatchItem != null) {
                                spoilableCount++;
                                sumFresh += SpoilageConfig.getInstance().getFreshDurationForItem(spoilableMatchItem);
                                sumStale += SpoilageConfig.getInstance().getStaleDurationForItem(spoilableMatchItem);
                            }
                        }

                        if (totalIngredients > 0 && spoilableCount > 0) {
                            double spoilableRatio = (double) spoilableCount / totalIngredients;
                            // Strictly MORE than half, not "half or more". At >= 0.5 a single
                            // food ingredient in a two-slot recipe was enough, which is how mud
                            // + wheat made packed mud spoil and pumpkin + torch made a jack
                            // o'lantern spoil. A real dish is mostly food; one vegetable in a
                            // pair is a component, not a meal.
                            if (spoilableRatio > 0.5) {
                                long avgFresh = sumFresh / spoilableCount;
                                long avgStale = sumStale / spoilableCount;
                                // derived = true: this output is spoilable only because its
                                // ingredients were. Recording that stops it being read back as
                                // evidence of foodness on the next of the five scanner passes,
                                // which is what walked spoilage from beetroot to dye to wool to
                                // beds.
                                SpoilageConfig.getInstance().registerDynamicFoodItem(outputId, avgFresh, avgStale, false, true);
                                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA, "RecipeScanner: Discovered composite recipe food " + outputId
                                        + " (Pass " + pass + ", Spoilable ratio: " + String.format("%.0f%%", spoilableRatio * 100) + ", Fresh duration: " + avgFresh + " ticks)");
                                totalCompositeFoodsFound++;
                                newlyDiscoveredThisPass++;
                            }
                        }
                    }
                } catch (Exception e) {
                    if (pass == 1) {
                        skippedThrown++;
                        if (firstThrownReason == null) {
                            firstThrownReason = recipeHolder.id().identifier()
                                    + " -> " + e.getClass().getName() + ": " + e.getMessage();
                        }
                    }
                }
            }
        } while (newlyDiscoveredThisPass > 0 && pass < 5);

        if (totalStorageFound > 0 || totalCompositeFoodsFound > 0) {
            SpoilageConfig.getInstance().save();
        }

        SpoilageEnhancedLogger.log("Recipe scan complete (" + pass + " passes). Scanned " + recipes.size()
                + " recipes, discovered " + totalStorageFound + " storage items and " + totalCompositeFoodsFound + " composite food items.");
        if (skippedUnreadableResult > 0) {
            SpoilageEnhancedLogger.log("Recipe scan: " + skippedUnreadableResult
                    + " recipes had an unreadable result, first: " + firstUnreadableReason);
        }
        if (skippedThrown > 0) {
            SpoilageEnhancedLogger.log("Recipe scan: " + skippedThrown
                    + " recipes threw and were skipped, first: " + firstThrownReason);
        }
    }

    /**
     * The item and stack size a recipe produces, without an {@link ItemStack} in sight.
     *
     * <p>This scan runs from {@code RecipeManager.apply}, where item component holders are not
     * bound yet. Materializing the result — {@code ItemStackTemplate.create()} — reads
     * {@code Item.components()} there and throws "Components not bound yet" for every single
     * recipe, which is how this scanner silently discovered nothing at all. Identity and count
     * are all the scanner ever needed, and neither touches components.</p>
     */
    private record RecipeOutput(Item item, int count) {}

    /** Why the last {@link #getRecipeOutput} call gave up. Without it a silent null is indistinguishable from "no result field". */
    private static volatile String lastOutputFailure = "no attempt";

    private static RecipeOutput getRecipeOutput(Recipe<?> recipe) {
        if (recipe == null) return null;
        lastOutputFailure = "no 'result' field found in class hierarchy";
        try {
            Class<?> cls = recipe.getClass();
            while (cls != null && cls != Object.class) {
                try {
                    Field field = cls.getDeclaredField("result");
                    field.setAccessible(true);
                    Object val = field.get(recipe);
                    if (val instanceof ItemStackTemplate template) {
                        Item item = template.item().value();
                        return item == null || item == Items.AIR || template.count() <= 0
                                ? null : new RecipeOutput(item, template.count());
                    } else if (val instanceof ItemStack stack) {
                        // Some modded recipes still hold a plain stack. Reading identity and count
                        // off an existing stack is safe — it binds nothing.
                        return stack.isEmpty() ? null : new RecipeOutput(stack.getItem(), stack.getCount());
                    }
                } catch (NoSuchFieldException ignored) {}
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            lastOutputFailure = t.getClass().getName() + ": " + t.getMessage();
        }
        return null;
    }

    private static Item getUniqueIngredient(List<Ingredient> ingredients) {
        Item uniqueItem = null;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            var it = ingredient.items().iterator();
            if (!it.hasNext()) continue;
            Item currentItem = it.next().value();

            if (uniqueItem == null) {
                uniqueItem = currentItem;
            } else if (uniqueItem != currentItem) {
                return null;
            }
        }
        return uniqueItem;
    }
}
