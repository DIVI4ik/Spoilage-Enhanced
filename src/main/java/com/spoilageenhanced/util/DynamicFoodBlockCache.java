package com.spoilageenhanced.util;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DynamicFoodBlockCache {

    /**
     * Cache keyed by {@link Block} (not {@link BlockState}) to avoid cache explosion from
     * state properties like age, facing, waterlogged, etc. A block's food drop is determined
     * by its type, not its transient state.
     *
     * Maximum cache size to prevent unbounded memory growth. Vanilla has ~1000 blocks,
     * modded packs may have more. 2048 gives comfortable headroom.
     */
    private static final int MAX_CACHE_SIZE = 2048;

    /**
     * Pass 108 (Lens 8): the LRU deque is GONE from the hit path. The old code did
     * ACCESS_ORDER.remove(block) — a LINEAR SCAN of up to 2,048 entries — on EVERY cache hit,
     * yet the eviction it powered essentially never fires: vanilla has 1,196 blocks (measured,
     * BlockCountTest) and the cap is 2,048, so the cache simply holds every block type that was
     * ever queried. The CHM alone is thread-safe; the size cap remains as a safety valve that
     * clears the whole map (correct and rare) if a pathological modded pack exceeds it.
     */
    private static final Map<Block, String> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger SIZE = new AtomicInteger(0);
    private static final String NO_FOOD_DROP = "__NO_FOOD__";

    /**
     * The food this block yields, or {@code null} when it is not a food source *yet*.
     *
     * <p>A crop that has not finished growing answers {@code null}. That single guard is what
     * keeps a seedling out of every path at once — the HUD, the lazy registration behind
     * {@code getSpoilageState}, and block tracking all ask this question, and any one of them
     * answering "food" is enough to start a freshness clock on a sprout. Guarding them
     * individually is how the seedling kept its timer after the placement path had already been
     * fixed: the HUD asked, the lookup registered the block on the spot, and the sprout was
     * fresh again.</p>
     *
     * <p>The check runs BEFORE the cache, which is keyed by block and cannot tell a seedling
     * from a ripe plant. Returning early leaves the cache untouched.</p>
     */
    public static String getFoodDrop(BlockState state, ServerLevel world, BlockPos pos) {
        // What this block yields is asked first, and the ripeness question only of blocks that
        // yield something. Both orders give the same answer — the lookup is deliberately
        // stage-independent — but this one keeps the cost off the HUD's path: deriving a
        // ripeness rule probes the loot table, and the HUD resolves a block for every frame the
        // player spends looking at one. Asking ripeness first meant every stone, lamp and door
        // in the world paid for a probe to be told it was not food anyway.
        String dropId = getFoodDropIgnoringGrowth(state, world, pos);
        if (dropId == null) {
            return null;
        }
        if (!bearsFoodYet(state, world, pos)) {
            return null;
        }
        return dropId;
    }

    /**
     * As {@link #getFoodDrop}, but answers for a crop at any growth stage.
     *
     * <p>Only the block-drop handler needs this: breaking an unripe crop still yields produce in
     * vanilla, and that produce has to be identified in order to be marked rotten.</p>
     */
    /**
     * How a block signals that it is carrying food, derived once per block.
     *
     * <p>Two shapes, because plants use two. Most count stages in an integer, and ripeness is a
     * threshold on it. Some carry a boolean instead — the fruit is either on the plant or it is
     * not, and the growth counter, where there is one, measures something else entirely.</p>
     */
    private record Ripeness(
            net.minecraft.world.level.block.state.properties.BooleanProperty flag,
            boolean bearingValue,
            net.minecraft.world.level.block.state.properties.IntegerProperty age,
            int ripeAge) {

        static Ripeness always() {
            return new Ripeness(null, false, null, 0);
        }

        boolean bearing(BlockState state) {
            if (flag != null) {
                return state.getValue(flag) == bearingValue;
            }
            if (age != null) {
                return state.getValue(age) >= ripeAge;
            }
            return true;
        }
    }

    /** Per-block ripeness rule. Derived on first use, cleared with the rest of the cache. */
    private static final Map<Block, Ripeness> RIPENESS = new ConcurrentHashMap<>();

    /**
     * Has this block grown far enough to actually carry food?
     *
     * <p>"Fully grown" and "bearing fruit" are not the same question, and using the first for
     * the second is wrong in both directions. A sweet berry bush is picked at stage 2 of 3 —
     * treating stage 2 as unripe hides a bush that visibly has berries on it. A potato at stage
     * 0 already drops a potato in its loot table, so asking the loot table alone would start a
     * freshness clock on a seedling.</p>
     *
     * <p>So the stage is derived per block, once, from what the block actually yields:</p>
     *
     * <ul>
     *   <li>If it yields food at stage 0, the loot table says nothing about ripeness — that is
     *       the planting stock coming back. Fall back to the final stage. Covers potato, carrot,
     *       nether wart.</li>
     *   <li>Otherwise the first stage that yields food IS ripeness. Covers berry bushes at 2 of
     *       3, and wheat and beetroot at their last stage, which is the same answer as before.</li>
     * </ul>
     *
     * <p>Derived from the loot table rather than from a list of known blocks, so a fruiting
     * plant from any mod is handled without naming it.</p>
     *
     * <p><b>A boolean is checked before any of that</b>, because for some plants the stage
     * counter is not the ripeness at all. Vanilla cave vines are the case that proved it: the
     * head block carries {@code age} 0..25, which is how far the vine has GROWN DOWNWARD, while
     * the berries live in a separate {@code berries} boolean. Reading the counter as ripeness
     * was wrong in both directions at once — a vine with berries on it at age 3 answered "not
     * ripe" and was never tracked, so its glow berries came out with no freshness, and a bare
     * vine that had finished growing answered "ripe" and was tracked as though it were carrying
     * fruit. {@code cave_vines_plant}, the body segments, has no counter at all, so every
     * segment in a lush cave counted as bearing berries.</p>
     */
    public static boolean bearsFoodYet(BlockState state, ServerLevel world, BlockPos pos) {
        return ripenessOf(state, world, pos).bearing(state);
    }

    /**
     * The block's ripeness rule, derived once and cached.
     *
     * <p>Derivation asks the loot table which property actually changes the answer, rather than
     * trusting a property's name — {@code berries} happens to be well named, but nothing
     * guarantees the next mod's will be.</p>
     */
    private static Ripeness ripenessOf(BlockState state, ServerLevel world, BlockPos pos) {
        Ripeness known = RIPENESS.get(state.getBlock());
        if (known != null) {
            return known;
        }
        Ripeness derived = deriveRipeness(state, world, pos);
        // Pass 633 (Lens 3 — cache correctness): the old guard silently dropped the new entry
        // when the cache was full, and the comment at line 33 claimed 'clears the whole map'
        // — the code did NOT clear. With 2048+ unique block types (a modded pack with many
        // crops), ripenessOf re-derived from scratch every call instead of caching. Same
        // pattern Pass 604 fixed for HudTextCache and FORMAT_TIME_CACHE. Now: when full and
        // the key is new, evict one arbitrary entry so the new one has room. The new entry
        // is the one currently being asked for, so it is the most useful to keep.
        if (RIPENESS.size() < MAX_CACHE_SIZE) {
            RIPENESS.put(state.getBlock(), derived);
        } else {
            var iter = RIPENESS.keySet().iterator();
            if (iter.hasNext()) {
                iter.next();
                iter.remove();
            }
            RIPENESS.put(state.getBlock(), derived);
        }
        return derived;
    }

    private static Ripeness deriveRipeness(BlockState state, ServerLevel world, BlockPos pos) {
        // A boolean that flips whether the block yields food IS the ripeness, whatever else the
        // block happens to count. Checked first for exactly that reason: cave vines have both,
        // and the counter is the wrong answer.
        try {
            for (net.minecraft.world.level.block.state.properties.Property<?> p : state.getProperties()) {
                if (!(p instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bp)) {
                    continue;
                }
                boolean yieldsWhenTrue = lootFoodDrop(state.setValue(bp, Boolean.TRUE), world, pos) != null;
                boolean yieldsWhenFalse = lootFoodDrop(state.setValue(bp, Boolean.FALSE), world, pos) != null;
                if (yieldsWhenTrue != yieldsWhenFalse) {
                    return new Ripeness(bp, yieldsWhenTrue, null, 0);
                }
            }
        } catch (Exception e) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                    "DynamicFoodBlockCache: could not probe fruit flags for "
                            + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ": " + e);
        }

        net.minecraft.world.level.block.state.properties.IntegerProperty age =
                com.spoilageenhanced.util.FoodSpoilageUtil.growthProperty(state);
        if (age == null) {
            return Ripeness.always();
        }
        return new Ripeness(null, false, age, computeRipeAge(state, world, pos, age));
    }

    /**
     * Whether this block decides ripeness by a flag rather than by a growth stage.
     *
     * <p>Callers that reason about growth stages need this, because for a flag plant those
     * questions have no meaningful answer: there is no "half grown" berry, only a vine that is
     * carrying some or is not.</p>
     */
    public static boolean ripensByFlag(BlockState state, ServerLevel world, BlockPos pos) {
        return ripenessOf(state, world, pos).flag() != null;
    }

    /**
     * What food this exact state yields, straight from the loot table. No cache, no config.
     *
     * <p>Both of those would defeat the per-stage probe below: the cache is keyed by block, so
     * the first stage tested would answer for all of them, and the config maps whole blocks
     * (sweet_berry_bush -> sweet_berries) regardless of stage, which would report berries on a
     * bare bush.</p>
     */
    private static String lootFoodDrop(BlockState state, ServerLevel world, BlockPos pos) {
        ItemStack fortuneTool = new ItemStack(Items.DIAMOND_AXE);
        for (int attempt = 0; attempt < 2; attempt++) {
            LootParams.Builder builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withParameter(LootContextParams.TOOL, attempt == 0 ? ItemStack.EMPTY : fortuneTool);

            List<ItemStack> drops = state.getDrops(builder);
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                Item item = drop.getItem();
                if (SpoilageConfig.getInstance().isSpoilable(item) || drop.has(DataComponents.FOOD)) {
                    return BuiltInRegistries.ITEM.getKey(item).toString();
                }
            }
        }
        return null;
    }

    private static int computeRipeAge(BlockState state, ServerLevel world, BlockPos pos,
            net.minecraft.world.level.block.state.properties.IntegerProperty age) {
        int max = 0;
        for (Integer v : age.getPossibleValues()) {
            if (v != null && v > max) max = v;
        }
        try {
            if (lootFoodDrop(state.setValue(age, 0), world, pos) != null) {
                return max;
            }
            for (int i = 1; i <= max; i++) {
                if (lootFoodDrop(state.setValue(age, i), world, pos) != null) {
                    return i;
                }
            }
        } catch (Exception e) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                    "DynamicFoodBlockCache: could not derive ripe age for "
                            + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ": " + e);
        }
        return max;
    }

    public static String getFoodDropIgnoringGrowth(BlockState state, ServerLevel world, BlockPos pos) {
        // Pass 536 (L3 — cache correctness): an excluded block is not a food source on this
        // path either. getFoodDrop checks excluded_blocks before its lookup, but this variant
        // went straight to the config/self-item/loot-table chain, so breaking an unripe crop
        // of a block the player excluded (BlockDropSpoilageHandler.before's isBarelyGrown
        // branch) still resolved its self-item and stamped the drop ROTTEN — the exclusion
        // was honoured for a ripe plant and ignored for a seedling of the same block.
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (SpoilageConfig.getInstance().getExcludedBlockSet().contains(blockId)) {
            return null;
        }
        Block block = state.getBlock();
        String cached = CACHE.get(block);
        if (cached != null) {
            return cached.equals(NO_FOOD_DROP) ? null : cached;
        }

        String configuredDrop = SpoilageConfig.getInstance().getTrackedBlockDropItem(blockId);
        if (configuredDrop != null) {
            putWithEviction(block, configuredDrop);
            return configuredDrop;
        }

        Item selfItem = block.asItem();
        if (selfItem != null && selfItem != Items.AIR && SpoilageConfig.getInstance().isSpoilable(selfItem)) {
            String itemId = BuiltInRegistries.ITEM.getKey(selfItem).toString();
            putWithEviction(block, itemId);
            return itemId;
        }

        try {
            String fromLoot = lootFoodDrop(state, world, pos);
            if (fromLoot != null) {
                putWithEviction(block, fromLoot);
                SpoilageEnhancedLogger.log("DynamicFoodBlockCache: Discovered spoilable drop " + fromLoot + " for block " + blockId);
                return fromLoot;
            }
        } catch (Exception e) {
            // Pass 128 (Lens 1): the old empty catch silently swallowed exceptions from
            // state.getDrops() (e.g. world shutting down, block entity unloaded, malformed
            // loot table). This caused the cache to store NO_FOOD_DROP for a block that
            // might actually be spoilable, permanently poisoning the cache entry until
            // the cap was exceeded and the map cleared. Log the exception so it's visible
            // in the trace log without crashing the server.
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                    "DynamicFoodBlockCache: Exception discovering drops for " + blockId + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        putWithEviction(block, NO_FOOD_DROP);
        return null;
    }

    private static void putWithEviction(Block block, String value) {
        // If already present, just update the value (no SIZE change).
        if (CACHE.get(block) != null) {
            CACHE.replace(block, value);
            return;
        }

        // Pass 108 (Lens 8): the LRU deque is gone (see the CACHE field comment). The size cap
        // stays as a safety valve: if a pathological modded pack somehow exceeds 2,048 distinct
        // queried block types, clear the whole map — correct (every entry re-computes on demand)
        // and vanishingly rare, since vanilla has 1,196 blocks total.
        // Pass 96 (Lens 12) putIfAbsent guard retained: when two threads both see "not present",
        // only one wins and only one SIZE.incrementAndGet runs.
        if (SIZE.get() >= MAX_CACHE_SIZE) {
            CACHE.clear();
            SIZE.set(0);
        }

        String prior = CACHE.putIfAbsent(block, value);
        if (prior == null) {
            // We won the race — record the new entry.
            SIZE.incrementAndGet();
        } else {
            // Lost the race — another thread already inserted. Update the value;
            // SIZE is correct because the other thread already incremented it.
            CACHE.replace(block, value);
        }
    }

    /**
     * Learns "this block yields this food" from a harvest that actually happened.
     *
     * <p>Every other discovery path asks the loot table, and a plant that is picked by hand
     * never appears in one. MegaCookery's apple tree is the case that exposed this: its loot
     * table lists leaves, a sapling and sticks, and the apple exists only inside
     * {@code useWithoutItem}, which pops it and winds the branch back from fruiting to
     * flowering. Nothing readable said "apple", so the tree was never tracked, and the apple it
     * handed over got no freshness at all while a vanilla berry bush worked perfectly.</p>
     *
     * <p>So the mapping is taken from the event instead of from a table: the player interacted
     * with a block, and a spoilable item came out of that exact position. That is the same
     * evidence a loot table would have given, only observed rather than declared, and it needs
     * no mod to be named anywhere.</p>
     *
     * <p>The guards are what keep this from learning nonsense:</p>
     *
     * <ul>
     *   <li><b>No block entities.</b> A container hands back what was put into it — taking a
     *       cooked porkchop off a campfire would otherwise teach us that campfires grow pork.
     *       A fruiting plant is a plain block; a store of other people's food is not.</li>
     *   <li><b>The item must appear at the block.</b> {@code popResource} spawns at the block
     *       being used, so anything landing elsewhere is a different event that merely overlaps
     *       in time.</li>
     *   <li><b>The block must still be the one that was used</b>, and must not already have an
     *       answer — a known mapping is never overwritten by an observation.</li>
     * </ul>
     *
     * <p>Written through to the config as well as the cache, so it survives a restart and shows
     * up as an ordinary {@code tracked_blocks} line the player can see and {@code excluded_blocks}
     * can override.</p>
     *
     * @return true when a new mapping was recorded
     */
    public static boolean learnFoodDropFromInteraction(BlockState usedState, BlockPos usedPos,
            BlockPos dropPos, ServerLevel world, String itemId) {
        if (usedState == null || usedPos == null || dropPos == null || itemId == null) {
            return false;
        }
        if (!usedPos.equals(dropPos)) {
            return false;
        }
        if (usedState.hasBlockEntity() || usedState.isAir()) {
            return false;
        }
        // The block has to still be the one that was interacted with. A harvest that replaces
        // the block with something else is a different event, and the state we were handed is
        // the pre-interaction snapshot, not what is standing there now.
        if (!world.getBlockState(usedPos).is(usedState.getBlock())) {
            return false;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(usedState.getBlock()).toString();
        if (SpoilageConfig.getInstance().getExcludedBlockSet().contains(blockId)) {
            return false;
        }
        // Only fill a genuine gap. getFoodDropIgnoringGrowth is the right question here rather
        // than getFoodDrop: a ripe-stage check would answer "no food" for a plant that is simply
        // between harvests, and we would relearn the same mapping on every pick.
        if (getFoodDropIgnoringGrowth(usedState, world, usedPos) != null) {
            return false;
        }

        if (!SpoilageConfig.getInstance().registerTrackedBlock(blockId, itemId, true)) {
            return false;
        }
        // registerFoodDrop replaces the NO_FOOD_DROP sentinel the failed lookup just cached.
        registerFoodDrop(usedState, itemId);
        SpoilageEnhancedLogger.log("DynamicFoodBlockCache: learned " + blockId + " -> " + itemId
                + " from a harvest at " + usedPos + " (not in its loot table)");
        return true;
    }

    public static void registerFoodDrop(BlockState state, String itemId) {
        if (state != null && itemId != null) {
            putWithEviction(state.getBlock(), itemId);
            SpoilageEnhancedLogger.log("DynamicFoodBlockCache: Explicitly registered food drop " + itemId + " for block " + state.getBlock());
        }
    }

    public static void clear() {
        CACHE.clear();
        SIZE.set(0);
        // The ripeness rules are derived from loot tables and from what counts as spoilable, and
        // both change on a datapack or config reload. Leaving them behind would keep answering
        // from the old world's rules.
        RIPENESS.clear();
    }

    /**
     * Returns current cache size for diagnostics.
     */
    public static int size() {
        return SIZE.get();
    }
}
