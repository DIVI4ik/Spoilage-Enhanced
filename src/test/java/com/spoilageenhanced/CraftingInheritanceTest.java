package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.CraftingSpoilageTransfer;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spoilage inheritance across a storage recipe, in both directions.
 *
 * <p>Crate Delight items do not exist in a unit test, so a vanilla item stands in for the crate:
 * what matters is that one spoilable item compresses into another and back, which is exactly the
 * shape of every {@code cratedelight:*_crate} recipe (nine of a food in, nine back out).</p>
 */
public class CraftingInheritanceTest {

    /** Stands in for a crate: a spoilable container item holding nine of the food. */
    private static Item CRATE;
    private static Item FOOD;

    private static final long CRATE_FRESH = 96000L;
    private static final long CRATE_STALE = 48000L;

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();

        // Items must not be touched before the bootstrap above has run.
        CRATE = Items.HAY_BLOCK;
        FOOD = Items.APPLE;

        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }

        // The recipe scanner does this at runtime once it sees a 9-in-1 recipe.
        SpoilageConfig.getInstance().registerDynamicFoodItem(
                BuiltInRegistries.ITEM.getKey(CRATE).toString(), CRATE_FRESH, CRATE_STALE);
    }

    private static ItemStack food(int count, SpoilageData data) {
        ItemStack stack = new ItemStack(FOOD, count);
        if (data != null) stack.set(ModDataComponentTypes.SPOILAGE, data);
        return stack;
    }

    private static List<ItemStack> grid(ItemStack... stacks) {
        List<ItemStack> grid = new ArrayList<>(List.of(stacks));
        while (grid.size() < 9) grid.add(ItemStack.EMPTY);
        return grid;
    }

    @Test
    public void crateIsSpoilableAfterDynamicRegistration() {
        assertTrue(SpoilageConfig.getInstance().isSpoilable(CRATE),
                "A registered storage item must count as spoilable, otherwise nothing is inherited");
        assertTrue(SpoilageConfig.getInstance().isSpoilable(FOOD),
                "Apples must be spoilable for this test to mean anything");
    }

    @Test
    public void compressionInheritsFreshnessProportionally() {
        long now = 1000L;
        long foodFresh = SpoilageConfig.getInstance().getFreshDurationForItem(FOOD);
        // Nine apples, all half-spent.
        long halfSpent = now + foodFresh / 2;
        List<ItemStack> nineApples = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nineApples.add(food(1, new SpoilageData(List.of(halfSpent), Collections.emptyList(), 0, 1.0)));
        }

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(nineApples, new ItemStack(CRATE, 1), now);

        assertNotNull(result, "A crate crafted from spoilable food must inherit something");
        assertEquals(SpoilageState.FRESH, result.state());
        assertEquals(1, result.data().freshExpirations().size(), "One crate means one portion");
        assertTrue(result.data().staleExpirations().isEmpty());
        assertEquals(0, result.data().rottenCount());

        long remaining = result.data().freshExpirations().get(0) - now;
        long expected = (long) (SpoilageConfig.getInstance().getFreshDurationForItem(CRATE) * 0.5);
        assertEquals(expected, remaining, 2L,
                "A crate of half-spent apples must itself be half-spent, scaled to the crate's own lifetime");
    }

    @Test
    public void compressionTakesTheWorstIngredient() {
        long now = 1000L;
        List<ItemStack> mixed = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            mixed.add(food(1, new SpoilageData(List.of(now + 90000L), Collections.emptyList(), 0, 1.0)));
        }
        // One stale apple among eight fresh ones.
        mixed.add(food(1, new SpoilageData(Collections.emptyList(), List.of(now + 10000L), 0, 1.0)));

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(mixed, new ItemStack(CRATE, 1), now);

        assertNotNull(result);
        assertEquals(SpoilageState.STALE, result.state(), "One stale ingredient must drag the crate down");
        assertEquals(1, result.data().staleExpirations().size());
        assertTrue(result.data().freshExpirations().isEmpty());
    }

    @Test
    public void compressionOfRottenFoodGivesARottenCrate() {
        long now = 1000L;
        List<ItemStack> rotten = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            rotten.add(food(1, new SpoilageData(Collections.emptyList(), Collections.emptyList(), 1, 1.0)));
        }

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(rotten, new ItemStack(CRATE, 1), now);

        assertNotNull(result);
        assertEquals(SpoilageState.ROTTEN, result.state());
        assertEquals(1, result.data().rottenCount());
    }

    @Test
    public void decompressionGivesEveryPortionTheCratesRemainingLife() {
        long now = 1000L;
        // A crate that is one quarter through its life.
        long crateFresh = SpoilageConfig.getInstance().getFreshDurationForItem(CRATE);
        ItemStack crate = new ItemStack(CRATE, 1);
        crate.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(now + (long) (crateFresh * 0.75)), Collections.emptyList(), 0, 1.0));

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(grid(crate), new ItemStack(FOOD, 9), now);

        assertNotNull(result, "Unpacking a crate must not hand out immortal food");
        assertEquals(9, result.data().freshExpirations().size(), "Nine apples means nine portions");

        long expected = (long) (SpoilageConfig.getInstance().getFreshDurationForItem(FOOD) * 0.75);
        // All items should be within ±10% spread of the base remaining life
        long spread = Math.max(1L, SpoilageConfig.getInstance().getFreshDurationForItem(FOOD) / 10);
        for (long expiration : result.data().freshExpirations()) {
            long remaining = expiration - now;
            assertTrue(Math.abs(remaining - expected) <= spread,
                    "Each apple should be within spread of the crate's remaining life, got " + remaining
                            + " expected ~" + expected + " ±" + spread);
        }
    }

    @Test
    public void freshlyCraftedIngredientWithoutDataCountsAsBrandNew() {
        long now = 1000L;
        // No component yet — that is what a just-crafted stack looks like before the first tick.
        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(grid(new ItemStack(FOOD, 9)), new ItemStack(CRATE, 1), now);

        assertNotNull(result);
        assertEquals(SpoilageState.FRESH, result.state());
        long remaining = result.data().freshExpirations().get(0) - now;
        assertEquals(SpoilageConfig.getInstance().getFreshDurationForItem(CRATE), remaining, 2L,
                "Food with no data yet must be treated as full life, not as expired");
    }

    @Test
    public void nonSpoilableResultInheritsNothing() {
        long now = 1000L;
        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(grid(food(9, null)), new ItemStack(Items.STONE, 1), now);

        assertNull(result, "A stone block must not gain a shelf life");
    }

    // ==================== BUG-03: worst FRESH ingredient selection ====================

    @Test
    public void worstFreshIngredientIsTheOneClosestToExpiring() {
        long now = 1000L;
        long foodFresh = SpoilageConfig.getInstance().getFreshDurationForItem(FOOD);
        List<ItemStack> ingredients = new ArrayList<>();
        // 8 fresh apples with plenty of time left
        for (int i = 0; i < 8; i++) {
            ingredients.add(food(1, new SpoilageData(List.of(now + foodFresh - 1000L), Collections.emptyList(), 0, 1.0)));
        }
        // 1 fresh apple that is almost expired (worst FRESH)
        ingredients.add(food(1, new SpoilageData(List.of(now + 100L), Collections.emptyList(), 0, 1.0)));

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(ingredients, new ItemStack(CRATE, 1), now);

        assertNotNull(result);
        assertEquals(SpoilageState.FRESH, result.state());
        // The crate should inherit the worst FRESH ingredient's remaining life
        long remaining = result.data().freshExpirations().get(0) - now;
        long expected = (long) (SpoilageConfig.getInstance().getFreshDurationForItem(CRATE) * 100.0 / foodFresh);
        assertEquals(expected, remaining, 2L,
                "Crate must inherit the worst FRESH ingredient (closest to expiring)");
    }

    // ==================== BUG-04: no resurrection on speed multiplier change ====================

    @Test
    public void rescaleDoesNotResurrectExpiredItems() {
        long now = 1000L;
        // Item that is already stale (expired fresh, in stale phase)
        long staleExp = now - 500L; // already expired
        SpoilageData data = new SpoilageData(
                Collections.emptyList(),
                List.of(staleExp),
                0,
                1.0
        );

        // Speed multiplier increases 2x — ratio = 0.5
        SpoilageData rescaled = FoodSpoilageUtil.rescaleItemTimestamps(
                data, now, 0.5, 2.0);

        // The stale expiration should NOT move into the future (no resurrection)
        assertTrue(rescaled.staleExpirations().get(0) <= now,
                "Expired stale item must not be resurrected to fresh after rescale");
    }

    // ==================== BUG-05: mergeItems list length invariant ====================

    @Test
    public void mergeItemsPreservesTotalCount() {
        long now = 1000L;
        SpoilageData batch1 = new SpoilageData(
                List.of(now + 5000L, now + 3000L), // 2 fresh
                List.of(now + 1000L), // 1 stale
                1, // 1 rotten
                1.0
        );
        SpoilageData batch2 = new SpoilageData(
                List.of(now + 4000L), // 1 fresh
                Collections.emptyList(),
                0,
                1.0
        );

        SpoilageData merged = FoodSpoilageUtil.mergeItems(batch1, batch2);
        int expectedTotal = 2 + 1 + 1 + 1 + 0 + 0; // 5 items
        int actualTotal = merged.freshExpirations().size()
                + merged.staleExpirations().size()
                + merged.rottenCount();
        assertEquals(expectedTotal, actualTotal,
                "Merged data must have list sizes matching total item count");
    }

    // ==================== BUG-06: eating removes worst items first ====================

    @Test
    public void eatingRemovesWorstItemsFirst() {
        long now = 1000L;
        // Stack of 3: 1 fresh (far from expiring), 1 stale, 1 rotten
        SpoilageData data = new SpoilageData(
                List.of(now + 10000L), // fresh
                List.of(now + 500L), // stale (almost expired)
                1, // rotten
                1.0
        );

        // Simulate eating 1 item — should remove the worst (rotten)
        SpoilageData currentData = new SpoilageData(
                new ArrayList<>(data.freshExpirations()),
                new ArrayList<>(data.staleExpirations()),
                data.rottenCount(),
                data.speedMultiplier()
        );
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(currentData, 1);
        SpoilageData remaining = split[0];

        assertEquals(0, remaining.rottenCount(),
                "Eating 1 from 3-item stack should remove the rotten item first");
        assertEquals(1, remaining.freshExpirations().size());
        assertEquals(1, remaining.staleExpirations().size());
    }

    // ==================== BUG-08: base duration used in crafting transfer ====================

    @Test
    public void craftingTransferUsesBaseDurationNotSpeedMultiplied() {
        long now = 1000L;
        // Set speed multiplier to 10x
        SpoilageConfig config = SpoilageConfig.getInstance();
        double originalMultiplier = config.getSpoilageSpeedMultiplier();
        config.setSpoilageSpeedMultiplier(10.0);

        try {
            long foodFresh = config.getBaseFreshDurationForItem(FOOD);
            long crateFresh = config.getBaseFreshDurationForItem(CRATE);

            // Half-spent apple
            long halfSpent = now + foodFresh / 2;
            List<ItemStack> ingredients = List.of(
                    food(1, new SpoilageData(List.of(halfSpent), Collections.emptyList(), 0, 1.0))
            );

            CraftingSpoilageTransfer.Result result =
                    CraftingSpoilageTransfer.compute(ingredients, new ItemStack(CRATE, 1), now);

            assertNotNull(result);
            long remaining = result.data().freshExpirations().get(0) - now;
            // Should be based on base crate duration * 0.5, NOT speed-multiplied
            long expected = (long) (crateFresh * 0.5);
            assertEquals(expected, remaining, 2L,
                    "With speed multiplier 10x, crafting must use BASE duration, not speed-adjusted");
        } finally {
            config.setSpoilageSpeedMultiplier(originalMultiplier);
        }
    }

    // ==================== BUG-10: crate unpacking dispersion ====================

    @Test
    public void crateUnpackingPreservesDispersion() {
        long now = 1000L;
        long crateFresh = SpoilageConfig.getInstance().getFreshDurationForItem(CRATE);
        // A crate with a single expiration (compressed from 9 items)
        ItemStack crate = new ItemStack(CRATE, 1);
        crate.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(now + (long) (crateFresh * 0.75)), Collections.emptyList(), 0, 1.0));

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(grid(crate), new ItemStack(FOOD, 9), now);

        assertNotNull(result);
        assertEquals(9, result.data().freshExpirations().size());

        // Items should NOT all have the same expiration (dispersion preserved)
        long first = result.data().freshExpirations().get(0);
        boolean hasVariation = false;
        for (int i = 1; i < result.data().freshExpirations().size(); i++) {
            if (result.data().freshExpirations().get(i) != first) {
                hasVariation = true;
                break;
            }
        }
        assertTrue(hasVariation,
                "Unpacking a crate should produce items with varying expirations (dispersion)");
    }

    // ==================== BUG-11: null-data vs FRESH distinction ====================

    @Test
    public void getWorstStateDistinguishesNullDataFromFresh() {
        // Item with no spoilage component at all
        ItemStack noData = new ItemStack(FOOD);
        // Item with explicit empty data
        ItemStack emptyData = new ItemStack(FOOD);
        emptyData.set(ModDataComponentTypes.SPOILAGE, SpoilageData.DEFAULT);

        // Both should return FRESH, but the distinction matters for the caller
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(noData),
                "Item without component should be treated as FRESH");
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getWorstState(emptyData),
                "Item with empty data should be treated as FRESH");

        // The key check: has() returns false for noData, true for emptyData
        assertFalse(noData.has(ModDataComponentTypes.SPOILAGE),
                "Freshly created item must NOT have spoilage component");
        assertTrue(emptyData.has(ModDataComponentTypes.SPOILAGE),
                "Item with DEFAULT data must HAVE spoilage component");
    }

    // ==================== BUG-13: extractWorstItems with amount > totalTracked ====================

    @Test
    public void extractWorstItemsHandlesAmountGreaterThanTracked() {
        long now = 1000L;
        // Stack with only 3 tracked items (1 fresh, 1 stale, 1 rotten)
        SpoilageData data = new SpoilageData(
                List.of(now + 10000L), // 1 fresh
                List.of(now + 500L),   // 1 stale
                1,                      // 1 rotten
                1.0
        );

        // Try to extract 5 items (more than the 3 tracked)
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 5);

        SpoilageData remaining = split[0];
        SpoilageData extracted = split[1];

        // The extracted data should have exactly 5 items (3 tracked + 2 padding)
        int extractedTotal = extracted.freshExpirations().size()
                + extracted.staleExpirations().size()
                + extracted.rottenCount();
        assertEquals(5, extractedTotal,
                "extractWorstItems must return exactly 'amount' items, padding with fresh when needed");

        // The remaining data should have 0 items (all 3 were extracted)
        int remainingTotal = remaining.freshExpirations().size()
                + remaining.staleExpirations().size()
                + remaining.rottenCount();
        assertEquals(0, remainingTotal,
                "After extracting more than tracked, remaining should be empty");
    }

    @Test
    public void extractBestItemsHandlesAmountGreaterThanTracked() {
        long now = 1000L;
        // Stack with only 3 tracked items
        SpoilageData data = new SpoilageData(
                List.of(now + 10000L), // 1 fresh
                List.of(now + 500L),   // 1 stale
                1,                      // 1 rotten
                1.0
        );

        // Try to extract 5 items (more than the 3 tracked)
        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(data, 5);

        SpoilageData extracted = split[1];
        int extractedTotal = extracted.freshExpirations().size()
                + extracted.staleExpirations().size()
                + extracted.rottenCount();
        assertEquals(5, extractedTotal,
                "extractBestItems must return exactly 'amount' items, padding with fresh when needed");
    }

    // ==================== BUG-14: rescaleItemTimestamps with Long.MAX_VALUE padding ====================

    @Test
    public void rescaleWithMaxValuePaddingDoesNotOverflow() {
        long now = 1000L;
        // Stack with a Long.MAX_VALUE padded entry (from BUG-13 fix when amount > totalTracked)
        SpoilageData data = new SpoilageData(
                List.of(Long.MAX_VALUE), // padded entry
                List.of(now + 500L),     // 1 stale
                0,
                1.0
        );

        // Speed multiplier halves (ratio = 2.0) — this MUST NOT overflow into a negative/near-future value
        SpoilageData rescaled = FoodSpoilageUtil.rescaleItemTimestamps(
                data, now, 2.0, 0.5);

        // The padded entry should remain "essentially never expires"
        // (rescaling should not corrupt it into a near-future expiration)
        long paddedAfter = rescaled.freshExpirations().get(0);
        assertEquals(Long.MAX_VALUE, paddedAfter,
                "Long.MAX_VALUE padded entry must remain Long.MAX_VALUE after rescaling");
    }

    @Test
    public void rescaleWithInfinityRatioDoesNotOverflow() {
        long now = 1000L;
        SpoilageData data = new SpoilageData(
                List.of(now + 5000L), // 1 fresh
                List.of(), 0, 1.0
        );

        // ratio = Infinity (config multiplier = 0.0, item multiplier = 1.0)
        SpoilageData rescaled = FoodSpoilageUtil.rescaleItemTimestamps(
                data, now, Double.POSITIVE_INFINITY, 0.0);

        // The fresh entry should NOT overflow to negative; it should be clamped to NEVER
        long freshAfter = rescaled.freshExpirations().get(0);
        assertEquals(Long.MAX_VALUE, freshAfter,
                "Infinity ratio must not overflow fresh entry into negative; got " + freshAfter);
    }
}
