package com.spoilageenhanced.util;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries spoilage across a crafting recipe: the result inherits the worst state among the
 * spoilable ingredients, keeping the same proportion of life left.
 *
 * <p>Lives outside the mixin so it can be exercised without a player at a crafting table — by a
 * unit test and by {@code /spoilage debug craft}. Mixins do not apply in tests, so logic left
 * inside {@code CraftingResultSlotMixin} could only ever be checked by hand.</p>
 *
 * <p>Both directions of a Crate Delight style storage recipe run through here: nine apples into a
 * crate takes the worst apple, and a crate back into nine apples gives every apple the crate's
 * remaining life rather than a fresh start.</p>
 */
public final class CraftingSpoilageTransfer {

    private CraftingSpoilageTransfer() {}

    /** The inherited data plus what it was derived from, for logging at the call site. */
    public record Result(SpoilageData data, ItemStack source, SpoilageState state) {}

    /**
     * @param ingredients the crafting grid contents; empty and non-spoilable stacks are ignored
     * @param result      the crafted stack, whose count decides how many portions are written
     * @param currentTime the world game time
     * @return the data to apply to {@code result}, or {@code null} when nothing should be carried
     */
    public static Result compute(List<ItemStack> ingredients, ItemStack result, long currentTime) {
        if (result == null || result.isEmpty()) return null;
        if (!SpoilageConfig.getInstance().isSpoilable(result.getItem())) return null;

        ItemStack worstIngredient = null;
        SpoilageState worstState = SpoilageState.FRESH;
        long worstExpiration = Long.MAX_VALUE;

        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()
                    || !SpoilageConfig.getInstance().isSpoilable(ingredient.getItem())) {
                continue;
            }

            SpoilageState state = FoodSpoilageUtil.getWorstState(ingredient);

            // ROTTEN always wins — no need to check further
            if (state == SpoilageState.ROTTEN) {
                worstState = SpoilageState.ROTTEN;
                worstIngredient = ingredient;
                break;
            }

            // For STALE and FRESH, pick the one with the minimum expiration (worst = closest to expiring)
            // Pass 116 (Lens 13): Collections.min allocated an Iterator and did boxed Long.compareTo
            // per element — replaced with the primitive min scan (same pattern as Pass 77's findMin).
            SpoilageData data = ingredient.get(ModDataComponentTypes.SPOILAGE);
            long expiration = Long.MAX_VALUE;
            if (data != null && !data.isEmpty()) {
                if (state == SpoilageState.STALE && !data.staleExpirations().isEmpty()) {
                    for (long exp : data.staleExpirations()) {
                        if (exp < expiration) expiration = exp;
                    }
                } else if (state == SpoilageState.FRESH && !data.freshExpirations().isEmpty()) {
                    for (long exp : data.freshExpirations()) {
                        if (exp < expiration) expiration = exp;
                    }
                }
            }

            // Upgrade priority: FRESH -> STALE -> ROTTEN (already handled above)
            // Within same state, pick the one with smallest expiration
            if (worstIngredient == null
                    || state.ordinal() > worstState.ordinal()
                    || (state == worstState && expiration < worstExpiration)) {
                worstIngredient = ingredient;
                worstState = state;
                worstExpiration = expiration;
            }
        }

        if (worstIngredient == null) return null;

        SpoilageData sourceData = worstIngredient.get(ModDataComponentTypes.SPOILAGE);
        if (sourceData == null) sourceData = SpoilageData.DEFAULT;

        List<Long> newFresh = new ArrayList<>();
        List<Long> newStale = new ArrayList<>();
        int newRotten = 0;

        if (worstState == SpoilageState.ROTTEN) {
            newRotten = result.getCount();
        } else if (worstState == SpoilageState.STALE) {
            long duration = SpoilageConfig.getInstance().getBaseStaleDurationForItem(result.getItem());
            long baseRemaining = (long) (duration * remainingRatio(
                    sourceData.staleExpirations(),
                    SpoilageConfig.getInstance().getBaseStaleDurationForItem(worstIngredient.getItem()),
                    currentTime));
            int count = result.getCount();
            if (count > 1 && sourceData.staleExpirations().size() == 1) {
                long spread = Math.max(1L, duration / 10);
                for (int i = 0; i < count; i++) {
                    long offset = (long) ((i - count / 2) * (spread * 2.0 / count));
                    long expiration = currentTime + Math.max(1L, baseRemaining + offset);
                    newStale.add(expiration);
                }
            } else {
                long expiration = currentTime + Math.max(1L, baseRemaining);
                for (int i = 0; i < count; i++) newStale.add(expiration);
            }
        } else {
            long duration = SpoilageConfig.getInstance().getBaseFreshDurationForItem(result.getItem());
            long baseRemaining = (long) (duration * remainingRatio(
                    sourceData.freshExpirations(),
                    SpoilageConfig.getInstance().getBaseFreshDurationForItem(worstIngredient.getItem()),
                    currentTime));
            int count = result.getCount();
            if (count > 1 && sourceData.freshExpirations().size() == 1) {
                long spread = Math.max(1L, duration / 10);
                for (int i = 0; i < count; i++) {
                    long offset = (long) ((i - count / 2) * (spread * 2.0 / count));
                    long expiration = currentTime + Math.max(1L, baseRemaining + offset);
                    newFresh.add(expiration);
                }
            } else {
                long expiration = currentTime + Math.max(1L, baseRemaining);
                for (int i = 0; i < count; i++) newFresh.add(expiration);
            }
        }

        SpoilageData data = new SpoilageData(newFresh, newStale, newRotten, sourceData.speedMultiplier());
        return new Result(data, worstIngredient, worstState);
    }

    /**
     * How much of the ingredient's life is left, as a 0..1 fraction of its full duration.
     * A stack with no data yet counts as brand new — freshly crafted food has no component
     * until the first server tick.
     */
    private static double remainingRatio(List<Long> expirations, long fullDuration, long currentTime) {
        if (expirations.isEmpty() || fullDuration <= 0) return 1.0;
        // Use the worst (minimum) expiration, matching the worst-ingredient selection logic.
        // Pass 116 (Lens 13): primitive min scan instead of Collections.min (Iterator + boxed
        // Long.compareTo per element).
        long worstExp = Long.MAX_VALUE;
        for (long exp : expirations) {
            if (exp < worstExp) worstExp = exp;
        }
        long remaining = worstExp - currentTime;
        return Math.max(0.0, Math.min(1.0, (double) remaining / fullDuration));
    }
}
