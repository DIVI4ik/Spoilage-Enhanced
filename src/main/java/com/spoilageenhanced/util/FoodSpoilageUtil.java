package com.spoilageenhanced.util;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FoodSpoilageUtil {

    /**
     * Reusable scratch buffer for {@link #extractWorstItems} / {@link #extractBestItems}. The
     * methods return {@code SpoilageData[2]}, and 13+ mixin sites call them per inventory /
     * hopper / merge interaction, so pooling the backing array eliminates one allocation per
     * call. The slot order is {remaining, extracted} (matches the public method contract).
     *
     * <p>ThreadLocal because the JVM is free to run different mixin invocations on different
     * threads (chunk save worker pool, off-thread async tick threads).</p>
     */
    private static final ThreadLocal<SpoilageData[]> EXTRACT_SCRATCH = ThreadLocal.withInitial(() -> new SpoilageData[2]);

    /**
     * Returns the cached scratch array, zeroing out the references so a previous call's
     * {@code SpoilageData} references don't survive (they were written into the array in
     * extract* and could otherwise be retained one more GC cycle than necessary).
     */
    private static SpoilageData[] borrowScratch() {
        SpoilageData[] arr = EXTRACT_SCRATCH.get();
        arr[0] = null;
        arr[1] = null;
        return arr;
    }

    public static SpoilageData[] extractWorstItems(SpoilageData sourceData, int amount) {
        SpoilageData[] result = borrowScratch();
        if (sourceData == null || amount <= 0) {
            result[0] = sourceData != null ? sourceData : SpoilageData.DEFAULT;
            result[1] = SpoilageData.DEFAULT;
            return result;
        }

        // Adaptive extraction (Lens 8/13): the original version called findMinIndex (O(n)) and
        // ArrayList.remove(int) (O(n)) per extracted item, making the whole call O(n*amount)
        // with heavy array shifting. The strategy per spoilage class is now picked by shape:
        //   - drain the whole class  -> move-all, O(n), no comparisons at all
        //   - small take (amount*8 <= n) -> repeated min-scan + swap-to-end + O(1) remove
        //   - large partial take     -> one sort, O(n log n), then partition
        // Worst-first order: rotten, then stale (min expiration), then fresh (min expiration).
        //
        // NOTE (Pass 119 revert): the accessors return the record's STORED list (the compact
        // constructor copies once at construction — NOT per accessor call). Mutating the
        // accessor result would corrupt the source SpoilageData, so the explicit copy here
        // is REQUIRED. Verified by identity-hash diagnostic: two consecutive accessor calls
        // return the same list object.
        List<Long> sourceFresh = new ArrayList<>(sourceData.freshExpirations());
        List<Long> sourceStale = new ArrayList<>(sourceData.staleExpirations());
        int sourceRotten = sourceData.rottenCount();

        List<Long> targetFresh = new ArrayList<>();
        List<Long> targetStale = new ArrayList<>();
        int targetRotten = 0;

        // Pass 363 (L7 — boundary): mirror the extractBestItems guard (line 140).
        // If amount is negative (should not happen with current callers, but the method
        // is public), Math.min(sourceRotten, negative) would return negative, causing
        // sourceRotten to increase and amount to increase — a latent bug. The
        // Math.max(amount, 0) guard makes the behavior consistent with extractBestItems.
        int rottenTake = Math.min(sourceRotten, Math.max(amount, 0));
        targetRotten = rottenTake;
        sourceRotten -= rottenTake;
        amount -= rottenTake;

        amount = extractWorstFromList(sourceStale, targetStale, amount);
        amount = extractWorstFromList(sourceFresh, targetFresh, amount);

        // Source exhausted before amount reached — pad with the "never expires" sentinel so
        // the invariant extracted.totalTracked() == requested amount holds (BUG-13).
        while (amount > 0) {
            targetFresh.add(Long.MAX_VALUE);
            amount--;
        }

        SpoilageData remaining = new SpoilageData(sourceFresh, sourceStale, sourceRotten, sourceData.speedMultiplier());
        SpoilageData extracted = new SpoilageData(targetFresh, targetStale, targetRotten, sourceData.speedMultiplier());
        result[0] = remaining;
        result[1] = extracted;
        return result;
    }

    /**
     * Moves up to {@code amount} worst (smallest) entries from {@code source} into {@code target}.
     * Returns how many items are still needed after this class was drained.
     *
     * <p>List order inside a spoilage class is not load-bearing: mergeItems re-sorts, and every
     * reader uses min/max/size, so the swap-to-end trick below is safe.</p>
     */
    private static int extractWorstFromList(List<Long> source, List<Long> target, int amount) {
        int n = source.size();
        if (amount <= 0 || n == 0) return amount;
        if (amount >= n) {
            // Drain the whole class — no comparisons needed at all.
            target.addAll(source);
            source.clear();
            return amount - n;
        }
        if (amount * 8L <= n) {
            // Small take: repeated min-scan with swap-to-end + O(1) removal.
            // O(amount*n) comparisons, zero shifting, no sort.
            for (int k = 0; k < amount; k++) {
                int minIdx = findMinIndex(source);
                long minVal = source.get(minIdx);
                int last = source.size() - 1;
                if (minIdx != last) source.set(minIdx, source.get(last));
                source.remove(last);
                target.add(minVal);
            }
            return 0;
        }
        // Large partial take: one sort, then split.
        source.sort(Long::compareTo);
        target.addAll(source.subList(0, amount));
        source.subList(0, amount).clear();
        return 0;
    }

    public static SpoilageData[] extractBestItems(SpoilageData sourceData, int amount) {
        SpoilageData[] result = borrowScratch();
        if (sourceData == null || amount <= 0) {
            result[0] = sourceData != null ? sourceData : SpoilageData.DEFAULT;
            result[1] = SpoilageData.DEFAULT;
            return result;
        }

        // Mirror of extractWorstItems: best-first order is fresh (max expiration), then stale
        // (max expiration), then rotten. Same adaptive per-class strategy.
        // NOTE (Pass 119 revert): see the note in extractWorstItems — the explicit copy is
        // REQUIRED because the accessor returns the record's stored list.
        List<Long> sourceFresh = new ArrayList<>(sourceData.freshExpirations());
        List<Long> sourceStale = new ArrayList<>(sourceData.staleExpirations());
        int sourceRotten = sourceData.rottenCount();

        List<Long> targetFresh = new ArrayList<>();
        List<Long> targetStale = new ArrayList<>();
        int targetRotten = 0;

        amount = extractBestFromList(sourceFresh, targetFresh, amount);
        amount = extractBestFromList(sourceStale, targetStale, amount);

        // Pass 375 (L12 — claim drift): the Math.max(amount, 0) guard is redundant — the top
        // guard at line 140 already returns early for amount <= 0, so this code is unreachable
        // for negative amounts. Kept for defensive consistency with extractWorstItems (Pass 363).
        int rottenTake = Math.min(sourceRotten, Math.max(amount, 0));
        targetRotten = rottenTake;
        sourceRotten -= rottenTake;
        amount -= rottenTake;

        while (amount > 0) {
            targetFresh.add(Long.MAX_VALUE);
            amount--;
        }

        SpoilageData remaining = new SpoilageData(sourceFresh, sourceStale, sourceRotten, sourceData.speedMultiplier());
        SpoilageData extracted = new SpoilageData(targetFresh, targetStale, targetRotten, sourceData.speedMultiplier());
        result[0] = remaining;
        result[1] = extracted;
        return result;
    }

    /** Mirror of {@link #extractWorstFromList} for the best (largest) entries. */
    private static int extractBestFromList(List<Long> source, List<Long> target, int amount) {
        int n = source.size();
        if (amount <= 0 || n == 0) return amount;
        if (amount >= n) {
            target.addAll(source);
            source.clear();
            return amount - n;
        }
        if (amount * 8L <= n) {
            for (int k = 0; k < amount; k++) {
                int maxIdx = findMaxIndex(source);
                long maxVal = source.get(maxIdx);
                int last = source.size() - 1;
                if (maxIdx != last) source.set(maxIdx, source.get(last));
                source.remove(last);
                target.add(maxVal);
            }
            return 0;
        }
        source.sort(Long::compareTo);
        target.addAll(source.subList(n - amount, n));
        source.subList(n - amount, n).clear();
        return 0;
    }

    public static SpoilageData mergeItems(SpoilageData targetData, SpoilageData toAdd) {
        if (toAdd == null || toAdd.isEmpty()) return targetData != null ? targetData : SpoilageData.DEFAULT;
        if (targetData == null || targetData.isEmpty()) return toAdd;

        // Lens 13 (GC): the old code called targetData.freshExpirations() 3 separate times and
        // toAdd.freshExpirations() 2 times. NOTE (Pass 119 diagnostic): the accessors return
        // the record's STORED list (the compact constructor copies once at construction, NOT
        // per accessor call — verified by identity-hash diagnostic). The win here is caching
        // the 4 references in locals to avoid repeated method-call overhead, and sizing the
        // output lists exactly to avoid ArrayList growth reallocation. The lists are only
        // read here (addAll into fresh output lists), never mutated.
        //
        // Pass 160 (Lens 12 — claim drift): the old comment said "Reuse lists to avoid
        // allocations - copy only when modification is needed" but the code ALWAYS creates
        // new ArrayLists (lines 215, 219) and copies both sides via addAll. The "reuse"
        // claim was only true when one side was empty (the empty list is reused via
        // Collections.emptyList()). Corrected the comment to describe what actually happens:
        // we pre-size the output lists to avoid growth reallocation, but we do NOT reuse
        // the input lists — both are copied into new lists.
        List<Long> targetFresh = targetData.freshExpirations();
        List<Long> targetStale = targetData.staleExpirations();
        List<Long> addFresh = toAdd.freshExpirations();
        List<Long> addStale = toAdd.staleExpirations();

        List<Long> fresh = new ArrayList<>(targetFresh.size() + addFresh.size());
        fresh.addAll(targetFresh);
        fresh.addAll(addFresh);

        List<Long> stale = new ArrayList<>(targetStale.size() + addStale.size());
        stale.addAll(targetStale);
        stale.addAll(addStale);

        int rotten = targetData.rottenCount() + toAdd.rottenCount();

        // Invariant: list sizes must equal total item count.
        // Defensive padding if source data was malformed.
        int expectedTotal = targetFresh.size() + targetStale.size() + targetData.rottenCount()
                + addFresh.size() + addStale.size() + toAdd.rottenCount();
        int actualTotal = fresh.size() + stale.size() + rotten;

        while (actualTotal < expectedTotal) {
            stale.add(0L); // expired stale = worst possible
            actualTotal++;
        }

        // Sort worst-to-best (ascending expiration: min = worst, max = best)
        // This ensures extractBestItems/extractWorstItems pick correct elements
        fresh.sort(Long::compareTo);
        stale.sort(Long::compareTo);

        return new SpoilageData(fresh, stale, rotten, targetData.speedMultiplier());
    }

    public static void initializeItemSpoilage(ItemStack stack, Level world) {
        if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem()))
            return;

        // Pass 503 (L1 — silent failure): the old guard `!stack.has(SPOILAGE)` skipped
        // items that have an empty SPOILAGE component (e.g. DEFAULT data, or a count=0
        // stack that was trimmed to empty by extractWorstItems). updateSpoilage calls
        // this method for both null AND empty data, so the empty path must also be
        // re-initialized — otherwise an empty-data stack would sit untracked forever,
        // and the 20-tick scan would log no warning. Check the DATA, not just the
        // component presence, and log when we repair an empty component so a corrupt
        // save is visible in the data log instead of invisible.
        SpoilageData existing = stack.get(ModDataComponentTypes.SPOILAGE);
        boolean wasEmpty = existing != null && existing.isEmpty();
        if (existing != null && !existing.isEmpty()) return;

        long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(stack.getItem());
        // Pass 503: world can be null in unit tests; the pre-world guard clauses above
        // already returned for the null-world no-op cases, so this only runs when a
        // real initialization is needed. Guard defensively for the test path.
        long currentTime = world != null ? world.getGameTime() : 0L;
        long expirationTime = currentTime + freshDuration;

        List<Long> freshList = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) {
            freshList.add(expirationTime);
        }

        SpoilageData data = new SpoilageData(freshList, Collections.emptyList(), 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
        stack.set(ModDataComponentTypes.SPOILAGE, data);
        if (wasEmpty) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.DATA,
                    "initializeItemSpoilage: repaired empty SPOILAGE data for " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
        } else {
            SpoilageEnhancedLogger.log("Initialized fresh spoilage data for " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
        }
    }

    public static SpoilageData rescaleItemTimestamps(SpoilageData data, long currentTime, double ratio, double newMultiplier) {
        if (data == null || data.isEmpty()) return data != null ? data : SpoilageData.DEFAULT;

        // Long.MAX_VALUE is the sentinel used by extractWorstItems/extractBestItems padding
        // (BUG-13) for stack slots that have no tracked spoilage data. Treat it as
        // "essentially never expires" so rescaling can't overflow it into the past.
        final long NEVER = Long.MAX_VALUE;

        List<Long> newFresh = new ArrayList<>();
        for (long oldExp : data.freshExpirations()) {
            if (oldExp >= NEVER) {
                newFresh.add(NEVER);
                continue;
            }
            long remaining = oldExp - currentTime;
            if (remaining <= 0) {
                // Already expired — keep expired to prevent resurrection (ROTTEN/STALE -> FRESH)
                newFresh.add(oldExp);
            } else if (Double.isInfinite(ratio) || Double.isNaN(ratio) || ratio <= 0.0d || ratio > 1e15d) {
                // ratio is invalid (infinite, NaN, zero, or negative) — a corrupt save with
                // speed_multiplier=0 would otherwise make remaining * ratio = 0 and every
                // fresh item would appear to expire this tick. Clamp to NEVER so the item
                // stays fresh until the next legitimate rescale.
                newFresh.add(NEVER);
            } else if (remaining > Long.MAX_VALUE / ratio) {
                // remaining * ratio would overflow long even at moderate ratios. Clamp to NEVER
                // so the rescaled timestamp stays far in the future rather than wrapping to the
                // past (which would make a fresh item appear rotten).
                newFresh.add(NEVER);
            } else {
                long newRemaining = Math.max(1L, (long) (remaining * ratio));
                newFresh.add(currentTime + newRemaining);
            }
        }

        List<Long> newStale = new ArrayList<>();
        for (long oldExp : data.staleExpirations()) {
            if (oldExp >= NEVER) {
                newStale.add(NEVER);
                continue;
            }
            long remaining = oldExp - currentTime;
            if (remaining <= 0) {
                // Already expired — keep expired to prevent resurrection
                newStale.add(oldExp);
            } else if (Double.isInfinite(ratio) || Double.isNaN(ratio) || ratio > 1e15d) {
                newStale.add(NEVER);
            } else if (remaining > Long.MAX_VALUE / Math.max(ratio, 1e-9d)) {
                newStale.add(NEVER);
            } else {
                long newRemaining = Math.max(1L, (long) (remaining * ratio));
                newStale.add(currentTime + newRemaining);
            }
        }

        return new SpoilageData(newFresh, newStale, data.rottenCount(), newMultiplier);
    }

    public static void rescaleItemTimestamps(ItemStack stack, long currentTime, double ratio) {
        if (stack.isEmpty() || !stack.has(ModDataComponentTypes.SPOILAGE)) return;
        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) return;
        double currentMultiplier = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
        stack.set(ModDataComponentTypes.SPOILAGE, rescaleItemTimestamps(data, currentTime, ratio, currentMultiplier));
    }

    public static void makeStale(ItemStack stack, Level world) {
        if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem())) return;
        long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(stack.getItem());
        long expire = world.getGameTime() + staleDuration;

        List<Long> staleList = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) staleList.add(expire);

        SpoilageData data = new SpoilageData(Collections.emptyList(), staleList, 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
        stack.set(ModDataComponentTypes.SPOILAGE, data);
    }

    public static void updateSpoilage(ItemStack stack, Level world) {
        if (stack == null || stack.isEmpty() || world == null)
            return;

        if (stack.has(DataComponents.CONTAINER)) {
            updateContainerItemSpoilage(stack, world);
        }

        if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem()))
            return;

        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) {
            initializeItemSpoilage(stack, world);
            return;
        }

        long currentTime = world.getGameTime();
        double currentMultiplier = SpoilageConfig.getInstance().getSpoilageSpeedMultiplier();
        double itemMultiplier = data.speedMultiplier();

        if (Math.abs(itemMultiplier - currentMultiplier) > 0.0001) {
            double ratio = itemMultiplier / currentMultiplier;
            data = rescaleItemTimestamps(data, currentTime, ratio, currentMultiplier);
        }

        // Pass 90 (Lens 8): durations are fetched lazily — only when trackers are missing or a
        // fresh item actually expires. In the steady state (counts match, nothing expired)
        // both lookups were wasted CHM gets per stack per 20-tick scan.
        SpoilageData updated = updateSpoilageDataLazy(
                data,
                stack.getCount(),
                currentTime,
                () -> SpoilageConfig.getInstance().getFreshDurationForItem(stack.getItem()),
                () -> SpoilageConfig.getInstance().getStaleDurationForItem(stack.getItem()),
                currentMultiplier
        );

        // Only call set() if something actually changed (still avoids the network packet + write).
        if (updated != data && !updated.equals(data)) {
            stack.set(ModDataComponentTypes.SPOILAGE, updated);
        }
    }

    /**
     * Pure-logic tick for one (data, count) pair. Extracted so benchmarks and tests can exercise
     * the hot path without constructing a Minecraft ServerLevel. Returns the same {@code data}
     * instance when nothing changed (list reuse preserved), or a new instance otherwise.
     *
     * <p>Lens 8/13 optimization history:
     * <ul>
     *   <li>Original: missing-tracker branch called {@code Collections.min(staleList)} and
     *       {@code Collections.min(freshList)} (each O(n)), then re-scanned lists inside the
     *       fresh→stale and stale→rotten loops with a "rewind to the start of the original
     *       list" trick that turned the common "some items expired" case into O(n²).</li>
     *   <li>Rewrite: scan once for min when adding missing trackers, partition lists in a
     *       single forward pass for expiration transitions, with no nested re-scan.</li>
     * </ul>
     */
    public static SpoilageData updateSpoilageData(
            SpoilageData data,
            int actualCount,
            long currentTime,
            long freshDuration,
            long staleDuration,
            double currentMultiplier
    ) {
        return updateSpoilageDataImpl(data, actualCount, currentTime,
                () -> freshDuration, () -> staleDuration, currentMultiplier);
    }

    /**
     * Lazy-duration variant (Pass 90, Lens 8): the fresh/stale durations are only needed when
     * trackers are missing or a fresh item actually expires. In the steady state (counts match,
     * nothing expired) both duration lookups were wasted CHM gets per stack per 20-tick scan.
     * Suppliers fetch each duration at most once, and only if the branch that needs it runs.
     */
    public static SpoilageData updateSpoilageDataLazy(
            SpoilageData data,
            int actualCount,
            long currentTime,
            java.util.function.LongSupplier freshDurationSupplier,
            java.util.function.LongSupplier staleDurationSupplier,
            double currentMultiplier
    ) {
        return updateSpoilageDataImpl(data, actualCount, currentTime,
                freshDurationSupplier, staleDurationSupplier, currentMultiplier);
    }

    private static SpoilageData updateSpoilageDataImpl(
            SpoilageData data,
            int actualCount,
            long currentTime,
            java.util.function.LongSupplier freshDurationSupplier,
            java.util.function.LongSupplier staleDurationSupplier,
            double currentMultiplier
    ) {
        // Fetched lazily on first use — see Pass 90 note above.
        long freshDuration = 0L;
        boolean freshDurationResolved = false;
        long staleDuration = 0L;
        boolean staleDurationResolved = false;
        // Reuse lists to avoid allocations - copy only when modification is needed
        List<Long> freshList = data.freshExpirations();
        List<Long> staleList = data.staleExpirations();
        int rottenCount = data.rottenCount();

        int totalTracked = freshList.size() + staleList.size() + rottenCount;

        // 1. Handle missing trackers (new drops/picked up items/merges) or excess trackers (eaten/consumed)
        boolean excessTrimmed = false;
        if (actualCount > totalTracked) {
            int missing = actualCount - totalTracked;
            freshList = new ArrayList<>(freshList);
            staleList = new ArrayList<>(staleList);
            if (totalTracked == 0) {
                if (!freshDurationResolved) {
                    freshDuration = freshDurationSupplier.getAsLong();
                    freshDurationResolved = true;
                }
                // Pass 233 (L7 — boundary): guard against overflow when freshDuration is
                // very large (e.g. a future config with a huge base duration, or a
                // custom freshDurationSupplier in a test). currentTime + freshDuration
                // can overflow to negative if freshDuration > Long.MAX_VALUE - currentTime,
                // which would make the entry look already-expired and move to stale on
                // the next tick. Clamp to Long.MAX_VALUE (the NEVER sentinel) in that case.
                long paddedFreshExp = (freshDuration > Long.MAX_VALUE - currentTime)
                        ? Long.MAX_VALUE
                        : currentTime + freshDuration;
                for (int i = 0; i < missing; i++) {
                    freshList.add(paddedFreshExp);
                }
            } else {
                // Stack was already degraded: missing items inherit the worst active condition
                // to prevent laundering. Single O(n) scan for min instead of Collections.min().
                if (rottenCount > 0) {
                    rottenCount += missing;
                } else if (!staleList.isEmpty()) {
                    long minStale = Long.MAX_VALUE;
                    for (long s : staleList) if (s < minStale) minStale = s;
                    for (int i = 0; i < missing; i++) staleList.add(minStale);
                } else if (!freshList.isEmpty()) {
                    long minFresh = Long.MAX_VALUE;
                    for (long f : freshList) if (f < minFresh) minFresh = f;
                    for (int i = 0; i < missing; i++) freshList.add(minFresh);
                } else {
                    if (!freshDurationResolved) {
                        freshDuration = freshDurationSupplier.getAsLong();
                        freshDurationResolved = true;
                    }
                    for (int i = 0; i < missing; i++) freshList.add(currentTime + freshDuration);
                }
            }
        } else if (actualCount < totalTracked) {
            int excess = totalTracked - actualCount;
            SpoilageData currentData = new SpoilageData(freshList, staleList, rottenCount, currentMultiplier);
            SpoilageData[] split = extractWorstItems(currentData, excess);
            freshList = split[0].freshExpirations();
            staleList = split[0].staleExpirations();
            rottenCount = split[0].rottenCount();
            excessTrimmed = true;
        }

        // 2. Check fresh expirations -> move to stale (single forward pass).
        // Original loop restarted at the head of freshList for every expired item, turning
        // this into O(n^2). The new loop appends survivors to remainingFresh as it walks,
        // and adds the moved entry to staleList in the same pass.
        List<Long> remainingFresh = null;
        boolean freshExpired = false;
        for (long exp : freshList) {
            if (currentTime >= exp) {
                if (!freshExpired) {
                    remainingFresh = new ArrayList<>(freshList.size());
                    freshExpired = true;
                    // Pass 120 (Lens 1 — silent failure): staleList may be the shared immutable
                    // Collections.emptyList() — either stored by the Pass 88 constructor fast
                    // path, or returned by split[0] when the excess-tracker extraction drained
                    // the whole stale side. The old identity guard (data.staleExpirations() ==
                    // staleList) missed the split[0] case because data is the ORIGINAL record,
                    // not the extraction result — the add() below would have thrown
                    // UnsupportedOperationException and crashed the tick. Check mutability by
                    // class instead of identity: wrap anything that is not an ArrayList.
                    // Pass 207 (L4): hoisted into the first-expired-item block so the class
                    // check runs exactly once per call, not once per expired item — the wrap
                    // is part of the same "prepare to move the first item" step as the
                    // remainingFresh allocation above it.
                    if (staleList.getClass() != ArrayList.class) {
                        staleList = new ArrayList<>(staleList);
                    }
                }
                if (!staleDurationResolved) {
                    staleDuration = staleDurationSupplier.getAsLong();
                    staleDurationResolved = true;
                }
                staleList.add(exp + staleDuration);
            } else if (freshExpired) {
                remainingFresh.add(exp);
            }
        }
        if (!freshExpired) {
            remainingFresh = freshList;
        }

        // 3. Check stale expirations -> move to rotten (single forward pass, same pattern).
        List<Long> remainingStale = null;
        boolean staleExpired = false;
        for (long exp : staleList) {
            if (currentTime >= exp) {
                if (!staleExpired) {
                    remainingStale = new ArrayList<>(staleList.size());
                    staleExpired = true;
                }
                rottenCount++;
            } else if (staleExpired) {
                remainingStale.add(exp);
            }
        }
        if (!staleExpired) {
            remainingStale = staleList;
        }

        boolean changed = remainingFresh != freshList
                || remainingStale != staleList
                || rottenCount != data.rottenCount()
                || currentMultiplier != data.speedMultiplier()
                || excessTrimmed
                // Pass 229 (L1 — silent failure): when padding adds entries to freshList
                // but no entries expire, remainingFresh = freshList (same reference), so
                // the reference-equality check above misses the padding. The padding
                // mutated freshList in place — detect it by comparing sizes.
                || remainingFresh.size() != data.freshExpirations().size()
                || remainingStale.size() != data.staleExpirations().size();
        if (!changed) {
            return data;
        }
        SpoilageData updated = new SpoilageData(remainingFresh, remainingStale, rottenCount, currentMultiplier);
        return updated;
    }

    public static void randomizeSpoilage(ItemStack stack, Level world, RandomSource random) {
        // Pass 173 (Lens 7 — boundary): updateSpoilage guards stack == null; this public
        // method did not, so a null stack NPE'd at stack.isEmpty() on the next line. The
        // only current caller (RandomizableContainerBlockEntityMixin:44) checks isEmpty
        // first, but the method is public — any future caller could pass null.
        if (stack == null || stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem()))
            return;

        long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(stack.getItem());
        long staleDuration = SpoilageConfig.getInstance().getStaleDurationForItem(stack.getItem());
        long currentTime = world.getGameTime();

        List<Long> freshList = new ArrayList<>();
        List<Long> staleList = new ArrayList<>();
        int rottenCount = 0;

        SpoilageConfig.LootRandomizationConfig lootCfg = SpoilageConfig.getInstance().getLootRandomizationConfig();
        float fChance = Math.max(0f, lootCfg.fresh_chance);
        float sChance = Math.max(0f, lootCfg.stale_chance);
        float rChance = Math.max(0f, lootCfg.rotten_chance);
        float total = fChance + sChance + rChance;
        if (total <= 0f) {
            fChance = 0.60f;
            sChance = 0.30f;
            rChance = 0.10f;
            total = 1.0f;
        }
        float freshThreshold = fChance / total;
        float staleThreshold = (fChance + sChance) / total;

        for (int i = 0; i < stack.getCount(); i++) {
            float roll = random.nextFloat();
            if (roll < freshThreshold) {
                long offset = (long) (random.nextFloat() * freshDuration);
                freshList.add(currentTime + offset);
            } else if (roll < staleThreshold) {
                long offset = (long) (random.nextFloat() * staleDuration);
                staleList.add(currentTime + offset);
            } else {
                rottenCount++;
            }
        }

        SpoilageData data = new SpoilageData(freshList, staleList, rottenCount, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
        stack.set(ModDataComponentTypes.SPOILAGE, data);
    }

    public static SpoilageState getWorstState(ItemStack stack) {
        if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem()))
            return SpoilageState.FRESH;

        if (!stack.has(ModDataComponentTypes.SPOILAGE)) {
            // Freshly spawned/crafted item without component attached yet
            return SpoilageState.FRESH;
        }

        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) return SpoilageState.FRESH;

        if (data.rottenCount() > 0) return SpoilageState.ROTTEN;
        if (!data.staleExpirations().isEmpty()) return SpoilageState.STALE;
        return SpoilageState.FRESH;
    }

    public static SpoilageState getBestState(ItemStack stack) {
        if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem()))
            return SpoilageState.FRESH;

        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) return SpoilageState.FRESH;

        if (!data.freshExpirations().isEmpty()) return SpoilageState.FRESH;
        if (!data.staleExpirations().isEmpty()) return SpoilageState.STALE;
        if (data.rottenCount() > 0) return SpoilageState.ROTTEN;
        return SpoilageState.FRESH;
    }

    public static long getBestTimestamp(ItemStack stack, SpoilageState bestState) {
        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) return -1L;

        if (bestState == SpoilageState.FRESH && !data.freshExpirations().isEmpty()) {
            return findMax(data.freshExpirations());
        }
        if (bestState == SpoilageState.STALE && !data.staleExpirations().isEmpty()) {
            return findMax(data.staleExpirations());
        }
        return -1L;
    }

    /**
     * The earliest expiration among the items in the worst active state — the timestamp a
     * placed block should inherit so a partially-spoiled stack keeps its true (worst)
     * condition instead of laundering it through the best item (BUG-12).
     */
    public static long getWorstTimestamp(ItemStack stack, SpoilageState worstState) {
        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) return -1L;

        if (worstState == SpoilageState.FRESH && !data.freshExpirations().isEmpty()) {
            return findMin(data.freshExpirations());
        }
        if (worstState == SpoilageState.STALE && !data.staleExpirations().isEmpty()) {
            return findMin(data.staleExpirations());
        }
        return -1L;
    }

    private static long findMin(List<Long> list) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            long val = list.get(i);
            if (val < min) min = val;
        }
        return min;
    }

    private static long findMax(List<Long> list) {
        long max = Long.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            long val = list.get(i);
            if (val > max) max = val;
        }
        return max;
    }

    /**
     * Pass 224 (PLAYER_REPORT §3): returns true if the block is eaten in place — the player
     * consumes it by interacting with the block, not by holding it as an item. Such blocks
     * bypass the ItemStackMixin rotten-effects path, so a rotten item placed as one of these
     * blocks would give clean food.
     *
     * <p>The vanilla block eaten in place is CakeBlock. Modded equivalents (any block that
     * calls player.getFoodData().eat() in its useWithoutItem) share this pattern but are
     * not detectable at runtime without a registry. The conservative check is: the block
     * is a CakeBlock. A more general fix would require modded blocks to register
     * themselves (e.g. via a config entry or an API call).</p>
     */
    public static boolean isEatenInPlace(net.minecraft.world.level.block.Block block) {
        if (block == null) return false;
        return block instanceof net.minecraft.world.level.block.CakeBlock;
    }

    /**
     * Pass 223 (PLAYER_REPORT §2): returns true if the worst-N slice of the stack's
     * trackers contains a ROTTEN entry. The transfer moves the WORST items first
     * (extractWorstItems), so a right-click (N=1) takes exactly the worst item. If that
     * item is rotten, the guard must reject the insertion even when the carried stack
     * is not entirely rotten.
     *
     * <p>Worst-first order: rotten, then stale (min expiration), then fresh (min expiration).
     * If the stack has at least one rotten tracker, the worst-N slice always contains a
     * rotten entry for any N >= 1. If the stack has no rotten trackers but has stale
     * trackers, the worst-N slice contains a stale entry (not rotten) for N <= staleCount,
     * and a fresh entry for N > staleCount. If the stack has only fresh trackers, the
     * worst-N slice is always fresh.</p>
     */
    public static boolean worstSliceContainsRotten(ItemStack stack, int n) {
        if (stack.isEmpty() || n <= 0) return false;
        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null) return false;
        return data.rottenCount() > 0;
    }

    public static boolean isEntirelyRotten(ItemStack stack) {
        if (stack.isEmpty()) return false;
        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        if (data == null) return false;
        return data.rottenCount() >= stack.getCount() && data.freshExpirations().isEmpty() && data.staleExpirations().isEmpty();
    }

    public static boolean isRottenEgg(ItemStack stack, Level world) {
        if (stack.isEmpty() || stack.getItem() != Items.EGG) return false;
        updateSpoilage(stack, world);
        return isEntirelyRotten(stack);
    }

    public static void makeFresh(ItemStack stack, Level world) {
        if (stack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(stack.getItem())) return;
        long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(stack.getItem());
        long expire = world.getGameTime() + freshDuration;

        List<Long> freshList = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) freshList.add(expire);

        SpoilageData data = new SpoilageData(freshList, Collections.emptyList(), 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
        stack.set(ModDataComponentTypes.SPOILAGE, data);
    }

    public static void updateContainerItemSpoilage(ItemStack containerStack, Level world) {
        if (containerStack.isEmpty()) return;
        ItemContainerContents container = containerStack.get(DataComponents.CONTAINER);
        if (container == null) return;

        // Pass 624 (Lens 4 — hot-path cost): the CONTAINER branch in ItemEntityMixin.onTick
        // calls this for every dropped container entity every 20 ticks. A shulker box full of
        // cobblestone (no spoilable items) still paid the full 256-slot copyInto + 256 isEmpty
        // checks + N isSpoilable CHM gets for the non-empty ones, then the loop did nothing.
        // Fast-path: iterate the container's non-empty item templates (no ItemStack allocation)
        // and bail before the copy if none of them are spoilable. nonEmptyItems() returns an
        // Iterable<ItemStackTemplate> backed by the container's internal list, so this is a
        // single pass over the populated slots — no stream pipeline, no Optional unwrap, no
        // ItemStack creation.
        boolean anySpoilable = false;
        for (ItemStackTemplate template : container.nonEmptyItems()) {
            if (SpoilageConfig.getInstance().isSpoilable(template.item().value())) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) return;

        // Lens 13 (GC): the old path did
        //   container.allItemsCopyStream().collect(Collectors.toCollection(ArrayList::new))
        // which allocates a Stream, an Iterator, Optional wrappers, and a fresh ArrayList on
        // every call. With 1,000 shulker boxes ticking every 20 ticks that is 1,000 ArrayList
        // + stream-pipeline allocations per tick. ItemContainerContents.copyInto() iterates the
        // backing list directly with no stream and no Optional wrapping, so we reuse one
        // thread-local NonNullList instead of allocating a fresh list each time.
        // NOTE: copyInto() only fills slots that already exist in the destination, so the scratch
        // list is pre-sized to the container max (256) and never cleared — copyInto overwrites
        // every slot in place. Clearing it would drop the size to 0 and copy nothing.
        NonNullList<ItemStack> items = CONTAINER_SCRATCH.get();
        container.copyInto(items);
        boolean changed = false;

        for (ItemStack item : items) {
            if (!item.isEmpty() && SpoilageConfig.getInstance().isSpoilable(item.getItem())) {
                // Trim over-tracked (count < totalTracked) to count, keeping the WORST trackers,
                // mirroring ItemEntityMixin.onTick (BUG-20 fix). A containerized item reached via
                // copyWithCount (hopper/dispenser into a shulker, Q-drop into a bundle) can carry
                // more trackers than its count; updateSpoilage() would otherwise "heal" it by keeping
                // the BEST trackers (eat-the-worst semantics), inverting the spoilage of the item.
                int count = item.getCount();
                if (count > 0) {
                    SpoilageData d = item.get(ModDataComponentTypes.SPOILAGE);
                    if (d != null && d.totalTracked() > count) {
                        SpoilageData[] split = extractWorstItems(d, count);
                        item.set(ModDataComponentTypes.SPOILAGE, split[1]); // split[1] = worst `count` trackers
                        // Pass 165 (Lens 1 — silent failure): the trim mutated the scratch copy,
                        // but `changed` was only set by updateSpoilage's before/after comparison
                        // below. When updateSpoilage returned early (null world) or found nothing
                        // to change (steady state), changed stayed false and the trimmed data was
                        // NEVER written back — the trim silently did nothing. The write-back must
                        // also fire for the trim itself.
                        changed = true;
                    }
                }
                SpoilageData before = item.get(ModDataComponentTypes.SPOILAGE);
                updateSpoilage(item, world);
                SpoilageData after = item.get(ModDataComponentTypes.SPOILAGE);
                if (!java.util.Objects.equals(before, after)) {
                    changed = true;
                }
            }
        }

        if (changed) {
            containerStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
    }

    /**
     * Reusable scratch list for {@link #updateContainerItemSpoilage}. Container contents are
     * bounded by {@code ItemContainerContents.MAX_SIZE} (256), so a single thread-local list
     * pre-sized to 256 (filled with ItemStack.EMPTY) is reused across calls instead of
     * allocating a fresh ArrayList per container tick. It is never cleared — copyInto() overwrites
     * every slot in place, so the size must stay at 256.
     */
    private static final ThreadLocal<NonNullList<ItemStack>> CONTAINER_SCRATCH =
            ThreadLocal.withInitial(() -> NonNullList.withSize(256, ItemStack.EMPTY));

    private static int findMinIndex(List<Long> list) {
        int minIdx = 0;
        long minVal = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) < minVal) {
                minVal = list.get(i);
                minIdx = i;
            }
        }
        return minIdx;
    }

    private static int findMaxIndex(List<Long> list) {
        int maxIdx = 0;
        long maxVal = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) > maxVal) {
                maxVal = list.get(i);
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public enum SpoilageState {
        FRESH,
        STALE,
        ROTTEN
    }

    // ======================== Crop maturity ========================

    /**
     * The block's growth stage property, or {@code null} when it does not grow.
     *
     * <p>Found by shape rather than by class so it works for content this mod has never heard
     * of. Vanilla crops, stems, cocoa, nether wart and berry bushes all expose an integer
     * property named {@code age}, and so does essentially every modded crop — testing for the
     * property catches all of them, while a list of block classes would cover vanilla and miss
     * every mod in the pack.</p>
     *
     * <p>A qualified name ending in {@code _age} counts too. A fruiting tree is normally a
     * leaves block that keeps its own ripeness counter alongside whatever else leaves carry, so
     * it cannot call the property plain {@code age} — MegaCookery's apple tree uses
     * {@code apple_age} (0 bare, 1 flowering, 2 fruiting). Matching only the exact name made
     * this block answer "not a crop", which meant it counted as ripe at every stage: a bare
     * branch with no apple on it was treated exactly like one carrying fruit. Exact {@code age}
     * still wins when a block has both, so nothing about vanilla changes.</p>
     */
    public static net.minecraft.world.level.block.state.properties.IntegerProperty growthProperty(
            net.minecraft.world.level.block.state.BlockState state) {
        if (state == null) return null;
        net.minecraft.world.level.block.state.properties.IntegerProperty qualified = null;
        for (net.minecraft.world.level.block.state.properties.Property<?> p : state.getProperties()) {
            if (p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip) {
                String name = ip.getName();
                if ("age".equals(name)) {
                    return ip;
                }
                if (qualified == null && name.endsWith("_age")) {
                    qualified = ip;
                }
            }
        }
        return qualified;
    }

    /**
     * True when this block still has growing left to do.
     *
     * <p>Freshness must not start while a crop is growing. A potato planted a moment ago used
     * to be tracked immediately, so its clock ran through the whole growth period and it could
     * be half spoiled before it was ever harvestable — the player watched food rot in the
     * ground. Ageing starts when the crop reaches its final stage, which is the point at which
     * there is actually something to spoil.</p>
     *
     * <p>A block with no growth stage is never immature: it is finished by definition.</p>
     */
    public static boolean isImmatureCrop(net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.state.properties.IntegerProperty age = growthProperty(state);
        if (age == null) return false;
        return state.getValue(age) < maxGrowth(age);
    }

    /** Highest value of a growth property. */
    private static int maxGrowth(net.minecraft.world.level.block.state.properties.IntegerProperty age) {
        int max = 0;
        for (Integer v : age.getPossibleValues()) {
            if (v != null && v > max) max = v;
        }
        return max;
    }

    /**
     * True when the crop has barely started growing — under half of its stages.
     *
     * <p>Used only for "digging this up gives rotten produce", which needs a stricter test than
     * {@link #isImmatureCrop}. Not being at the final stage does not mean a plant has nothing to
     * give: a sweet berry bush is harvested at stage 2 of 3 in vanilla, and treating that as
     * unripe would have handed the player rotten berries for a perfectly normal pick. Cocoa at
     * 1 of 2 is the same story.</p>
     *
     * <p>Half is the line because the two rules are answering different questions. Freshness
     * must not start until the plant is actually finished, so that one uses the final stage.
     * Rotten produce is a penalty for destroying something that had barely begun, and a crop
     * most of the way to ripe has not barely begun — losing the yield early is already its own
     * cost.</p>
     */
    public static boolean isBarelyGrown(net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.state.properties.IntegerProperty age = growthProperty(state);
        if (age == null) return false;
        int max = maxGrowth(age);
        if (max <= 0) return false;
        return state.getValue(age) * 2 < max;
    }
}
