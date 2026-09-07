package com.spoilageenhanced.util;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public class AutoFoodDetector {

    public static List<String> detectAllFoodItems() {
        List<String> foodItemIds = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

            if (isNeverSpoilable(item)) {
                continue;
            }

            if (isAlwaysSpoilable(item) || SpoilageConfig.getInstance().isSpoilable(item)) {
                if (!foodItemIds.contains(itemId)) {
                    foodItemIds.add(itemId);
                }
                continue;
            }

            DataComponentMap components = safeComponents(item);
            if (components != null && components.has(DataComponents.FOOD)) {
                if (!foodItemIds.contains(itemId)) {
                    foodItemIds.add(itemId);
                }
            }
        }

        SpoilageEnhancedLogger.log("AutoFoodDetector: Found " + foodItemIds.size() + " food items.");
        return foodItemIds;
    }


    /**
     * {@code Item.components()} throws "Components not bound yet" for items whose registry holder
     * is not fully bound at the moment we scan (this happens during datapack reload). Every scan
     * path goes through here so one unbound item cannot abort world loading.
     */
    private static DataComponentMap safeComponents(Item item) {
        if (item == null) {
            return null;
        }
        try {
            if (!item.builtInRegistryHolder().areComponentsBound()) {
                return null;
            }
            return item.components();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isNeverSpoilable(Item item) {
        if (item == Items.POTION
                || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.GOLDEN_APPLE
                || item == Items.GOLDEN_CARROT
                || item == Items.GLISTERING_MELON_SLICE
                || item == Items.ROTTEN_FLESH
                || item == Items.SPIDER_EYE) {
            return true;
        }

        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return id.equals("minecraft:golden_apple")
                || id.equals("minecraft:enchanted_golden_apple")
                || id.equals("minecraft:golden_carrot")
                || id.equals("minecraft:glistering_melon_slice")
                || id.equals("minecraft:rotten_flesh")
                || id.equals("minecraft:spider_eye");
    }

    public static boolean isAlwaysSpoilable(Item item) {
        if (item == Items.EGG || item == Items.MILK_BUCKET || item == Items.CAKE) {
            return true;
        }
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        // 26.2 split eggs into three items. Without the variants their Crate Delight crates
        // (blue_egg_crate, brown_egg_crate) never become spoilable and the recipe scanner skips them.
        return id.equals("minecraft:egg") || id.equals("minecraft:blue_egg") || id.equals("minecraft:brown_egg")
                || id.equals("minecraft:milk_bucket") || id.equals("minecraft:cake");
    }


    // ======================== Tag / block scan (modded content) ========================

    /**
     * Second detection pass for content the FOOD component alone does not cover:
     *
     * <ul>
     *   <li>modded consumables that only carry a CONSUMABLE component (drinks, tonics, ...);</li>
     *   <li>items tagged with the conventional {@code c:foods...} / {@code c:drinks...} tags used
     *       by both Fabric and NeoForge, even when the item itself has no FOOD component;</li>
     *   <li>modded blocks whose item form is spoilable, so world blocks get spoilage tracking
     *       (the vanilla melon/pumpkin/hay mapping, generalized).</li>
     * </ul>
     *
     * Durations come from the same name-based heuristics used for everything else, scaled by a
     * per-category factor (raw meat spoils faster than bread). Anything already known — a FOOD
     * item, an explicit config entry or an excluded item — is left alone, so the user's config
     * always wins. Idempotent: re-running it registers nothing new.
     */
    public static void scanTagsAndBlocks() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        int items = 0;
        int blocks = 0;

        // Declared mappings first. These are the blocks whose food is picked by hand and so
        // appears in no loot table, tag or recipe — nothing below can derive them, and without
        // this the mod only learns them by watching someone harvest one. Registering here means
        // the block loop's isBlockTracked check skips them, and a hand-written config entry
        // still wins because registerTrackedBlock inserts with putIfAbsent.
        blocks += KnownHandPickedBlocks.registerAll();

        for (Item item : BuiltInRegistries.ITEM) {
            try {
                if (item == null || item == Items.AIR) continue;
                if (isNeverSpoilable(item)) continue;
                if (config.isSpoilable(item)) continue;
                if (!isValidFoodCandidate(item)) continue;

                Double factor = detectFoodFactor(item);
                if (factor == null) continue;

                String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
                long fresh = Math.max(1L, (long) (config.getBaseFreshDurationForItem(item) * factor));
                long stale = Math.max(1L, (long) (config.getBaseStaleDurationForItem(item) * factor));
                config.registerDynamicFoodItem(itemId, fresh, stale, false);
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                        "AutoFoodDetector: registered " + itemId + " (fresh=" + fresh + ", stale=" + stale + ")");
                items++;
            } catch (Throwable t) {
                // One broken item must never abort world loading.
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                        "AutoFoodDetector: skipped an item during the tag scan: " + t);
            }
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            try {
                if (block == null) continue;
                String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
                if (config.isBlockTracked(blockId)) continue;

                Item blockItem = block.asItem();
                if (blockItem == null || blockItem == Items.AIR) continue;
                if (isNeverSpoilable(blockItem) || !config.isSpoilable(blockItem)) continue;

                String dropId = BuiltInRegistries.ITEM.getKey(blockItem).toString();
                if (config.registerTrackedBlock(blockId, dropId, false)) {
                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                            "AutoFoodDetector: tracking block " + blockId + " -> " + dropId);
                    blocks++;
                }
            } catch (Throwable t) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                        "AutoFoodDetector: skipped a block during the tag scan: " + t);
            }
        }

        if (items > 0 || blocks > 0) {
            config.save();
        }
        SpoilageEnhancedLogger.log("AutoFoodDetector: tag/block scan registered " + items
                + " items and " + blocks + " blocks.");
    }

    /**
     * @return a duration factor when the item looks edible, or {@code null} when it does not.
     */
    private static Double detectFoodFactor(Item item) {
        Double tagFactor = foodTagFactor(item);
        if (tagFactor != null) {
            return tagFactor;
        }

        DataComponentMap components = safeComponents(item);
        if (components != null && components.has(DataComponents.CONSUMABLE)) {
            // Consumable but not FOOD: drinks and similar. Potions and the other
            // "never spoils" items were filtered out by the caller.
            return 0.8d;
        }
        return null;
    }

    /**
     * Looks for conventional {@code c:} food tags. Reading the holder's tags covers every
     * {@code c:foods/...} subtag without hardcoding the list, which is what modded packs use.
     */
    private static Double foodTagFactor(Item item) {
        try {
            List<TagKey<Item>> tags = item.builtInRegistryHolder().tags().toList();
            Double best = null;
            for (TagKey<Item> tag : tags) {
                Identifier id = tag.location();
                if (!"c".equals(id.getNamespace())) continue;
                String path = id.getPath();
                if (!path.equals("foods") && !path.startsWith("foods/")
                        && !path.equals("drinks") && !path.startsWith("drinks/")) {
                    continue;
                }

                double factor;
                if (path.contains("raw_meat") || path.contains("raw_fish") || path.contains("raw_")) {
                    factor = 0.6d;
                } else if (path.contains("soup") || path.contains("stew") || path.startsWith("drinks")) {
                    factor = 0.8d;
                } else if (path.contains("bread") || path.contains("dried") || path.contains("candy")
                        || path.contains("sweets") || path.contains("golden")) {
                    factor = 1.5d;
                } else {
                    factor = 1.0d;
                }

                // The most specific rule wins; ties keep the shortest lifetime.
                best = best == null ? factor : Math.min(best, factor);
            }
            return best;
        } catch (Throwable ignored) {
            // Tags are not bound yet (or the holder is unbound) — nothing to do this pass.
            return null;
        }
    }

    public static boolean isContainerItem(Item item) {
        if (item == null) return false;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return id.equals("minecraft:bowl")
                || id.equals("minecraft:bucket")
                || id.equals("minecraft:glass_bottle")
                || id.endsWith("_bucket")
                || id.endsWith(":plate")
                || id.endsWith("_tray")
                || id.endsWith(":skillet");
    }

    public static boolean isFoodOrMealItem(Item item) {
        if (item == null) return false;
        if (isNeverSpoilable(item)) return false;
        if (isAlwaysSpoilable(item)) return true;

        DataComponentMap components = safeComponents(item);
        if (components != null && components.has(DataComponents.FOOD)) return true;

        // An item the recipe scanner inferred is NOT evidence that the next recipe up the
        // chain makes food. Answering true here is what made the definition circular: the
        // scanner marks an output spoilable, isSpoilable then reports it spoilable because it
        // sits in item_durations, and this method accepted that as proof of foodness — so the
        // next recipe inherited, and the next. Across five scanner passes that carried
        // beetroot into red dye, dye into wool, wool into beds, and put a spoilage timer on
        // every bed, carpet and shulker box in a live world.
        if (SpoilageConfig.getInstance().isDerivedItem(item)) return false;

        return SpoilageConfig.getInstance().isSpoilable(item);
    }

    /**
     * Could this item plausibly be a MEAL — something a recipe turns food INTO?
     *
     * <p>Stricter than {@link #isValidFoodCandidate}, and deliberately so. That method only asks
     * "is this obviously not a tool", which almost everything passes; used as the gate on
     * recipe inference it let anything craftable from food become food. An apple sapling made
     * from one apple scored 100% spoilable ingredients and got a freshness timer — a sapling
     * you plant, rotting in your inventory.</p>
     *
     * <p>Being made of food does not make something food. A meal is edible, so the output has
     * to look edible in its own right: a FOOD or CONSUMABLE component, or a conventional food
     * tag. Everything else made from food — saplings, decorations, dyes — is a different thing
     * that happens to have started as an ingredient.</p>
     *
     * <p>The cost of this strictness: a modded cake-like block item, which is eaten as a block
     * and so carries no FOOD component, will not be picked up automatically. That is what
     * {@code additional_tracked_items} in the config is for, and it is the better trade — one
     * item somebody adds by hand, against every sapling and trinket in the pack.</p>
     */
    public static boolean looksEdibleItself(Item item) {
        if (item == null || item == Items.AIR) return false;
        if (isNeverSpoilable(item)) return false;
        if (isAlwaysSpoilable(item)) return true;

        DataComponentMap components = safeComponents(item);
        if (components != null
                && (components.has(DataComponents.FOOD) || components.has(DataComponents.CONSUMABLE))) {
            return true;
        }
        return foodTagFactor(item) != null;
    }

    public static boolean isValidFoodCandidate(Item item) {
        if (item == null || item == Items.AIR) return false;
        DataComponentMap components = safeComponents(item);
        if (components != null && components.has(DataComponents.MAX_DAMAGE)) return false;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (id.endsWith("_sword") || id.endsWith("_axe") || id.endsWith("_pickaxe")
                || id.endsWith("_shovel") || id.endsWith("_hoe") || id.endsWith("_helmet")
                || id.endsWith("_chestplate") || id.endsWith("_leggings") || id.endsWith("_boots")
                // Saplings are planting stock, like seeds. Every mod names them the same way,
                // so the suffix catches them all: an apple sapling crafted from one apple was
                // given a freshness timer, and a sapling that rots in your inventory is not a
                // thing anyone wants. looksEdibleItself already refuses it at the recipe gate;
                // this closes the tag-based path as well, where a mod tagging its sapling under
                // a food tag would otherwise walk straight in.
                || id.endsWith("_sapling")
                || id.endsWith("_seeds") || id.equals("minecraft:stick") || id.equals("minecraft:paper")
                || id.equals("minecraft:string") || id.equals("minecraft:feather") || id.equals("minecraft:flint")) {
            return false;
        }
        return true;
    }
}
