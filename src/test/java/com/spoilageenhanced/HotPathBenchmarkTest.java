package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Baseline benchmark for FoodSpoilageUtil hot paths.
 *
 * <p>Targets (Lens 8 / 11 / 13):
 * <ul>
 *   <li>{@link FoodSpoilageUtil#extractWorstItems} - allocates source + target ArrayLists per call,
 *       uses O(n²) findMinIndex removal.</li>
 *   <li>{@link FoodSpoilageUtil#extractBestItems} - same O(n²) pattern.</li>
 *   <li>{@link FoodSpoilageUtil#mergeItems} - sorts both lists O(n log n), boxes Long.</li>
 *   <li>{@link FoodSpoilageUtil#getWorstState}/{@link FoodSpoilageUtil#getBestState} - called per render/tooltip tick.</li>
 *   <li>{@link SpoilageConfig#isSpoilable} - called on every hopper transfer.</li>
 * </ul>
 *
 * The harness measures wall-clock time of N repeated invocations after JIT warmup.
 * Output is human-readable so we can paste concrete numbers into NIGHT_REPORT.md.
 */
public class HotPathBenchmarkTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        // Bind item components the same way ModFeatureSimulationTest does — without this,
        // item.components() throws on every isSpoilable() call, which measures the unbound
        // exception path (16us) instead of the real in-game cached path.
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    /**
     * Build a SpoilageData with `count` items, half fresh, half stale, no rotten.
     */
    private static SpoilageData buildMixed(int count, long baseTime) {
        List<Long> fresh = new ArrayList<>(count / 2);
        List<Long> stale = new ArrayList<>(count / 2);
        for (int i = 0; i < count / 2; i++) {
            fresh.add(baseTime + 1000L + i);
            stale.add(baseTime - 500L + i);
        }
        return new SpoilageData(fresh, stale, 0, 1.0);
    }

    private static SpoilageData buildAllFresh(int count, long baseTime) {
        List<Long> fresh = new ArrayList<>(count);
        for (int i = 0; i < count; i++) fresh.add(baseTime + 1000L + i);
        return new SpoilageData(fresh, Collections.emptyList(), 0, 1.0);
    }

    /**
     * Helper to run an operation N times after JIT warmup and report wall-clock time.
     */
    private static long timeOp(int warmup, int iterations, Runnable op) {
        // Warmup phase - lets HotSpot JIT compile and inline
        for (int i = 0; i < warmup; i++) op.run();

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) op.run();
        long end = System.nanoTime();
        return TimeUnit.NANOSECONDS.toMicros(end - start);
    }

    @Test
    void benchmarkExtractWorstItems_64items() {
        SpoilageData source = buildMixed(64, 100_000L);
        // Warm up 1000x, then measure 1000x
        long micros = timeOp(1000, 1000, () -> FoodSpoilageUtil.extractWorstItems(source, 1));
        System.out.println("[BENCH] extractWorstItems(64-item stack, take 1 worst): "
                + (micros / 1000.0) + " us/call  total=" + micros + " us / 1000 iters");
    }

    @Test
    void benchmarkExtractWorstItems_1000items() {
        SpoilageData source = buildMixed(1000, 100_000L);
        long micros = timeOp(500, 500, () -> FoodSpoilageUtil.extractWorstItems(source, 1));
        System.out.println("[BENCH] extractWorstItems(1000-item stack, take 1 worst): "
                + (micros / 500.0) + " us/call  total=" + micros + " us / 500 iters");
    }

    @Test
    void benchmarkExtractBestItems_64items() {
        SpoilageData source = buildMixed(64, 100_000L);
        long micros = timeOp(1000, 1000, () -> FoodSpoilageUtil.extractBestItems(source, 1));
        System.out.println("[BENCH] extractBestItems(64-item stack, take 1 best): "
                + (micros / 1000.0) + " us/call  total=" + micros + " us / 1000 iters");
    }

    @Test
    void benchmarkExtractWorstItems_drainEntireStack_64items() {
        SpoilageData source = buildMixed(64, 100_000L);
        // Hopper transfer path: drain entire 64-item stack into an empty slot
        long micros = timeOp(1000, 1000, () -> FoodSpoilageUtil.extractWorstItems(source, 64));
        System.out.println("[BENCH] extractWorstItems(64-item stack, drain all 64 worst): "
                + (micros / 1000.0) + " us/call  total=" + micros + " us / 1000 iters");
    }

    @Test
    void benchmarkMergeItems_64plus64() {
        SpoilageData a = buildMixed(64, 100_000L);
        SpoilageData b = buildMixed(64, 110_000L);
        // Pass 82: defensive-copy elimination. Long warmup so JIT compiles + inlines.
        long micros = timeOp(50000, 200000, () -> FoodSpoilageUtil.mergeItems(a, b));
        System.out.println("[BENCH] mergeItems(64-item + 64-item) (Pass 82, long warmup): "
                + (micros / 200000.0) + " us/call  total=" + micros + " us / 200000 iters");
    }

    @Test
    void benchmarkIsSpoilable_apple() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // First call populates cache, subsequent calls hit cache - measure hot path
        config.isSpoilable(Items.APPLE); // prime
        long micros = timeOp(10000, 100000, () -> config.isSpoilable(Items.APPLE));
        System.out.println("[BENCH] isSpoilable(apple) cached: "
                + (micros / 100000.0) + " us/call  total=" + micros + " us / 100000 iters");
    }

    @Test
    void benchmarkIsSpoilable_cacheMiss() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        config.clearCache();
        // First call hits cache miss path - measure cold path
        long micros = timeOp(100, 100, () -> {
            config.clearCache();
            config.isSpoilable(Items.APPLE);
        });
        System.out.println("[BENCH] isSpoilable(apple) cold (cache miss + lookup): "
                + (micros / 100.0) + " us/call  total=" + micros + " us / 100 iters");
    }

    @Test
    void benchmarkIsSpoilable_unboundComponents() {
        // Pass 600 (L1 — silent failure): when item.components() throws (components unbound),
        // the additional/duration checks below are config-only and their answers are final.
        // Before the fix, unboundOut stayed true and the answer was never cached, so every
        // call re-threw and re-looked-up. After the fix, the answer is cached and subsequent
        // calls hit the cache. Measure the repeated-call cost to confirm the fix.
        SpoilageConfig config = SpoilageConfig.getInstance();
        // The test bootstrap binds all items, so the throw path cannot be reproduced here.
        // What CAN be measured is the cached path the fix enables: before the fix, an item
        // answered from additional_tracked_items / item_durations with unbound components
        // never entered the cache, so every call paid the full compute (throw + set
        // lookups). After the fix it is cached after the first call. Measure the repeated
        // call cost on the cached path for an item that resolves through item_durations.
        config.clearCache();
        // Prime: first call computes and caches
        config.isSpoilable(Items.APPLE);
        // Measure repeated calls (should be cache hits)
        long micros = timeOp(10000, 100000, () -> config.isSpoilable(Items.APPLE));
        System.out.println("[BENCH] isSpoilable(apple) repeated (cached after fix): "
                + (micros / 100000.0) + " us/call  total=" + micros + " us / 100000 iters");
    }

    @Test
    void benchmarkGetFreshDurationForItem() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Warm the cache first (Pass 76 added a per-Item base-duration cache).
        config.getFreshDurationForItem(Items.APPLE);
        // Long warmup to push the call through C2 compilation reliably
        long micros = timeOp(50000, 200000, () -> config.getFreshDurationForItem(Items.APPLE));
        System.out.println("[BENCH] getFreshDurationForItem(apple) (Pass 76 cached, long warmup): "
                + (micros / 200000.0) + " us/call  total=" + micros + " us / 200000 iters");
    }

    @Test
    void benchmarkGetFreshDurationForItem_cold() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        // Force cache miss each time to measure the slow String.contains chain path.
        long micros = timeOp(100, 100, () -> {
            SpoilageConfig.clearDurationCache();
            config.getFreshDurationForItem(Items.APPLE);
        });
        System.out.println("[BENCH] getFreshDurationForItem(apple) cold (cache miss + chain): "
                + (micros / 100.0) + " us/call  total=" + micros + " us / 100 iters");
    }

    @Test
    void benchmarkGetFreshAndStaleDuration_mixed_items() {
        // Simulates 1,000-item inventory tick: each item asks both fresh + stale. With cache,
        // each lookup is a single CHM hit. Cold path is the ~40-branch String.contains chain.
        SpoilageConfig config = SpoilageConfig.getInstance();
        SpoilageConfig.clearDurationCache();
        // Warm the cache for several items
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            config.getFreshDurationForItem(ref.value());
        }
        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            config.getFreshDurationForItem(Items.APPLE);
            config.getStaleDurationForItem(Items.APPLE);
        }
        long micros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
        System.out.println("[BENCH] 10000 * (fresh+stale) for apple (Pass 76 cached): "
                + (micros / 10000.0) + " us/pair  total=" + micros + " us / 10000 pairs");
    }

    @Test
    void benchmarkExtractWorstItems_stress1k_transfers() {
        // Simulate 1,000 hopper transfers, each moving 1 item from a 64-item stack
        SpoilageData source = buildMixed(64, 100_000L);
        SpoilageData current = source;
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(current, 1);
            current = split[0]; // remaining
            if (current.totalTracked() == 0) current = source; // reset for sustained measurement
        }
        long micros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
        System.out.println("[BENCH] 1000 hopper transfers (1-item from 64-stack): total="
                + micros + " us (" + (micros / 1000.0) + " us/transfer)");
    }

    /**
     * Pure-logic tick. Simulates the "no items expired" steady-state path that dominates
     * inventory ticks (most items don't actually expire on any given 20-tick scan).
     */
    @Test
    void benchmarkUpdateSpoilageData_steadyState_64items() {
        SpoilageData source = buildAllFresh(64, 100_000L);
        // currentTime is far before any expiration, so no items transition.
        long micros = timeOp(1000, 1000, () -> FoodSpoilageUtil.updateSpoilageData(source, 64, 50_000L, 24000L, 24000L, 1.0));
        System.out.println("[BENCH] updateSpoilageData(steady-state, 64 items, no expirations): "
                + (micros / 1000.0) + " us/call  total=" + micros + " us / 1000 iters");
    }

    /**
     * 1,000 stacked ticks simulating an inventory full of food items being scanned.
     */
    @Test
    void benchmarkUpdateSpoilageData_1000items_steadyState() {
        SpoilageData source = buildAllFresh(1000, 100_000L);
        long micros = timeOp(500, 500, () -> FoodSpoilageUtil.updateSpoilageData(source, 1000, 50_000L, 24000L, 24000L, 1.0));
        System.out.println("[BENCH] updateSpoilageData(steady-state, 1000 items): "
                + (micros / 500.0) + " us/call  total=" + micros + " us / 500 iters");
    }

    /**
     * Tick where items DO expire (currentTime is past half the entries). This is the worst case
     * that the original loop turned into O(n^2) with the rewind-to-head trick.
     */
    @Test
    void benchmarkGetWorstAndBestState() {
        // Build a real ItemStack with attached SpoilageData to measure the hot path
        // called on every frame during tooltip and durability bar rendering.
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(Items.APPLE, 64);
        SpoilageData data = buildMixed(64, 100_000L);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        // Long warmup so C2 compiler kicks in
        long micros = timeOp(50000, 200000, () -> {
            FoodSpoilageUtil.getWorstState(stack);
            FoodSpoilageUtil.getBestState(stack);
        });
        System.out.println("[BENCH] (getWorstState + getBestState) for 64-item stack: "
                + (micros / 200000.0) + " us/pair  total=" + micros + " us / 200000 pairs");
    }

    @Test
    void benchmarkGetWorstAndBestTimestamp() {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(Items.APPLE, 64);
        SpoilageData data = buildMixed(64, 100_000L);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        long micros = timeOp(50000, 200000, () -> {
            FoodSpoilageUtil.getWorstTimestamp(stack, FoodSpoilageUtil.SpoilageState.FRESH);
            FoodSpoilageUtil.getBestTimestamp(stack, FoodSpoilageUtil.SpoilageState.FRESH);
        });
        System.out.println("[BENCH] (getWorstTimestamp + getBestTimestamp) for 64-item stack: "
                + (micros / 200000.0) + " us/pair  total=" + micros + " us / 200000 pairs");
    }

    @Test
    void benchmarkUpdateContainerItemSpoilage_shulker9() {
        // Simulate a shulker box with 9 items being ticked every 20 ticks. The Pass 78 change
        // moves from stream+collect(ArrayList::new) per call to a reused thread-local
        // NonNullList + ItemContainerContents.copyInto() (no stream pipeline, no Optional wrap).
        net.minecraft.world.item.ItemStack shulker = new net.minecraft.world.item.ItemStack(Items.APPLE, 1);
        java.util.List<net.minecraft.world.item.ItemStack> contents = new java.util.ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack apple = new net.minecraft.world.item.ItemStack(Items.APPLE, 64);
            apple.set(ModDataComponentTypes.SPOILAGE, buildAllFresh(64, 100_000L));
            contents.add(apple);
        }
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));

        long micros = timeOp(5000, 50000, () -> FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null));
        System.out.println("[BENCH] updateContainerItemSpoilage(shulker with 9 items) (Pass 78): "
                + (micros / 50000.0) + " us/call  total=" + micros + " us / 50000 iters");
    }

    @Test
    void benchmarkUpdateContainerItemSpoilage_shulker27() {
        // 27-slot container (double chest equivalent). Heavier case.
        net.minecraft.world.item.ItemStack shulker = new net.minecraft.world.item.ItemStack(Items.APPLE, 1);
        java.util.List<net.minecraft.world.item.ItemStack> contents = new java.util.ArrayList<>(27);
        for (int i = 0; i < 27; i++) {
            net.minecraft.world.item.ItemStack apple = new net.minecraft.world.item.ItemStack(Items.APPLE, 64);
            apple.set(ModDataComponentTypes.SPOILAGE, buildAllFresh(64, 100_000L));
            contents.add(apple);
        }
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));

        long micros = timeOp(5000, 50000, () -> FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null));
        System.out.println("[BENCH] updateContainerItemSpoilage(shulker with 27 items) (Pass 78): "
                + (micros / 50000.0) + " us/call  total=" + micros + " us / 50000 iters");
    }

    @Test
    void benchmarkUpdateContainerItemSpoilage_shulkerNoSpoilable() {
        // Pass 624 (Lens 4): fast-path for containers with NO spoilable items.
        // A shulker full of cobblestone (non-spoilable) should bail before the 256-slot copy.
        net.minecraft.world.item.ItemStack shulker = new net.minecraft.world.item.ItemStack(Items.APPLE, 1);
        java.util.List<net.minecraft.world.item.ItemStack> contents = new java.util.ArrayList<>(27);
        for (int i = 0; i < 27; i++) {
            contents.add(new net.minecraft.world.item.ItemStack(Items.COBBLESTONE, 64));
        }
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));

        long micros = timeOp(5000, 50000, () -> FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null));
        System.out.println("[BENCH] updateContainerItemSpoilage(shulker with 27 NON-spoilable items) (Pass 624): "
                + (micros / 50000.0) + " us/call  total=" + micros + " us / 50000 iters");
    }

    @Test
    void benchmarkContainerCopy_streamVsCopyInto() {
        // Isolates the copy-out step so the JIT measures the allocation pattern directly,
        // not the full updateSpoilage path (which requires a non-null Level).
        net.minecraft.world.item.ItemStack shulker = new net.minecraft.world.item.ItemStack(Items.APPLE, 1);
        java.util.List<net.minecraft.world.item.ItemStack> contents = new java.util.ArrayList<>(27);
        // Apple has max stack 64 so we don't go higher; ItemStackTemplate.create clamps to 1,
        // so we just need 27 *present* slots, not stacks of 64.
        for (int i = 0; i < 27; i++) {
            contents.add(new net.minecraft.world.item.ItemStack(Items.APPLE));
        }
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));
        var container = shulker.get(net.minecraft.core.component.DataComponents.CONTAINER);

        // Old path: stream + collect ArrayList::new (allocates per call)
        long microsOld = timeOp(5000, 50000, () -> {
            java.util.List<net.minecraft.world.item.ItemStack> items = container.allItemsCopyStream()
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            // touch the list so JIT can't elide it
            if (items.isEmpty()) throw new IllegalStateException();
        });
        System.out.println("[BENCH] container copy via stream().collect(ArrayList::new) [old]: "
                + (microsOld / 50000.0) + " us/call  total=" + microsOld + " us / 50000 iters");

        // New path: ItemContainerContents.copyInto(pre-sized NonNullList). copyInto only fills
        // slots that already exist in the destination, so the scratch list must be pre-sized
        // (withSize(256, EMPTY)) and NOT cleared — copyInto overwrites every slot in place.
        net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> scratch =
                net.minecraft.core.NonNullList.withSize(256, net.minecraft.world.item.ItemStack.EMPTY);
        long microsNew = timeOp(5000, 50000, () -> {
            container.copyInto(scratch);
            if (scratch.isEmpty()) throw new IllegalStateException();
        });
        System.out.println("[BENCH] container copy via copyInto(pre-sized NonNullList) [Pass 78]: "
                + (microsNew / 50000.0) + " us/call  total=" + microsNew + " us / 50000 iters");
    }

    @Test
    void benchmarkExtractWorstItems_64items_scratchPool() {
        // Pass 79: extractWorstItems/extractBestItems now reuse a ThreadLocal<SpoilageData[2]>
        // via borrowScratch() instead of allocating a fresh SpoilageData[2] per call. Mixin sites
        // (Hopper, ItemEntity merge, PlayerInventory pickup, etc.) call these per inventory /
        // merge interaction — eliminating the per-call array allocation adds up at 1,000+ ops/s.
        SpoilageData source = buildMixed(64, 100_000L);
        long micros = timeOp(50000, 200000, () -> FoodSpoilageUtil.extractWorstItems(source, 1));
        System.out.println("[BENCH] extractWorstItems(64, take 1) (Pass 79 scratch pool): "
                + (micros / 200000.0) + " us/call  total=" + micros + " us / 200000 iters");
    }

    @Test
    void benchmarkExtractWorstItems_64items_drain_scratchPool() {
        SpoilageData source = buildMixed(64, 100_000L);
        long micros = timeOp(10000, 100000, () -> FoodSpoilageUtil.extractWorstItems(source, 64));
        System.out.println("[BENCH] extractWorstItems(64, drain 64) (Pass 79 scratch pool): "
                + (micros / 100000.0) + " us/call  total=" + micros + " us / 100000 iters");
    }

    @Test
    void benchmarkHopperGuard_isSpoilable_vs_hasComponent() {
        // Pass 83: HopperBlockEntityMixin.onTryMoveInItem calls isSpoilable() (CHM get) on every
        // hopper transfer attempt, even for non-food items. Both branches below it immediately
        // check itemStack.get(SPOILAGE) != null. Reordering to check has(SPOILAGE) first skips
        // the CHM lookup for stacks with no spoilage data (the common case for non-food items).
        SpoilageConfig config = SpoilageConfig.getInstance();
        config.isSpoilable(Items.APPLE); // prime cache

        // Food item WITH spoilage component (worst case: both checks needed)
        net.minecraft.world.item.ItemStack apple = new net.minecraft.world.item.ItemStack(Items.APPLE, 64);
        apple.set(ModDataComponentTypes.SPOILAGE, buildAllFresh(64, 100_000L));

        // Non-food item WITHOUT spoilage component (the common hopper case)
        net.minecraft.world.item.ItemStack stone = new net.minecraft.world.item.ItemStack(Items.STONE, 64);

        long microsIsSpoilableApple = timeOp(50000, 200000, () -> config.isSpoilable(Items.APPLE));
        System.out.println("[BENCH] isSpoilable(apple) cached [guard cost]: "
                + (microsIsSpoilableApple / 200000.0) + " us/call");

        long microsIsSpoilableStone = timeOp(50000, 200000, () -> config.isSpoilable(Items.STONE));
        System.out.println("[BENCH] isSpoilable(stone) cached [guard cost, non-food]: "
                + (microsIsSpoilableStone / 200000.0) + " us/call");

        long microsHasApple = timeOp(50000, 200000, () -> apple.has(ModDataComponentTypes.SPOILAGE));
        System.out.println("[BENCH] apple.has(SPOILAGE) [component-map lookup]: "
                + (microsHasApple / 200000.0) + " us/call");

        long microsHasStone = timeOp(50000, 200000, () -> stone.has(ModDataComponentTypes.SPOILAGE));
        System.out.println("[BENCH] stone.has(SPOILAGE) [component-map lookup, non-food]: "
                + (microsHasStone / 200000.0) + " us/call");
    }

    @Test
    void benchmarkHopperTickPath_cumulative() {
        // Pass 100: end-to-end measurement of the hopper transfer path that Passes 75-99
        // collectively optimized. Compares the realistic case (1 hopper tick processing 1
        // item with 1-item transfer from a 64-stack source to a 64-stack target) against
        // the simpler "no work needed" case (1 hopper tick on a non-food item).
        SpoilageConfig config = SpoilageConfig.getInstance();
        config.isSpoilable(Items.APPLE);
        config.isSpoilable(Items.STONE);

        // 64-stack food source + empty target (worst case — the full extract/merge path runs)
        net.minecraft.world.item.ItemStack sourceFood = new net.minecraft.world.item.ItemStack(Items.APPLE, 64);
        sourceFood.set(ModDataComponentTypes.SPOILAGE, buildAllFresh(64, 100_000L));
        net.minecraft.world.item.ItemStack targetEmpty = new net.minecraft.world.item.ItemStack(Items.AIR, 0);
        // 64-stack stone (no spoilage data — the guard short-circuits)
        net.minecraft.world.item.ItemStack stone = new net.minecraft.world.item.ItemStack(Items.STONE, 64);

        // Measure the guard cost on a non-food item: the isSpoilable call that USED to be
        // a CHM get per hopper transfer (before Pass 83). Now the hasNonDefault short-circuits
        // before isSpoilable ever runs.
        long microsGuardNonFood = timeOp(50000, 200000, () -> {
            if (stone.hasNonDefault(ModDataComponentTypes.SPOILAGE)) throw new IllegalStateException("stone should not have SPOILAGE");
            if (config.isSpoilable(Items.STONE)) throw new IllegalStateException("stone should not be spoilable");
        });
        System.out.println("[BENCH] hopper guard for non-food item (Pass 99 hasNonDefault skips isSpoilable): "
                + (microsGuardNonFood / 200000.0) + " us/call");

        // Measure the guard cost on a food item with data: both checks needed
        long microsGuardFood = timeOp(50000, 200000, () -> {
            if (!sourceFood.hasNonDefault(ModDataComponentTypes.SPOILAGE)) throw new IllegalStateException("apple should have SPOILAGE");
            if (!config.isSpoilable(Items.APPLE)) throw new IllegalStateException("apple should be spoilable");
        });
        System.out.println("[BENCH] hopper guard for food item (both checks): "
                + (microsGuardFood / 200000.0) + " us/call");
    }

    @Test
    void benchmarkStackHas_vsHasNonDefault() {
        // Pass 99: ItemStack.has(type) delegates to DataComponentMap.has -> get(type) != null,
        // and PatchedDataComponentMap.get(type) does patch-then-prototype lookups. For a
        // mod-added component like SPOILAGE that is never in any item prototype, the
        // prototype lookup is wasted. hasNonDefault(type) is a single patch.containsKey()
        // call and is semantically identical for SPOILAGE.
        SpoilageConfig config = SpoilageConfig.getInstance();
        net.minecraft.world.item.ItemStack apple = new net.minecraft.world.item.ItemStack(Items.APPLE, 64);
        apple.set(ModDataComponentTypes.SPOILAGE, buildAllFresh(64, 100_000L));
        net.minecraft.world.item.ItemStack stone = new net.minecraft.world.item.ItemStack(Items.STONE, 64);

        long microsHasApple = timeOp(50000, 200000, () -> apple.has(ModDataComponentTypes.SPOILAGE));
        long microsHasNonDefaultApple = timeOp(50000, 200000, () -> apple.hasNonDefault(ModDataComponentTypes.SPOILAGE));
        long microsHasStone = timeOp(50000, 200000, () -> stone.has(ModDataComponentTypes.SPOILAGE));
        long microsHasNonDefaultStone = timeOp(50000, 200000, () -> stone.hasNonDefault(ModDataComponentTypes.SPOILAGE));

        System.out.println("[BENCH] apple.has(SPOILAGE) [pass 93+ guard]: " + (microsHasApple / 200000.0) + " us/call");
        System.out.println("[BENCH] apple.hasNonDefault(SPOILAGE) [Pass 99]: " + (microsHasNonDefaultApple / 200000.0) + " us/call");
        System.out.println("[BENCH] stone.has(SPOILAGE) [pass 83 guard]: " + (microsHasStone / 200000.0) + " us/call");
        System.out.println("[BENCH] stone.hasNonDefault(SPOILAGE) [Pass 99]: " + (microsHasNonDefaultStone / 200000.0) + " us/call");
    }

    @Test
    void benchmarkSpoilageData_equals() {
        // Pass 91: updateSpoilage calls updated.equals(data) once per tick per stack to decide
        // whether to write the component back. The record's auto-generated equals delegates to
        // List.equals -> Long.equals (boxed) per element. The custom equals short-circuits on
        // rottenCount and speedMultiplier primitives first.
        SpoilageData a = buildAllFresh(64, 100_000L);
        SpoilageData b = buildAllFresh(64, 100_000L); // equal content, different instance

        // Equal case (full comparison must run)
        long microsEqual = timeOp(50000, 200000, () -> {
            if (!a.equals(b)) throw new IllegalStateException("must be equal");
        });
        System.out.println("[BENCH] SpoilageData.equals (64 items, equal) (Pass 91): "
                + (microsEqual / 200000.0) + " us/call");

        // Unequal case with different rottenCount (short-circuit on first primitive check)
        SpoilageData c = new SpoilageData(a.freshExpirations(), a.staleExpirations(), 1, 1.0);
        long microsUnequal = timeOp(50000, 200000, () -> {
            if (a.equals(c)) throw new IllegalStateException("must not be equal");
        });
        System.out.println("[BENCH] SpoilageData.equals (64 items, rottenCount differs) (Pass 91): "
                + (microsUnequal / 200000.0) + " us/call");
    }

    @Test
    void benchmarkSpoilageData_emptyListConstruction() {
        // Pass 88: the compact constructor used to allocate new ArrayList<>(emptyList) for every
        // component-less side. Now empty lists share the immutable Collections.emptyList().
        // This is the shape of all-fresh stacks (stale side empty), all-rotten stacks (both
        // empty), and SpoilageData.DEFAULT.
        List<Long> fresh = buildAllFresh(64, 100_000L).freshExpirations();

        // OLD pattern (what the pre-Pass-88 constructor did): allocate an empty ArrayList
        // wrapper for the empty side, then construct the record.
        long microsOld = timeOp(50000, 200000, () -> {
            List<Long> staleCopy = new java.util.ArrayList<>(Collections.<Long>emptyList());
            List<Long> freshCopy = new java.util.ArrayList<>(fresh);
            new SpoilageData(freshCopy, staleCopy, 0, 1.0);
        });
        System.out.println("[BENCH] SpoilageData(64 fresh, empty stale) OLD pattern (2 ArrayList copies): "
                + (microsOld / 200000.0) + " us/call");

        // NEW pattern (Pass 88): empty side shares the immutable empty list; only the
        // populated side is defensively copied.
        long microsNew = timeOp(50000, 200000, () -> {
            List<Long> freshCopy = new java.util.ArrayList<>(fresh);
            new SpoilageData(freshCopy, Collections.<Long>emptyList(), 0, 1.0);
        });
        System.out.println("[BENCH] SpoilageData(64 fresh, empty stale) NEW pattern (Pass 88): "
                + (microsNew / 200000.0) + " us/call");

        // Both empty (all-rotten stack) — now nearly free
        long microsBothEmpty = timeOp(50000, 200000, () ->
                new SpoilageData(Collections.emptyList(), Collections.emptyList(), 64, 1.0));
        System.out.println("[BENCH] new SpoilageData(both empty, 64 rotten) (Pass 88): "
                + (microsBothEmpty / 200000.0) + " us/call");
    }

    @Test
    void benchmarkSpoilageBarPixels_compute() {
        // Pass 333 (L5 — render-path): SpoilageBarPixels.compute is called per slot per frame
        // in the inventory GUI. Pass 606 replaced the int[3] return with a Result record
        // returned by value — the JIT scalarizes the three int fields into registers,
        // eliminating the allocation without the overhead of a caller-provided scratch buffer.
        long micros = timeOp(50000, 200000, () ->
                com.spoilageenhanced.client.SpoilageBarPixels.compute(21, 7, 4, 13));
        System.out.println("[BENCH] SpoilageBarPixels.compute(21 fresh, 7 stale, 4 rotten, 13 height) "
                + (micros / 200000.0) + " us/call (Pass 606 Result record)");
    }
}