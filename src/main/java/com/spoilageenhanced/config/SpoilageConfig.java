package com.spoilageenhanced.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spoilageenhanced.platform.SpoilageEnhancedPlatform;
import com.spoilageenhanced.util.AutoFoodDetector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SpoilageConfig {
    /**
     * {@code disableHtmlEscaping} is not cosmetic here — this file is meant to be read and edited
     * by a person. Gson escapes {@code =}, {@code '}, {@code <}, {@code >} and {@code &} by
     * default, which turned the guide at the top of the config into lines like
     * {@code "===== Spoilage Enhanced"} and every quoted section name
     * into {@code 'additional_tracked_items'}. Nothing reads this JSON from a web page,
     * so the escaping bought nothing and cost legibility across the whole file.
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String CONFIG_FILENAME = "spoilage_enhanced.json";

    private static volatile SpoilageConfig INSTANCE;

    // ======================== Top-level guide ========================
    //
    // These fields exist only to be serialised into the config file, where they are the first
    // thing a person reads. Gson writes fields in declaration order, so this block lands at the
    // top of the JSON — keep it there, and keep each line one sentence long.
    //
    // The file used to open with three lines saying what it was and nothing about how to use it.
    // Everything a pack author actually needs — how to make one item spoil, how to give it a
    // custom time, how to stop an item spoiling, why hand-editing tracked_blocks does not stick —
    // was spread across per-field comments further down, or was not written anywhere at all.
    @SuppressWarnings("unused")
    private String _help_01 = "===== Spoilage Enhanced — configuration guide. Read the _help lines below, then edit the sections. =====";
    @SuppressWarnings("unused")
    private String _help_02 = "APPLYING CHANGES: save the file, then run /spoilage config reload in game. No restart needed. On a server, run it there.";
    @SuppressWarnings("unused")
    private String _help_03 = "UNITS: every duration is in TICKS. 20 ticks = 1 second. 24000 ticks = 1 Minecraft day = 20 real minutes.";
    @SuppressWarnings("unused")
    private String _help_04 = "ITEM IDs: point at an item in game and press F3+H to show ids in tooltips. The format is always modid:item_id, e.g. minecraft:bread.";
    @SuppressWarnings("unused")
    private String _help_05 = "YOUR EDITS ARE SAFE: anything you write here is never overwritten. Auto-detection only ADDS entries it does not already find.";
    @SuppressWarnings("unused")
    private String _help_06 = "--- HOW TO: make an item spoil that currently does not ---";
    @SuppressWarnings("unused")
    private String _help_07 = "Add its id to 'additional_tracked_items'. That is enough — it will use the default durations below.";
    @SuppressWarnings("unused")
    private String _help_08 = "--- HOW TO: give an item its own spoilage time ---";
    @SuppressWarnings("unused")
    private String _help_09 = "Add an entry to 'item_durations': \"modid:item_id\": { \"fresh\": 24000, \"stale\": 12000 }. 'fresh' is how long it stays good, 'stale' how long it then lingers before going rotten.";
    @SuppressWarnings("unused")
    private String _help_10 = "Example — make bread last three days fresh and one day stale: \"minecraft:bread\": { \"fresh\": 72000, \"stale\": 24000 }.";
    @SuppressWarnings("unused")
    private String _help_11 = "An item listed in 'item_durations' is tracked automatically; you do not also need it in 'additional_tracked_items'.";
    @SuppressWarnings("unused")
    private String _help_12 = "--- HOW TO: stop something spoiling ---";
    @SuppressWarnings("unused")
    private String _help_13 = "Add the item id to 'excluded_items'. This also stops any BLOCK that drops it from being tracked.";
    @SuppressWarnings("unused")
    private String _help_14 = "--- HOW TO: stop a world block from getting a freshness timer ---";
    @SuppressWarnings("unused")
    private String _help_15 = "Add the block id to 'excluded_blocks'. Do NOT delete lines from 'tracked_blocks' — that map is rewritten by the mod and your deletion will come back.";
    @SuppressWarnings("unused")
    private String _help_16 = "--- HOW TO: change the pace for everything at once ---";
    @SuppressWarnings("unused")
    private String _help_17 = "Set 'spoilage_speed_multiplier'. It DIVIDES every duration: 2.0 spoils twice as fast, 0.5 twice as slow, 1.0 is normal. Range 0.01 to 100.";
    @SuppressWarnings("unused")
    private String _help_18 = "--- WHAT THE OTHER SECTIONS DO ---";
    @SuppressWarnings("unused")
    private String _help_19 = "'effects' / 'milk_effects' / 'animal_feeding' — what eating or feeding stale and rotten food does. 'composter' — fill chance per stage (rotten is 1.0, i.e. guaranteed). 'loot_randomization' — condition of food found in generated chests.";
    @SuppressWarnings("unused")
    private String _help_20 = "'tracked_blocks' and 'derived_items' are written by the mod itself. Safe to read, safe to delete wholesale (they rebuild), but hand edits to them do not stick.";

    // ======================== General Settings ========================
    @SuppressWarnings("unused")
    private String _comment_enable_logging = "Master switch for writing mod logs to disk (spoilage_enhanced_logs/ folder)";
    public boolean enable_logging = true;

    @SuppressWarnings("unused")
    private String _comment_enableTraceLogging = "Enable detailed trace logging (very verbose, useful for debugging)";
    public boolean enableTraceLogging = true;

    @SuppressWarnings("unused")
    private String _comment_spoilage_speed_multiplier = "Global speed multiplier. 1.0 = normal, 2.0 = spoils 2x faster, 0.5 = 2x slower. Range: 0.01 to 100.0";
    public double spoilage_speed_multiplier = 1.0;

    @SuppressWarnings("unused")
    private String _comment_default_fresh = "Default fresh duration in ticks for items without explicit overrides (24000 = 1 game day)";
    private long default_fresh_duration_ticks = 24000L;

    @SuppressWarnings("unused")
    private String _comment_default_stale = "Default stale duration in ticks for items without explicit overrides (24000 = 1 game day)";
    private long default_stale_duration_ticks = 24000L;

    // ======================== Item Lists ========================
    @SuppressWarnings("unused")
    private String _comment_excluded_items = "Items that NEVER spoil (e.g. magical food, already-rotten items). Use mod_id:item_id format. Also stops auto-detection from ever adding them back, and stops any block that drops them from being tracked.";
    private List<String> excluded_items = new ArrayList<>();

    /**
     * Blocks that are never tracked, whatever auto-detection decides.
     *
     * <p>{@code tracked_blocks} is written by the mod, so deleting a line from it is not a
     * decision that survives: the next scan adds it again. This list is the one a player or
     * pack author edits, and nothing in the mod ever writes to it.</p>
     */
    @SuppressWarnings("unused")
    private String _comment_excluded_blocks = "Blocks that NEVER get spoilage tracking, whatever auto-detection decides. Use mod_id:block_id format. Editing tracked_blocks by hand does not stick - the scanner rewrites it - so list a block HERE to keep it out for good.";
    private List<String> excluded_blocks = new ArrayList<>();
    private transient Set<String> excludedBlockSet;

    @SuppressWarnings("unused")
    private String _comment_additional_tracked_items = "Non-food items that should still spoil (eggs, mushrooms, wheat, etc.). Detected foods are tracked automatically.";
    private List<String> additional_tracked_items = new CopyOnWriteArrayList<>();

    /**
     * Recipe outputs the scanner inferred from their ingredients, kept apart from everything
     * else so they can never be used as evidence for the next inference.
     *
     * <p>Without this separation the definition of "food" is circular: RecipeScanner marks an
     * output spoilable, {@code isSpoilable} then answers true for it because it is now in
     * {@code item_durations}, and {@code isFoodOrMealItem} accepts that as proof it is food —
     * so the next recipe up the chain inherits too. The scanner runs up to five passes and
     * feeds on its own output, so contamination spreads: beetroot -> red dye -> red wool ->
     * red bed. Every bed colour, all wool, carpets, shulker boxes, dyes, packed mud and mud
     * bricks ended up spoiling in a live world that way.</p>
     */
    @SuppressWarnings("unused")
    private String _comment_derived_items = "Items the recipe scanner inferred from ingredients. Not treated as food when scanning further recipes - this is what stops spoilage spreading down crafting chains. Rebuilt automatically; safe to delete.";
    private List<String> derived_items = new CopyOnWriteArrayList<>();
    private transient Set<String> derivedSet;

    /**
     * Bumped when a change makes previously written entries wrong. On load, a config carrying
     * a lower number has its auto-discovered entries dropped and rebuilt under the current
     * rules — fixing the code alone would not remove what the old rules already wrote, because
     * the scanner only ever appends.
     *
     * <p>Starts at 0 deliberately: a file written before this field existed has no such key, so
     * Gson leaves it at the initialised value and the migration runs exactly once.</p>
     */
    private int config_version = 0;
    public static final int CURRENT_CONFIG_VERSION = 3;
    // 2 -> 3: the recipe scanner now requires an output to look edible in its own right before
    // inheriting spoilage from its ingredients. Entries written under the old rule are wrong
    // and, because the scanners only append, would otherwise stay forever — an apple sapling
    // crafted from one apple had a freshness timer in a live save.

    // ======================== Effects Config ========================
    @SuppressWarnings("unused")
    private String _comment_effects = "Status effects applied when eating spoiled food. Durations in ticks (20 ticks = 1 sec).";
    private EffectsConfig effects = new EffectsConfig();

    // ======================== Milk Effects Config ========================
    @SuppressWarnings("unused")
    private String _comment_milk_effects = "Status effects when consuming spoiled milk. Rotten milk blocks clearing of status effects.";
    private MilkEffectsConfig milk_effects = new MilkEffectsConfig();

    // ======================== Animal Feeding Config ========================
    @SuppressWarnings("unused")
    private String _comment_animal_feeding = "Effects when animals are fed spoiled food. Breeding is cancelled for rotten food.";
    private AnimalFeedingConfig animal_feeding = new AnimalFeedingConfig();

    // ======================== Composter Config ========================
    @SuppressWarnings("unused")
    private String _comment_composter = "Composting chances by spoilage state (0.0 to 1.0). Rotten food is great for composting!";
    private ComposterConfig composter = new ComposterConfig();

    // ======================== Loot Randomization Config ========================
    @SuppressWarnings("unused")
    private String _comment_loot_randomization = "Spoilage state distribution for food found in dungeon chests. Values are cumulative thresholds.";
    private LootRandomizationConfig loot_randomization = new LootRandomizationConfig();

    // ======================== Item Durations ========================
    @SuppressWarnings("unused")
    private String _comment_item_durations = "Per-item spoilage durations in ticks (24000 = 1 game day). Every standard food has its own value, ordered roughly by how fast it really goes off: milk 9600, raw fish 16800-19200, poultry and red meat 20400-25200, eggs and produce 27600-37200, cooked 31800-39000, baked 42000-51600, dried and preserved 60000-96000. Add items from ANY mod here. YOUR EDITS ARE KEPT: a value present in this file is never overwritten by the built-in default.";
    private Map<String, ItemDuration> item_durations = new ConcurrentHashMap<>();

    // ======================== Tracked Blocks ========================
    @SuppressWarnings("unused")
    private String _comment_tracked_blocks = "Block-to-drop mapping for world blocks. Key = block ID, Value = item drop ID. Used for block spoilage tracking.";
    private Map<String, String> tracked_blocks = new ConcurrentHashMap<>();

    // ======================== Transient (not serialized) ========================
    private transient Set<String> excludedSet;
    private transient Set<String> additionalSet;
    private transient List<String> spoilableFoodItems = new ArrayList<>();
    private final transient Map<Item, Boolean> spoilableCache = new ConcurrentHashMap<>();
    private static final Map<Item, String> ITEM_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * Per-Item base (pre-multiplier) duration cache, Lens 8. getBaseFreshDurationForItem walks a
     * ~40-branch String.contains chain for every item without an explicit override, and
     * updateSpoilage calls it (plus the stale variant) for every tracked stack every 20 ticks —
     * 1,000 active stacks meant ~2,000 full string-scan chains per second. The cache holds
     * {fresh, stale} base ticks; the speed multiplier is applied outside it because it changes
     * through setSpoilageSpeedMultiplier without a config reload.
     *
     * <p>Invalidated by {@link #clearDurationCache()} on reload(), registerDynamicStorageItem,
     * registerDynamicFoodItem, and clearCache() — every path that can change item_durations.</p>
     */
    private static final Map<Item, long[]> BASE_DURATION_CACHE = new ConcurrentHashMap<>();

    // ======================== Inner Config Classes ========================

    public static class EffectsConfig {
        @SuppressWarnings("unused")
        public String _comment_stale = "Stale food: reduced nutrition. Chance-based nausea.";
        public double stale_nausea_chance = 0.5;
        public int stale_nausea_duration_ticks = 200;
        public int stale_hunger_penalty_percent = 50;
        public int stale_saturation_penalty_percent = 50;

        @SuppressWarnings("unused")
        public String _comment_rotten = "Rotten food: zero nutrition, guaranteed poison.";
        public int rotten_poison_duration_ticks = 200;
        public boolean rotten_removes_all_hunger = true;
    }

    public static class MilkEffectsConfig {
        @SuppressWarnings("unused")
        public String _comment = "Stale milk: nausea but still clears effects. Rotten milk: blocks clearing, applies debuffs.";
        public int stale_nausea_duration_ticks = 200;
        public int rotten_nausea_duration_ticks = 300;
        public int rotten_poison_duration_ticks = 200;
        public int rotten_hunger_duration_ticks = 300;
        public boolean rotten_blocks_effect_clearing = true;
    }

    public static class AnimalFeedingConfig {
        @SuppressWarnings("unused")
        public String _comment = "Feeding rotten food to animals cancels breeding and applies effects.";
        public int rotten_poison_duration_ticks = 200;
        public int rotten_weakness_duration_ticks = 300;
        public boolean rotten_cancels_breeding = true;
        public boolean rotten_show_particles = true;
    }

    public static class ComposterConfig {
        @SuppressWarnings("unused")
        public String _comment = "Composting chances (0.0-1.0). Fresh food has low chance, rotten food is guaranteed.";
        public float fresh_chance = 0.30f;
        public float stale_chance = 0.65f;
        public float rotten_chance = 1.0f;
    }

    public static class LootRandomizationConfig {
        @SuppressWarnings("unused")
        public String _comment = "Distribution for dungeon/village chest food. fresh + stale + rotten should equal 1.0. If not, values are normalized.";
        public float fresh_chance = 0.60f;
        public float stale_chance = 0.30f;
        public float rotten_chance = 0.10f;
    }

    public static class ItemDuration {
        public long fresh;
        public long stale;

        public ItemDuration() {}

        public ItemDuration(long fresh, long stale) {
            this.fresh = fresh;
            this.stale = stale;
        }
    }

    // ======================== Config Accessors ========================

    public EffectsConfig getEffectsConfig() {
        return effects != null ? effects : (effects = new EffectsConfig());
    }

    public MilkEffectsConfig getMilkEffectsConfig() {
        return milk_effects != null ? milk_effects : (milk_effects = new MilkEffectsConfig());
    }

    public AnimalFeedingConfig getAnimalFeedingConfig() {
        return animal_feeding != null ? animal_feeding : (animal_feeding = new AnimalFeedingConfig());
    }

    public ComposterConfig getComposterConfig() {
        return composter != null ? composter : (composter = new ComposterConfig());
    }

    public LootRandomizationConfig getLootRandomizationConfig() {
        return loot_randomization != null ? loot_randomization : (loot_randomization = new LootRandomizationConfig());
    }

    // ======================== Singleton ========================

    public static SpoilageConfig getInstance() {
        SpoilageConfig local = INSTANCE;
        if (local == null) {
            synchronized (SpoilageConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    INSTANCE = local = load();
                }
            }
        }
        return local;
    }

    /**
     * Reload configuration from disk (used by /spoilage config reload command).
     * Returns true if reload was successful.
     */
    public static boolean reload() {
        try {
            synchronized (SpoilageConfig.class) {
                INSTANCE = load();
                if (INSTANCE != null) {
                    INSTANCE.spoilableCache.clear();
                    INSTANCE.excludedSet = null;
                    INSTANCE.additionalSet = null;
                    INSTANCE.excludedBlockSet = null;
                    INSTANCE.derivedSet = null;
                }
                ITEM_ID_CACHE.clear();
                clearDurationCache();
                // The block -> food-drop cache answers from tracked_blocks / spoilability, both
                // of which a config reload can change. Without this, a hand-edited
                // tracked_blocks entry only took effect after the next datapack reload.
                com.spoilageenhanced.util.DynamicFoodBlockCache.clear();
            }
            // Pass 532 (L11 — load behaviour): the logger's static flag cache must be
            // refreshed after a config reload, otherwise enable_logging / enableTraceLogging
            // changes from the reloaded config are ignored until restart.
            com.spoilageenhanced.util.SpoilageEnhancedLogger.refreshConfigCache();
            return true;
        } catch (Exception e) {
            System.err.println("[Spoilage Enhanced] Failed to reload config: " + e.getMessage());
            return false;
        }
    }

    // ======================== Item ID Resolution ========================

    public static String getItemId(Item item) {
        if (item == null || item == Items.AIR) return "minecraft:air";
        String cached = ITEM_ID_CACHE.get(item);
        if (cached != null) return cached;
        String id = resolveItemId(item);
        ITEM_ID_CACHE.put(item, id);
        return id;
    }

    private static String resolveItemId(Item item) {
        try {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            if (key != null && !key.getPath().equals("air")) {
                return key.toString();
            }
        } catch (Exception ignored) {}
        try {
            Optional<ResourceKey<Item>> rk = BuiltInRegistries.ITEM.getResourceKey(item);
            if (rk.isPresent()) {
                return rk.get().identifier().toString();
            }
        } catch (Exception ignored) {}
        try {
            String descId = item.getDescriptionId();
            if (descId != null) {
                if (descId.startsWith("item.minecraft.")) {
                    return "minecraft:" + descId.substring("item.minecraft.".length());
                }
                if (descId.startsWith("block.minecraft.")) {
                    return "minecraft:" + descId.substring("block.minecraft.".length());
                }
                if (descId.startsWith("item.")) {
                    String[] parts = descId.split("\\.");
                    if (parts.length >= 3) {
                        return parts[1] + ":" + parts[2];
                    }
                }
                if (descId.startsWith("block.")) {
                    String[] parts = descId.split("\\.");
                    if (parts.length >= 3) {
                        return parts[1] + ":" + parts[2];
                    }
                }
            }
        } catch (Exception ignored) {}
        // Pass 625 (Lens 1 — silent failure): the old fallback returned item.toString() which for
        // an ItemStack-like Item produces a string like "1 apple" — not a valid id. The result
        // gets cached in ITEM_ID_CACHE and later compared against excludedSet/additionalSet
        // entries like "minecraft:apple", where it never matches. The item is then permanently
        // invisible to per-item spoilage rules. Log WARNING so the failure is visible, then
        // return a stable sentinel that can be detected by callers (excludedSet/additionalSet
        // membership tests will never match for this item, which is the same behavior as before
        // — but the log makes it diagnosable).
        com.spoilageenhanced.util.SpoilageEnhancedLogger.log(com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory.GENERAL,
                "SpoilageConfig.resolveItemId: all three id-resolution paths failed for item class "
                + item.getClass().getName() + " (toString=" + item + ", descriptionId="
                + safeDescriptionId(item) + "); item will be treated as not matching any per-item rule");
        return "spoilage_enhanced:unresolved:" + item.getClass().getName();
    }

    private static String safeDescriptionId(Item item) {
        try { return item.getDescriptionId(); } catch (Exception e) { return "<getDescriptionId threw " + e.getClass().getSimpleName() + ">"; }
    }

    // ======================== Spoilability Checks ========================

    public void clearCache() {
        spoilableCache.clear();
        ITEM_ID_CACHE.clear();
        clearDurationCache();
        // Rebuilt lazily. Without this an edit to excluded_blocks would need a restart, while
        // every other list in this file takes effect on `/spoilage config reload`.
        excludedBlockSet = null;
        // Pass 617 (Lens 3 - cache correctness): same staleness as excludedBlockSet. Without
        // this an edit to additional_tracked_items would not take effect until restart.
        additionalSet = null;
        derivedSet = null;
    }

    /**
     * Drop the base-duration cache. Callers must invoke this whenever {@link #item_durations} or
     * the dynamic food/storage registration tables change, so cached entries don't outlive the
     * config that produced them.
     */
    public static void clearDurationCache() {
        BASE_DURATION_CACHE.clear();
    }

    public boolean isSpoilable(Item item) {
        if (item == null || item == Items.AIR) return false;
        Boolean cached = spoilableCache.get(item);
        if (cached != null) return cached;

        // Pass 95 (Lens 12): the unbound-components status is now RETURNED from the compute
        // instead of communicated through the shared componentsWereUnbound instance field.
        // The old field was a cross-thread race: thread A computing an unbound item set it
        // true, thread B computing a bound item set it false, and whichever write thread A
        // observed decided whether its provisional answer got cached — a bound-item result
        // could be cached under a stale "unbound" flag (hiding a spoilable item until the
        // next clearCache) or an unbound-item provisional answer could be cached as final.
        boolean[] unboundOut = new boolean[1];
        boolean result = computeIsSpoilable(item, unboundOut);
        // Binding-timing guard (Lens 2): when the item's components are not bound yet,
        // item.components() throws and computeIsSpoilable answers "false" without ever
        // seeing the FOOD component. Caching that answer would permanently hide a spoilable
        // item until the next clearCache() — which only happens on datapack reload. Skip the
        // cache in that case so the next call re-evaluates once components are bound.
        if (!unboundOut[0]) {
            spoilableCache.put(item, result);
        }
        return result;
    }

    private boolean computeIsSpoilable(Item item, boolean[] unboundOut) {
        String idStr = getItemId(item);

        if (getExcludedSet().contains(idStr)) {
            unboundOut[0] = false;
            return false;
        }
        // Items that spoil without being edible (eggs, milk, cake). Checked before the FOOD
        // component because the component is unreadable during the datapack-load scan, and a
        // "not spoilable" answer there is what keeps their Crate Delight crates unregistered.
        if (AutoFoodDetector.isAlwaysSpoilable(item)) {
            unboundOut[0] = false;
            return true;
        }
        unboundOut[0] = false;
        try {
            if (item.components() != null && item.components().has(DataComponents.FOOD)) {
                return true;
            }
        } catch (Exception e) {
            // Components not bound yet — the answer below is provisional, not final.
            unboundOut[0] = true;
        }

        // Pass 600 (L1 — silent failure): when components are unbound, the FOOD check above
        // threw and unboundOut[0] is true. The additional/duration checks below are config-only
        // (no component access), so their answers are final regardless of binding state. If
        // either matches, the item IS spoilable — mark unboundOut false so the caller caches
        // the answer instead of re-running the throw + these lookups on every call. Without
        // this, every isSpoilable() call for an item in additional_tracked_items or
        // item_durations during the datapack-load scan re-throws and re-looks-up, which is
        // measurable (102 callers, many on hot paths).
        if (getAdditionalSet().contains(idStr)) {
            unboundOut[0] = false;
            return true;
        }
        if (item_durations.containsKey(idStr)) {
            unboundOut[0] = false;
            return true;
        }
        return false;
    }

    public boolean isExcluded(Item item) {
        String idStr = getItemId(item);
        return getExcludedSet().contains(idStr);
    }

    // ======================== Duration Calculations ========================

    public long getBaseFreshDurationForItem(Item item) {
        // Pass 515 (L1 — silent failure): a null item reaches BASE_DURATION_CACHE.get(null)
        // -> null -> computeBaseDurations(null) -> BASE_DURATION_CACHE.put(null, ...) -> NPE
        // ("Cannot invoke Object.hashCode() because key is null"). All current callers
        // guard against null, but a future caller might not. Return the default fresh
        // duration for null instead of crashing.
        if (item == null || item == Items.AIR) {
            return default_fresh_duration_ticks;
        }
        long[] cached = BASE_DURATION_CACHE.get(item);
        if (cached != null) {
            return cached[0];
        }
        long[] computed = computeBaseDurations(item);
        BASE_DURATION_CACHE.put(item, computed);
        return computed[0];
    }

    public long getBaseStaleDurationForItem(Item item) {
        if (item == null || item == Items.AIR) {
            return default_stale_duration_ticks;
        }
        long[] cached = BASE_DURATION_CACHE.get(item);
        if (cached != null) {
            return cached[1];
        }
        long[] computed = computeBaseDurations(item);
        BASE_DURATION_CACHE.put(item, computed);
        return computed[1];
    }

    /**
     * Shared slow path for both base durations — computes {fresh, stale} from the explicit
     * override table or the ~40-branch String.contains categorization chain. Stored as a
     * {@code long[2]} so a single cache hit returns both numbers and avoids re-running the
     * chain for the stale lookup right after the fresh lookup in {@link FoodSpoilageUtil}.
     */
    private long[] computeBaseDurations(Item item) {
        String idStr = getItemId(item);
        ItemDuration override = item_durations.get(idStr);
        if (override != null && override.fresh > 0) {
            long fresh = override.fresh;
            long stale = (override.stale > 0) ? override.stale : fresh;
            return new long[] { fresh, stale };
        }

        // Smart dynamic categorization for vanilla & modded items
        String name = idStr.toLowerCase();
        if (name.contains("milk") || name.contains("dairy")) {
            return new long[] { 9600L, 9600L }; // 8 min
        }
        if (name.contains("fish") || name.contains("cod") || name.contains("salmon") || name.contains("tropical")) {
            if (name.contains("cooked")) return new long[] { 36000L, 36000L }; // 30 min
            return new long[] { 19200L, 19200L }; // 16 min
        }
        if (name.contains("beef") || name.contains("pork") || name.contains("chicken") || name.contains("mutton")
                || name.contains("rabbit") || name.contains("meat") || name.contains("egg")) {
            if (name.contains("cooked") || name.contains("roasted") || name.contains("fried") || name.contains("baked") || name.contains("grilled")) {
                return new long[] { 36000L, 36000L }; // 30 min
            }
            return new long[] { 24000L, 24000L }; // 20 min
        }
        if (name.contains("stew") || name.contains("soup") || name.contains("broth")) {
            return new long[] { 24000L, 24000L }; // 20 min
        }
        if (name.contains("mushroom")) {
            return new long[] { 24000L, 24000L }; // 20 min
        }
        if (name.contains("crate") || name.contains("bag") || name.contains("bale") || name.contains("hay")) {
            return new long[] { 96000L, 96000L }; // 80 min
        }
        if (name.contains("honey") || name.contains("dried") || name.contains("kelp")) {
            return new long[] { 60000L, 60000L }; // 50 min
        }
        if (name.contains("bread") || name.contains("cookie") || name.contains("pie") || name.contains("cake")
                || name.contains("wheat") || name.contains("rice") || name.contains("pumpkin") || name.contains("baked_potato")) {
            return new long[] { 48000L, 48000L }; // 40 min
        }
        if (name.contains("apple") || name.contains("berry") || name.contains("berries") || name.contains("carrot")
                || name.contains("potato") || name.contains("beetroot") || name.contains("melon") || name.contains("salad")
                || name.contains("fruit") || name.contains("vegetable") || name.contains("tomato") || name.contains("onion") || name.contains("cabbage")) {
            return new long[] { 36000L, 36000L }; // 30 min
        }

        return new long[] { default_fresh_duration_ticks, default_fresh_duration_ticks };
    }

    public long getFreshDurationForItem(Item item) {
        return applySpeedMultiplier(getBaseFreshDurationForItem(item));
    }

    public long getStaleDurationForItem(Item item) {
        return applySpeedMultiplier(getBaseStaleDurationForItem(item));
    }

    private long applySpeedMultiplier(long baseDuration) {
        if (spoilage_speed_multiplier <= 0.0) return baseDuration;
        return Math.max(1L, (long) (baseDuration / spoilage_speed_multiplier));
    }

    public double getSpoilageSpeedMultiplier() {
        return spoilage_speed_multiplier;
    }

    public void setSpoilageSpeedMultiplier(double multiplier) {
        // Pass 631 (Lens 7 — boundary): the config load path clamps spoilage_speed_multiplier
        // to [0.01, 100] at line 824, but the runtime setter only had a floor (0.01) — no upper
        // clamp. Calling `spoilage speed 10000` at runtime left the value at 10000, which
        // then feeds rescaleItemTimestamps as a ratio of up to 10000/100 = 100. The
        // (long)(remaining * ratio) cast inside rescale can overflow for very large
        // remaining values. Clamp the setter to the same range the config load uses.
        if (Double.isNaN(multiplier) || multiplier <= 0.0) multiplier = 0.01;
        else if (multiplier > 100.0) multiplier = 100.0;
        this.spoilage_speed_multiplier = multiplier;
        save();
    }

    public long getFreshDurationTicks() {
        return default_fresh_duration_ticks;
    }

    public long getStaleDurationTicks() {
        return default_stale_duration_ticks;
    }

    // ======================== Sets & Lists ========================

    public Set<String> getExcludedSet() {
        if (excludedSet == null) {
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(excluded_items);
            excludedSet = set;
        }
        return excludedSet;
    }

    public Set<String> getAdditionalSet() {
        if (additionalSet == null) {
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(additional_tracked_items);
            additionalSet = set;
        }
        return additionalSet;
    }

    public Set<String> getExcludedBlockSet() {
        if (excludedBlockSet == null) {
            Set<String> set = ConcurrentHashMap.newKeySet();
            if (excluded_blocks != null) set.addAll(excluded_blocks);
            excludedBlockSet = set;
        }
        return excludedBlockSet;
    }

    public Set<String> getDerivedSet() {
        if (derivedSet == null) {
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(derived_items);
            derivedSet = set;
        }
        return derivedSet;
    }

    /** True when this item is spoilable only because a recipe it comes out of had spoilable inputs. */
    public boolean isDerivedItem(Item item) {
        return item != null && getDerivedSet().contains(getItemId(item));
    }

    public List<String> getSpoilableFoodItems() {
        return spoilableFoodItems;
    }

    // ======================== Tracked Blocks ========================

    public String getTrackedBlockDropItem(String blockId) {
        // Pass 327 (L7 — boundary): tracked_blocks is a ConcurrentHashMap, and
        // CHM.containsKey(null) throws NullPointerException. The current callers all pass
        // a non-null id (BuiltInRegistries.BLOCK.getKey(block).toString()), but the method
        // is public — a future caller could pass null. Mirror the null guard style of
        // FoodSpoilageUtil.updateSpoilage (Pass 173).
        if (blockId == null) {
            return null;
        }
        // An excluded block is not tracked, whatever tracked_blocks happens to say.
        //
        // The check has to be here rather than only at registration, because tracked_blocks is
        // written automatically: deleting a line from it is not a decision that sticks, since
        // the next scan puts it straight back. excluded_blocks is the list a player or pack
        // author edits and the mod never touches.
        if (getExcludedBlockSet().contains(blockId)) {
            return null;
        }
        String drop = tracked_blocks != null ? tracked_blocks.get(blockId) : null;
        if (drop != null) {
            // Excluding the ITEM also stops the block that drops it. Someone who writes
            // "bread never spoils" means it, and would not expect to have to say it twice.
            if (getExcludedSet().contains(drop)) {
                return null;
            }
            return drop;
        }
        if ("minecraft:melon".equals(blockId))
            return "minecraft:melon_slice";
        if ("minecraft:pumpkin".equals(blockId) || "minecraft:carved_pumpkin".equals(blockId))
            return "minecraft:pumpkin";
        if ("minecraft:red_mushroom_block".equals(blockId))
            return "minecraft:red_mushroom";
        if ("minecraft:brown_mushroom_block".equals(blockId))
            return "minecraft:brown_mushroom";
        if ("minecraft:hay_block".equals(blockId))
            return "minecraft:wheat";
        if ("minecraft:dried_kelp_block".equals(blockId))
            return "minecraft:dried_kelp";
        if ("minecraft:carrots".equals(blockId))
            return "minecraft:carrot";
        if ("minecraft:potatoes".equals(blockId))
            return "minecraft:potato";
        if ("minecraft:beetroots".equals(blockId))
            return "minecraft:beetroot";
        if ("minecraft:wheat".equals(blockId))
            return "minecraft:wheat";
        if ("minecraft:sweet_berry_bush".equals(blockId))
            return "minecraft:sweet_berries";
        if ("minecraft:cave_vines".equals(blockId) || "minecraft:cave_vines_plant".equals(blockId))
            return "minecraft:glow_berries";
        return null;
    }

    public Map<String, String> getTrackedBlocks() {
        return Collections.unmodifiableMap(tracked_blocks);
    }

    /**
     * Adds a block -> drop mapping discovered at runtime (see AutoFoodDetector). Existing
     * mappings are never overwritten, so hand-written config entries always win.
     */
    public boolean registerTrackedBlock(String blockId, String dropItemId, boolean autoSave) {
        if (blockId == null || dropItemId == null) return false;
        // Auto-detection must not add back what someone deliberately excluded - neither the
        // block itself nor a block whose drop they said never spoils.
        if (getExcludedBlockSet().contains(blockId) || getExcludedSet().contains(dropItemId)) {
            return false;
        }
        if (tracked_blocks == null) {
            tracked_blocks = new ConcurrentHashMap<>();
        }
        if (tracked_blocks.putIfAbsent(blockId, dropItemId) != null) {
            return false;
        }
        if (autoSave) {
            save();
        }
        return true;
    }

    public boolean isBlockTracked(String blockId) {
        return getTrackedBlockDropItem(blockId) != null;
    }

    // ======================== Dynamic Registration ========================

    public void registerDynamicStorageItem(String storageItemId, String sourceFoodId) {
        registerDynamicStorageItem(storageItemId, sourceFoodId, false);
    }

    public void registerDynamicStorageItem(String storageItemId, String sourceFoodId, boolean autoSave) {
        net.minecraft.resources.Identifier sourceId = net.minecraft.resources.Identifier.parse(sourceFoodId);
        Item sourceItem = BuiltInRegistries.ITEM.getValue(sourceId);
        if (!isSpoilable(sourceItem)) return;

        net.minecraft.resources.Identifier storageId = net.minecraft.resources.Identifier.parse(storageItemId);
        Item storageItem = BuiltInRegistries.ITEM.getValue(storageId);
        if (storageItem == null || storageItem == Items.AIR || !AutoFoodDetector.isValidFoodCandidate(storageItem)) return;
        if (getExcludedSet().contains(storageItemId)) return;

        if (!additional_tracked_items.contains(storageItemId)) {
            additional_tracked_items.add(storageItemId);
            getAdditionalSet().add(storageItemId);

            long baseFresh = getBaseFreshDurationForItem(sourceItem);
            long baseStale = getBaseStaleDurationForItem(sourceItem);

            item_durations.put(storageItemId, new ItemDuration(baseFresh, baseStale));
            spoilableCache.clear();
            clearDurationCache();
            // Pass 617 (Lens 3 - cache correctness): getAdditionalSet() is built lazily from
            // additional_tracked_items and cached in the additionalSet field. The two caches
            // above were being cleared but additionalSet was not, so a dynamically registered
            // storage item stayed invisible to isSpoilable() until the next /spoilage config
            // reload (which is the only other path that nulls it). Drop it here too, mirroring
            // reload() (SpoilageConfig.java:276-277).
            additionalSet = null;

            if (autoSave) {
                save();
            }
        }
    }

    public void registerDynamicFoodItem(String itemId, long freshTicks, long staleTicks) {
        registerDynamicFoodItem(itemId, freshTicks, staleTicks, false);
    }

    public void registerDynamicFoodItem(String itemId, long freshTicks, long staleTicks, boolean autoSave) {
        registerDynamicFoodItem(itemId, freshTicks, staleTicks, autoSave, false);
    }

    /**
     * @param derived {@code true} when this item is spoilable only because its ingredients
     *                were — a recipe output, not something recognised as food in its own
     *                right. Such an item is recorded in {@code derived_items} and must not
     *                count as food evidence when the scanner looks at further recipes, or
     *                spoilage walks down every crafting chain that starts at a vegetable.
     */
    public void registerDynamicFoodItem(String itemId, long freshTicks, long staleTicks, boolean autoSave, boolean derived) {
        if (getExcludedSet().contains(itemId)) return;

        if (derived && !derived_items.contains(itemId)) {
            derived_items.add(itemId);
            getDerivedSet().add(itemId);
        }

        if (!additional_tracked_items.contains(itemId)) {
            additional_tracked_items.add(itemId);
            getAdditionalSet().add(itemId);
        }

        if (!item_durations.containsKey(itemId)) {
            item_durations.put(itemId, new ItemDuration(freshTicks, staleTicks));
            spoilableCache.clear();
            clearDurationCache();
            if (autoSave) {
                save();
            }
        }
        // Pass 617 (Lens 3 - cache correctness): the derived flag can be set on an item that
        // already has a duration entry (re-registration with derived=true), so this must run
        // outside the containsKey branch. Both additionalSet and derivedSet are built lazily
        // from additional_tracked_items / derived_items and would otherwise stay stale.
        additionalSet = null;
        if (derived) derivedSet = null;
    }

    // ======================== Load / Save ========================

    public static SpoilageConfig load() {
        Path configDir = SpoilageEnhancedPlatform.getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILENAME);

        SpoilageConfig config = null;
        if (Files.exists(configFile)) {
            try (Reader reader = new FileReader(configFile.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, SpoilageConfig.class);
            } catch (Exception e) {
                System.err.println("[Spoilage Enhanced] Failed to load spoilage config: " + e.getMessage());
            }
        }

        if (config != null) {
            // Ensure nested configs are not null (backward compatibility with old configs)
            if (config.effects == null) config.effects = new EffectsConfig();
            if (config.milk_effects == null) config.milk_effects = new MilkEffectsConfig();
            if (config.animal_feeding == null) config.animal_feeding = new AnimalFeedingConfig();
            if (config.composter == null) config.composter = new ComposterConfig();
            if (config.loot_randomization == null) config.loot_randomization = new LootRandomizationConfig();
            // A hand-edited config can carry a broken speed multiplier (0, negative, NaN).
            // Runtime guards keep it from crashing, but every duration would silently fall
            // back to the un-scaled base — clamp it here so the value behaves as documented.
            if (Double.isNaN(config.spoilage_speed_multiplier) || config.spoilage_speed_multiplier <= 0.0) {
                config.spoilage_speed_multiplier = 1.0;
            } else if (config.spoilage_speed_multiplier > 100.0) {
                config.spoilage_speed_multiplier = 100.0;
            }
            if (config.config_version < CURRENT_CONFIG_VERSION) {
                config.migrateAutoDiscovered();
            }
            config.populateDefaults();
        } else {
            config = new SpoilageConfig();
            config.config_version = CURRENT_CONFIG_VERSION;
            config.populateDefaults();
            config.save();
        }

        return config;
    }

    /**
     * Drops everything the scanners inferred so it is rebuilt under the current rules.
     *
     * <p>Fixing the inference alone would not help an existing world: the scanners only ever
     * append, so whatever the old rules wrote stays written. A config from before this version
     * lists every bed colour, all wool, carpets, shulker boxes, dyes, packed mud and mud bricks
     * as food — 133 wrong entries in one real save — and each of them would keep spoiling
     * forever.</p>
     *
     * <p>What survives is anything the built-in defaults define and anything a player added:
     * the baseline below is a pristine config, and only entries absent from it are removed.
     * Hand-added items are indistinguishable from discovered ones in the file, so they go too
     * and are the one real cost of this migration — noted in the log so it is not a surprise.</p>
     */
    private void migrateAutoDiscovered() {
        SpoilageConfig baseline = new SpoilageConfig();
        baseline.populateDefaults();

        int removedItems = additional_tracked_items.size();
        additional_tracked_items.removeIf(id -> !baseline.additional_tracked_items.contains(id));
        removedItems -= additional_tracked_items.size();

        int removedDurations = item_durations.size();
        item_durations.keySet().removeIf(id -> !baseline.item_durations.containsKey(id));
        removedDurations -= item_durations.size();

        int removedBlocks = tracked_blocks.size();
        tracked_blocks.keySet().removeIf(id -> !baseline.tracked_blocks.containsKey(id));
        removedBlocks -= tracked_blocks.size();

        derived_items.clear();
        derivedSet = null;
        additionalSet = null;
        clearCache();

        config_version = CURRENT_CONFIG_VERSION;

        System.out.println("[Spoilage Enhanced] Config migrated to version " + CURRENT_CONFIG_VERSION
                + ": dropped " + removedItems + " tracked items, " + removedDurations
                + " durations and " + removedBlocks + " blocks that the old recipe scanner had"
                + " inferred (beds, wool, dyes and similar). They will be rebuilt from recipes"
                + " under the corrected rules. Items you added by hand were removed too and"
                + " need re-adding.");
    }

    public void populateDefaults() {
        // Excluded items
        addDefaultExcluded("minecraft:poisonous_potato");
        addDefaultExcluded("minecraft:spider_eye");
        addDefaultExcluded("minecraft:rotten_flesh");
        addDefaultExcluded("minecraft:chorus_fruit");
        addDefaultExcluded("minecraft:golden_apple");
        addDefaultExcluded("minecraft:enchanted_golden_apple");
        addDefaultExcluded("minecraft:golden_carrot");
        addDefaultExcluded("minecraft:suspicious_stew");
        addDefaultExcluded("minecraft:pufferfish");

        // Seeds are planting stock, not food. Nobody eats them and they keep indefinitely, so a
        // freshness timer on a packet of seeds is noise at best; at worst it rots the only thing
        // standing between the player and a field. Listed explicitly because the name-based
        // detection only skips ids ending in "_seeds" — pitcher_pod does not.
        addDefaultExcluded("minecraft:wheat_seeds");
        addDefaultExcluded("minecraft:beetroot_seeds");
        addDefaultExcluded("minecraft:melon_seeds");
        addDefaultExcluded("minecraft:pumpkin_seeds");
        addDefaultExcluded("minecraft:torchflower_seeds");
        addDefaultExcluded("minecraft:pitcher_pod");

        // Additional items
        addDefaultAdditional("minecraft:milk_bucket");
        addDefaultAdditional("minecraft:egg");
        addDefaultAdditional("minecraft:red_mushroom");
        addDefaultAdditional("minecraft:brown_mushroom");
        addDefaultAdditional("minecraft:wheat");
        addDefaultAdditional("minecraft:melon");
        addDefaultAdditional("minecraft:pumpkin");
        addDefaultAdditional("minecraft:carved_pumpkin");
        addDefaultAdditional("minecraft:hay_block");
        addDefaultAdditional("minecraft:dried_kelp_block");
        addDefaultAdditional("minecraft:red_mushroom_block");
        addDefaultAdditional("minecraft:brown_mushroom_block");

        // Tracked block drops
        tracked_blocks.putIfAbsent("minecraft:melon", "minecraft:melon_slice");
        tracked_blocks.putIfAbsent("minecraft:pumpkin", "minecraft:pumpkin");
        tracked_blocks.putIfAbsent("minecraft:carved_pumpkin", "minecraft:pumpkin");
        tracked_blocks.putIfAbsent("minecraft:red_mushroom_block", "minecraft:red_mushroom");
        tracked_blocks.putIfAbsent("minecraft:brown_mushroom_block", "minecraft:brown_mushroom");
        tracked_blocks.putIfAbsent("minecraft:hay_block", "minecraft:wheat");
        tracked_blocks.putIfAbsent("minecraft:dried_kelp_block", "minecraft:dried_kelp");
        tracked_blocks.putIfAbsent("minecraft:carrots", "minecraft:carrot");
        tracked_blocks.putIfAbsent("minecraft:potatoes", "minecraft:potato");
        tracked_blocks.putIfAbsent("minecraft:beetroots", "minecraft:beetroot");
        tracked_blocks.putIfAbsent("minecraft:wheat", "minecraft:wheat");
        tracked_blocks.putIfAbsent("minecraft:sweet_berry_bush", "minecraft:sweet_berries");
        tracked_blocks.putIfAbsent("minecraft:cave_vines", "minecraft:glow_berries");
        tracked_blocks.putIfAbsent("minecraft:cave_vines_plant", "minecraft:glow_berries");

        // === Default item durations ===
        //
        // Every standard food gets its OWN number rather than sharing a tier. Identical values
        // made distinct foods indistinguishable in play: a chicken breast and a steak spoiled
        // on the same schedule, so the timer told the player nothing they could act on. The
        // ordering below is the point - roughly by how fast the real thing goes off: dairy and
        // raw fish first, then poultry before red meat, then produce, then cooked, baked and
        // finally dried and preserved. 24000 ticks = one game day = 20 real minutes.
        //
        // putIfAbsent, NOT put. These are defaults for a config that does not mention the item
        // yet, and they must never overwrite a value someone edited. The old code used put(),
        // so every hand-tuned duration was silently reset to the built-in one on the next
        // start and the config looked like it was ignoring the player.
        dur("minecraft:milk_bucket", 9600, 9600);
        dur("minecraft:tropical_fish", 16800, 16800);
        dur("minecraft:cod", 18000, 18000);
        dur("minecraft:salmon", 19200, 19200);
        dur("minecraft:chicken", 20400, 20400);
        dur("minecraft:rabbit", 21600, 21600);
        dur("minecraft:mutton", 22800, 22800);
        dur("minecraft:porkchop", 24000, 24000);
        dur("minecraft:beef", 25200, 25200);
        dur("minecraft:mushroom_stew", 25800, 25800);
        dur("minecraft:beetroot_soup", 26400, 26400);
        dur("minecraft:rabbit_stew", 27000, 27000);
        dur("minecraft:egg", 27600, 27600);
        dur("minecraft:brown_egg", 28200, 28200);
        dur("minecraft:blue_egg", 28800, 28800);
        dur("minecraft:red_mushroom", 29400, 29400);
        dur("minecraft:brown_mushroom", 30000, 30000);
        dur("minecraft:sweet_berries", 30600, 30600);
        dur("minecraft:glow_berries", 31200, 31200);
        dur("minecraft:cooked_cod", 31800, 31800);
        dur("minecraft:cooked_salmon", 32400, 32400);
        dur("minecraft:melon_slice", 33000, 33000);
        dur("minecraft:apple", 33600, 33600);
        dur("minecraft:cooked_chicken", 34200, 34200);
        dur("minecraft:cooked_rabbit", 34800, 34800);
        dur("minecraft:carrot", 35400, 35400);
        dur("minecraft:potato", 36000, 36000);
        dur("minecraft:cooked_mutton", 36600, 36600);
        dur("minecraft:beetroot", 37200, 37200);
        dur("minecraft:cooked_porkchop", 37800, 37800);
        dur("minecraft:cake", 38400, 38400);
        dur("minecraft:cooked_beef", 39000, 39000);
        dur("minecraft:red_mushroom_block", 39600, 39600);
        dur("minecraft:brown_mushroom_block", 40200, 40200);
        dur("minecraft:pumpkin_pie", 42000, 42000);
        dur("minecraft:cookie", 44400, 44400);
        dur("minecraft:bread", 45600, 45600);
        dur("minecraft:baked_potato", 46800, 46800);
        dur("minecraft:wheat", 48000, 48000);
        dur("minecraft:melon", 49200, 49200);
        dur("minecraft:pumpkin", 50400, 50400);
        dur("minecraft:carved_pumpkin", 51600, 51600);
        dur("minecraft:dried_kelp", 60000, 60000);
        dur("minecraft:honey_bottle", 62400, 62400);
        dur("minecraft:dried_kelp_block", 72000, 72000);
        dur("minecraft:hay_block", 96000, 96000);    }

    /** Default duration for one item. putIfAbsent so an edited config value is never clobbered. */
    private void dur(String id, long freshTicks, long staleTicks) {
        item_durations.putIfAbsent(id, new ItemDuration(freshTicks, staleTicks));
    }

    private void addDefaultExcluded(String id) {
        if (!excluded_items.contains(id)) {
            excluded_items.add(id);
        }
    }

    private void addDefaultAdditional(String id) {
        if (!additional_tracked_items.contains(id)) {
            additional_tracked_items.add(id);
        }
    }

    public void save() {
        // Pass 433 (Lens 1 — silent failure): the test suite calls paths that reach save()
        // (AutoFoodDetector.scanTagsAndBlocks, RecipeScanner.scan, registerDynamicFoodItem
        // with autoSave=true), and save() unconditionally writes to
        // SpoilageEnhancedPlatform.getConfigDir() — which in tests resolves to the real
        // project's config/spoilage_enhanced.json. Every full test run dirties the working
        // tree with entries like "testmod:dynamic_food_365186306154000" and a stray
        // "minecraft:bowl", and the garbage is one careless `git add` away from shipping to
        // players. Detect a real game environment by the presence of a `saves/` directory
        // containing at least one world with a level.dat (the definitive marker of a running
        // Minecraft instance). The unit suite's run directory (run/fabric/saves) exists but
        // is empty — it has no level.dat — so the guard correctly skips the write.
        //
        // Pass 454 (L13 — behaviour): the guard as written in Pass 433 only recognised the
        // CLIENT layout (saves/<world>/level.dat). A DEDICATED SERVER keeps its world at
        // <gameDir>/world/level.dat — no saves/ directory at all — so the guard returned
        // early and silently blocked EVERY config save on every dedicated server: "spoilage
        // logging false" did not persist, "spoilage speed 2" did not persist, dynamic food
        // registrations did not persist. Verified live: after "spoilage logging false" the
        // server's config still read enable_logging=true. Accept the server layout too:
        // world/level.dat directly under the game dir.
        Path gameDir = SpoilageEnhancedPlatform.getGameDir();
        boolean isRealGame = false;
        Path savesDir = gameDir.resolve("saves");
        if (java.nio.file.Files.isDirectory(savesDir)) {
            // Client layout: saves/<world>/level.dat
            try (java.nio.file.DirectoryStream<Path> stream = java.nio.file.Files.newDirectoryStream(savesDir)) {
                for (Path worldDir : stream) {
                    if (java.nio.file.Files.isRegularFile(worldDir.resolve("level.dat"))) {
                        isRealGame = true;
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        if (!isRealGame) {
            // Dedicated-server layout: world/level.dat directly under the game dir.
            if (java.nio.file.Files.isRegularFile(gameDir.resolve("world").resolve("level.dat"))) {
                isRealGame = true;
            }
        }
        if (!isRealGame) {
            return;
        }
        File file = SpoilageEnhancedPlatform.getConfigDir().resolve(CONFIG_FILENAME).toFile();
        file.getParentFile().mkdirs();
        // Charset stated explicitly: FileWriter/FileReader without one use the PLATFORM
        // default, so a config written on one machine could be read as mojibake on another.
        // The file already contains non-ASCII (the guide at the top), and ids may too.
        try (FileWriter writer = new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
