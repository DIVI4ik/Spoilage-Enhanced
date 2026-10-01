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
        if (item == null || item == Items.AIR) return false;
        if (item == Items.POTION
                || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.GOLDEN_APPLE
                || item == Items.GOLDEN_CARROT
                || item == Items.GLISTERING_MELON_SLICE
                || item == Items.ROTTEN_FLESH
                || item == Items.SPIDER_EYE
                || item == Items.ICE
                || item == Items.PACKED_ICE
                || item == Items.BLUE_ICE) {
            return true;
        }

        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (id.equals("minecraft:golden_apple")
                || id.equals("minecraft:enchanted_golden_apple")
                || id.equals("minecraft:golden_carrot")
                || id.equals("minecraft:glistering_melon_slice")
                || id.equals("minecraft:rotten_flesh")
                || id.equals("minecraft:spider_eye")
                || id.equals("minecraft:ice")
                || id.equals("minecraft:packed_ice")
                || id.equals("minecraft:blue_ice")) {
            return true;
        }

        return isIceId(id);
    }

    public static boolean isIceItem(Item item) {
        if (item == null || item == Items.AIR) return false;
        if (item == Items.ICE || item == Items.PACKED_ICE || item == Items.BLUE_ICE) return true;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return isIceId(id);
    }

    public static boolean isIceId(String id) {
        if (id == null) return false;
        if (id.equals("minecraft:ice")
                || id.equals("minecraft:packed_ice")
                || id.equals("minecraft:blue_ice")
                || id.equals("minecraft:frosted_ice")) {
            return true;
        }
        if ((id.endsWith(":ice") || id.endsWith("_ice"))
                && !id.contains("cream") && !id.contains("tea") && !id.contains("coffee")
                && !id.contains("juice") && !id.contains("drink") && !id.contains("pop")
                && !id.contains("cake")) {
            return true;
        }
        if (id.contains("ice_cube") || id.contains("ice_shard") || id.contains("ice_shaving")) {
            return true;
        }
        return false;
    }

    public static boolean isAlwaysSpoilable(Item item) {
        if (item == null) return false;
        if (item == Items.EGG || item == Items.MILK_BUCKET || item == Items.CAKE) {
            return true;
        }
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        // 26.2 split eggs into three items. Without the variants their Crate Delight crates
        // (blue_egg_crate, brown_egg_crate) never become spoilable and the recipe scanner skips them.
        // candle_cake has no item form (asItem() returns Items.AIR) — it is eaten only via the
        // block-eating path, so it is covered by CakeEatMixin (which injects CakeBlock.eat, the
        // method CandleCakeBlock.useWithoutItem calls). It is NOT listed here.
        // betterend bucket items (bucket_end_fish, bucket_cubozoa) are MobBucketItem subclasses
        // like vanilla fish buckets, but the mod doesn't add the FOOD component to them.
        // They contain edible fish and should spoil like milk_bucket does.
        return id.equals("minecraft:egg") || id.equals("minecraft:blue_egg") || id.equals("minecraft:brown_egg")
                || id.equals("minecraft:milk_bucket") || id.equals("minecraft:cake")
                || id.equals("betterend:bucket_end_fish") || id.equals("betterend:bucket_cubozoa");
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
                // Pass 1292: log only when the registration actually happened — an
                // excluded item is silently rejected inside registerDynamicFoodItem,
                // and the old unconditional line logged 'registered' for items the
                // exclusion had just refused (chorus_fruit in the default config).
                if (config.registerDynamicFoodItem(itemId, fresh, stale, false)) {
                    SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                            "AutoFoodDetector: registered " + itemId + " (fresh=" + fresh + ", stale=" + stale + ")");
                    items++;
                }
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
        if (isKnownNonFoodMaterial(item)) {
            return null;
        }

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
            var holder = item.builtInRegistryHolder();
            if (holder == null) return null;
            List<TagKey<Item>> tags = holder.tags().toList();
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
        if (isNeverSpoilable(item) || isKnownNonFoodMaterial(item)) return false;
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
        if (isNeverSpoilable(item) || isKnownNonFoodMaterial(item)) return false;
        if (isAlwaysSpoilable(item)) return true;

        DataComponentMap components = safeComponents(item);
        if (components != null
                && (components.has(DataComponents.FOOD) || components.has(DataComponents.CONSUMABLE))) {
            return true;
        }
        return foodTagFactor(item) != null;
    }

    /**
     * Universal non-food material heuristic: rejects structural materials, minerals, ores,
     * geology, redstone, and non-food blocks from vanilla and any mods.
     */
    public static boolean isKnownNonFoodMaterial(Item item) {
        if (item == null || item == Items.AIR) return false;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return isKnownNonFoodMaterial(id);
    }

    public static boolean isKnownNonFoodMaterial(String id) {
        if (id == null || id.isEmpty()) return false;
        String path = id.toLowerCase();
        int colon = path.indexOf(':');
        if (colon != -1) {
            path = path.substring(colon + 1);
        }

        // 1. Ice and snow (excluding desserts / drinks like ice_cream, iced_tea)
        if (isIceId(id)) {
            return true;
        }
        if ((path.endsWith("_snow") || path.equals("snow") || path.startsWith("snow_") || path.equals("powder_snow"))
                && !path.contains("cone") && !path.contains("dessert") && !path.contains("ice_cream")) {
            return true;
        }

        // 2. Construction / structural / architectural items & blocks
        if (path.endsWith("_slab") || path.endsWith("_stairs") || path.endsWith("_wall")
                || path.endsWith("_fence") || path.endsWith("_fence_gate")
                || path.endsWith("_door") || path.endsWith("_trapdoor")
                || path.endsWith("_pane") || path.endsWith("_glass") || path.equals("glass")
                || path.endsWith("_brick") || path.endsWith("_bricks") || path.equals("brick") || path.equals("bricks")
                || path.endsWith("_tiles") || path.endsWith("_planks")
                || path.endsWith("_pillar") || path.endsWith("_bars") || path.equals("iron_bars")
                || path.endsWith("_chain") || path.equals("chain") || path.endsWith("_rod")
                || path.endsWith("_lantern") || path.equals("lantern") || path.endsWith("_torch") || path.equals("torch")
                || path.endsWith("_scaffolding") || path.equals("scaffolding") || path.endsWith("_button")
                || path.endsWith("_pressure_plate") || path.endsWith("_sign")
                || path.endsWith("_hanging_sign") || path.endsWith("_anvil") || path.equals("anvil")) {
            return true;
        }

        // 3. Minerals, metals, and raw ore materials
        // Note: Do NOT match raw_ prefix directly because raw_beef, raw_porkchop, raw_chicken exist!
        if (path.endsWith("_ore") || path.endsWith("_raw_ore")
                || path.equals("raw_iron") || path.equals("raw_copper") || path.equals("raw_gold")
                || path.startsWith("raw_iron_") || path.startsWith("raw_copper_") || path.startsWith("raw_gold_")
                || path.endsWith("_ingot")
                || path.equals("diamond") || path.endsWith("_diamond")
                || path.equals("emerald") || path.endsWith("_emerald")
                || path.equals("amethyst") || path.endsWith("_amethyst")
                || path.equals("quartz") || path.endsWith("_quartz")
                || path.equals("ruby") || path.endsWith("_ruby")
                || path.equals("sapphire") || path.endsWith("_sapphire")
                || path.equals("topaz") || path.endsWith("_topaz")
                || path.equals("coal") || path.equals("charcoal")
                || path.equals("redstone") || path.equals("glowstone")
                || path.equals("lapis_lazuli") || path.endsWith("_lapis")
                || path.equals("netherite_scrap")
                || path.endsWith("_dust") || path.endsWith("_shard")
                || path.endsWith("_crystal") || path.endsWith("_gem")) {
            return true;
        }
        // Nuggets: avoid chicken_nugget, fish_nugget, etc.
        if (path.endsWith("_nugget")) {
            if (!path.contains("chicken") && !path.contains("fish") && !path.contains("meat")
                    && !path.contains("tofu") && !path.contains("cheese") && !path.contains("turkey")
                    && !path.contains("pork") && !path.contains("beef") && !path.contains("veggie")) {
                return true;
            }
        }
        // Known solid mineral/metal blocks
        if (path.equals("iron_block") || path.equals("gold_block") || path.equals("copper_block")
                || path.equals("diamond_block") || path.equals("netherite_block")
                || path.equals("emerald_block") || path.equals("lapis_block")
                || path.equals("redstone_block") || path.equals("amethyst_block")
                || path.equals("quartz_block")) {
            return true;
        }

        // 4. Geology, terrain, stone variants
        if (path.endsWith("_stone") || path.equals("stone")
                || path.endsWith("_cobblestone") || path.equals("cobblestone")
                || path.endsWith("_deepslate") || path.equals("deepslate")
                || path.endsWith("_granite") || path.equals("granite")
                || path.endsWith("_diorite") || path.equals("diorite")
                || path.endsWith("_andesite") || path.equals("andesite")
                || path.endsWith("_tuff") || path.equals("tuff")
                || path.endsWith("_basalt") || path.equals("basalt")
                || path.endsWith("_obsidian") || path.equals("obsidian")
                || path.endsWith("_sandstone") || path.equals("sandstone")
                || path.endsWith("_gravel") || path.equals("gravel")
                || path.endsWith("_sand") || path.equals("sand")
                || path.endsWith("_dirt") || path.equals("dirt")
                || path.endsWith("_mud") || path.equals("mud")
                || path.endsWith("_clay") || path.equals("clay")
                || path.endsWith("_concrete") || path.equals("concrete") || path.endsWith("_concrete_powder")
                || path.endsWith("_terracotta") || path.equals("terracotta")) {
            return true;
        }

        // 5. Mechanisms, redstone, utility
        if (path.endsWith("_piston") || path.equals("piston")
                || path.endsWith("_dispenser") || path.equals("dispenser")
                || path.endsWith("_dropper") || path.equals("dropper")
                || path.endsWith("_hopper") || path.equals("hopper")
                || path.endsWith("_observer") || path.equals("observer")
                || path.endsWith("_repeater") || path.equals("repeater")
                || path.endsWith("_comparator") || path.equals("comparator")
                || path.endsWith("_rail") || path.equals("rail")) {
            return true;
        }

        // 6. Textiles, bedding, furniture
        if (path.endsWith("_wool") || path.equals("wool")
                || path.endsWith("_carpet") || path.equals("carpet")
                || path.endsWith("_bed") || path.equals("bed")
                || path.endsWith("_banner") || path.equals("banner")
                || path.endsWith("_shulker_box") || path.equals("shulker_box")
                || path.endsWith("_candle") || path.equals("candle")) {
            return true;
        }

        return false;
    }

    /**
     * Verifies if {@code storageItem} is plausible food storage for {@code foodItem}.
     * Prevents non-food compression recipes (e.g. 9 ice -> packed ice, 9 iron -> iron block)
     * from becoming food storage blocks.
     */
    public static boolean isPlausibleFoodStorage(Item storageItem, Item foodItem) {
        if (storageItem == null || storageItem == Items.AIR || foodItem == null || foodItem == Items.AIR) {
            return false;
        }
        if (isNeverSpoilable(storageItem) || isNeverSpoilable(foodItem)) {
            return false;
        }
        if (isKnownNonFoodMaterial(storageItem) || isKnownNonFoodMaterial(foodItem)) {
            return false;
        }
        if (!isFoodOrMealItem(foodItem)) {
            return false;
        }

        String storageId = BuiltInRegistries.ITEM.getKey(storageItem).toString().toLowerCase();
        // Conventional storage forms in Minecraft and food mods (Farmer's Delight, Crate Delight, etc.):
        if (storageId.contains("crate") || storageId.contains("bag") || storageId.contains("sack")
                || storageId.contains("basket") || storageId.contains("bale") || storageId.contains("bundle")
                || storageId.contains("barrel") || storageId.contains("box") || storageId.contains("pack")) {
            return true;
        }
        // Known vanilla and mod food blocks:
        if (storageItem == Items.HAY_BLOCK || storageItem == Items.MELON
                || storageItem == Items.DRIED_KELP_BLOCK || storageItem == Items.HONEY_BLOCK
                || storageItem == Items.HONEYCOMB_BLOCK) {
            return true;
        }
        // If it's a block (ends with _block), only accept if the food item is an edible food/produce
        if (storageId.endsWith("_block")) {
            return isFoodOrMealItem(foodItem);
        }
        // Container items like bowls or plates
        if (isContainerItem(storageItem)) {
            return true;
        }
        return false;
    }

    public static boolean isValidFoodCandidate(Item item) {
        if (item == null || item == Items.AIR) return false;
        if (isNeverSpoilable(item) || isIceItem(item) || isKnownNonFoodMaterial(item)) return false;
        DataComponentMap components = safeComponents(item);
        if (components != null && components.has(DataComponents.MAX_DAMAGE)) return false;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (isIceId(id) || isKnownNonFoodMaterial(id)) return false;
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
